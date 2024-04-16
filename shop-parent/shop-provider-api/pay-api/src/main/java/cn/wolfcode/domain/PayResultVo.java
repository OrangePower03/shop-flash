package cn.wolfcode.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayResultVo implements Serializable {
    private String orderNo; // 订单号
    private String totalAmount; // 订单金额
    private String tradeNo; // 支付宝流水号
}
