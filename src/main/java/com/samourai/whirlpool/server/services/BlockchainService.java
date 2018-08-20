package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
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
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(utxoHash);
        if (rpcTransaction == null) {
            log.error("UTXO transaction not found: "+utxoHash);
            throw new IllegalInputException("UTXO not found");
        }

        RpcOut rpcOut = Utils.findTxOutput(rpcTransaction, utxoIndex);
        if (rpcOut == null) {
            log.error("UTXO not found: "+utxoHash+":"+utxoIndex);
            throw new IllegalInputException("UTXO not found");
        }

        // verify pubkey: pubkey should control this utxo
        checkPubkey(rpcOut, pubkeyHex);

        // verify confirmations
        checkInputConfirmations(rpcTransaction, minConfirmations);

        // verify input comes from a valid tx0 (or from a valid mix)
        if (samouraiFeesMin > 0) {
            boolean isLiquidity = tx0Service.checkInput(rpcOut, rpcTransaction, samouraiFeesMin);
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
        String toAddressFromUtxo = rpcOut.getToAddressSingle();
        if (toAddressFromUtxo == null || !toAddressFromPubkey.equals(toAddressFromUtxo)) {
            throw new IllegalInputException("Invalid pubkey for UTXO");
        }
    }

    protected void checkInputConfirmations(RpcTransaction rpcTransaction, int minConfirmations) throws UnconfirmedInputException {
        int inputConfirmations = rpcTransaction.getConfirmations();
        if (inputConfirmations < minConfirmations) {
            throw new UnconfirmedInputException("Input needs at least "+minConfirmations+" confirmations");
        }
    }
}
