package cn.wolfcode.web.controller;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResultVo;
import cn.wolfcode.service.IOrderInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Map;


@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {
    @Autowired
    private IOrderInfoService orderInfoService;

    @RequireLogin
    @GetMapping("/refund")
    public Result<String> refund(String orderNo, @RequestUser UserInfo userInfo, @RequestHeader String token) {
        return Result.success(orderInfoService.refund(orderNo, userInfo.getPhone(), token));
    }


    @GetMapping("/pay")
    public Result<String> pay(String orderNo, int type /*支付方式*/) {
        // 判断支付方式
        if(OrderInfo.PAY_TYPE_ONLINE == type) {
            return Result.success(orderInfoService.onlinePay(orderNo));
        }
        return null;
    }

    @PostMapping("/success")
    public Result<?> paySuccess(@RequestBody PayResultVo payResult) {
        orderInfoService.alipaySuccess(payResult);
        return Result.success();
    }
}
