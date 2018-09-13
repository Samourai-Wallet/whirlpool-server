package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

@Service
public class BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private Bech32UtilGeneric bech32Util;
    private RpcClientService rpcClientService;

    public BlockchainDataService(CryptoService cryptoService, Bech32UtilGeneric bech32Util, RpcClientService rpcClientService) {
        this.cryptoService = cryptoService;
        this.bech32Util = bech32Util;
        this.rpcClientService = rpcClientService;
    }
    
    protected Optional<RpcTransaction> getRpcTransaction(String txid) {
        if (log.isDebugEnabled()) {
            log.debug("RPC query: getRawTransaction " + txid);
        }
        Optional<RpcRawTransactionResponse> queryRawTxHex = rpcClientService.getRawTransaction(txid);
        if (!queryRawTxHex.isPresent()) {
            log.error("Tx not found: " + txid);
            return Optional.empty();
        }
        try {
            NetworkParameters params = cryptoService.getNetworkParameters();
            RpcTransaction rpcTx = new RpcTransaction(queryRawTxHex.get(), params, bech32Util);
            return Optional.of(rpcTx);
        } catch(Exception e) {
            log.error("Unable to parse RpcRawTransactionResponse", e);
            return Optional.empty();
        }
    }

    protected Optional<RpcOutWithTx> getRpcOutWithTx(String hash, long index) {
        Optional<RpcTransaction> txResponse = getRpcTransaction(hash);
        if (!txResponse.isPresent()) {
            log.error("UTXO transaction not found: " + hash);
            return Optional.empty();
        }
        RpcTransaction tx = txResponse.get();
        return Utils.getRpcOutWithTx(tx, index);
    }
}
