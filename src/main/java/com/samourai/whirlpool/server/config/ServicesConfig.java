package com.samourai.whirlpool.server.config;

import com.samourai.javaserver.config.ServerServicesConfig;
import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.wallet.api.explorer.ExplorerApi;
import com.samourai.wallet.bip47.rpc.java.SecretPointFactoryJava;
import com.samourai.wallet.bip47.rpc.secretPoint.ISecretPointFactory;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableCaching
public class ServicesConfig extends ServerServicesConfig {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected WhirlpoolServerConfig whirlpoolServerConfig;

  public ServicesConfig(WhirlpoolServerConfig whirlpoolServerConfig) {
    super();
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  @Bean
  TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  @Bean
  ServerUtils serverUtils() {
    return ServerUtils.getInstance();
  }

  @Bean
  WhirlpoolProtocol whirlpoolProtocol() {
    return new WhirlpoolProtocol();
  }

  @Bean
  ISecretPointFactory secretPointFactory() {
    return SecretPointFactoryJava.getInstance();
  }

  @Bean
  WhirlpoolFee whirlpoolFee(ISecretPointFactory secretPointFactory) {
    return WhirlpoolFee.getInstance(secretPointFactory);
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

  @Bean
  CryptoTestUtil cryptoTestUtil() {
    return CryptoTestUtil.getInstance();
  }

  @Bean
  ExplorerApi explorerApi(WhirlpoolServerConfig serverConfig) {
    return new ExplorerApi(serverConfig.isTestnet());
  }
}
