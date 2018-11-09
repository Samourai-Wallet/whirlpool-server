package com.samourai.whirlpool.server.integration.manual;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.impl.Bip47Util;
import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import java.math.BigInteger;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;

public class ManualMixer {
  protected Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();

  // parameters
  private final int nbMixes;
  private final NetworkParameters params;
  private HashMap<String, BIP47Wallet> bip47Wallets;
  private HashMap<String, ECKey> toPrivKeys;
  private HashMap<String, String> toUTXO;
  private boolean deterministPaymentCodeMatching; // for testing purpose only

  // unsigned tx
  public String unsignedHexTx;
  public String unsignedStrTxHash;

  // mix results
  public String hexTx;
  public String strTxHash;

  public ManualMixer(
      NetworkParameters params,
      int nbMixes,
      HashMap<String, BIP47Wallet> bip47Wallets,
      HashMap<String, ECKey> toPrivKeys,
      HashMap<String, String> toUTXO) {
    this.params = params;
    this.nbMixes = nbMixes;

    this.bip47Wallets = bip47Wallets;
    this.toPrivKeys = toPrivKeys;
    this.toUTXO = toUTXO;
    this.deterministPaymentCodeMatching = false;
  }

  private List<String> deterministShift(List<String> mixers) {
    // 0 -> 1, 1->2, 2->3, 3->4, 4->0
    List<String> shuffledMixers = new ArrayList<>();
    for (int i = 1; i < nbMixes; i++) {
      shuffledMixers.add(mixers.get(i));
    }
    shuffledMixers.add(mixers.get(0));
    return shuffledMixers;
  }

  private static class PaymentCodeAndAddress {
    private String paymentCode;
    private String address;

    public PaymentCodeAndAddress(String paymentCode, String address) {
      this.paymentCode = paymentCode;
      this.address = address;
    }

    public String getPaymentCode() {
      return paymentCode;
    }

    public String getAddress() {
      return address;
    }

    @Override
    public String toString() {
      return paymentCode;
    }
  }

  public void mix(
      Map<String, String> mixables, BigInteger biUnitSpendAmount, BigInteger biUnitReceiveAmount)
      throws Exception {
    boolean isTestnet = FormatsUtilGeneric.getInstance().isTestNet(params);
    List<PaymentCodeAndAddress> paymentCodes = new ArrayList<>();
    for (Map.Entry<String, String> entry : mixables.entrySet()) {
      paymentCodes.add(new PaymentCodeAndAddress(entry.getValue(), entry.getKey()));
    }

    if (deterministPaymentCodeMatching) {
      // determinist paymentCodes matching for tests reproductibility
      // sort by paymentCode
      Collections.sort(paymentCodes, Comparator.comparing(PaymentCodeAndAddress::getPaymentCode));
    }

    Transaction tx = new Transaction(params);
    List<TransactionInput> inputs = new ArrayList<TransactionInput>();
    List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();

    tx.clearOutputs();
    for (int i = 0; i < nbMixes; i++) {

      // sender BIP47 payment code
      int iFromPaymentCode = (i == paymentCodes.size() - 1 ? 0 : i + 1);
      String fromPCode = paymentCodes.get(iFromPaymentCode).getPaymentCode();

      // receiver BIP47 payment code
      int iToPaymentCode = i;
      String toPCode = paymentCodes.get(iToPaymentCode).getPaymentCode();

      // sender calculates address with receiver's payment code
      PaymentAddress sendAddress =
          Bip47Util.getInstance()
              .getSendAddress(bip47Wallets.get(fromPCode), new PaymentCode(toPCode), 0, params);
      // receiver calculates address with sender's payment code
      PaymentAddress receiveAddress =
          Bip47Util.getInstance()
              .getReceiveAddress(bip47Wallets.get(toPCode), new PaymentCode(fromPCode), 0, params);

      // sender calculates from pubkey
      String addressFromSender =
          bech32Util.toBech32(sendAddress.getSendECKey().getPubKey(), params);
      // receiver can calculate from privkey
      String addressToReceiver =
          bech32Util.toBech32(receiveAddress.getReceiveECKey().getPubKey(), params);
      Assert.assertEquals(addressFromSender, addressToReceiver);

      Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", addressToReceiver);
      byte[] scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
      TransactionOutput txOutSpend =
          new TransactionOutput(
              params, null, Coin.valueOf(biUnitReceiveAmount.longValue()), scriptPubKey);
      outputs.add(txOutSpend);
    }

    //
    // BIP69 sort outputs
    //
    Collections.sort(outputs, new BIP69OutputComparator());
    for (TransactionOutput to : outputs) {
      tx.addOutput(to);
    }

    //
    // create 1 mix tx
    //
    Map<String, String> serializedInputs = new HashMap<String, String>();
    for (int i = 0; i < nbMixes; i++) {

      // send from address
      int iFromPaymentCode = (i == paymentCodes.size() - 1 ? 0 : i + 1);
      String fromAddress = paymentCodes.get(iFromPaymentCode).getAddress();

      final ECKey ecKey = toPrivKeys.get(fromAddress);
      final SegwitAddress segwitAddress = new SegwitAddress(ecKey, params);
      final Script redeemScript = segwitAddress.segWitRedeemScript();

      String utxo = toUTXO.get(Hex.toHexString(redeemScript.getProgram()));
      String[] s = utxo.split("-");

      Sha256Hash txHash = Sha256Hash.wrap(Hex.decode(s[0]));
      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params, Long.parseLong(s[1]), txHash, Coin.valueOf(biUnitSpendAmount.longValue()));
      TransactionInput txInput =
          new TransactionInput(
              params,
              null,
              new byte[] {} /*redeemScript.getProgram()*/,
              outPoint,
              Coin.valueOf(biUnitSpendAmount.longValue()));

      serializedInputs.put(Hex.toHexString(txInput.bitcoinSerialize()), fromAddress);
      inputs.add(txInput);
    }

    //
    // BIP69 sort inputs
    //
    Collections.sort(inputs, new BIP69InputComparator());
    for (TransactionInput ti : inputs) {
      tx.addInput(ti);
    }
    unsignedStrTxHash = tx.getHashAsString();
    unsignedHexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    System.out.println("UNSIGNED TX: " + tx);
    System.out.println("unsignedStrTxHash = " + unsignedStrTxHash);
    System.out.println("unsignedHexTx = " + unsignedHexTx);

    System.out.println("txs: -------------------------------------------");

    //
    // sign mix tx
    //
    for (int i = 0; i < tx.getInputs().size(); i++) {

      // from address
      String fromAddress =
          serializedInputs.get(Hex.toHexString(tx.getInputs().get(i).bitcoinSerialize()));

      final ECKey ecKey = toPrivKeys.get(fromAddress);
      final SegwitAddress segwitAddress = new SegwitAddress(ecKey, params);
      final Script redeemScript = segwitAddress.segWitRedeemScript();
      final Script scriptCode = redeemScript.scriptCode();

      TransactionSignature sig =
          tx.calculateWitnessSignature(
              i,
              ecKey,
              scriptCode,
              Coin.valueOf(biUnitSpendAmount.longValue()),
              Transaction.SigHash.ALL,
              false);
      final TransactionWitness witness = new TransactionWitness(2);
      witness.setPush(0, sig.encodeToBitcoin());
      witness.setPush(1, ecKey.getPubKey());
      tx.setWitness(i, witness);

      Assert.assertEquals(0, tx.getInput(i).getScriptBytes().length);
    }

    tx.verify();

    strTxHash = tx.getHashAsString();
    hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    System.out.println("FINAL TX: " + tx);
    System.out.println("strTxHash = " + strTxHash);
    System.out.println("hexTx = " + hexTx);
  }

  public void __setDeterministPaymentCodeMatching(boolean deterministPaymentCodeMatching) {
    this.deterministPaymentCodeMatching = deterministPaymentCodeMatching;
  }
}
