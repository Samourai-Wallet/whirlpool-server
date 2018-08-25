package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

import java.lang.invoke.MethodHandles;
import java.net.URL;

@Configuration
@Profile("!" + Utils.PROFILE_TEST)
public class BlockchainDataServiceConfig {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected WhirlpoolServerConfig whirlpoolServerConfig;

    public BlockchainDataServiceConfig(WhirlpoolServerConfig whirlpoolServerConfig) {
        log.info("Using PRODUCTION BlockchainDataService");
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    @Bean
    BlockchainDataService blockchainDataService() throws Exception {
        return new BlockchainDataService(computeBitcoinJSONRPCClient(), whirlpoolServerConfig);
    }

    private BitcoinJSONRPCClient computeBitcoinJSONRPCClient() throws Exception {
        URL rpcClientUrl = computeRpcClientUrl();
        BitcoinJSONRPCClient rpcClient = new BitcoinJSONRPCClient(rpcClientUrl);
        return rpcClient;
    }

    private URL computeRpcClientUrl() throws Exception {
        WhirlpoolServerConfig.RpcClientConfig config = whirlpoolServerConfig.getRpcClient();
        String rpcClientUrl = config.getProtocol() + "://" + config.getUser() + ":" + config.getPassword() + "@" + config.getHost() + ":" + config.getPort();
        try {
            return new URL(rpcClientUrl);
        }
        catch(Exception e) {
            throw new Exception("invalid rpcClientUrl: "+rpcClientUrl);
        }
    }

}
