package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
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

@Service
public class Tx0Service {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CryptoService cryptoService;
  private FormatsUtilGeneric formatsUtil;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private DbService dbService;
  private Bech32UtilGeneric bech32Util;

  public Tx0Service(
      CryptoService cryptoService,
      FormatsUtilGeneric formatsUtil,
      WhirlpoolServerConfig whirlpoolServerConfig,
      DbService dbService,
      Bech32UtilGeneric bech32UtilGeneric) {
    this.cryptoService = cryptoService;
    this.formatsUtil = formatsUtil;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.dbService = dbService;
    this.bech32Util = bech32UtilGeneric;
  }

  protected boolean checkInput(RpcOutWithTx rpcOutWithTx) throws IllegalInputException {
    RpcOut rpcOut = rpcOutWithTx.getRpcOut();
    RpcTransaction tx = rpcOutWithTx.getTx();
    if (!rpcOut.getHash().equals(tx.getTxid())) {
      throw new IllegalInputException("Unexpected usage of checkInput: rpcOut.hash != tx.hash");
    }

    long inputValue = rpcOutWithTx.getRpcOut().getValue();
    boolean liquidity = doCheckInput(tx, inputValue);
    return liquidity;
  }

  protected boolean doCheckInput(RpcTransaction tx, long inputValue) throws IllegalInputException {
    // is it a tx0?
    Integer x = findSamouraiFeesXpubIndiceFromTx0(tx);
    if (x != null) {
      // this is a tx0 => mustMix

      // check fees paid
      if (!isTx0FeesPaid(tx, x)) {
        throw new IllegalInputException("Input rejected (invalid fee for tx0=" + tx.getTxid()+ ", x="+ x+ ")");
      }
      return false; // mustMix
    } else {
      // this is not a valid tx0 => liquidity coming from a previous whirlpool tx

      boolean isWhirlpoolTx = isWhirlpoolTx(tx, inputValue);
      if (!isWhirlpoolTx) {
        throw new IllegalInputException("Input rejected (not a premix or whirlpool input)");
      }
      return true; // liquidity
    }
  }

  protected boolean isWhirlpoolTx(RpcTransaction tx, long denomination) {
    return dbService.hasMixTxid(tx.getTxid(), denomination);
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
    DeterministicKey mKey =
        formatsUtil.createMasterPubKeyFromXPub(whirlpoolServerConfig.getSamouraiFees().getXpub());
    DeterministicKey cKey =
        HDKeyDerivation.deriveChildKey(
            mKey, new ChildNumber(0, false)); // assume external/receive chain
    DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(x, false));
    ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
    String feeAddressBech32 =
        bech32Util.toBech32(feeECKey.getPubKey(), cryptoService.getNetworkParameters());
    return feeAddressBech32;
  }

  protected Integer findSamouraiFeesXpubIndiceFromTx0(RpcTransaction rpcTransaction) {
    for (RpcOut rpcOut : rpcTransaction.getOuts()) {
      if (rpcOut.getValue() == 0) {
        try {
          Script script = new Script(rpcOut.getScriptPubKey());
          if (script.getChunks().size() == 2) {
            // read OP_RETURN
            ScriptChunk scriptChunkOpCode = script.getChunks().get(0);
            if (scriptChunkOpCode.isOpCode()
                && scriptChunkOpCode.equalsOpCode(ScriptOpCodes.OP_RETURN)) {
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
        } catch (Exception e) {
          log.error("", e);
        }
      }
    }
    return null;
  }
}
