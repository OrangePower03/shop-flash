package cn.wolfcode.common.web.resolver;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.redis.CommonRedisKey;
import com.alibaba.fastjson.JSON;
import org.aspectj.weaver.ast.Var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class UserInfoMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 这个是判断controller中哪个参数是需要自动注入的
        return parameter.hasParameterAnnotation(RequestUser.class) &&
               parameter.getParameterType().equals(UserInfo.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        String token = webRequest.getHeader(CommonConstants.TOKEN_NAME);
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
}
