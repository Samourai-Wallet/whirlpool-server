package com.samourai.whirlpool.server.config;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.CryptoService;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.lang.invoke.MethodHandles;

@Configuration
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
    CryptoService cryptoService() throws Exception {
        return new CryptoService(whirlpoolServerConfig.getNetworkParameters());
    }

    @Bean
    TaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }

    @Bean
    WhirlpoolProtocol whirlpoolProtocol() { return new WhirlpoolProtocol(); }

    @Bean
    FormatsUtil getFormatsUtil() {
        return FormatsUtil.getInstance();
    }

    @Bean
    Bech32Util bech32Util() {
        return Bech32Util.getInstance();
    }

    @Bean
    BIP47Util bip47Util() {
        return BIP47Util.getInstance();
    }

}
