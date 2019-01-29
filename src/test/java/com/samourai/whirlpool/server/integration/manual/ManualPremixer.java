package com.samourai.whirlpool.server.integration.manual;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.server.utils.BIP47WalletAndHDWallet;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

public class ManualPremixer {
  private Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  private HD_WalletFactoryJava hdWalletFactory = HD_WalletFactoryJava.getInstance();

  // parameters
  private final NetworkParameters params;
  private int nbMixes;

  // init results
  public HashMap<String, BIP47WalletAndHDWallet> wallets;
  private HashMap<String, JSONObject> payloads;

  // premix results
  public HashMap<String, String> mixables;
  public HashMap<String, ECKey> toPrivKeys;
  public HashMap<String, String> toUTXO;
  public BigInteger biUnitSpendAmount;
  public BigInteger biUnitReceiveAmount;
  public long fee;

  public ManualPremixer(NetworkParameters params, int nbMixes) {
    this.params = params;
    this.nbMixes = nbMixes;
  }

  public void initWallets() throws Exception {
    wallets = new HashMap<String, BIP47WalletAndHDWallet>();
    payloads = new HashMap<String, JSONObject>();

    String words = "all all all all all all all all all all all all";

    //
    // create 5 wallets
    //
    for (int i = 0; i < nbMixes; i++) {
      // init BIP44 wallet
      HD_Wallet hdw =
          hdWalletFactory.restoreWallet(words, "all" + Integer.toString(10 + i), 1, params);
      // init BIP84 wallet for input
      HD_Wallet hdw84 =
          hdWalletFactory.getHD(
              84, hdWalletFactory.computeSeedFromWords(words), hdw.getPassphrase(), params);
      // init BIP47 wallet for input
      BIP47Wallet bip47w = hdWalletFactory.getBIP47(hdw.getSeedHex(), hdw.getPassphrase(), params);

      //
      // collect addresses for tx0 utxos
      //
      String tx0spendFrom =
          bech32Util.toBech32(hdw84.getAccount(0).getChain(0).getAddressAt(0), params);
      System.out.println("tx0 spend address:" + tx0spendFrom);

      //
      // collect wallet payment codes
      //
      String pcode = bip47w.getAccount(0).getPaymentCode();
      BIP47WalletAndHDWallet bip47WalletAndHDWallet = new BIP47WalletAndHDWallet(bip47w, hdw84);
      wallets.put(pcode, bip47WalletAndHDWallet);

      JSONObject payloadObj = new JSONObject();
      payloadObj.put("pcode", pcode);
      payloads.put(pcode, payloadObj);
    }
  }

  public void premix(
      Map<String, String> utxos,
      long swFee,
      long selectedAmount,
      long unitSpendAmount,
      long unitReceiveAmount,
      long fee)
      throws Exception {
    boolean isTestnet = FormatsUtilGeneric.getInstance().isTestNet(params);
    int feeIdx = 0; // address index, in prod get index from Samourai API

    System.out.println("tx0: -------------------------------------------");

    // net miner's fee
    // fee = unitSpendAmount - unitReceiveAmount;

    BigInteger biSelectedAmount = BigInteger.valueOf(selectedAmount);
    biUnitSpendAmount = BigInteger.valueOf(unitSpendAmount);
    biUnitReceiveAmount = BigInteger.valueOf(unitReceiveAmount);
    BigInteger biSWFee = BigInteger.valueOf(swFee);
    BigInteger biChange =
        BigInteger.valueOf(selectedAmount - ((unitSpendAmount * nbMixes) + fee + swFee));

    mixables = new HashMap<String, String>();

    List<BIP47WalletAndHDWallet> _wallets = new ArrayList<BIP47WalletAndHDWallet>();
    _wallets.addAll(wallets.values());

    toPrivKeys = new HashMap<String, ECKey>();
    toUTXO = new HashMap<String, String>();

    //
    // tx0
    //
    for (int i = 0; i < nbMixes; i++) {
      // init BIP84 wallet for input
      HD_Wallet hdw84 = _wallets.get(i).getHdWallet();
      // init BIP47 wallet for input
      BIP47Wallet bip47w = _wallets.get(i).getBip47Wallet();

      String tx0spendFrom =
          bech32Util.toBech32(hdw84.getAccount(0).getChain(0).getAddressAt(0), params);
      ECKey ecKeySpendFrom = hdw84.getAccount(0).getChain(0).getAddressAt(0).getECKey();
      System.out.println("tx0 spend address:" + tx0spendFrom);
      String tx0change =
          bech32Util.toBech32(hdw84.getAccount(0).getChain(1).getAddressAt(0), params);
      System.out.println("tx0 change address:" + tx0change);

      String pcode = bip47w.getAccount(0).getPaymentCode();
      JSONObject payloadObj = payloads.get(pcode);
      payloadObj.put("tx0change", tx0change);
      payloadObj.put("tx0utxo", utxos.get(tx0spendFrom));
      JSONArray spendTos = new JSONArray();
      for (int j = 0; j < nbMixes; j++) {
        String toAddress =
            bech32Util.toBech32(
                hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j), params);
        toPrivKeys.put(
            toAddress,
            hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey());
        spendTos.put(toAddress);
        mixables.put(toAddress, pcode);
      }
      payloadObj.put("spendTos", spendTos);
      //            System.out.println("payload:"  + payloadObj.toString());
      payloads.put(pcode, payloadObj);

      //
      // make tx:
      // 5 spendTo outputs
      // SW fee
      // change
      // OP_RETURN
      //
      List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
      Transaction tx = new Transaction(params);

      //
      // 5 spend outputs
      //
      for (int k = 0; k < spendTos.length(); k++) {
        Pair<Byte, byte[]> pair =
            Bech32Segwit.decode(isTestnet ? "tb" : "bc", (String) spendTos.get(k));
        byte[] scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());

        TransactionOutput txOutSpend =
            new TransactionOutput(
                params, null, Coin.valueOf(biUnitSpendAmount.longValue()), scriptPubKey);
        outputs.add(txOutSpend);
      }

      //
      // 1 change output
      //
      Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", tx0change);
      byte[] _scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
      TransactionOutput txChange =
          new TransactionOutput(params, null, Coin.valueOf(biChange.longValue()), _scriptPubKey);
      outputs.add(txChange);

      // derive fee address
      final String XPUB_FEES =
          "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
      DeterministicKey mKey =
          FormatsUtilGeneric.getInstance().createMasterPubKeyFromXPub(XPUB_FEES);
      DeterministicKey cKey =
          HDKeyDerivation.deriveChildKey(
              mKey, new ChildNumber(0, false)); // assume external/receive chain
      DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIdx, false));
      ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
      String feeAddress = bech32Util.toBech32(feeECKey.getPubKey(), params);
      System.out.println("fee address:" + feeAddress);

      Script outputScript = ScriptBuilder.createP2WPKHOutputScript(feeECKey);
      TransactionOutput txSWFee =
          new TransactionOutput(
              params, null, Coin.valueOf(biSWFee.longValue()), outputScript.getProgram());
      outputs.add(txSWFee);

      // add OP_RETURN output
      byte[] idxBuf = ByteBuffer.allocate(4).putInt(feeIdx).array();
      Script op_returnOutputScript =
          new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(idxBuf).build();
      TransactionOutput txFeeIdx =
          new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
      outputs.add(txFeeIdx);

      feeIdx++; // go to next address index, in prod get index from Samourai API

      //
      //
      //
      // bech32 outputs
      //
      Collections.sort(outputs, new BIP69OutputComparator());
      for (TransactionOutput to : outputs) {
        tx.addOutput(to);
      }

      String utxo = utxos.get(tx0spendFrom);
      String[] s = utxo.split("-");

      Sha256Hash txHash = Sha256Hash.wrap(Hex.decode(s[0]));
      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params, Long.parseLong(s[1]), txHash, Coin.valueOf(biSelectedAmount.longValue()));

      final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(ecKeySpendFrom);
      tx.addSignedInput(outPoint, segwitPubkeyScript, ecKeySpendFrom);

      final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
      final String strTxHash = tx.getHashAsString();

      tx.verify();
      // System.out.println(tx);
      System.out.println("tx hash:" + strTxHash);
      System.out.println("tx hex:" + hexTx + "\n");

      for (TransactionOutput to : tx.getOutputs()) {
        toUTXO.put(Hex.toHexString(to.getScriptBytes()), strTxHash + "-" + to.getIndex());
      }
    }
  }
}
