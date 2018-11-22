package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.SecretPointJava;
import com.samourai.wallet.bip47.rpc.secretPoint.ISecretPoint;
import com.samourai.wallet.hd.HD_Address;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final SecureRandom secureRandom = new SecureRandom();
  private NetworkParameters networkParameters;

  public CryptoService(NetworkParameters networkParameters) {
    this.networkParameters = networkParameters;

    // initialize bitcoinj context
    new Context(networkParameters);
  }

  public AsymmetricCipherKeyPair generateKeyPair() {
    // Generate a 2048-bit RSA key pair.
    RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
    /*new RsaKeyGenerationParameters(
    RSA_F4,
    secureRandom,
    2048,
    100)
    */
    generator.init(
        new RSAKeyGenerationParameters(new BigInteger("10001", 16), secureRandom, 2048, 80));
    return generator.generateKeyPair();
  }

  public PublicKey computePublicKey(AsymmetricCipherKeyPair keyPair) throws Exception {
    RSAKeyParameters pubKey = (RSAKeyParameters) keyPair.getPublic();
    RSAPublicKeySpec rsaPublicKeySpec =
        new RSAPublicKeySpec(pubKey.getModulus(), pubKey.getExponent());
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePublic(rsaPublicKeySpec);
  }

  public byte[] signBlindedOutput(byte[] blindedOutput, AsymmetricCipherKeyPair keyPair) {
    // sign blinded output
    RSAEngine engine = new RSAEngine();
    engine.init(false, keyPair.getPrivate());
    return engine.processBlock(blindedOutput, 0, blindedOutput.length);
  }

  public boolean verifyUnblindedSignedBordereau(
      String revealedBordereau, byte[] unblindedSignedBordereau, AsymmetricCipherKeyPair keyPair) {
    PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), 32);
    signer.init(false, keyPair.getPublic());

    byte[] data = revealedBordereau.getBytes();
    signer.update(data, 0, data.length);
    return signer.verifySignature(unblindedSignedBordereau);
  }

  public boolean isValidTxHash(String txHash) {
    try {
      Sha256Hash.wrap(txHash);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public NetworkParameters getNetworkParameters() {
    return networkParameters;
  }

  public byte[] xorUnmask(byte[] dataMasked, BIP47Wallet secretWalletBip47, TransactionOutPoint input0OutPoint, byte[] input0Pubkey) {
    HD_Address notifAddressServer = secretWalletBip47.getAccount(0).getNotificationAddress();
    try {
      ISecretPoint secretPointUnmask =
          new SecretPointJava(notifAddressServer.getECKey().getPrivKeyBytes(), input0Pubkey);
      byte[] dataUnmasked =
          PaymentCode.xorMask(dataMasked, secretPointUnmask, input0OutPoint);
      return dataUnmasked;
    } catch (Exception e) {
      log.error("", e);
      return null;
    }
  }

  public Integer xorUnmaskInteger(byte[] dataMasked, BIP47Wallet secretWalletBip47, TransactionOutPoint input0OutPoint, byte[] input0Pubkey) {
    byte[] dataUnmaskedBytes = xorUnmask(dataMasked, secretWalletBip47, input0OutPoint, input0Pubkey);
    if (dataUnmaskedBytes == null) {
      return null;
    }
    int dataUnmasked = ByteBuffer.wrap(dataUnmaskedBytes).getInt();
    return dataUnmasked;
  }
}
