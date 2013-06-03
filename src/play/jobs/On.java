package play.jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 此Annotation用来写Quartz的Cron表达式
 * 例如：@On("0 0 12 * * ?")
 * 
 * Run the job using a Cron expression
 * We use the Quartz CRON trigger (http://www.opensymphony.com/quartz/wikidocs/CronTriggers%20Tutorial.html)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface On {
    String value();
}
