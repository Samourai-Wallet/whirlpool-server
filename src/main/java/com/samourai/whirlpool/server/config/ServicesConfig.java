package com.samourai.whirlpool.server.config;

import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.CryptoService;
import java.lang.invoke.MethodHandles;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableCaching
public class ServicesConfig {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected WhirlpoolServerConfig whirlpoolServerConfig;

  public ServicesConfig(WhirlpoolServerConfig whirlpoolServerConfig) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  @Bean
  LayoutDialect layoutDialect() {
    // enable layout:decorate for thymeleaf
    return new LayoutDialect();
  }

  @Bean
  CryptoService cryptoService() {
    return new CryptoService(whirlpoolServerConfig.getNetworkParameters());
  }

  @Bean
  TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  @Bean
  WhirlpoolProtocol whirlpoolProtocol() {
    return new WhirlpoolProtocol();
  }

  @Bean
  FormatsUtilGeneric formatsUtilGeneric() {
    return FormatsUtilGeneric.getInstance();
  }

  @Bean
  Bech32UtilGeneric bech32UtilGeneric() {
    return Bech32UtilGeneric.getInstance();
  }

  @Bean
  HD_WalletFactoryJava hdWalletFactory() {
    return HD_WalletFactoryJava.getInstance();
  }

  @Bean
  MessageSignUtilGeneric messageSignUtil() {
    return MessageSignUtilGeneric.getInstance();
  }

  @Bean
  TxUtil txUtil() {
    return TxUtil.getInstance();
  }
}
