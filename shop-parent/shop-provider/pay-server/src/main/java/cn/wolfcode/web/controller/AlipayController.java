package cn.wolfcode.web.controller;

import cn.wolfcode.common.utils.AssertUtils;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.PayResultVo;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.SeckillFeignService;
import cn.wolfcode.web.msg.PayCodeMsg;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties alipayProperties;
    @Autowired
    private SeckillFeignService seckillFeignService;

    // 支付宝的同步回调接口，地址可以自己指定，但需要将这个地址传递给支付宝
    @GetMapping("/return_url")
    public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean verified = verify(request);
        log.info("[支付宝同步回调] 正在执行同步回调");
        AssertUtils.isTrue(verified, "支付宝同步回调验证失败");
        String url = "http://localhost/order_detail.html?orderNo=" + request.getParameter("out_trade_no");
        log.info("[支付宝支付] 支付跳转地址：{}", url);
        response.sendRedirect(url);
    }

    // 支付宝的异步回调接口，地址也要传递给支付宝
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
            Result<?> result = seckillFeignService.paySuccess(new PayResultVo(outTradeNo, totalAmount, tradeNo));
            AssertUtils.isTrue(!result.hasError(), "更新订单支付成功失败");
        }
        else {
            log.info("[支付宝异步回调] 订单交易状态异常，订单号{}", outTradeNo);
        }
        return "success";
    }

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
            log.info("[支付宝退款] 收到支付宝发送的退款消息，订单号：{}", refund.getOutTradeNo());
            // 判断支付退款是否成功
            if("Y".equalsIgnoreCase(response.getFundChange())) {
                return Result.success("退款成功");
            }
            else {
                AlipayTradeFastpayRefundQueryResponse refundQueryResponse = refundQuery(refund.getOutTradeNo());
                if("10000".equals(refundQueryResponse.getCode()) && "REFUND_SUCCESS".equalsIgnoreCase(refundQueryResponse.getRefundStatus())) {
                    return Result.success("退款成功");
                }
            }
        }
        catch(AlipayApiException e) {
            throw new RuntimeException(e);
        }
        return Result.error(PayCodeMsg.REFUND_FAILED);
    }

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
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "POST");
            // 支付返回的响应，是一串前端的html代码，直接将其返回前端会跳转支付宝的支付界面
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

    private boolean verify(HttpServletRequest request) {
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
            return AlipaySignature.rsaCheckV1(params,
                    alipayProperties.getAlipayPublicKey(),
                    alipayProperties.getCharset(), alipayProperties.getSignType());
        }
        catch(AlipayApiException e) {
            e.printStackTrace();
            return false;
        }
    }

    private AlipayTradeFastpayRefundQueryResponse refundQuery(String orderNo) {
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
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
}
