package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcOutWithTx;
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
import java.util.*;

@Service
public class Tx0Service {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private BlockchainDataService blockchainDataService;
    private CryptoService cryptoService;
    private FormatsUtil formatsUtil;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    public Tx0Service(BlockchainDataService blockchainDataService, CryptoService cryptoService, FormatsUtil formatsUtil, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.blockchainDataService = blockchainDataService;
        this.cryptoService = cryptoService;
        this.formatsUtil = formatsUtil;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    protected boolean checkInput(RpcOutWithTx rpcOutWithTx, long samouraiFeesMin) throws IllegalInputException {
        List<String> txsPath = new ArrayList<>();
        return checkInput(rpcOutWithTx, samouraiFeesMin, txsPath);
    }

    private boolean checkInput(RpcOutWithTx rpcOutWithTx, long samouraiFeesMin, List<String> txsPath) throws IllegalInputException {
        boolean isLiquidity;
        RpcOut rpcOut = rpcOutWithTx.getRpcOut();
        RpcTransaction tx = rpcOutWithTx.getTx();

        if (!rpcOut.getHash().equals(tx.getTxid())) {
            throw new IllegalInputException("Unexpected usage of checkInput: rpcOut.hash != tx.hash");
        }

        // is it a tx0?
        Integer x = findSamouraiFeesXpubIndiceFromTx0(tx);
        if (x != null) {
            // this is a tx0 => mustMix
            isLiquidity = false;

            txsPath.add("tx0:" + tx.getTxid());

            // check fees paid
            if (!isTx0FeesPaid(tx, samouraiFeesMin, x)) {
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet (fees payment not found for utxo "+tx.getTxid()+"-"+rpcOut.getIndex()+", x="+x+") (verified path:"+txsPathStr+")");
            }
        }
        else {
            // this is not a tx0 => liquidity (coming from a previous whirlpool tx)
            isLiquidity = true;

            txsPath.add("mix:" + tx.getTxid());

            // check valid whirlpool tx
            long denomination = rpcOut.getValue();
            checkWhirlpoolTx(tx, samouraiFeesMin, denomination, txsPath);
        }
        return isLiquidity;
    }

    protected void checkWhirlpoolTx(RpcTransaction tx, long samouraiFeesMin, long denomination, List<String> txsPath) throws IllegalInputException {
        txsPath.add(tx.getTxid());

        // tx should have same number of inputs-outputs > 1
        if (tx.getIns().size() != tx.getOuts().size() || tx.getIns().size() < 2) {
            String txsPathStr = txsPathToString(txsPath);
            throw new IllegalInputException(tx.getTxid()+" is not a valid whirlpool tx, inputs/outputs count mismatch (verified path:"+txsPathStr+")");
        }

        // each output should match the denomination
        for (RpcOut out : tx.getOuts()) {
            if (out.getValue() != denomination) {
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException(tx.getTxid()+" is not a valid whirlpool tx, denomination mismatch for output "+tx.getTxid()+"-"+out.getIndex()+" (verified path:"+txsPathStr+")");
            }
        }

        Map<Long, RpcOutWithTx> cachedOrigins = new HashMap();
        for (RpcIn in : tx.getIns()) {
            // RPC request
            //!!!!!!!! TODO cache
            RpcOutWithTx rpcOutWithTxOrigin = blockchainDataService.getRpcOutWithTx(in.getOriginHash(), in.getOriginIndex()).orElseThrow(
                    () -> new IllegalInputException("UTXO origin not found: " + in.getOriginHash() + "-" + in.getOriginIndex())
            );
            cachedOrigins.put(in.getOriginIndex(), rpcOutWithTxOrigin);

            // each input should be >= the denomination
            RpcOut outOrigin = rpcOutWithTxOrigin.getRpcOut();
            if (outOrigin.getValue() < denomination) {
                String txsPathStr = txsPathToString(txsPath);
                throw new IllegalInputException(tx.getTxid()+" is not a valid whirlpool tx, denomination mismatch for input "+outOrigin.getHash()+"-"+outOrigin.getIndex()+" (verified path:"+txsPathStr+")");
            }
        }

        // recursively check each input validity: should come from a valid tx0 or from another whirlpool tx)
        for (RpcIn in : tx.getIns()) {
            RpcOutWithTx rpcOutWithTxOrigin = cachedOrigins.get(in.getOriginIndex());
            RpcOut outOrigin = rpcOutWithTxOrigin.getRpcOut();

            List<String> txsPathNew = new ArrayList<>(txsPath);
            txsPathNew.add("mixInput:"+outOrigin.getHash() + "-" + outOrigin.getIndex());

            checkInput(rpcOutWithTxOrigin, samouraiFeesMin, txsPathNew);
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
                String rpcOutToAddress = rpcOut.getToAddress();
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
