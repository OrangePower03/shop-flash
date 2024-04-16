package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "pay-service", path = "/alipay")
public interface PaymentFeignApi {
    @PostMapping("/prepay")
    Result<String> doPay(@RequestBody PayVo pay);

    @PostMapping("/refund")
    Result<String> alipayRefund(@RequestBody RefundVo refund);
}
