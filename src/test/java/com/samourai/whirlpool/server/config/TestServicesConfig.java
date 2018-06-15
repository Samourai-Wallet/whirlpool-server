package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.TestBlockchainDataService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

@Configuration
public class TestServicesConfig extends ServicesConfig {

    @Bean
    public BlockchainDataService blockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig) {
        return new TestBlockchainDataService(rpcClient, whirlpoolServerConfig);
    }
}
