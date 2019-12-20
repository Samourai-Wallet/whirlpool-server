package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.beans.CachedResult;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
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
    Assert.assertEquals(
        "result111",
        cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

    // TEST: second call => cached
    Assert.assertEquals(
        "result111",
        cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

    // TEST: second call on different cacheName => not cached
    Assert.assertEquals(
        "result111",
        cacheService.getOrPut(
            "AnotherCacheName", "111", String.class, (v) -> spy.foo("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

    // TEST: different call => not cached
    Assert.assertEquals(
        "result222",
        cacheService.getOrPut(CACHE_NAME, "222", String.class, (v) -> spy.foo("result222")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(++nbCallsExpected)).foo(Mockito.anyString());

    // TEST: different call again => cached
    Assert.assertEquals(
        "result222",
        cacheService.getOrPut(CACHE_NAME, "222", String.class, (v) -> spy.foo("result222")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());

    // TEST: first call again => still cached
    Assert.assertEquals(
        "result111",
        cacheService.getOrPut(CACHE_NAME, "111", String.class, (v) -> spy.foo("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(nbCallsExpected)).foo(Mockito.anyString());
  }

  protected CachedResult<String, Exception> fooCachedResult(String result) {
    return new CachedResult(result);
  }

  @Test
  public void getOrPutCachedResult_value() throws Exception {
    String CACHE_NAME = "TEST_getOrPutCachedResult_value";

    // TEST: first call => not cached
    final CacheServiceTest spy = Mockito.spy(this);
    int nbCallsExpected = 0;
    Assert.assertEquals(
        "result111",
        cacheService.getOrPutCachedResult(
            CACHE_NAME, "111", (v) -> spy.fooCachedResult("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(++nbCallsExpected)).fooCachedResult(Mockito.anyString());

    // TEST: second call => cached
    Assert.assertEquals(
        "result111",
        cacheService.getOrPutCachedResult(
            CACHE_NAME, "111", (v) -> spy.fooCachedResult("result111")));
    // VERIFY
    Mockito.verify(spy, Mockito.times(nbCallsExpected)).fooCachedResult(Mockito.anyString());
  }

  protected CachedResult<String, Exception> fooCachedResult(Exception e) {
    return new CachedResult(e);
  }

  @Test
  public void getOrPutCachedResult_exception() throws Exception {
    String CACHE_NAME = "TEST_getOrPutCachedResult_exception";

    // TEST: first call => not cached, exception
    final CacheServiceTest spy = Mockito.spy(this);
    int nbCallsExpected = 0;
    try {
      cacheService.getOrPutCachedResult(
          CACHE_NAME,
          "222",
          (v) -> spy.fooCachedResult(new Exception("222"))); // exception expected
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("222", e.getMessage());
    }
    // VERIFY
    Mockito.verify(spy, Mockito.times(++nbCallsExpected))
        .fooCachedResult(Mockito.any(Exception.class));

    // TEST: second call again => cached, exception
    try {
      cacheService.getOrPutCachedResult(
          CACHE_NAME,
          "222",
          (v) -> spy.fooCachedResult(new Exception("222"))); // exception expected
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("222", e.getMessage());
    }
    // VERIFY
    Mockito.verify(spy, Mockito.times(nbCallsExpected))
        .fooCachedResult(Mockito.any(Exception.class));
  }
}
