package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.Callback;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig.SecretWalletConfig;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
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
  private Bech32UtilGeneric bech32Util;
  private HD_WalletFactoryJava hdWalletFactory;
  private BIP47Wallet secretWalletBip47;
  private TxUtil txUtil;
  private BlockchainDataService blockchainDataService;

  public Tx0Service(
      CryptoService cryptoService,
      FormatsUtilGeneric formatsUtil,
      WhirlpoolServerConfig whirlpoolServerConfig,
      Bech32UtilGeneric bech32UtilGeneric,
      HD_WalletFactoryJava hdWalletFactory,
      TxUtil txUtil,
      BlockchainDataService blockchainDataService)
      throws Exception {
    this.cryptoService = cryptoService;
    this.formatsUtil = formatsUtil;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.bech32Util = bech32UtilGeneric;
    this.hdWalletFactory = hdWalletFactory;
    this.secretWalletBip47 = computeSecretWallet();
    this.txUtil = txUtil;
    this.blockchainDataService = blockchainDataService;
  }

  private BIP47Wallet computeSecretWallet() throws Exception {
    SecretWalletConfig secretWallet = whirlpoolServerConfig.getSecretWallet();
    HD_Wallet hdw =
        hdWalletFactory.restoreWallet(
            secretWallet.getWords(),
            secretWallet.getPassphrase(),
            1,
            cryptoService.getNetworkParameters());
    return hdWalletFactory.getBIP47(
        hdw.getSeedHex(), hdw.getPassphrase(), cryptoService.getNetworkParameters());
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

  protected Integer findSamouraiFeesIndice(RpcTransaction rpcTransaction) {
    ByteBuffer opReturnMaskedValue = findOpReturnValue(rpcTransaction);
    if (opReturnMaskedValue == null) {
      return null;
    }

    // decode opReturnMaskedValue
    Transaction tx = rpcTransaction.getTx();
    TransactionOutPoint input0OutPoint = tx.getInput(0).getOutpoint();
    Callback<byte[]> fetchInputOutpointScriptBytes =
        computeCallbackFetchOutpointScriptBytes(input0OutPoint); // needed for P2PK
    byte[] input0Pubkey = txUtil.findInputPubkey(tx, 0, fetchInputOutpointScriptBytes);
    Integer dataUnmasked = cryptoService.xorUnmaskInteger(opReturnMaskedValue.array(), secretWalletBip47, input0OutPoint, input0Pubkey);
    return dataUnmasked;
  }

  private Callback<byte[]> computeCallbackFetchOutpointScriptBytes(
      TransactionOutPoint outPoint) {
    Callback<byte[]> fetchInputOutpointScriptBytes = () -> {
      // fetch output script bytes for outpoint
      String outpointHash = outPoint.getHash().toString();
      Optional<RpcOutWithTx> outpointRpcOut =
          blockchainDataService.getRpcOutWithTx(outpointHash, outPoint.getIndex());
      if (!outpointRpcOut.isPresent()) {
        log.error("Tx not found for outpoint: " + outpointHash);
        return null;
      }
      return outpointRpcOut.get().getRpcOut().getScriptPubKey();
    };
    return fetchInputOutpointScriptBytes;
  }

  protected ByteBuffer findOpReturnValue(RpcTransaction rpcTransaction) {
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
                return bb;
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
