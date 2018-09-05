package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.CachedResult;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class CacheServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected String foo(String result) {
        return result;
    }

    @Test
    public void getOrPut() throws Exception {
        String CACHE_NAME = "TEST_getOrPut";

        // TEST: first call => not cached
        final CacheServiceTest spy = Mockito.spy(this);
        int nbCallsExpected = 0;
        Assert.assertEquals("result111", cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

        // TEST: second call => cached
        Assert.assertEquals("result111", cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

        // TEST: second call on different cacheName => not cached
        Assert.assertEquals("result111", cacheService.getOrPut("AnotherCacheName", "111", String.class, (v) -> spy.foo("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

        // TEST: different call => not cached
        Assert.assertEquals("result222", cacheService.getOrPut(CACHE_NAME, "222", String.class, (v) -> spy.foo("result222")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

        // TEST: different call again => cached
        Assert.assertEquals("result222", cacheService.getOrPut(CACHE_NAME, "222", String.class, (v) -> spy.foo("result222")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

        // TEST: first call again => still cached
        Assert.assertEquals("result111", cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());
    }

    protected CachedResult<String,Exception> fooCachedResult(String result) {
        return new CachedResult(result);
    }

    protected CachedResult<String,Exception> fooCachedResult(Exception e) {
        return new CachedResult(e);
    }

    @Test
    public void getOrPutCachedResult() throws Exception {
        String CACHE_NAME = "TEST_getOrPutCachedResult";

        // TEST: first call => not cached
        final CacheServiceTest spy = Mockito.spy(this);
        int nbCallsExpected = 0;
        Assert.assertEquals("result111", cacheService.getOrPutCachedResult(CACHE_NAME, "111", (v) -> spy.fooCachedResult("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

        // TEST: second call => cached
        Assert.assertEquals("result111", cacheService.getOrPutCachedResult(CACHE_NAME, "111", (v) -> spy.fooCachedResult("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

        // TEST: different call => not cached, exception
        try {
            cacheService.getOrPutCachedResult(CACHE_NAME, "111", (v) -> new CachedResult<>(spy.fooCachedResult(new Exception("222")))); // exception expected
            Assert.assertTrue(false);
        } catch(Exception e) {
            Assert.assertEquals("222", e.getMessage());
        }
        // VERIFY
        Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

        // TEST: different call again => cached, exception
        try {
            cacheService.getOrPutCachedResult(CACHE_NAME, "111", (v) -> new CachedResult<>(spy.fooCachedResult(new Exception("222")))); // exception expected
            Assert.assertTrue(false);
        } catch(Exception e) {
            Assert.assertEquals("222", e.getMessage());
        }
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

        // TEST: first call => still cached
        Assert.assertEquals("result111", cacheService.getOrPutCachedResult(CACHE_NAME, "111", (v) -> spy.fooCachedResult("result111")));
        // VERIFY
        Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());
    }
}