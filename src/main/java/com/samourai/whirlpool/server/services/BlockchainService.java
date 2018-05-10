package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;

@Service
public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private BlockchainDataService blockchainDataService;
    private FormatsUtil formatsUtil;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    public BlockchainService(CryptoService cryptoService, BlockchainDataService blockchainDataService, FormatsUtil formatsUtil, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.cryptoService = cryptoService;
        this.blockchainDataService = blockchainDataService;
        this.formatsUtil = formatsUtil;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    public TxOutPoint validateAndGetPremixInput(String utxoHash, long utxoIndex, byte[] pubkeyHex, int minConfirmations, long samouraiFeesMin) throws IllegalInputException {
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(utxoHash);
        if (rpcTransaction == null) {
            log.error("UTXO transaction not found: "+utxoHash);
            throw new IllegalInputException("UTXO not found");
        }

        RpcOut rpcOut = findOut(rpcTransaction, utxoIndex);
        if (rpcOut == null) {
            log.error("UTXO not found: "+utxoHash+":"+utxoIndex);
            throw new IllegalInputException("UTXO not found");
        }

        // verify pubkey: pubkey should control this utxo
        checkPubkey(rpcOut, pubkeyHex);

        // verify confirmations
        checkInputConfirmations(rpcTransaction, minConfirmations);

        // verify paid fees
        if (samouraiFeesMin > 0) {
            checkInputPaidFees(rpcTransaction, utxoIndex, samouraiFeesMin);
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

    protected void checkInputConfirmations(RpcTransaction rpcTransaction, int minConfirmations) throws IllegalInputException {
        int inputConfirmations;
        try {
            inputConfirmations = rpcTransaction.getConfirmations();
        }
        catch(Exception e) {
            log.error("Couldn't retrieve input confirmations", e);
            throw new IllegalInputException("Couldn't retrieve input confirmations");
        }
        if (inputConfirmations < minConfirmations) {
            throw new IllegalInputException("Input needs at least "+minConfirmations+" confirmations");
        }
    }

    protected void checkInputPaidFees(RpcTransaction rpcTransaction, long utxoIndex, long minFees) throws IllegalInputException {
        if (!isSamouraiFeesPaid(rpcTransaction, utxoIndex, minFees)) {
            throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet");
        }
    }

    private boolean isSamouraiFeesPaid(RpcTransaction rpcTransaction, long utxoIndex, long minFees) {
        Integer x = findSamouraiFeesXpubIndiceFromPremixTx(rpcTransaction);
        if (x == null) {
            log.error("no samouraiFeesXpubIndiceFromPremixTx found for txid="+rpcTransaction.getHash());
            return false;
        }

        // find samourai payment address from xpub indice in tx payload
        String feesAddressBech32 = computeSamouraiFeesAddress(x);

        // make sure tx contains an output to samourai fees
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getIndex() != utxoIndex) {
                if (rpcOut.getValue() >= minFees) {
                    // is this the fees payment output?
                    String rpcOutToAddress = rpcOut.getToAddressSingle();
                    if (rpcOutToAddress != null && feesAddressBech32.equals(rpcOutToAddress)) {
                        // ok, this is the fees payment output
                        return true;
                    }
                }
            }
        }
        log.error("isSamouraiFeesPaid: fees payment for x="+x+" : not found for utxo "+rpcTransaction.getHash()+":"+utxoIndex);
        return false;
    }

    private String computeSamouraiFeesAddress(int x) {
        DeterministicKey mKey = formatsUtil.createMasterPubKeyFromXPub(whirlpoolServerConfig.getSamouraiFees().getXpub());
        DeterministicKey cKey = HDKeyDerivation.deriveChildKey(mKey, new ChildNumber(0, false)); // assume external/receive chain
        DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(x, false));
        ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
        String feeAddressBech32 = new SegwitAddress(feeECKey.getPubKey(), cryptoService.getNetworkParameters()).getBech32AsString();
        return feeAddressBech32;
    }

    private Integer findSamouraiFeesXpubIndiceFromPremixTx(RpcTransaction rpcTransaction)  {
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getValue() == 0) {
                try {
                    Script script = new Script(rpcOut.getScriptPubKey());
                    if (script.getChunks().size() == 2) {
                        // read OP_RETURN
                        ScriptChunk scriptChunkOpCode = script.getChunks().get(0);
                        if (scriptChunkOpCode.isOpCode() && scriptChunkOpCode.equalsOpCode(ScriptOpCodes.OP_RETURN)) {
                            // read data
                            ScriptChunk scriptChunkPushData = script.getChunks().get(1);
                            if (scriptChunkPushData.isPushData()) {
                                // get int
                                ByteBuffer bb = ByteBuffer.wrap(scriptChunkPushData.data);
                                int samouraiFeesXXpubIndice = bb.getInt();
                                if (samouraiFeesXXpubIndice >= 0) {
                                    return samouraiFeesXXpubIndice;
                                }
                            }
                        }
                    }
                }
                catch(Exception e) {e.printStackTrace();}
            }
        }
        return null;
    }

    private RpcOut findOut(RpcTransaction rpcTransaction, long utxoIndex) {
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getIndex() == utxoIndex) {
                return rpcOut;
            }
        }
        return null;
    }
}
