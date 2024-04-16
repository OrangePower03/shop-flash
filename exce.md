# 项目经验

##项目错误

1. 在spring cloud 2020后移除了ribbon负载均衡的依赖后不会再默认的走负载均衡了，网关那可能会报错，需要添加上spring的loadbalance依赖才行，并且要注意并不是只要网关加而已，每一个服务都得加。否则后端不会报错，但浏览器会显示503




## 理论错误

1. Maven无法下载依赖，爆红。

   若是在公司中可能是因为要下载的依赖是公司内部的依赖，需要连接公司的仓库才可，其中公司的仓库会连阿里的镜像仓库。

   也有可能是在第一次下载时因为网络等因素导致终止下载，Maven会有一个`xxx.jar.lastUpdated`或者`xxx.pom.lastUpdated`文件是用来解决依赖重复下载的问题的，若是有这个文件了就不会再帮你下载了，需要你将其删掉




## 新知识点

###Spring Cache

用于解决每次都要手动书写Redis缓存代码的问题，只需要一个注解就可以实现先从Redis中查询，没有再查数据库，最后将数据缓存在Redis中并返回

操作是引入依赖，配置一个配置类，在service类上书写查询sql的语句即可，最后在方法上加上一个注解。但这个会导致数据不一致的问题，改了数据库但没改Redis，解决这个方法的也有对应的注解可以实现，其中有一个双写的注解和一个清除的注解，前者适合用在新增操作，后者适合用在删改操作



### 参数解析器

参数解析器，需要实现以上接口来覆盖一些方法来处理指定的参数，其中，在调用到controller之前会调用到这些解析器扫描这些参数是否符合条件。用法如下

```java
public class UserInfoMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 这个是判断controller中哪个参数是需要自动注入的，可以加上一些自己的注解
        return true;
    }

    @Override
    // 这里是返回这个参数的应该被解析并填充的值
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String token = webRequest.getHeader(CommonConstants.TOKEN_NAME);
        return JSON.parseObject(redisTemplate.opsForValue()
                   .get(CommonRedisKey.USER_TOKEN.getRealKey(token)),
                        UserInfo.class);
    }
}

```

接着注册到`WebMvcConfigurer`里

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Bean
    public UserInfoMethodArgumentResolver userInfoMethodArgumentResolver() {
        return new UserInfoMethodArgumentResolver();
    }

    // 加入参数解析器
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userInfoMethodArgumentResolver());
    }
}
```



### 锁的续期

看门狗实现锁续期，使用JDK内置的定时任务来实现

![](https://cq-note.oss-cn-hangzhou.aliyuncs.com/202404061559386.png)

代码实现，任务的提交，`watchDogScheduledExecutor`这个是定时任务的线程池对象，自己配置的

```java
Future<?> watchDog = watchDogScheduledExecutor.scheduleAtFixedRate(
        () -> {
            log.info("锁正在续期...");
            // 查询key是否存在，存在表示业务没执行完
            String value = redisTemplate.opsForValue().get(lockKey);
            if(threadId.equals(value)) {
                redisTemplate.expire(lockKey, (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),TimeUnit.SECONDS);
            }
            // 不存在key，业务已经执行完毕，这部分的代码不用在这写，应该在业务完成的地方
        }
        ,
        (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),
        (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),
        TimeUnit.SECONDS
);
```

销毁，要注意的是，如果看门狗是一个线程，那么可以采用休眠来控制续期，销毁采用`interrupt`来将线程打断，但这样会报警告，所以采用了定时任务线程池来掐表续期，在这样下是不能调用线程中断来取消看门狗的，因为任务还在将线程中断是没有任何意义的，应该将任务给停止掉，以此来避免看门狗无休止的续期消耗资源

```java
if(threadId.equals(value)) {
    if(watchDog != null)
        watchDog.cancel(true);
    redisTemplate.delete(lockKey);
}
```





### 红锁

用于解决在Redis分布式集群状态下的锁的一致性问题，大致的理念就是在奇数的Redis主从复制服务器中，加锁需要在规定时间内加锁成功一半以上才算成功，否则算加锁失败，以此来解决加锁时主节点宕机而导致从节点未保存有主节点的锁进而导致多锁并发



### Servlet异步

在Servlet3.0后推出的异步请求模式，大体的概念是在原先的模型下加上了一个线程池用于处理用户的业务。当请求到达Tomcat时不再由Tomcat来把持业务的从头到尾所有过程了，而是交给这个线程池来处理业务，Tomcat的线程池用于处理请求的接收和请求的返回。以此来提高Tomcat的请求处理效率，但对单个用户的体验是没用提高的，只是多加了线程来处理更多的请求而已。用法有很多种，集成SpringBoot后可以直接将Controller接口的返回值改为`Callable<Result>`就可以了，细的就不过多讲





## 秒杀

性能优化点

![](https://cq-note.oss-cn-hangzhou.aliyuncs.com/202404131452161.png)

1. 设定本地商品售罄标识，减少redis访问量
2. 预先热加载商品数据
3. 使用redis做重复下单标识
4. 库存预扣减，将库存同步到redis中，实现高并发库存预扣减，减少进入库存扣减请求数
5. 秒杀服务和库存扣减服务进行分离，中间使用mq来做异步下单




### 遇到的问题

1. 本地标识，分布式环境下商品数量的回滚，需要借助mq发送广播消息来删除回滚的商品
2. 异步下单，无法直接返回下单结果，需要使用websocket来发送下单结果
3. 超时订单回滚，需要借助mq的定时消息来实现分布式下的数据回滚
4. 回滚数据，需要删除用户下单标识、mysql库存+1，redis预库存不能直接+1，要判断是否为负数，从查到改的过程需要加锁




## 支付宝

支付宝开放平台中的[沙箱](https://www.bilibili.com/video/BV1du411E7dF?p=66&vd_source=8b22808cb79705e8fbb030f4a374d1d0)模拟支付，可以实时的模拟商家和客户支付的场景，支持扫脸指纹等支付，也支持APP、电脑网页端等支付。

在沙箱控制台上会有需要填写的一些信息，比如接口加密方式、应用网关地址、授权回调地址、接口内容加密方式等。其中网关地址需要外网的地址，而我们测试环境下是一个内网的环境，这会导致无法接收到支付宝发来的信息，可以用内网穿透的方式来解决

RSA加密，公钥私钥加密，公钥加密私钥解密是数据加密的过程，可以保证数据不被观测，但无法保证数据不被篡改或者替换。比如你我都要支付宝的公钥，那么我把数据加密都发到支付宝的过程中被你拦截了并通过你的公钥伪造了一份数据发到支付宝，支付宝不会区分数据加密使用的公钥。私钥加密公钥解密是数据签名的过程，可以保证数据不被篡改但无法保证数据不被观测，因为私钥加密并不是将数据加密了，而是将数据加密后生成的一段签名放在原数据后面给支付宝端拿我的公钥解密签名验证





###支付流程

在用户确认支付后支付宝端会返回一个get请求到我们的系统，即下图的第6步的，所以我们需要提供这个接口，这个接口的作用是跳转到我们系统的一个界面，用来告诉用户订单支付成功的，但不会改后台数据的状态。真正会改后台状态的是第7步，这个过程是异步的，一般在第6步之前就返回到我们的系统了。若是支付宝返回了支付错误的状态，可以通过访问支付宝提供的一个接口来获取正在的支付状态，这个就是第八步。

![](https://cq-note.oss-cn-hangzhou.aliyuncs.com/202404132021765.png)

将其归到项目中需要做的流程如下

1. 我们需要编写对接支付宝平台的代码，调用支付宝提供的SDK来发起支付请求，支付宝会返回一个字符串，我们只需要将这个字符串返回给前端就可以完成页面的跳转。
2. 接下来就是用户支付操作，不需要我们编写代码。
3. 当用户支付完成后支付宝会调用我们预先提供的异步调用的接口来进行数据的存储，其中这个异步回调需要校验支付宝传来的数据的正确性，可以使用支付宝SDK的一个校验的工具完成。当执行完异步回调后支付宝需要收到一个`success`的字符串作为支付成功的标志，否则支付宝会重调接口。
4. 与此同时支付宝会再调用一个接口来对用户界面进行跳转，这个接口只需要校验数据正确性后就可以对界面进行跳转了，至此支付流程结束

![](https://cq-note.oss-cn-hangzhou.aliyuncs.com/202404132330319.png)



### 支付实现

在[支付宝开放平台官网](https://opendocs.alipay.com/open/270/105899?pathHash=d57664bf&ref=api)上有开发文档，但讲的有点散，可以根据[上面的视频](#支付)来学习，这里大致讲一下代码实现的要求。

1. 先配置了沙箱中的数据，比如公钥的配置，这个可以用支付宝提供的小工具来生成自定义的密钥，可以根据[文档](https://opendocs.alipay.com/common/02kipl)来下载和获取。接着需要配置好一个外网地址来接收支付宝的回调信息，如果没有就无法获取到支付宝的回调信息，可以利用内网穿透来解决这个问题

   ![](https://cq-note.oss-cn-hangzhou.aliyuncs.com/202404141348538.png)

2. 接着编写代码，需要有一个配置项。由于项目是一个分布式的项目，所以可以将这些配置项都移植到nacos中配置，appId、你的私钥、支付宝公钥、支付宝网关都是可以在官网获得的，签名方式采用官网中默认的RSA2，字符编码utf-8，两个回调地址就是支付服务的两个接口，并且要传递给支付宝的

   ```java
   @Data
   @Component
   @ConfigurationProperties(prefix = "alipay")
   public class AlipayProperties {
       //商品应用ID
       private String appId;
       // 商户私钥，您的PKCS8格式RSA2私钥
       private String merchantPrivateKey;
       // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
       private String alipayPublicKey;
       // 签名方式
       private String signType ;
       // 字符编码格式
       private String charset;
       // 支付宝网关
       private String gatewayUrl ;
       // 同步回调地址
       private String returnUrl;
       // 异步接收地址
       private String notifyUrl;
   }
   ```

3. 将这些参数交给支付宝的SDK管理

   ```java
   @Configuration
   public class AlipayConfig {
       @Bean
       public AlipayClient alipayClient(AlipayProperties alipayProperties){
           return new DefaultAlipayClient(
             alipayProperties.getGatewayUrl(),
             alipayProperties.getAppId(), 
             alipayProperties.getMerchantPrivateKey(),
             "json", 
             alipayProperties.getCharset(), 
             alipayProperties.getAlipayPublicKey(),
             alipayProperties.getSignType());
       }
   }
   ```

4. 接着编写和支付宝对接的一个接口，这个接口会完成支付流程，这个在[官网](https://opendocs.alipay.com/open/270/105899?pathHash=d57664bf&ref=api)上也有代码示例

   ```java
   @PostMapping("/prepay")
   public Result<String> doPay(@RequestBody PayVo pay) {
       // 向支付宝发起支付请求
       // 创建一个支付请求的SDK对象
       AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
       
       /*必传参数*/
       JSONObject bizContent = new JSONObject();
       //商户订单号，商家自定义，保持唯一性
       bizContent.put("out_trade_no", pay.getOutTradeNo());
       //支付金额，最小值0.01元
       bizContent.put("total_amount", pay.getTotalAmount());
       //订单标题，不可使用特殊符号
       bizContent.put("subject", pay.getSubject());
       bizContent.put("body", pay.getBody());
       //电脑网站支付场景固定传值FAST_INSTANT_TRADE_PAY
       bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

       request.setBizContent(bizContent.toString());
       //异步接收地址，仅支持http/https，公网可访问
       request.setNotifyUrl(alipayProperties.getNotifyUrl());
       //同步跳转地址，仅支持http/https
       request.setReturnUrl(alipayProperties.getReturnUrl());

       try {
           // 支付
           AlipayTradePagePayResponse response = 
             alipayClient.pageExecute(request, "POST");
           // 支付返回的响应，是一串html代码，直接将其返回前端会跳转支付宝的支付界面
           String from = response.getBody();
           if (response.isSuccess()) {
               log.info("[支付宝支付]，返回的表单信息：{}", from);
               return Result.success(from);
           }
           else {
               String msg= response.getMsg();
               log.info("[支付宝支付]，出现异常，信息：{}", msg);
               return Result.error(new PayCodeMsg(503, msg));
           }
       }
       catch(AlipayApiException e) {
           e.printStackTrace();
           return Result.error(PayCodeMsg.PAY_FAILED);
       }
   }
   ```

5. 执行到这就到支付环节被支付宝方回调了，这一步[官网](https://opendocs.alipay.com/open/270/105902?pathHash=d5cd617e&ref=api)也有说明。需要注意的是

   * 支付宝异步回调这个接口是POST请求，在异步通知交互时支付宝收到的返回不是`success`会认为通知失败并会重发。这个会导致幂等性，若是支付宝重复返回请求会导致系统会处理两次，若是支付成功有业务的累加，比如积分的累加会重复加两次
   * 调用内部服务前需要先验证支付宝发送的数据是否正确，那就少不了校验的过程，其中校验不仅在异步回调中使用，在同步调用中也会使用，抽取出来，校验过程在官网中要求是将请求中的所有数据加入一个map中传递给校验的API

   校验代码实现

   ```java
   boolean verify(HttpServletRequest request) {
       // 将所有的参数都收集到map中等待验证
       Map<String,String> params=new HashMap<>();
       request.getParameterMap().forEach((k, v) -> {
           if(v.length == 1) {
               params.put(k, v[0]);
           }
           else {
               StringBuilder value = new StringBuilder();
               for (String s : v) {
                   value.append(s).append(",");
               }
               params.put(k, value.substring(0, value.length() - 1));
           }
       });
       log.info("[支付宝异步回调] 回调的参数：{}", params);
       try {
           // 验证数据正确性
           return AlipaySignature.rsaCheckV1(params,      // map
                   alipayProperties.getAlipayPublicKey(), // 支付宝给的公钥
                   alipayProperties.getCharset(),         // 使用的字符集，utf-8
                   alipayProperties.getSignType());       // 使用的加密算法，RSA2
       }
       catch(AlipayApiException e) {
           e.printStackTrace();
           return false;
       }
   }
   ```

   ​

   异步回调代码实现如下

   ```java
   @PostMapping("/notify_url")
   public String notifyUrl(HttpServletRequest request) {
       boolean verified = verify(request);
       if(!verified) return "fail";
       // 我们系统的订单号
       String outTradeNo = request.getParameter("out_trade_no");
       // 支付宝交易的流水号
       String tradeNo = request.getParameter("trade_no");
       // 金额
       String totalAmount = request.getParameter("total_amount");
       // 交易状态
       String tradeStatus = request.getParameter("trade_status");
       // 检查交易状态
       if("TRADE_SUCCESS".equals(tradeStatus)) {
           log.info("[支付宝异步回调] 订单支付成功，订单号：{}", outTradeNo);
           // 调用秒杀服务来更新订单状态
           Result<?> result = seckillFeignService.paySuccess(new PayResultVo(outTradeNo, totalAmount, tradeNo));
           AssertUtils.isTrue(!result.hasError(), "更新订单支付成功失败");
       }
       else {
           log.info("[支付宝异步回调] 订单交易状态异常，订单号{}", outTradeNo);
       }
       return "success";
   }
   ```

   同步回调代码的调用就只用校验即可返回数据给前端，传回来的数据是没有支付状态的，要注意，在这里是不用判断支付状态的

   ​




### 退款

对于[退款](https://opendocs.alipay.com/open/f60979b3_alipay.trade.refund?scene=common&pathHash=e4c921a7)官方也有对应的文档，需要传的参数和退款成功的响应等数据都写明，这里大致描述流程。

系统接收到退款的请求，（这里不考虑退款的时间等业务）会直接在秒杀服务中校验，通过后会发送请求到支付服务中，支付服务负责对接支付宝退款接口。

1. 退款必须需要的参数有(商户的订单号`out_trade_no`/支付宝流水号`trade_no`)、退款金额`refund_amount`两个，其余参数可查阅官网。请求发起成功会返回一个`code = 10000`，退款成功会有一个`fund_change=Y`的标识

   ```java
   @PostMapping("/refund")
   Result<String> alipayRefund(@RequestBody RefundVo refund) {
       // 对接支付宝退款
       AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
       JSONObject bizContent = new JSONObject();
       bizContent.put("refund_amount", refund.getRefundAmount()); // 金额
       bizContent.put("out_trade_no", refund.getOutTradeNo());  // 订单号
       bizContent.put("refund_reason", refund.getRefundReason()); // 原因

       request.setBizContent(bizContent.toString());
       try {
           AlipayTradeRefundResponse response = alipayClient.execute(request);
           AssertUtils.isTrue(response.isSuccess(), response.getSubMsg());
           log.info("[支付宝退款] 收到支付宝发送的退款消息，订单号：{}",
                    refund.getOutTradeNo());
           // 判断支付退款是否成功
           if("Y".equals(response.getFundChange())) {
               return Result.success("退款成功");
           }
           else {
               AlipayTradeFastpayRefundQueryResponse refundQueryResponse =
                 refundQuery(refund.getOutTradeNo());
               if("10000".equals(refundQueryResponse.getCode()) &&
                  "REFUND_SUCCESS".equals(refundQueryResponse.getRefundStatus()))                 {
               	return Result.success("退款成功");
               }
           }
       }
       catch(AlipayApiException e) {
           throw new RuntimeException(e);
       }
       return Result.error(PayCodeMsg.REFUND_FAILED);
   }
   ```

   ​

2. 如果以上有一个不满足，可能是响应时网络因素导致错误，官方也做了准备，可以调用官方的一个查询接口查看订单是否退款完成，参数需要有商户订单号`out_trade_no`、退款请求号`out_request_no`，这个如果在第1步并未传递那么就会被标志为商户订单号。当请求发送成功会返回`code=10000`，退款成功会让`refund_status=REFUND_SUCCESS`

   ```java
   AlipayTradeFastpayRefundQueryResponse refundQuery(String orderNo) {
       AlipayTradeFastpayRefundQueryRequest request = 
         new AlipayTradeFastpayRefundQueryRequest();
       JSONObject bizContent = new JSONObject();
       bizContent.put("out_request_no", orderNo);
       bizContent.put("out_trade_no", orderNo);
       request.setBizContent(bizContent.toString());
       try {
           return alipayClient.execute(request);
       } catch (AlipayApiException e) {
           throw new RuntimeException(e);
       }
   }
   ```

   ​






##无法解决问题

1. nacos配置时不能配置path，剥离了`/nacos`会导致无法读取配置，控制台会正常输出配置监听状态但无法正常的从远端推送新数据，怀疑是无法监听到对应的配置

