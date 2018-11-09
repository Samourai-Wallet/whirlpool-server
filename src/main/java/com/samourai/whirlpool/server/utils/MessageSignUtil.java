package com.samourai.whirlpool.server.utils;

import java.security.SignatureException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;

public class MessageSignUtil {

  private static NetworkParameters netParams = null;

  private static MessageSignUtil instance = null;

  private MessageSignUtil() {
    ;
  }

  public static MessageSignUtil getInstance(NetworkParameters params) {

    netParams = params;

    if (instance == null) {
      instance = new MessageSignUtil();
    }

    return instance;
  }

  public boolean verifySignedMessage(String address, String strMessage, String strSignature)
      throws SignatureException {

    if (address == null || strMessage == null || strSignature == null) {
      return false;
    }

    ECKey ecKey = signedMessageToKey(strMessage, strSignature);
    if (ecKey != null) {
      return ecKey.toAddress(netParams).toString().equals(address);
    } else {
      return false;
    }
  }

  public String signMessage(ECKey key, String strMessage) {

    if (key == null || strMessage == null || !key.hasPrivKey()) {
      return null;
    }

    return key.signMessage(strMessage);
  }

  public ECKey signedMessageToKey(String strMessage, String strSignature)
      throws SignatureException {

    if (strMessage == null || strSignature == null) {
      return null;
    }

    return ECKey.signedMessageToKey(strMessage, strSignature);
  }
}
