package com.samourai.whirlpool.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.rpc.JSONRpcClientServiceImpl;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final SecureRandom secureRandom = new SecureRandom();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static int BTC_TO_SATOSHIS = 100000000;
  public static final String PROFILE_TEST = "test";
  public static final String PROFILE_DEFAULT = "default";

  public static String getRandomString(int length) {
    RandomStringGenerator randomStringGenerator =
        new RandomStringGenerator.Builder()
            .filteredBy(CharacterPredicates.ASCII_ALPHA_NUMERALS)
            .build();
    return randomStringGenerator.generate(length);
  }

  public static String generateUniqueString() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static String getRawTx(Transaction tx) {
    return org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize());
  }

  public static TransactionWitness witnessUnserialize64(String[] witnesses64) {
    TransactionWitness witness = new TransactionWitness(witnesses64.length);
    for (int i = 0; i < witnesses64.length; i++) {
      String witness64 = witnesses64[i];
      byte[] witnessItem = WhirlpoolProtocol.decodeBytes(witness64);
      witness.setPush(i, witnessItem);
    }
    return witness;
  }

  public static <K, V extends Comparable<? super V>> Map<K, V> sortMapByValue(Map<K, V> map) {
    Comparator<Map.Entry<K, V>> comparator = Map.Entry.comparingByValue();
    Map<K, V> sortedMap =
        map.entrySet()
            .stream()
            .sorted(comparator)
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    return sortedMap;
  }

  public static <K, V> Map.Entry<K, V> getRandomEntry(Map<K, V> map) {
    Object entries[] = map.entrySet().toArray();
    return (Map.Entry<K, V>) entries[secureRandom.nextInt(entries.length)];
  }

  public static String computeInputId(TxOutPoint outPoint) {
    return outPoint.getHash() + ":" + outPoint.getIndex();
  }

  public static String toJsonString(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (Exception e) {
      log.error("", e);
    }
    return null;
  }

  public static void setLoggerDebug(String logger) {
    LogbackUtils.setLogLevel(logger, Level.DEBUG.toString());
  }

  public static BigDecimal satoshisToBtc(long satoshis) {
    return new BigDecimal(satoshis).divide(new BigDecimal(BTC_TO_SATOSHIS));
  }

  public static void testJsonRpcClientConnectivity(RpcClientService rpcClientService)
      throws Exception {
    // connect to rpc node
    if (!JSONRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass())) {
      throw new Exception("Expected rpcClient of type " + JSONRpcClientServiceImpl.class.getName());
    }
    if (!rpcClientService.testConnectivity()) {
      throw new Exception("rpcClient couldn't connect to bitcoin node");
    }
  }

  public static String getToAddressBech32(
      TransactionOutput out, Bech32UtilGeneric bech32Util, NetworkParameters params) {
    Script script = out.getScriptPubKey();
    if (script.isOpReturn()) {
      return null;
    }
    if (script.isSentToP2WPKH() || script.isSentToP2WSH()) {
      try {
        return bech32Util.getAddressFromScript(script, params);
      } catch (Exception e) {
        log.error("toAddress failed for bech32", e);
      }
    }
    try {
      return script.getToAddress(params).toBase58();
    } catch (Exception e) {
      log.error("unable to find toAddress", e);
    }
    return null;
  }

  public static byte[] feePayloadShortToBytes(short feePayloadAsShort) {
    return ByteBuffer.allocate(WhirlpoolFee.FEE_PAYLOAD_LENGTH).putShort(feePayloadAsShort).array();
  }

  public static short feePayloadBytesToShort(byte[] feePayload) {
    return ByteBuffer.wrap(feePayload).getShort();
  }

  public static String computeXpubAddressBech32(int x, String xpub, NetworkParameters params) {
    DeterministicKey mKey = FormatsUtilGeneric.getInstance().createMasterPubKeyFromXPub(xpub);
    DeterministicKey cKey =
        HDKeyDerivation.deriveChildKey(
            mKey, new ChildNumber(0, false)); // assume external/receive chain
    DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(x, false));
    ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
    String feeAddressBech32 =
        Bech32UtilGeneric.getInstance().toBech32(feeECKey.getPubKey(), params);
    return feeAddressBech32;
  }

  public static String bytesToBinaryString(byte[] bytes) {
    List<String> strs = new ArrayList<>();
    for (byte b : bytes) {
      String str = String.format("%8s", Integer.toBinaryString((b + 256) % 256)).replace(' ', '0');
      strs.add(str);
    }
    return StringUtils.join(strs.toArray(), " ");
  }

  public static byte[] bytesFromBinaryString(String str) {
    String[] bytesStrs = str.split(" ");
    byte[] result = new byte[bytesStrs.length];
    for (int i = 0; i < bytesStrs.length; i++) {
      String byteStr = bytesStrs[i];
      result[i] = (byte) (int) (Integer.valueOf(byteStr, 2));
    }
    return result;
  }
}
