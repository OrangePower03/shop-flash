package cn.wolfcode.web.msg;
import cn.wolfcode.common.web.CodeMsg;

/**
 * Created by wolfcode
 */
public class PayCodeMsg extends CodeMsg {
    public static final PayCodeMsg PAY_FAILED = new PayCodeMsg(501, "支付宝内部错误，支付失败");
    public static final PayCodeMsg REFUND_FAILED = new PayCodeMsg(501, "支付宝内部错误，退款失败");

    public PayCodeMsg(Integer code, String msg){
        super(code,msg);
    }
}
