package play;

import java.lang.annotation.Annotation;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.RootParamNode;
import play.db.Model;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Router.Route;
import play.mvc.results.Result;
import play.templates.BaseTemplate;
import play.templates.Template;
import play.test.BaseTest;
import play.test.TestEngine.TestResults;
import play.vfs.VirtualFile;

/**
 * A framework plugin
 */
public abstract class PlayPlugin implements Comparable<PlayPlugin> {

    /**
     * plugin的优先级，按照play.plugins中配置的优先级执行(0为最大的优先级)
     * Plugin priority (0 for highest priority)
     */
    public int index;

    /**
     * 加载plugin方法，见play.plugins.PluginCollection.loadPlugins()(加载插件) -> 
     * play.plugins.PluginCollection.initializePlugin(PlayPlugin)(初始化插件)
     * Called at plugin loading
     */
    public void onLoad() {
    }

    /**
     * 是否编译源码,如果已经编译了源码，将不会再编译*.java文件,如果此时使用的是java文件，不是class，将会报错
     * @return
     */
    public boolean compileSources() {
        return false;
    }

    /**
     * Run a test class
     */
    public TestResults runTest(Class<BaseTest> clazz) {
        return null;
    }

    /**
     * Use method using RootParamNode instead
     * @return
     */
    @Deprecated
    public Object bind(String name, Class clazz, Type type, Annotation[] annotations, Map<String, String[]> params) {
        return null;
    }

    /**
     * 用于修改客户端请求的参数，会将方法的每一个参数与获取的结果都来调用一次,详见 play.mvc.ActionInvoker.getActionMethodArgs(Method, Object) 方法，例如：get 一个请求过来
     * 地址是 url/test?index=1 就会调用一次 bind 方法，参数name=test 此时如果返回一个结果 11 ，就能覆盖 test的原来的1变成了11
     * Called when play need to bind a Java object from HTTP params.
     *
     * When overriding this method, do not call super impl.. super impl is calling old bind method
     * to be backward compatible.
     */
    public Object bind( RootParamNode rootParamNode, String name, Class<?> clazz, Type type, Annotation[] annotations) {
        // call old method to be backward compatible
        return bind(name, clazz, type, annotations, rootParamNode.originalParams);
    }

    /**
     * Use bindBean instead
     */
    @Deprecated
    public Object bind(String name, Object o, Map<String, String[]> params) {
        return null;
    }

    /**
     * Called when play need to bind an existing Java object from HTTP params.
     * When overriding this method, DO NOT call the super method, since its default impl is to
     * call the old bind method to be backward compatible.
     */
    public Object bindBean(RootParamNode rootParamNode, String name, Object bean) {
        // call old method to be backward compatible.
        return bind(name, bean, rootParamNode.originalParams);
    }

    public Map<String, Object> unBind(Object src, String name) {
        return null;
    }
    
    /**
     * 通过locale获取国际化的信息,会根据优先级来获取，但是如果高优先级返回了，则低优先级无法调用
     * 详见 : play.plugins.PluginCollection.getMessage(String, Object, Object...)
     * 
     * Translate the given key for the given locale and arguments.
     * If null is returned, Play's normal message translation mechanism will be
     * used.
     */
    public String getMessage(String locale, Object key, Object... args) {
        return null;
    }

    /**
     * 通过访问，路径为 /@statu , header中包含authorization,并且值等于System.getProperty("statusKey", Play.secretKey),
     * statusKey 必须在启动参数中加入  -DstatusKey=yourkey
     * secretKey = configuration.getProperty("application.secret", "").trim();
     * 获取状态
     * Return the plugin status
     */
    public String getStatus() {
        return null;
    }

    /**
     * 详见 getStatus 唯一的区别是 response.contentType.equals("application/json")
     * Return the plugin status in JSON format
     */
    public JsonObject getJsonStatus() {
        return null;
    }

    /**
     * 用于给ApplicationClass增强，增加一些方法，可以查看play.db.jpa.JPAPlugin.enhance(ApplicationClass)
     * Enhance this class
     * @param applicationClass
     * @throws java.lang.Exception
     */
    public void enhance(ApplicationClass applicationClass) throws Exception {
    }

    /**
     * This hook is not plugged, don't implement it
     * @param template
     */
    @Deprecated
    public void onTemplateCompilation(Template template) {
    }

    /**
     * 可以修改请求的返回结果，例如
     * response.status = 302;
	   response.setHeader("Location", "http://www.baidu.com");
	   return true; 
	   将会跳转到baidu
     * Give a chance to this plugin to fully manage this request
     * @param request The Play request
     * @param response The Play response
     * @return true if this plugin has managed this request
     */
    public boolean rawInvocation(Request request, Response response) throws Exception {
        return false;
    }

    /**
     * 用于处理静态文件，必须在 routes 中配置 GET     /js/   staticDir:public/js 这种静态文件请求的资源
     * Let a chance to this plugin to manage a static resource
     * @param request The Play request
     * @param response The Play response
     * @return true if this plugin has managed this request
     */
    public boolean serveStatic(VirtualFile file, Request request, Response response) {
        return false;
    }

    /**
     * 在 detectChange前调用
     */
    public void beforeDetectingChanges() {
    }

    /**
     * 获取模板文件
     * @param file
     * @return
     */
    public Template loadTemplate(VirtualFile file) {
        return null;
    }

    /**
     * 开发模式时候，修改会引起应用重新加载，则会调用该方法，可以重新加载一些资源文件，图片等,例如 play.i18n.MessagesPlugin.detectChange()
     * It's time for the plugin to detect changes.
     * Throw an exception is the application must be reloaded.
     */
    public void detectChange() {
    }

    /**
     * 判断class文件是否修改，如果修改，则会重新加载java类
     * It's time for the plugin to detect changes.
     * Throw an exception is the application must be reloaded.
     */
    public boolean detectClassesChange() {
        return false;
    }

    /**
     * 在容器启动时候调用
     * Called at application start (and at each reloading)
     * Time to start stateful things.
     */
    public void onApplicationStart() {
    }

    /**
     * 在容器启动后调用
     * Called after the application start.
     */
    public void afterApplicationStart() {
    }

    /**
     * 在容器停止的时候调用
     * Called at application stop (and before each reloading)
     * Time to shutdown stateful things.
     */
    public void onApplicationStop() {
    }

    
    /**
     * 调用顺序
     * beforeInvocation > afterInvocation(如果异常 onInvocationException) > invocationFinally
     */
    
    /**
     * 在于一个请求前调用
     * Called before a Play! invocation.
     * Time to prepare request specific things.
     */
    public void beforeInvocation() {
    }

    /**
     * 在一个请求结束后调用
     * Called after an invocation.
     * (unless an excetion has been thrown).
     * Time to close request specific things.
     */
    public void afterInvocation() {
    }

    /**
     * 当action处理中出现异常时候做处理
     * Called if an exception occured during the invocation.
     * @param e The catched exception.
     */
    public void onInvocationException(Throwable e) {
    }

    /**
     * 在一次请求后调用，类似于finally，例子：play.db.DBPlugin.invocationFinally()
     * Called at the end of the invocation.
     * (even if an exception occured).
     * Time to close request specific things.
     */
    public void invocationFinally() {
    }

    
    /**
     * 调用时间 beforeActionInvocation > onActionInvocationResult > afterActionInvocation > onInvocationSuccess
     */
    
    /**
     * 在 Controller 方法之前调用
     * Called before an 'action' invocation,
     * ie an HTTP request processing.
     */
    public void beforeActionInvocation(Method actionMethod) {
    }

    /**
     * 执行晚 Controller 返回结果后调用，result，包含页面内容等
     * Called when the action method has thrown a result.
     * @param result The result object for the request.
     */
    public void onActionInvocationResult(Result result) {
    }

    /**
     * 请求成功后调用
     */
    public void onInvocationSuccess() {
    }
    
    /**
     * 在 Controller 方法调用完后执行
     * Called at the end of the action invocation.
     */
    public void afterActionInvocation() {
    }

    /**
     * Called when the request has been routed.
     * @param route The route selected.
     */
    public void onRequestRouting(Route route) {
    }


    /**
     * 在 application.conf 文件读取后执行
     * Called when the application.conf has been read.
     */
    public void onConfigurationRead() {
    }

    /**
     * 当 routes 文件加载后执行
     * Called after routes loading.
     */
    public void onRoutesLoaded() {
    }

    /** 
     * Event may be sent by plugins or other components
     * @param message convention: pluginClassShortName.message
     * @param context depends on the plugin
     */
    public void onEvent(String message, Object context) {
    }

    public List<ApplicationClass> onClassesChange(List<ApplicationClass> modified) {
        return new ArrayList<ApplicationClass>();
    }

    public List<String> addTemplateExtensions() {
        return new ArrayList<String>();
    }

    /**
     * Override to provide additional mime types from your plugin. These mimetypes get priority over
     * the default framework mimetypes but not over the application's configuration.
     * @return a Map from extensions (without dot) to mimetypes
     */
    public Map<String, String> addMimeTypes() {
        return new HashMap<String, String>();
    }

    /**
     * Let a chance to the plugin to compile it owns classes.
     * Must be added to the mutable list.
     */
    @Deprecated
    public void compileAll(List<ApplicationClass> classes) {
    }

    /**
     * Let some plugins route themself
     * @param request
     */
    public void routeRequest(Request request) {
    }

    public Model.Factory modelFactory(Class<? extends Model> modelClass) {
        return null;
    }

    public void afterFixtureLoad() {
    }

    /**
     * Inter-plugin communication.
     */
    public static void postEvent(String message, Object context) {
        Play.pluginCollection.onEvent(message, context);
    }

    public void onApplicationReady() {
    }

    /**
     * plugin排序，影响执行顺序
     */
    // ~~~~~
    public int compareTo(PlayPlugin o) {
        int res = index < o.index ? -1 : (index == o.index ? 0 : 1);
        if (res!=0) {
            return res;
        }

        // index is equal in both plugins.
        // sort on classtype to get consistent order
        res = this.getClass().getName().compareTo(o.getClass().getName());
        if (res != 0 ) {
            // classnames where different
            return res;
        }

        // identical classnames.
        // sort on instance to get consistent order.
        // We only return 0 (equal) if both identityHashCode are identical
        // which is only the case if both this and other are the same object instance.
        // This is consistent with equals() when no special equals-method is implemented.
        int thisHashCode = System.identityHashCode(this);
        int otherHashCode = System.identityHashCode(o);
        return (thisHashCode < otherHashCode ? -1 : (thisHashCode == otherHashCode ? 0 : 1));
    }

    public String overrideTemplateSource(BaseTemplate template, String source) {
        return null;
    }

    public Object willBeValidated(Object value) {
        return null;
    }
    
}
