package cn.wolfcode.common.utils;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;

import java.util.Objects;

public class AssertUtils {
    public static void nonNull(Object obj, String msg) {
        isTrue(obj != null, msg);
    }

    public static void isTrue(Boolean ret, String msg) {
        isTrue(ret, new CodeMsg(501,msg));
    }

    public static void isTrue(Boolean ret, CodeMsg msg) {
        if(ret == null || !ret)
            throw new BusinessException(msg);
    }

    public static <T> void isEquals(T o1, T o2, String msg) {
        isEquals(o1, o2, new CodeMsg(501,msg));
    }

    public static <T> void isEquals(T o1, T o2, CodeMsg msg) {
        isTrue(Objects.equals(o1, o2), msg);
    }
}
