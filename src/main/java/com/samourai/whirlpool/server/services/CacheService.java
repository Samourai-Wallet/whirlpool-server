package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.CachedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

@Service
public class CacheService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CacheManager cacheManager;

    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public <T> T getOrPut(String cacheName, String cacheKey, Class<T> typeResult, Function<Void,T> get) {
        Cache cache = cacheManager.getCache(cacheName);
        T cachedResult = cache.get(cacheKey, typeResult);
        if (cachedResult == null) {
            cachedResult = get.apply(null);
            cache.put(cacheKey, cachedResult);
            if (log.isDebugEnabled()) {
                log.debug("cache.put: " + cacheName + " -> " + cacheKey);
            }
        }
        return cachedResult;
    }

    public <T, E extends Exception> T getOrPutCachedResult(String cacheName, String cacheKey, Function<Void,CachedResult<T,E>> get) throws E {
        CachedResult<T,E> cachedResult = getOrPut(cacheName, cacheKey, CachedResult.class, (Function)get);
        return cachedResult.getOrException();
    }

    public void clear(String cacheName) {
        if (log.isDebugEnabled()) {
            log.debug("cache.clear: " + cacheName);
        }
        cacheManager.getCache(cacheName).clear();
    }

    public void _reset() {
        cacheManager.getCacheNames().forEach(cacheName -> this.clear(cacheName));
    }
}
