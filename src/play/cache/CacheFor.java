package play.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cache an action's result.
 *
 * <p>If a time is not specified, the results will be cached for 1 hour by default.
 *
 * <p>Example: <code>@CacheFor("1h")</code>
 * 用于action做缓存,详见 
 * play.mvc.ActionInvoker.invoke(Request, Response)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheFor {
	//缓存的时间
    String value() default "1h";
    //作为缓存的key
    String id() default "";
    /**
     * action缓存可以，实现方式:
     *  cacheKey = actionMethod.getAnnotation(CacheFor.class).id();
     *  if ("".equals(cacheKey)) {
     *       cacheKey = "urlcache:" + request.url + request.querystring;
     *   }
     *   详见:play.mvc.ActionInvoker.invoke(Request, Response)
     */
}
