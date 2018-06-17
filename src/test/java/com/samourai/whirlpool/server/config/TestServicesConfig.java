package com.samourai.whirlpool.server.config;

import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.TestBlockchainDataService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

@Configuration
public class TestServicesConfig extends ServicesConfig {

    @Bean
    public BlockchainDataService blockchainDataService(BitcoinJSONRPCClient rpcClient, CryptoService cryptoService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig) {
        return new TestBlockchainDataService(rpcClient, cryptoService, bech32Util, whirlpoolServerConfig);
    }
}
