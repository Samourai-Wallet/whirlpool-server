package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.wallet.bip47.rpc.BIP47Account;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.Callback;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFeeData;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig.SecretWalletConfig;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import java.lang.invoke.MethodHandles;
import java.util.Map.Entry;
import java.util.Optional;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeeValidationService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CryptoService cryptoService;
  private WhirlpoolServerConfig serverConfig;
  private Bech32UtilGeneric bech32Util;
  private HD_WalletFactoryJava hdWalletFactory;
  private BIP47Account secretAccountBip47;
  private TxUtil txUtil;
  private BlockchainDataService blockchainDataService;
  private WhirlpoolFee whirlpoolFee;
  private XManagerClient xManagerClient;

  public FeeValidationService(
      CryptoService cryptoService,
      WhirlpoolServerConfig serverConfig,
      Bech32UtilGeneric bech32UtilGeneric,
      HD_WalletFactoryJava hdWalletFactory,
      TxUtil txUtil,
      BlockchainDataService blockchainDataService,
      WhirlpoolFee whirlpoolFee,
      XManagerClient xManagerClient)
      throws Exception {
    this.cryptoService = cryptoService;
    this.serverConfig = serverConfig;
    this.bech32Util = bech32UtilGeneric;
    this.hdWalletFactory = hdWalletFactory;
    this.secretAccountBip47 = computeSecretAccount();
    this.txUtil = txUtil;
    this.blockchainDataService = blockchainDataService;
    this.whirlpoolFee = whirlpoolFee;
    this.xManagerClient = xManagerClient;
  }

  private BIP47Account computeSecretAccount() throws Exception {
    SecretWalletConfig secretWallet = serverConfig.getSamouraiFees().getSecretWallet();
    HD_Wallet hdw =
        hdWalletFactory.restoreWallet(
            secretWallet.getWords(),
            secretWallet.getPassphrase(),
            1,
            cryptoService.getNetworkParameters());
    return hdWalletFactory
        .getBIP47(hdw.getSeedHex(), hdw.getPassphrase(), cryptoService.getNetworkParameters())
        .getAccount(0);
  }

  public WhirlpoolFeeData decodeFeeData(Transaction tx) {
    byte[] opReturnMaskedValue = findOpReturnValue(tx);
    if (opReturnMaskedValue == null) {
      return null;
    }

    // decode opReturnMaskedValue
    TransactionOutPoint input0OutPoint = tx.getInput(0).getOutpoint();
    Callback<byte[]> fetchInputOutpointScriptBytes =
        computeCallbackFetchOutpointScriptBytes(input0OutPoint); // needed for P2PK
    byte[] input0Pubkey = txUtil.findInputPubkey(tx, 0, fetchInputOutpointScriptBytes);
    WhirlpoolFeeData feeData =
        whirlpoolFee.decode(opReturnMaskedValue, secretAccountBip47, input0OutPoint, input0Pubkey);
    return feeData;
  }

  public String getFeePaymentCode() {
    return secretAccountBip47.getPaymentCode();
  }

  public boolean isValidTx0(
      Transaction tx0, long tx0Time, WhirlpoolFeeData feeData, PoolFee poolFee)
      throws NotifiableException {
    // validate feePayload
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        validateFeePayload(feeData.getFeePayload(), tx0Time);
    int feePercent = (scodeConfig != null ? scodeConfig.getFeeValuePercent() : 100);
    if (feePercent == 0) {
      // no fee
      return true;
    }
    // validate for feeIndice with feePercent
    return isTx0FeePaid(tx0, tx0Time, feeData.getFeeIndice(), poolFee, feePercent);
  }

  protected boolean isTx0FeePaid(
      Transaction tx0, long tx0Time, int x, PoolFee poolFee, int feeValuePercent)
      throws NotifiableException {
    if (x < 0) {
      log.error("Invalid samouraiFee indice: " + x);
      return false;
    }

    // make sure tx contains an output to samourai fees
    for (TransactionOutput txOutput : tx0.getOutputs()) {
      // is this the fee output?
      long amount = txOutput.getValue().getValue();
      if (poolFee.checkTx0FeePaid(amount, tx0Time, feeValuePercent)) {
        // ok, valid fee amount => this is either change output or fee output
        String toAddress =
            Utils.getToAddressBech32(txOutput, bech32Util, cryptoService.getNetworkParameters());
        if (toAddress != null) {
          // validate fee address
          boolean isFeeAddress = false;
          try {
            isFeeAddress =
                xManagerClient.verifyAddressIndexResponseOrException(
                    XManagerService.WHIRLPOOL, toAddress, x);
          } catch (Exception e) {
            log.error("!!! XMANAGER UNAVAILABLE !!! unable to validate Tx0");
            throw new NotifiableException("XM unavailable, please retry later");
          }

          if (isFeeAddress) {
            // ok, this is the fee address
            return true;
          } else {
            log.warn(
                "Tx0: invalid fee address for amount="
                    + amount
                    + " for tx0="
                    + tx0.getHashAsString()
                    + ", tx0Time="
                    + tx0Time
                    + ", x="
                    + x
                    + ", poolFee="
                    + poolFee
                    + ", feeValuePercent="
                    + feeValuePercent
                    + ", feesAddressBech32="
                    + toAddress);
          }
        }
      }
    }
    log.warn(
        "Tx0: no valid fee payment found for tx0="
            + tx0.getHashAsString()
            + ", tx0Time="
            + tx0Time
            + ", x="
            + x
            + ", poolFee="
            + poolFee
            + ", feeValuePercent="
            + feeValuePercent);
    return false;
  }

  private Callback<byte[]> computeCallbackFetchOutpointScriptBytes(TransactionOutPoint outPoint) {
    Callback<byte[]> fetchInputOutpointScriptBytes =
        () -> {
          // fetch output script bytes for outpoint
          String outpointHash = outPoint.getHash().toString();
          Optional<RpcTransaction> outpointRpcOut =
              blockchainDataService.getRpcTransaction(outpointHash);
          if (!outpointRpcOut.isPresent()) {
            log.error("Tx not found for outpoint: " + outpointHash);
            return null;
          }
          return outpointRpcOut.get().getTx().getOutput(outPoint.getIndex()).getScriptBytes();
        };
    return fetchInputOutpointScriptBytes;
  }

  protected byte[] findOpReturnValue(Transaction tx) {
    for (TransactionOutput txOutput : tx.getOutputs()) {
      if (txOutput.getValue().getValue() == 0) {
        try {
          Script script = txOutput.getScriptPubKey();
          if (script.getChunks().size() == 2) {
            // read OP_RETURN
            ScriptChunk scriptChunkOpCode = script.getChunks().get(0);
            if (scriptChunkOpCode.isOpCode()
                && scriptChunkOpCode.equalsOpCode(ScriptOpCodes.OP_RETURN)) {
              // read data
              ScriptChunk scriptChunkPushData = script.getChunks().get(1);
              if (scriptChunkPushData.isPushData()) {
                return scriptChunkPushData.data;
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

  private WhirlpoolServerConfig.ScodeSamouraiFeeConfig validateFeePayload(
      byte[] feePayload, long tx0Time) {
    if (feePayload == null || feePayload.length != WhirlpoolFee.FEE_PAYLOAD_LENGTH) {
      return null;
    }

    // search in configuration
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = getScodeByFeePayload(feePayload);
    if (scodeConfig == null) {
      // scode not found
      return null;
    }
    if (!isScodeValid(scodeConfig, tx0Time)) {
      // scode expired
      return null;
    }
    return scodeConfig;
  }

  public boolean isScodeValid(
      WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig, long tx0Time) {
    // check expiration
    if (scodeConfig.getExpiration() != null) {
      if (tx0Time > scodeConfig.getExpiration()) {
        log.warn(
            "SCode expired: expiration=" + scodeConfig.getExpiration() + ", tx0Time=" + tx0Time);
        return false;
      } else {
        if (log.isDebugEnabled()) {
          log.debug(
              "SCode still valid: expiration="
                  + scodeConfig.getExpiration()
                  + ", tx0Time="
                  + tx0Time);
        }
      }
    }
    return true;
  }

  protected WhirlpoolServerConfig.ScodeSamouraiFeeConfig getScodeByFeePayload(byte[] feePayload) {
    short feePayloadAsShort = Utils.feePayloadBytesToShort(feePayload);
    Optional<Entry<String, WhirlpoolServerConfig.ScodeSamouraiFeeConfig>> feePayloadEntry =
        serverConfig
            .getSamouraiFees()
            .getScodes()
            .entrySet()
            .stream()
            .filter(e -> e.getValue().getPayload() == feePayloadAsShort)
            .findFirst();
    if (!feePayloadEntry.isPresent()) {
      // scode not found
      log.warn("No SCode found for payload=" + Utils.feePayloadBytesToShort(feePayload));
      return null;
    }
    return feePayloadEntry.get().getValue();
  }

  public WhirlpoolServerConfig.ScodeSamouraiFeeConfig getScodeConfigByScode(
      String scode, long tx0Time) {
    String scodeUpperCase = (scode != null ? scode.toUpperCase() : null);
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        serverConfig.getSamouraiFees().getScodes().get(scodeUpperCase);
    if (scodeConfig == null) {
      return null;
    }
    if (!isScodeValid(scodeConfig, tx0Time)) {
      return null;
    }
    return scodeConfig;
  }
}
