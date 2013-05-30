package play.cache;

import java.util.Map;

/**
 * A cache implementation.
 * expiration is specified in seconds
 * @see play.cache.Cache
 */
public interface CacheImpl {
	/**
	 * 添加缓存,此方法可能会报错 RuntimeException
	 * @param key 缓存的key
	 * @param value 缓存的值
	 * @param expiration 缓存时间
	 */
    public void add(String key, Object value, int expiration);

    /**
     * 安全的添加缓存方法，如果要判断缓存是否添加成功，只用根据返回值判断，而不用通过捕捉异常
     * @param key
     * @param value
     * @param expiration
     * @return
     */
    public boolean safeAdd(String key, Object value, int expiration);

    public void set(String key, Object value, int expiration);

    public boolean safeSet(String key, Object value, int expiration);

    public void replace(String key, Object value, int expiration);

    public boolean safeReplace(String key, Object value, int expiration);

    public Object get(String key);

    public Map<String, Object> get(String[] keys);

    public long incr(String key, int by);

    public long decr(String key, int by);

    public void clear();

    public void delete(String key);

    public boolean safeDelete(String key);

    public void stop();
}
