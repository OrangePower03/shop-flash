package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("seckill-service")
public interface SeckillFeignService {
    @PostMapping("/orderPay/success")
    Result<?> paySuccess(@RequestBody PayResultVo payResult);
}
