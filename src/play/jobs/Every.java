package play.jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 设置任务执行间隔，支持天(d)，小时(h)，分(m[i]n)，秒(s)
 * Run a job at specified intervale
 * Example, @Every("1h")
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Every {
    String value();
}
