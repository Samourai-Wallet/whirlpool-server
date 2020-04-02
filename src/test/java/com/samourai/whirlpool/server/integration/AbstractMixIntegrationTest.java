package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.*;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractMixIntegrationTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired protected RegisterInputService registerInputService;

  @Autowired protected ConfirmInputService confirmInputService;

  @Autowired protected RegisterOutputService registerOutputService;

  protected TxOutPoint registerInput(Mix mix, String username, int confirmations, boolean liquidity)
      throws Exception {
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey = new ECKey();
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
            inputBalance,
            confirmations);

    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        liquidity,
        "127.0.0.1",
        "headers");
    return txOutPoint;
  }

  protected RSABlindingParameters computeBlindingParams(Mix mix) {
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    return blindingParams;
  }

  protected byte[] registerInputAndConfirmInput(
      Mix mix,
      String username,
      int confirmations,
      boolean liquidity,
      String receiveAddressOrNullForReuseInputAddr,
      RSABlindingParameters blindingParams)
      throws Exception {
    String mixId = mix.getMixId();

    int nbConfirming = mix.getNbConfirmingInputs();

    // REGISTER_INPUT
    TxOutPoint txOutPoint = registerInput(mix, username, confirmations, liquidity);

    boolean queued = (mix.getNbConfirmingInputs() == nbConfirming);
    if (queued) {
      if (log.isDebugEnabled()) {
        log.debug("Not confirming input: it was queued");
      }
      return null;
    }

    // blind bordereau
    if (receiveAddressOrNullForReuseInputAddr == null) {
      // reuse inputAddress
      receiveAddressOrNullForReuseInputAddr = txOutPoint.getToAddress();
    }
    if (blindingParams == null) {
      blindingParams = computeBlindingParams(mix);
    }
    byte[] blindedBordereau =
        clientCryptoService.blind(receiveAddressOrNullForReuseInputAddr, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInputOrQueuePool(
        mixId, username, blindedBordereau, "userHash" + username);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());
    return signedBlindedBordereau;
  }
}
