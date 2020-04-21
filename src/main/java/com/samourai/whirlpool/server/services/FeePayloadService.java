package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip47.rpc.BIP47Account;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.util.XorMask;
import com.samourai.whirlpool.server.services.fee.WhirlpoolFeeData;
import java.nio.ByteBuffer;
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeePayloadService {
  private static final Logger log = LoggerFactory.getLogger(FeePayloadService.class);

  private static final short FEE_PAYLOAD_VERSION = 1;

  private XorMask xorMask;

  public FeePayloadService(XorMask xorMask) {
    this.xorMask = xorMask;
  }

  /*public byte[] encode(
      int feeIndice,
      short scodePayload,
      short partner,
      String paymentCode,
      NetworkParameters params,
      byte[] input0PrivKey,
      TransactionOutPoint input0OutPoint)
      throws Exception {
    byte[] feePayload = encodeFeePayload(feeIndice, scodePayload, partner);
    if (feePayload == null) {
      return null;
    }
    return xorMask.mask(feePayload, paymentCode, params, input0PrivKey, input0OutPoint);
  }*/

  public WhirlpoolFeeData decode(
      byte[] feePayloadMasked,
      BIP47Account secretAccountBip47,
      TransactionOutPoint input0OutPoint,
      byte[] input0Pubkey) {
    byte[] feePayload =
        xorMask.unmask(feePayloadMasked, secretAccountBip47, input0OutPoint, input0Pubkey);
    if (feePayload == null) {
      return null;
    }
    return decodeFeePayload(feePayload);
  }

  // encode/decode bytes

  public byte[] encodeFeePayload(int feeIndice, short scodePayload, short partner) {
    // feeVersion:short(2) | indice:int(4) | scode:short(2) | feePartner:short(2)
    ByteBuffer byteBuffer =
        ByteBuffer.allocate(WhirlpoolProtocol.FEE_PAYLOAD_LENGTH)
            .putShort(FEE_PAYLOAD_VERSION)
            .putInt(feeIndice)
            .putShort(scodePayload)
            .putShort(partner);
    return byteBuffer.array();
  }

  protected WhirlpoolFeeData decodeFeePayload(byte[] data) {
    if (data.length != WhirlpoolProtocol.FEE_PAYLOAD_LENGTH) {
      log.error(
          "Invalid samouraiFee length: "
              + data.length
              + " vs "
              + WhirlpoolProtocol.FEE_PAYLOAD_LENGTH);
      return null;
    }
    ByteBuffer bb = ByteBuffer.wrap(data);
    short version = bb.getShort();
    if (version != FEE_PAYLOAD_VERSION) {
      log.error("Invalid samouraiFee version: " + version + " vs " + FEE_PAYLOAD_VERSION);
      return null;
    }
    int feeIndice = bb.getInt();
    short scodePayload = bb.getShort();
    short feePartner = bb.getShort();
    return new WhirlpoolFeeData(feeIndice, scodePayload, feePartner);
  }
}
