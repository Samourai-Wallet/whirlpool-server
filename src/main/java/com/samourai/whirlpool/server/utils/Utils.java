package com.samourai.whirlpool.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.rpc.JSONRpcClientServiceImpl;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
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

  public static byte[][] computeWitness(String[] witnesses64) {
    byte[][] witnesses = new byte[witnesses64.length][];
    for (int i = 0; i < witnesses64.length; i++) {
      String witness64 = witnesses64[i];
      witnesses[i] = WhirlpoolProtocol.decodeBytes(witness64);
    }
    return witnesses;
  }

  public static TransactionWitness witnessUnserialize(byte[][] serialized) {
    TransactionWitness witness = new TransactionWitness(serialized.length);
    for (int i = 0; i < serialized.length; i++) {
      witness.setPush(i, serialized[i]);
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
}
