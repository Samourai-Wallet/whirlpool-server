package com.samourai.whirlpool.server.api.backend;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.MultiAddrResponse;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.JavaHttpClientService;
import java8.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class BackendApiTest extends AbstractIntegrationTest {
  private BackendApi backendApi;

  public BackendApiTest() {
    backendApi =
        new BackendApi(
            new JavaHttpClientService(),
            BackendServer.TESTNET.getBackendUrlClear(),
            Optional.empty());
  }

  @Test
  public void fetchAddresses() throws Exception {
    MultiAddrResponse.Address address =
        backendApi.fetchAddress(
            "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt");
    Assert.assertEquals(
        "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt",
        address.address);
  }
}
