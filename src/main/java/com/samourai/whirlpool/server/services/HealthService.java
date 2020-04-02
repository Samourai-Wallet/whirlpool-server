package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.cli.services.CliTorClientService;
import com.samourai.whirlpool.cli.services.JavaHttpClientService;
import com.samourai.whirlpool.cli.services.JavaStompClientService;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.listener.AbstractWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.MemoryWalletPersistHandler;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.apache.commons.lang3.StringUtils;
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
  private String lastError;
  private WhirlpoolClientConfig whirlpoolClientConfig;

  @Autowired
  public HealthService(
      WhirlpoolServerConfig whirlpoolServerConfig, SimpUserRegistry simpUserRegistry) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.simpUserRegistry = simpUserRegistry;
    this.lastError = null;
    this.whirlpoolClientConfig = null;
  }

  @Scheduled(fixedDelay = 120000)
  public void scheduleConnectCheck() {
    try {
      WhirlpoolClient whirlpoolClient = computeWhirlpoolClientConfig().newClient();
      MixParams mixParams = computeMixParams();
      WhirlpoolClientListener listener =
          new AbstractWhirlpoolClientListener() {
            @Override
            public void fail(MixFailReason reason, String notifiableError) {
              super.fail(reason, notifiableError);
              if (notifiableError.contains(RegisterInputService.ERROR_INVALID_HASH)) {
                // expected response
                if (log.isTraceEnabled()) {
                  log.trace("healthCheck SUCCESS");
                }
                lastError = null;
              } else {
                // unexpected error
                log.error("healthCheck ERROR: " + notifiableError);
                log.info("Active users: " + simpUserRegistry.getUserCount());
                logThreads();
                lastError = notifiableError;
              }
            }
          };
      whirlpoolClient.whirlpool(mixParams, listener);
    } catch (Exception e) {
      log.error("healthCheck ERROR", e);
      lastError = e.getMessage();
    }
  }

  private void logThreads() {
    int i = 0;
    Collection<Thread> threads = ServerUtils.getInstance().getThreads();
    for (Thread thread : threads) {
      String stackTrace =
          Thread.State.BLOCKED.equals(thread.getState())
              ? StringUtils.join(thread.getStackTrace(), "\n")
              : "";
      log.info(
          "Thread #" + i + " " + thread.getName() + " " + thread.getState() + ": " + stackTrace);
      i++;
    }
  }

  private WhirlpoolClientConfig computeWhirlpoolClientConfig() {
    if (whirlpoolClientConfig == null) {
      CliConfig cliConfig = new CliConfig();
      cliConfig.setServer(
          whirlpoolServerConfig.isTestnet() ? WhirlpoolServer.TESTNET : WhirlpoolServer.MAINNET);

      CliTorClientService cliTorClientService = new CliTorClientService(cliConfig);
      JavaHttpClientService httpClientService =
          new JavaHttpClientService(cliTorClientService, cliConfig);
      IStompClientService stompClientService =
          new JavaStompClientService(cliTorClientService, cliConfig, httpClientService);
      WhirlpoolWalletPersistHandler persistHandler = new MemoryWalletPersistHandler();

      String serverUrl = cliConfig.getServer().getServerUrlClear();
      NetworkParameters params = whirlpoolServerConfig.getNetworkParameters();
      boolean mobile = false;
      whirlpoolClientConfig =
          new WhirlpoolClientConfig(
              httpClientService, stompClientService, persistHandler, serverUrl, params, mobile);
    }
    return whirlpoolClientConfig;
  }

  private MixParams computeMixParams() {
    WhirlpoolServerConfig.PoolConfig poolConfig = whirlpoolServerConfig.getPools()[0];
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(new Utxo("healthCheck", 0), poolConfig.getDenomination());
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
    MixParams mixParams =
        new MixParams(
            poolConfig.getId(), poolConfig.getDenomination(), premixHandler, postmixHandler);
    return mixParams;
  }

  public String getLastError() {
    return lastError;
  }
}
