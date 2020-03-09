package com.samourai.whirlpool.server.services;

import com.samourai.http.client.CliHttpClient;
import com.samourai.http.client.IHttpClient;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.cli.services.CliTorClientService;
import com.samourai.whirlpool.cli.services.JavaHttpClientService;
import com.samourai.whirlpool.cli.services.JavaStompClientService;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.MemoryWalletPersistHandler;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private SimpUserRegistry simpUserRegistry;
  private Exception lastError;

  @Autowired
  public HealthService(
      WhirlpoolServerConfig whirlpoolServerConfig, SimpUserRegistry simpUserRegistry) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.simpUserRegistry = simpUserRegistry;
    this.lastError = null;
  }

  @Scheduled(fixedDelay = 120000)
  public void scheduleConnectCheck() {
    try {
      WhirlpoolClient whirlpoolClient = computeWhirlpoolClient();

      UtxoWithBalance utxoWithBalance = new UtxoWithBalance(new Utxo("healthCheck", 0), 123);
      IPremixHandler premixHandler = new PremixHandler(utxoWithBalance, new ECKey(), "healthCheck");
      IPostmixHandler postmixHandler =
          new IPostmixHandler() {
            @Override
            public String computeReceiveAddress(NetworkParameters params) {
              return "healthCheck";
            }

            @Override
            public void confirmReceiveAddress() {}

            @Override
            public void cancelReceiveAddress() {}
          };
      MixParams mixParams = new MixParams("healthCheck", 123, premixHandler, postmixHandler);
      whirlpoolClient.whirlpool(mixParams, new LoggingWhirlpoolClientListener("healthCheck"));
    } catch (Exception e) {
      log.error("", e);
      if (e.getMessage().contains("foooooooo")) {
        if (log.isDebugEnabled()) {
          log.debug("healthCheck success");
        }
        lastError = null;
      } else {
        log.error("healthCheck ERROR", e);
        log.info("Active users: " + simpUserRegistry.getUserCount());
        logThreads();
        this.lastError = e;
      }
    }
  }

  private void logThreads() {
    int i = 0;
    Collection<Thread> threads = ServerUtils.getInstance().getThreads();
    for (Thread thread : threads) {
      log.info("Thread #" + i + " " + thread.getName() + " " + thread.getState());
      for (StackTraceElement line : thread.getStackTrace()) {
        log.info(" " + line.toString());
      }
      i++;
    }
  }

  private WhirlpoolClient computeWhirlpoolClient() {
    CliConfig cliConfig = new CliConfig();
    cliConfig.setServer(WhirlpoolServer.MAINNET);

    CliTorClientService cliTorClientService = new CliTorClientService(cliConfig);
    JavaHttpClientService httpClientService =
        new JavaHttpClientService(cliTorClientService, cliConfig);
    IHttpClient httpClient = new CliHttpClient(cliTorClientService, cliConfig);
    IStompClientService stompClientService =
        new JavaStompClientService(cliTorClientService, cliConfig, httpClientService);
    WhirlpoolWalletPersistHandler persistHandler = new MemoryWalletPersistHandler();

    String serverUrl = cliConfig.getServer().getServerUrlClear();
    NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();
    boolean mobile = false;
    WhirlpoolClientConfig whirlpoolClientConfig =
        new WhirlpoolClientConfig(
            httpClient, stompClientService, persistHandler, serverUrl, params, mobile);
    return WhirlpoolClientImpl.newClient(whirlpoolClientConfig);
  }

  public Exception getLastError() {
    return lastError;
  }
}
