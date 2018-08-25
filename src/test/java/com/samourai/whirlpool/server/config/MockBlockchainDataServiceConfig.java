package com.samourai.whirlpool.server.config;

import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.MockBlockchainDataService;
import com.samourai.whirlpool.server.utils.TestUtils;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.invoke.MethodHandles;

@Profile(Utils.PROFILE_TEST)
@Configuration
public class MockBlockchainDataServiceConfig {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public MockBlockchainDataServiceConfig() {
        log.info("Using MOCKED BlockchainDataService");
    }

    @Bean
    public MockBlockchainDataService mockBlockchainDataService(WhirlpoolServerConfig whirlpoolServerConfig, TestUtils testUtils, CryptoService cryptoService, Bech32Util bech32Util) throws Exception {
        log.warn("Using MOCKED BlockchainDataService");
        return new MockBlockchainDataService(cryptoService, bech32Util, whirlpoolServerConfig, testUtils);
    }
}
