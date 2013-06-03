package play.jobs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.libs.Expression;
import play.libs.Time;
import play.libs.Time.CronExpression;
import play.utils.Java;
import play.utils.PThreadFactory;

/**
 * Job插件，实现PlayPlugin，在play.plugins中配置是否开启JobsPlugin插件
 *
 */
public class JobsPlugin extends PlayPlugin {

	// 定义线程池对象
    public static ScheduledThreadPoolExecutor executor = null;
    // 存放任务列表
    public static List<Job> scheduledJobs = null;

    /**
     * 输出插件的状态信息，无需太细研究
     */
    @Override
    public String getStatus() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        // 还未初始化完成
        if (executor == null) {
            out.println("Jobs execution pool:");
            out.println("~~~~~~~~~~~~~~~~~~~");
            out.println("(not yet started)");
            return sw.toString();
        }
        // 初始化完成之后打印信息
        out.println("Jobs execution pool:");
        out.println("~~~~~~~~~~~~~~~~~~~");
        out.println("Pool size: " + executor.getPoolSize());
        out.println("Active count: " + executor.getActiveCount());
        out.println("Scheduled task count: " + executor.getTaskCount());
        out.println("Queue size: " + executor.getQueue().size());
        SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        if (!scheduledJobs.isEmpty()) {
        	//执行列表不为空
            out.println();
            out.println("Scheduled jobs ("+scheduledJobs.size()+"):");
            out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~");
            for (Job job : scheduledJobs) {
                out.print(job.getClass().getName());
                if (job.getClass().isAnnotationPresent(OnApplicationStart.class)) {
                    OnApplicationStart appStartAnnotation = job.getClass().getAnnotation(OnApplicationStart.class);
                    out.print(" run at application start" + (appStartAnnotation.async()?" (async)" : "") + ".");
                }

                if( job.getClass().isAnnotationPresent(On.class)) {

                    String cron = job.getClass().getAnnotation(On.class).value();
                    if (cron != null && cron.startsWith("cron.")) {
                        cron = Play.configuration.getProperty(cron);
                    }
                    out.print(" run with cron expression " + cron + ".");
                }
                if (job.getClass().isAnnotationPresent(Every.class)) {
                    out.print(" run every " + job.getClass().getAnnotation(Every.class).value() + ".");
                }
                if (job.lastRun > 0) {
                    out.print(" (last run at " + df.format(new Date(job.lastRun)));
                    if(job.wasError) {
                        out.print(" with error)");
                    } else {
                        out.print(")");
                    }
                } else {
                    out.print(" (has never run)");
                }
                out.println();
            }
        }
        if (!executor.getQueue().isEmpty()) {
            out.println();
            out.println("Waiting jobs:");
            out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            for (Object o : executor.getQueue()) {
                ScheduledFuture task = (ScheduledFuture)o;
                out.println(Java.extractUnderlyingCallable((FutureTask)task) + " will run in " + task.getDelay(TimeUnit.SECONDS) + " seconds");        
            }
        }
        return sw.toString();
    }


    /**
     * 初始化完成之后，开始执行
     */
    @Override
    public void afterApplicationStart() {
    	// 存放所有的Job类的Class
        List<Class<?>> jobs = new ArrayList<Class<?>>();
        // 从Play自定义的ApplicationClassloader中查找所有的加载的类
        for (Class clazz : Play.classloader.getAllClasses()) {
        	// 若当前类的父类是Job
            if (Job.class.isAssignableFrom(clazz)) {
                jobs.add(clazz);
            }
        }
        scheduledJobs = new ArrayList<Job>();
        // 循环所有的Job类的Class
        for (final Class<?> clazz : jobs) {
        	// 存在有@OnApplicationStart注解
            // @OnApplicationStart
            if (clazz.isAnnotationPresent(OnApplicationStart.class)) {
                //check if we're going to run the job sync or async
                OnApplicationStart appStartAnnotation = clazz.getAnnotation(OnApplicationStart.class);
                // 判断注解上async（异步）是否为false
                if( !appStartAnnotation.async()) {
                	// 同步
                    //run job sync
                    try {
                    	// 实例化Job，并法如执行列表
                        Job<?> job = ((Job<?>) clazz.newInstance());
                        scheduledJobs.add(job);
                        job.run(); // 注意这里，显示调用run方法，不是start，这里是当前线程调用，没有开启新线程
                        if(job.wasError) {
                        	// 出现错误，存在最后保存的错误直接抛出
                            if(job.lastException != null) {
                                throw job.lastException;
                            }
                            throw new RuntimeException("@OnApplicationStart Job has failed");
                        }
                    } catch (InstantiationException e) {
                        throw new UnexpectedException("Job could not be instantiated", e);
                    } catch (IllegalAccessException e) {
                        throw new UnexpectedException("Job could not be instantiated", e);
                    } catch (Throwable ex) {
                        if (ex instanceof PlayException) {
                            throw (PlayException) ex;
                        }
                        throw new UnexpectedException(ex);
                    }
                } else {
                	// 异步
                    //run job async
                    try {
                    	// 实例化Job对象并加入执行列表
                        Job<?> job = ((Job<?>) clazz.newInstance());
                        scheduledJobs.add(job);
                        //start running job now in the background
                        @SuppressWarnings("unchecked")
                        Callable<Job> callable = (Callable<Job>)job;
                        // 开始提交任务至新的线程，这里是有返回值的线程
                        executor.submit(callable);
                    } catch (InstantiationException ex) {
                        throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                    } catch (IllegalAccessException ex) {
                        throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                    }
                }
            }
            // 存在有@On注解
            // @On
            if (clazz.isAnnotationPresent(On.class)) {
                try {
                    Job<?> job = ((Job<?>) clazz.newInstance());
                    scheduledJobs.add(job);
                    // 将job交给scheduleForCRON处理cron表达式，这里解析cron表达式有Play自己实现，没有用到quartz第三方的包
                    scheduleForCRON(job); 
                } catch (InstantiationException ex) {
                    throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                } catch (IllegalAccessException ex) {
                    throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                }
            }
            // 存在有@Every注解
            // @Every
            if (clazz.isAnnotationPresent(Every.class)) {
                try {
                    Job job = (Job) clazz.newInstance();
                    scheduledJobs.add(job);
                    // 获取@Every注解上的值
                    String value = job.getClass().getAnnotation(Every.class).value();
                    // cron.开头的从配置文件中读取内容，例如 @Every("cron.oneHour"),在application.conf中配置对应 cron.oneHour=1h 即可
                    if (value.startsWith("cron.")) {
                        value = Play.configuration.getProperty(value);
                    }
                    // 这里其实只是想过滤写表达式的情况，比如 @Every("${cron.oneHour}") 会被替换成从 cron.oneHour 后去application.conf中查找值
                    value = Expression.evaluate(value, value).toString();
                    if(!"never".equalsIgnoreCase(value)){
                    	// 在固定延迟的时间后执行任务，参数说明 （任务，第一次多少时间后执行，随后每次多少时间执行，单位）
                        executor.scheduleWithFixedDelay(job, Time.parseDuration(value), Time.parseDuration(value), TimeUnit.SECONDS);
                    }
                } catch (InstantiationException ex) {
                    throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                } catch (IllegalAccessException ex) {
                    throw new UnexpectedException("Cannot instanciate Job " + clazz.getName());
                }
            }
        }
    }

    /**
     * 当容器启动的时候，做初始化操作，初始化线程池对象
     */
    @Override
    public void onApplicationStart() {
    	//从application.conf可查看默认线程数为10
        int core = Integer.parseInt(Play.configuration.getProperty("play.jobs.pool", "10")); 
        //默认10个线程，PThreadFactory类是扩展ThreadFactory类
        executor = new ScheduledThreadPoolExecutor(core, new PThreadFactory("jobs"), new ThreadPoolExecutor.AbortPolicy());
    }

    public static <V> void scheduleForCRON(Job<V> job) {
    	// 必须包含 @On注解
        if (!job.getClass().isAnnotationPresent(On.class)) {
            return;
        }
        // 获取表达式值
        String cron = job.getClass().getAnnotation(On.class).value();
        // 表达式存在配置文件中
        if (cron.startsWith("cron.")) {
            cron = Play.configuration.getProperty(cron);
        }
        // 存在 ${cron.xxx}的情况，过滤一下
        cron = Expression.evaluate(cron, cron).toString();
        // 为空直接输出错误日志
        if (cron == null || "".equals(cron) || "never".equalsIgnoreCase(cron)) {
            Logger.info("Skipping job %s, cron expression is not defined", job.getClass().getName());
            return;
        }
        try {
        	// TODO: 这里的逻辑还有些不清楚
            Date now = new Date();
            cron = Expression.evaluate(cron, cron).toString(); //TODO: 这里是否与上面重复
            // 获取到cron表达式
            CronExpression cronExp = new CronExpression(cron);
            // 获取到下一次执行的时间
            Date nextDate = cronExp.getNextValidTimeAfter(now);
            if (nextDate == null) {
                Logger.warn("The cron expression for job %s doesn't have any match in the future, will never be executed", job.getClass().getName());
                return;
            }
            // 防止重复任务执行
            if (nextDate.equals(job.nextPlannedExecution)) {
                // Bug #13: avoid running the job twice for the same time
                // (happens when we end up running the job a few minutes before the planned time)
                Date nextInvalid = cronExp.getNextInvalidTimeAfter(nextDate); //得到下一次执行的时间
                nextDate = cronExp.getNextValidTimeAfter(nextInvalid);
            }
            job.nextPlannedExecution = nextDate; 
            // 执行任务
            executor.schedule((Callable<V>)job, nextDate.getTime() - now.getTime(), TimeUnit.MILLISECONDS);
            job.executor = executor;
        } catch (Exception ex) {
            throw new UnexpectedException(ex);
        }
    }

    /**
     * 程序关闭时执行，具体什么叫程序关闭，有待研究，不知道是不是虚拟机关闭
     * TODO: 具体研究
     */
    @Override
    public void onApplicationStop() {
        
    	// TODO: 这里不太明白，从自定义classloader中拿到了所有的Job子类？
        List<Class> jobs = Play.classloader.getAssignableClasses(Job.class);
        
        for (final Class clazz : jobs) {
        	// 包含@OnApplicationStop注解
            // @OnApplicationStop
            if (clazz.isAnnotationPresent(OnApplicationStop.class)) {
                try {
                	// 获取任务加入执行列表
                    Job<?> job = ((Job<?>) clazz.newInstance());
                    scheduledJobs.add(job);
                    job.run(); //同步处理代码块
                    if (job.wasError) {
                    	// 异常处理
                        if (job.lastException != null) {
                            throw job.lastException;
                        }
                        throw new RuntimeException("@OnApplicationStop Job has failed");
                    }
                } catch (InstantiationException e) {
                    throw new UnexpectedException("Job could not be instantiated", e);
                } catch (IllegalAccessException e) {
                    throw new UnexpectedException("Job could not be instantiated", e);
                } catch (Throwable ex) {
                    if (ex instanceof PlayException) {
                        throw (PlayException) ex;
                    }
                    throw new UnexpectedException(ex);
                }
            }
        }
        // 关闭线程池
        executor.shutdownNow();
        // 清空线程池队列
        executor.getQueue().clear();
    }
}
