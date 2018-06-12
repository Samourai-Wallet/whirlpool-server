package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.beans.RpcIn;
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

    public TxOutPoint validateAndGetPremixInput(String utxoHash, long utxoIndex, byte[] pubkeyHex, int minConfirmations, long samouraiFeesMin, boolean liquidity) throws IllegalInputException {
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
        checkPremixInput(rpcOut, rpcTransaction, samouraiFeesMin, liquidity);

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

    protected boolean checkPremixInput(RpcOut rpcOut, RpcTransaction rpcTransaction, long samouraiFeesMin, Boolean liquidity) throws IllegalInputException {
        boolean isLiquidity;

        if (samouraiFeesMin == 0) {
            // skip fees payment verification
            log.warn("checkPremixInput disabled by configuration");
            return liquidity;
        }

        // is it a tx0?
        Integer x = findSamouraiFeesXpubIndiceFromTx0(rpcTransaction);
        if (x != null) {
            // this is a tx0 => mustMix
            isLiquidity = false;
            if (liquidity != null && liquidity) {
                throw new IllegalArgumentException("Input not recognized as a liquidity");
            }

            // check fees paid
            checkTx0PaidFees(rpcTransaction, rpcOut.getIndex(), samouraiFeesMin, x);
        }
        else {
            // this is not a tx0 => liquidity (coming from a previous whirlpool tx)
            isLiquidity = true;
            if (liquidity != null && !liquidity) {
                throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet (fees payment not found for utxo "+rpcTransaction.getHash()+":"+rpcOut.getIndex()+", x=unknown)");
            }

            // check valid whirlpool tx
            long denomination = rpcOut.getValue();
            checkWhirlpoolTx(rpcTransaction, samouraiFeesMin, denomination);
        }
        return isLiquidity;
    }

    protected void checkWhirlpoolTx(RpcTransaction rpcTransaction, long samouraiFeesMin, long denomination) throws IllegalInputException {
        // tx should have same number of inputs-outputs > 1
        if (rpcTransaction.getIns().size() != rpcTransaction.getOuts().size() || rpcTransaction.getIns().size() < 2) {
            throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, inputs/outputs count mismatch");
        }

        // each output should match the denomination
        for (RpcOut out : rpcTransaction.getOuts()) {
            if (out.getValue() != denomination) {
                throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, output denomination mismatch");
            }
        }

        // each input should match the denomination
        for (RpcIn in : rpcTransaction.getIns()) {
            RpcOut fromOut = in.getFromOut();
            RpcTransaction fromTx = in.getFromTx();

            if (fromOut.getValue() != denomination) {
                throw new IllegalInputException(rpcTransaction.getHash()+" is not a valid whirlpool tx, input denomination mismatch for "+fromOut.getIndex()+"/"+fromTx.getHash());
            }
        }

        // recursively check each input validity: should come from a valid tx0 or from another whirlpool tx)
        for (RpcIn in : rpcTransaction.getIns()) {
            RpcOut fromOut = in.getFromOut();
            RpcTransaction fromTx = in.getFromTx();

            checkPremixInput(fromOut, fromTx, samouraiFeesMin, null);
        }

        // OK, this is a valid whirlpool TX
    }

    protected void checkTx0PaidFees(RpcTransaction tx0, long utxoIndexNotFee, long minFees, int x) throws IllegalInputException {
        // find samourai payment address from xpub indice in tx payload
        String feesAddressBech32 = computeSamouraiFeesAddress(x);

        // make sure tx contains an output to samourai fees
        for (RpcOut rpcOut : tx0.getOuts()) {
            if (rpcOut.getIndex() != utxoIndexNotFee) {
                if (rpcOut.getValue() >= minFees) {
                    // is this the fees payment output?
                    String rpcOutToAddress = rpcOut.getToAddressSingle();
                    if (rpcOutToAddress != null && feesAddressBech32.equals(rpcOutToAddress)) {
                        // ok, this is the fees payment output
                        return;
                    }
                }
            }
        }
        throw new IllegalInputException("Input doesn't belong to a Samourai pre-mix wallet (fees payment not found for utxo "+tx0.getHash()+":"+utxoIndexNotFee+", x="+x+")");
    }

    private String computeSamouraiFeesAddress(int x) {
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

    private RpcOut findOut(RpcTransaction rpcTransaction, long utxoIndex) {
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getIndex() == utxoIndex) {
                return rpcOut;
            }
        }
        return null;
    }
}
