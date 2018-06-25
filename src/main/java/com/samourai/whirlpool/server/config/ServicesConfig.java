package com.samourai.whirlpool.server.config;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.MockBlockchainDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

import java.lang.invoke.MethodHandles;

@Configuration
public class ServicesConfig {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Bean
    CryptoService cryptoService() throws Exception {
        return new CryptoService(whirlpoolServerConfig.getKeyPair(), whirlpoolServerConfig.getNetworkParameters());
    }

    @Bean
    BitcoinJSONRPCClient bitcoinJSONRPCClient() throws Exception {
        WhirlpoolServerConfig.RpcClientConfig config = whirlpoolServerConfig.getRpcClient();
        String url = config.getProtocol()+"://"+config.getUser()+":"+config.getPassword()+"@"+config.getHost()+":"+config.getPort();
        BitcoinJSONRPCClient bitcoinClient = new BitcoinJSONRPCClient(url);
        return bitcoinClient;
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
    public BlockchainDataService blockchainDataService(BitcoinJSONRPCClient rpcClient, CryptoService cryptoService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig) {
        if (whirlpoolServerConfig.getRpcClient().isMockTxBroadcast()) {
            log.warn("server.rpc-client.mock-tx-broadcast=TRUE, tx WON'T be broadcasted by server");
            return new MockBlockchainDataService(rpcClient, cryptoService, bech32Util, whirlpoolServerConfig);
        }
        return new BlockchainDataService(rpcClient, whirlpoolServerConfig);
    }
}
