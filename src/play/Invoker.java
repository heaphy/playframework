package play;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import java.util.ArrayList;

import play.Play.Mode;
import play.classloading.enhancers.LocalvariablesNamesEnhancer.LocalVariablesNamesTracer;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.i18n.Lang;
import play.libs.F;
import play.libs.F.Promise;
import play.utils.PThreadFactory;

/**
 * Run some code in a Play! context
 */
public class Invoker {

    /**
     * Main executor for requests invocations.
     * 执行调用的线程池
     */
    public static ScheduledThreadPoolExecutor executor = null;

    /**
     * 执行一些调用在线程池中
     * Run the code in a new thread took from a thread pool.
     * @param invocation The code to run
     * @return The future object, to know when the task is completed
     */
    public static Future<?> invoke(final Invocation invocation) {
    	//性能检测的一个工具
        Monitor monitor = MonitorFactory.getMonitor("Invoker queue size", "elmts.");
        monitor.add(executor.getQueue().size());
        invocation.waitInQueue = MonitorFactory.start("Waiting for execution");
        return executor.submit(invocation);
    }

    /**
     * 执行一些调用在线程池中，并延时多少毫秒后执行
     * Run the code in a new thread after a delay
     * @param invocation The code to run
     * @param millis The time to wait before, in milliseconds 毫秒
     * @return The future object, to know when the task is completed
     */
    public static Future<?> invoke(final Invocation invocation, long millis) {
        Monitor monitor = MonitorFactory.getMonitor("Invocation queue", "elmts.");
        monitor.add(executor.getQueue().size());
        //创建并执行在给定延迟后启用的 ScheduledFuture。
        return executor.schedule(invocation, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Run the code in the same thread than caller.
     * @param invocation The code to run
     */
    public static void invokeInThread(DirectInvocation invocation) {
        boolean retry = true;
        while (retry) {
        	//注意这里，没有调用 start() 方法，而是run , 如果调用start()没法在线程执行完后去暂停和调用其他任务
            invocation.run();
            if (invocation.retry == null) {
                retry = false;
            } else {
                try {
                    if (invocation.retry.task != null) {
                        invocation.retry.task.get();
                    } else {
                        Thread.sleep(invocation.retry.timeout);
                    }
                } catch (Exception e) {
                    throw new UnexpectedException(e);
                }
                retry = true;
            }
        }
    }

    /**
     * 调用的上下文，用来保存执行方法的注解,例如，后面的插件可以获取到Controller的注解，做一些其它的操作 例如 play.db.jpa.JPAPlugin.beforeInvocation()
     * 这样的一个设计，比较方便获取到方法上的注解，便于插件根据注解来做一些操作
     * The class/method that will be invoked by the current operation
     */
    public static class InvocationContext {
    	// 通过ThreadLocal来安全获取当成线程的上下文
        public static ThreadLocal<InvocationContext> current = new ThreadLocal<InvocationContext>();
        // 执行方法的注解，应用场景例如：jpa ，根据是否拥有 Transactional 来做是否开启事物的操作
        private final List<Annotation> annotations;
        // 调用方法的类型,貌似只有 toString 方法用到啦
        private final String invocationType;

        public static InvocationContext current() {
            return current.get();
        }

        public InvocationContext(String invocationType) {
            this.invocationType = invocationType;
            this.annotations = new ArrayList<Annotation>();
        }

        public InvocationContext(String invocationType, List<Annotation> annotations) {
            this.invocationType = invocationType;
            this.annotations = annotations;
        }

        public InvocationContext(String invocationType, Annotation[] annotations) {
            this.invocationType = invocationType;
            this.annotations = Arrays.asList(annotations);
        }

        public InvocationContext(String invocationType, Annotation[]... annotations) {
            this.invocationType = invocationType;
            this.annotations = new ArrayList<Annotation>();
            for (Annotation[] some : annotations) {
                this.annotations.addAll(Arrays.asList(some));
            }
        }

        public List<Annotation> getAnnotations() {
            return annotations;
        }

        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getAnnotation(Class<T> clazz) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().isAssignableFrom(clazz)) {
                    return (T) annotation;
                }
            }
            return null;
        }

        public <T extends Annotation> boolean isAnnotationPresent(Class<T> clazz) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().isAssignableFrom(clazz)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the InvocationType for this invocation - Ie: A plugin can use this to
         * find out if it runs in the context of a background Job
         */
        public String getInvocationType() {
            return invocationType;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("InvocationType: ");
            builder.append(invocationType);
            builder.append(". annotations: ");
            for (Annotation annotation : annotations) {
                builder.append(annotation.toString()).append(",");
            }
            return builder.toString();
        }
    }

    /**
     * 运行一下调用在play的下
     * An Invocation in something to run in a Play! context
     */
    public static abstract class Invocation implements Runnable {

        /**
         * 性能的
         * If set, monitor the time the invocation waited in the queue
         */
        Monitor waitInQueue;

        /**
         * 子类需要覆盖这个类
         * Override this method
         * @throws java.lang.Exception
         */
        public abstract void execute() throws Exception;


        /**
         * 这是是用来清空语言的
         * Needs this method to do stuff *before* init() is executed.
         * The different Invocation-implementations does a lot of stuff in init()
         * and they might do it before calling super.init()
         */
        protected void preInit() {
            // clear language for this request - we're resolving it later when it is needed
            Lang.clear();
        }

        /**
         * Init the call (especially usefull in DEV mode to detect changes)
         */
        public boolean init() {
        	/*
        	 *  设置该线程的上下文 ClassLoader。上下文 ClassLoader 可以在创建线程设置，
        	 *  并允许创建者在加载类和资源时向该线程中运行的代码提供适当的类加载器。
        	 *  首先，如果有安全管理器，则通过 RuntimePermission("setContextClassLoader") 
        	 *  权限调用其 checkPermission 方法，查看是否可以设置上下文 ClassLoader。 
             */

            Thread.currentThread().setContextClassLoader(Play.classloader);
            //检测文件是否修改
            Play.detectChanges();
            if (!Play.started) {
                if (Play.mode == Mode.PROD) {
                    throw new UnexpectedException("Application is not started");
                }
                Play.start();
            }
            //设置当前的线程的运行上下文
            InvocationContext.current.set(getInvocationContext());
            return true;
        }

		/**
		 * 运行的上下文，由子类提供实现
		 * @return 运行的上下文
		 */
        public abstract InvocationContext getInvocationContext();

        /**
         * Things to do before an Invocation
         */
        public void before() {
            Thread.currentThread().setContextClassLoader(Play.classloader);
            Play.pluginCollection.beforeInvocation();
        }

        /**
         * Things to do after an Invocation.
         * (if the Invocation code has not thrown any exception)
         */
        public void after() {
            Play.pluginCollection.afterInvocation();
            LocalVariablesNamesTracer.checkEmpty(); // detect bugs ....
        }

        /**
         * Things to do when the whole invocation has succeeded (before + execute + after)
         */
        public void onSuccess() throws Exception {
            Play.pluginCollection.onInvocationSuccess();
        }

        /**
         * Things to do if the Invocation code thrown an exception
         */
        public void onException(Throwable e) {
            Play.pluginCollection.onInvocationException(e);
            if (e instanceof PlayException) {
                throw (PlayException) e;
            }
            throw new UnexpectedException(e);
        }

        /**
         * 在 suspendRequest.task 执行完后调用，或者延时 suspendRequest.timeout 毫秒后调用
         * The request is suspended
         * @param suspendRequest
         */
        public void suspend(Suspend suspendRequest) {
            if (suspendRequest.task != null) {
                WaitForTasksCompletion.waitFor(suspendRequest.task, this);
            } else {
                Invoker.invoke(this, suspendRequest.timeout);
            }
        }

        /**
         * Things to do in all cases after the invocation.
         */
        public void _finally() {
            Play.pluginCollection.invocationFinally();
            InvocationContext.current.remove();
        }

        /**
         * It's time to execute.
         */
        public void run() {
            if (waitInQueue != null) {
                waitInQueue.stop();
            }
            try {
                preInit();
                if (init()) {
                    before();
                    execute();
                    after();
                    onSuccess();
                }
            } catch (Suspend e) {
                suspend(e);
                after();
            } catch (Throwable e) {
                onException(e);
            } finally {
                _finally();
            }
        }
    }

    /**
     * 带有线程执行完后，可以暂停，执行一些方法
     * A direct invocation (in the same thread than caller)
     */
    public static abstract class DirectInvocation extends Invocation {

        public static final String invocationType = "DirectInvocation";

        Suspend retry = null;

        @Override
        public boolean init() {
            retry = null;
            return super.init();
        }

        @Override
        public void suspend(Suspend suspendRequest) {
            retry = suspendRequest;
        }

        @Override
        public InvocationContext getInvocationContext() {
            return new InvocationContext(invocationType);
        }
    }

    /**
     * Init executor at load time.
     */
    static {
        int core = Integer.parseInt(Play.configuration.getProperty("play.pool", Play.mode == Mode.DEV ? "1" : ((Runtime.getRuntime().availableProcessors() + 1) + "")));
        executor = new ScheduledThreadPoolExecutor(core, new PThreadFactory("play"), new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 暂停的异常,表示该请求必须暂停
     * Throwable to indicate that the request must be suspended
     */
    public static class Suspend extends PlayException {

        /**
         * 暂停多少毫秒
         * Suspend for a timeout (in milliseconds).
         */
        long timeout;
        
        /**
         * 需要等待执行的任务
         * Wait for task execution.
         */
        Future<?> task;

        public Suspend(long timeout) {
            this.timeout = timeout;
        }

        public Suspend(Future<?> task) {
            this.task = task;
        }

        @Override
        public String getErrorTitle() {
            return "Request is suspended";
        }

        @Override
        public String getErrorDescription() {
            if (task != null) {
                return "Wait for " + task;
            }
            return "Retry in " + timeout + " ms.";
        }
    }

    /**
     * 实用的跟踪任务完成，以恢复暂停的请求。
     * 在执行 Invocation 前，执行其他一些操作
     * Utility that track tasks completion in order to resume suspended requests.
     */
    static class WaitForTasksCompletion extends Thread {

        static WaitForTasksCompletion instance;
        //队列，其实可以用java里面消费者模式来实现，效果可能更佳
        Map<Future<?>, Invocation> queue;

        public WaitForTasksCompletion() {
            queue = new ConcurrentHashMap<Future<?>, Invocation>();
            setName("WaitForTasksCompletion");
            //标记为守护进程
            setDaemon(true);
        }

        public static <V> void waitFor(Future<V> task, final Invocation invocation) {
            if (task instanceof Promise) {
                Promise<V> smartFuture = (Promise<V>) task;
                smartFuture.onRedeem(new F.Action<F.Promise<V>>() {
                    public void invoke(Promise<V> result) {
                        executor.submit(invocation);
                    }
                });
            } else {
            	//加入类的锁,此处可以优化,使用内部类的方式来实现懒加载的方式创建 instance
                synchronized (WaitForTasksCompletion.class) {
                    if (instance == null) {
                        instance = new WaitForTasksCompletion();
                        Logger.warn("Start WaitForTasksCompletion");
                        instance.start();
                    }
                    instance.queue.put(task, invocation);
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                	//如果队列里面不为空
                    if (!queue.isEmpty()) {
                        for (Future<?> task : new HashSet<Future<?>>(queue.keySet())) {
                        	//task 是否执行完成
                            if (task.isDone()) {
                                //将 task 对于的 Invocation 提交到线程池
                            	executor.submit(queue.get(task));
                            	//将task 移除
                                queue.remove(task);
                            }
                        }
                    }
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Logger.warn(ex, "While waiting for task completions");
                }
            }
        }
    }
}
