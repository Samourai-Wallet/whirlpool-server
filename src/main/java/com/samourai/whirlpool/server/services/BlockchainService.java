package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.server.beans.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.UnconfirmedInputException;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private BlockchainDataService blockchainDataService;
    private Tx0Service tx0Service;

    public BlockchainService(CryptoService cryptoService, BlockchainDataService blockchainDataService, Tx0Service tx0Service) {
        this.cryptoService = cryptoService;
        this.blockchainDataService = blockchainDataService;
        this.tx0Service = tx0Service;
    }

    public TxOutPoint validateAndGetPremixInput(String utxoHash, long utxoIndex, byte[] pubkeyHex, int minConfirmations, long samouraiFeesMin, boolean liquidity) throws IllegalInputException, UnconfirmedInputException {
        RpcOutWithTx rpcOutWithTx = blockchainDataService.getRpcOutWithTx(utxoHash, utxoIndex).orElseThrow(
                () -> new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex)
        );
        RpcOut rpcOut = rpcOutWithTx.getRpcOut();

        // verify pubkey: pubkey should control this utxo
        checkPubkey(rpcOut, pubkeyHex);

        // verify confirmations
        checkInputConfirmations(rpcOutWithTx.getTx(), minConfirmations);

        // verify input comes from a valid tx0 (or from a valid mix)
        if (samouraiFeesMin > 0) {
            boolean isLiquidity = tx0Service.checkInput(rpcOutWithTx, samouraiFeesMin);
            if (!isLiquidity && liquidity) {
                throw new IllegalArgumentException("Input rejected: not recognized as a liquidity (but as a mustMix)");
            }
            if (isLiquidity && !liquidity) {
                throw new IllegalInputException("Input rejected: not recognized as a mustMix (but as a liquidity)");
            }
        }
        else {
            log.warn("tx0 verification disabled by configuration (samouraiFeesMin=0)");
        }

        TxOutPoint txOutPoint = new TxOutPoint(utxoHash, utxoIndex, rpcOut.getValue());
        return txOutPoint;
    }

    protected void checkPubkey(RpcOut rpcOut, byte[] pubkeyHex) throws IllegalInputException {
        String toAddressFromPubkey = new SegwitAddress(pubkeyHex, cryptoService.getNetworkParameters()).getBech32AsString();
        String toAddressFromUtxo = rpcOut.getToAddress();
        if (toAddressFromUtxo == null || !toAddressFromPubkey.equals(toAddressFromUtxo)) {
            throw new IllegalInputException("Invalid pubkey for UTXO");
        }
    }

    protected void checkInputConfirmations(RpcTransaction tx, int minConfirmations) throws UnconfirmedInputException {
        int inputConfirmations = tx.getConfirmations();
        if (inputConfirmations < minConfirmations) {
            throw new UnconfirmedInputException("Input needs at least "+minConfirmations+" confirmations");
        }
    }
}
