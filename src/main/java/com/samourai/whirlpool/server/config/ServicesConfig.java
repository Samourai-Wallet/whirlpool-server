package com.samourai.whirlpool.server.config;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

@Configuration
public class ServicesConfig {
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
}
