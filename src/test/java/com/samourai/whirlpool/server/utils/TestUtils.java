package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CryptoTestUtil;
import com.samourai.whirlpool.client.tx0.UnspentOutputWithKey;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.CryptoService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TestUtils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CryptoService cryptoService;
  protected Bech32UtilGeneric bech32Util;
  protected HD_WalletFactoryJava hdWalletFactory;
  private CryptoTestUtil cryptoTestUtil;

  public TestUtils(
      CryptoService cryptoService,
      Bech32UtilGeneric bech32Util,
      HD_WalletFactoryJava hdWalletFactory,
      CryptoTestUtil cryptoTestUtil) {
    this.cryptoService = cryptoService;
    this.bech32Util = bech32Util;
    this.hdWalletFactory = hdWalletFactory;
    this.cryptoTestUtil = cryptoTestUtil;
  }

  public SegwitAddress generateSegwitAddress() {
    return cryptoTestUtil.generateSegwitAddress(cryptoService.getNetworkParameters());
  }

  public BIP47WalletAndHDWallet generateWallet(byte[] seed, String passphrase) throws Exception {
    // init BIP44 wallet
    HD_Wallet inputWallet =
        hdWalletFactory.getHD(44, seed, passphrase, cryptoService.getNetworkParameters());

    // init BIP47 wallet
    BIP47Wallet bip47InputWallet = new BIP47Wallet(47, inputWallet, 1);

    return new BIP47WalletAndHDWallet(bip47InputWallet, inputWallet);
  }

  public BIP47WalletAndHDWallet generateWallet() throws Exception {
    byte seed[] = cryptoTestUtil.generateSeed();
    return generateWallet(seed, "test");
  }

  public void assertPool(int nbMustMix, int nbLiquidity, Pool pool) {
    Assert.assertEquals(nbMustMix, pool.getMustMixQueue().getSize());
    Assert.assertEquals(nbLiquidity, pool.getLiquidityQueue().getSize());
  }

  public void assertPoolEmpty(Pool pool) {
    assertPool(0, 0, pool);
  }

  public void assertMix(int nbInputsConfirmed, int confirming, Mix mix) {
    Assert.assertEquals(nbInputsConfirmed, mix.getNbInputs());
    Assert.assertEquals(confirming, mix.getNbConfirmingInputs());
  }

  public void assertMix(int nbInputs, Mix mix) {
    assertMix(nbInputs, 0, mix);
  }

  public void assertMixEmpty(Mix mix) {
    assertMix(0, mix);
  }

  public AsymmetricCipherKeyPair readPkPEM(String pkPem) throws Exception {
    PemReader pemReader =
        new PemReader(new InputStreamReader(new ByteArrayInputStream(pkPem.getBytes())));
    PemObject pemObject = pemReader.readPemObject();

    RSAPrivateCrtKeyParameters privateKeyParams =
        (RSAPrivateCrtKeyParameters) PrivateKeyFactory.createKey(pemObject.getContent());
    return new AsymmetricCipherKeyPair(privateKeyParams, privateKeyParams); // TODO
  }

  public String computePkPEM(AsymmetricCipherKeyPair keyPair) throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PemWriter writer = new PemWriter(new OutputStreamWriter(os));

    PrivateKeyInfo pkInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyPair.getPrivate());

    writer.writeObject(new PemObject("PRIVATE KEY", pkInfo.getEncoded()));
    writer.flush();
    writer.close();
    String pem = new String(os.toByteArray());
    return pem;
  }

  public ConfirmedInput computeConfirmedInput(String utxoHash, long utxoIndex, boolean liquidity) {
    TxOutPoint outPoint = new TxOutPoint(utxoHash, utxoIndex, 1234, 99, null, "fakeReceiveAddress");
    RegisteredInput registeredInput = new RegisteredInput("foo", liquidity, outPoint, "127.0.0.1");
    ConfirmedInput confirmedInput =
        new ConfirmedInput(registeredInput, null, "userHash" + utxoHash + utxoIndex);
    return confirmedInput;
  }

  public UnspentResponse.UnspentOutput computeUnspentOutput(String hash, int index, long value) {
    UnspentResponse.UnspentOutput spendFrom = new UnspentResponse.UnspentOutput();
    spendFrom.tx_hash = hash;
    spendFrom.tx_output_n = index;
    spendFrom.value = value;
    spendFrom.script = "foo";
    spendFrom.addr = "foo";
    spendFrom.confirmations = 1234;
    spendFrom.xpub = new UnspentResponse.UnspentOutput.Xpub();
    spendFrom.xpub.path = "foo";
    return spendFrom;
  }

  public UnspentResponse.UnspentOutput computeUnspentOutput(TransactionOutPoint outPoint) {
    return computeUnspentOutput(
        outPoint.getHash().toString(), (int) outPoint.getIndex(), outPoint.getValue().value);
  }

  public UnspentOutputWithKey generateUnspentOutputWithKey(long value, NetworkParameters params)
      throws Exception {
    ECKey input0Key = new ECKey();
    String input0OutPointAddress = new SegwitAddress(input0Key, params).getBech32AsString();
    TransactionOutPoint input0OutPoint =
        cryptoTestUtil.generateTransactionOutPoint(input0OutPointAddress, value, params);
    UnspentResponse.UnspentOutput utxo = computeUnspentOutput(input0OutPoint);
    return new UnspentOutputWithKey(utxo, input0Key.getPrivKeyBytes());
  }
}
