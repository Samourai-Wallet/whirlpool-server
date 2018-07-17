package com.samourai.whirlpool.server.config;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.MockBlockchainDataService;
import nz.net.ultraq.thymeleaf.LayoutDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

import java.lang.invoke.MethodHandles;
import java.net.URL;

@Configuration
public class ServicesConfig {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String RPC_CLIENT_PROTOCOL_MOCK = "mock";

    @Autowired
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Bean
    LayoutDialect layoutDialect() {
        // enable layout:decorate for thymeleaf
        return new LayoutDialect();
    }

    @Bean
    CryptoService cryptoService() throws Exception {
        return new CryptoService(whirlpoolServerConfig.getKeyPair(), whirlpoolServerConfig.getNetworkParameters());
    }

    @Bean
    BitcoinJSONRPCClient bitcoinJSONRPCClient(WhirlpoolServerConfig whirlpoolServerConfig) throws Exception {
        boolean mockRpc = isMockRpc(whirlpoolServerConfig);
        if (mockRpc) {
            log.warn("server.rpc-client.protocol=mock, no real blockchain data will be used");
            return null;
        }
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

    @Bean
    public BlockchainDataService blockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig, MockBlockchainDataService mockBlockchainDataService) {
        if (mockBlockchainDataService != null) {
            log.info("Loading BlockchainDataService... mocked");
            return mockBlockchainDataService;
        }

        log.info("Loading BlockchainDataService... production");
        return new BlockchainDataService(rpcClient, whirlpoolServerConfig);
    }

    @Bean
    public MockBlockchainDataService mockBlockchainDataService(BitcoinJSONRPCClient rpcClient, CryptoService cryptoService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig) {
        boolean mockRpc = isMockRpc(whirlpoolServerConfig);
        if (whirlpoolServerConfig.getRpcClient().isMockTxBroadcast() || mockRpc) {
            log.warn("server.rpc-client.mock-tx-broadcast=TRUE, tx WON'T be broadcasted by server");
            return new MockBlockchainDataService(rpcClient, cryptoService, bech32Util, whirlpoolServerConfig, mockRpc);
        }

        // mock disabled
        return null;
    }

    private boolean isMockRpc(WhirlpoolServerConfig whirlpoolServerConfig) {
        return RPC_CLIENT_PROTOCOL_MOCK.equals(whirlpoolServerConfig.getRpcClient().getProtocol().toLowerCase());
    }

}
