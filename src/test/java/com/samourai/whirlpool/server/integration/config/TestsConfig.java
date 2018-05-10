package com.samourai.whirlpool.server.integration.config;

import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestsConfig {
    @Autowired
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Bean
    ClientCryptoService clientCryptoService() {
        return new ClientCryptoService(whirlpoolServerConfig.getNetworkParameters());
    }
}
