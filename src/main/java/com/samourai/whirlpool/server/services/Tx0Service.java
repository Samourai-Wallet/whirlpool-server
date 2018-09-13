package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.server.beans.CachedResult;
import com.samourai.whirlpool.server.beans.rpc.RpcIn;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Tx0Service {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String CACHE_CHECK_INPUT = "Tx0Service.checkInput";

    private BlockchainDataService blockchainDataService;
    private CryptoService cryptoService;
    private FormatsUtilGeneric formatsUtil;
    private WhirlpoolServerConfig whirlpoolServerConfig;
    private CacheService cacheService;

    public Tx0Service(BlockchainDataService blockchainDataService, CryptoService cryptoService, FormatsUtilGeneric formatsUtil, WhirlpoolServerConfig whirlpoolServerConfig, CacheService cacheService) {
        this.blockchainDataService = blockchainDataService;
        this.cryptoService = cryptoService;
        this.formatsUtil = formatsUtil;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
        this.cacheService = cacheService;
    }

    protected boolean checkInput(RpcOutWithTx rpcOutWithTx) throws IllegalInputException {
        RpcOut rpcOut = rpcOutWithTx.getRpcOut();
        RpcTransaction tx = rpcOutWithTx.getTx();
        if (!rpcOut.getHash().equals(tx.getTxid())) {
            throw new IllegalInputException("Unexpected usage of checkInput: rpcOut.hash != tx.hash");
        }

        List<String> txsPath = new ArrayList<>();
        txsPath.add("mixInput:"+rpcOutWithTx.getRpcOut().getHash() + "-" + rpcOutWithTx.getRpcOut().getIndex());

        long denomination = rpcOutWithTx.getRpcOut().getValue();

        return checkInput(tx, denomination, txsPath);
    }

    private boolean checkInput(RpcTransaction tx, long denomination, List<String> txsPath) throws IllegalInputException {
        // use cache
        String cacheKey = tx.getTxid() + ":" + denomination;
        boolean liquidity = cacheService.getOrPutCachedResult(CACHE_CHECK_INPUT, cacheKey, (v) -> doCheckInputCacheable(tx, denomination, txsPath));
        return liquidity;
    }

    protected CachedResult<Boolean,IllegalInputException> doCheckInputCacheable(RpcTransaction tx, long denomination, List<String> txsPath) {
        try {
            // is it a tx0?
            Integer x = findSamouraiFeesXpubIndiceFromTx0(tx);
            if (x != null) {
                // this is a tx0 => mustMix
                txsPath.add("tx0:" + tx.getTxid());

                // check fees paid
                if (!isTx0FeesPaid(tx, x)) {
                    String txsPathStr = txsPathToString(txsPath);
                    throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet (fees payment not valid for tx0 " + tx.getTxid() + ", x=" + x + ") (verified path:" + txsPathStr + ")");
                }
                return new CachedResult(false);
            } else {
                // this is not a valid tx0 => may be a liquidity coming from a previous whirlpool tx, or an invalid input

                // check valid whirlpool tx
                checkWhirlpoolTx(tx, denomination, txsPath);

                return new CachedResult(true);
            }
        } catch(IllegalInputException e) {
            return new CachedResult(e);
        }
    }

    protected void checkWhirlpoolTx(RpcTransaction tx, long denomination, List<String> txsPath) throws IllegalInputException {
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
            RpcTransaction txOrigin = rpcOutWithTxOrigin.getTx();

            List<String> txsPathNew = new ArrayList<>(txsPath);
            txsPathNew.add("mixInput:"+outOrigin.getHash() + "-" + outOrigin.getIndex());

            checkInput(txOrigin, denomination, txsPathNew); // denomination from previous whirlpool should be the same
        }

        // OK, this is a valid whirlpool TX
    }

    private String txsPathToString(List<String> txsPath) {
        String txsPathStr = String.join(" => ", txsPath.toArray(new String[]{}));
        return txsPathStr;
    }

    protected boolean isTx0FeesPaid(RpcTransaction tx0, int x) {
        long samouraiFeesMin = whirlpoolServerConfig.getSamouraiFees().getAmount();

        // find samourai payment address from xpub indice in tx payload
        String feesAddressBech32 = computeSamouraiFeesAddress(x);

        // make sure tx contains an output to samourai fees
        for (RpcOut rpcOut : tx0.getOuts()) {
            if (rpcOut.getValue() >= samouraiFeesMin) {
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
