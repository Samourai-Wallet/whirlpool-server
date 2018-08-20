package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class Tx0Service {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private FormatsUtil formatsUtil;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    public Tx0Service(CryptoService cryptoService, FormatsUtil formatsUtil, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.cryptoService = cryptoService;
        this.formatsUtil = formatsUtil;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    protected boolean checkInput(RpcOut rpcOut, RpcTransaction rpcTransaction, long samouraiFeesMin) throws IllegalInputException {
        List<String> txsPath = new ArrayList<>();
        return checkInput(rpcOut, rpcTransaction, samouraiFeesMin, txsPath);
    }

    private boolean checkInput(RpcOut rpcOut, RpcTransaction rpcTransaction, long samouraiFeesMin, List<String> txsPath) throws IllegalInputException {
        boolean isLiquidity;

        // is it a tx0?
        Integer x = findSamouraiFeesXpubIndiceFromTx0(rpcTransaction);
        if (x != null) {
            // this is a tx0 => mustMix
            isLiquidity = false;

            // check fees paid
            if (!isTx0FeesPaid(rpcTransaction, samouraiFeesMin, x)) {
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet (fees payment not found for utxo "+rpcTransaction.getHash()+"-"+rpcOut.getIndex()+", x="+x+") (verified path:"+txsPathStr+")");
            }
        }
        else {
            // this is not a tx0 => liquidity (coming from a previous whirlpool tx)
            isLiquidity = true;

            // check valid whirlpool tx
            long denomination = rpcOut.getValue();
            txsPath.add(rpcTransaction.getHash() + '-' + rpcOut.getIndex());
            checkWhirlpoolTx(rpcTransaction, samouraiFeesMin, denomination, txsPath);
        }
        return isLiquidity;
    }

    protected void checkWhirlpoolTx(RpcTransaction rpcTransaction, long samouraiFeesMin, long denomination, List<String> txsPath) throws IllegalInputException {
        // tx should have same number of inputs-outputs > 1
        if (rpcTransaction.getIns().size() != rpcTransaction.getOuts().size() || rpcTransaction.getIns().size() < 2) {
            String txsPathStr = txsPathToString(txsPath);
            throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, inputs/outputs count mismatch (verified path:"+txsPathStr+")");
        }

        // each output should match the denomination
        for (RpcOut out : rpcTransaction.getOuts()) {
            if (out.getValue() != denomination) {
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, denomination mismatch for output "+rpcTransaction.getHash()+"-"+out.getIndex()+" (verified path:"+txsPathStr+")");
            }
        }

        // each input should be >= the denomination
        for (RpcIn in : rpcTransaction.getIns()) {
            RpcOut fromOut = in.getFromOut();

            if (fromOut.getValue() < denomination) {
                RpcTransaction fromTx = in.getFromTx();
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, denomination mismatch for input "+fromTx.getHash()+"-"+fromOut.getIndex()+" (verified path:"+txsPathStr+")");
            }
        }

        // recursively check each input validity: should come from a valid tx0 or from another whirlpool tx)
        for (RpcIn in : rpcTransaction.getIns()) {
            RpcOut fromOut = in.getFromOut();
            RpcTransaction fromTx = in.getFromTx();
            checkInput(fromOut, fromTx, samouraiFeesMin, txsPath);
        }

        // OK, this is a valid whirlpool TX
    }

    private String txsPathToString(List<String> txsPath) {
        String txsPathStr = String.join(" => ", txsPath.toArray(new String[]{}));
        return txsPathStr;
    }

    protected boolean isTx0FeesPaid(RpcTransaction tx0, long minFees, int x) {
        // find samourai payment address from xpub indice in tx payload
        String feesAddressBech32 = computeSamouraiFeesAddress(x);

        // make sure tx contains an output to samourai fees
        for (RpcOut rpcOut : tx0.getOuts()) {
            if (rpcOut.getValue() >= minFees) {
                // is this the fees payment output?
                String rpcOutToAddress = rpcOut.getToAddressSingle();
                if (rpcOutToAddress != null && feesAddressBech32.equals(rpcOutToAddress)) {
                    // ok, this is the fees payment output
                    return true;
                }
            }
        }
        return false;
    }

    protected String computeSamouraiFeesAddress(int x) {
        DeterministicKey mKey = formatsUtil.createMasterPubKeyFromXPub(whirlpoolServerConfig.getSamouraiFees().getXpub());
        DeterministicKey cKey = HDKeyDerivation.deriveChildKey(mKey, new ChildNumber(0, false)); // assume external/receive chain
        DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(x, false));
        ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
        String feeAddressBech32 = new SegwitAddress(feeECKey.getPubKey(), cryptoService.getNetworkParameters()).getBech32AsString();
        return feeAddressBech32;
    }

    protected Integer findSamouraiFeesXpubIndiceFromTx0(RpcTransaction rpcTransaction)  {
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
                catch(Exception e) {
                    log.error("", e);
                }
            }
        }
        return null;
    }
}
