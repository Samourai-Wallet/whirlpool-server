package com.samourai.whirlpool.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.services.rpc.JSONRpcClientServiceImpl;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

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

    public static boolean listEqualsIgnoreOrder(List first, List second) {
        return (first.size() == second.size() &&
                first.containsAll(second) && second.containsAll(first));
    }

    public static String sha512Hex(byte[] data) {
        return org.bitcoinj.core.Utils.HEX.encode(DigestUtils.getSha512Digest().digest(data));
    }

    public static Integer findTxInput(Transaction tx, String hash, long index) {
        for (int i=0; i<tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInput(i);
            TransactionOutPoint outPoint = input.getOutpoint();
            if (outPoint.getHash().toString().equals(hash) && outPoint.getIndex() == index) {
                return i;
            }
        }
        return null;
    }

    public static Optional<RpcOut> findTxOutput(RpcTransaction rpcTransaction, long utxoIndex) {
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getIndex() == utxoIndex) {
                return Optional.of(rpcOut);
            }
        }
        return Optional.empty();
    }

    public static String getRawTx(Transaction tx) {
        return org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize());
    }

    public static TransactionWitness witnessUnserialize(byte[][] serialized) {
        TransactionWitness witness = new TransactionWitness(serialized.length);
        for (int i=0; i<serialized.length; i++) {
            witness.setPush(i, serialized[i]);
        }
        return witness;
    }

    public static <K,V extends Comparable<? super V>> Map<K,V> sortMapByValue(Map<K,V> map) {
        Comparator<Map.Entry<K,V>> comparator = Map.Entry.comparingByValue();
        Map<K,V> sortedMap = map.entrySet().stream()
                        .sorted(comparator)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        return sortedMap;
    }

    public static <K,V> Map.Entry<K,V> getRandomEntry(Map<K,V> map) {
        Object entries[] = map.entrySet().toArray();
        return (Map.Entry<K,V>) entries[secureRandom.nextInt(entries.length)];
    }

    public static String computeInputId(TxOutPoint outPoint) {
        return outPoint.getHash()+":"+outPoint.getIndex();
    }

    public static String toJsonString(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        }
        catch(Exception e) {
            log.error("", e);
        }
        return null;
    }

    public static <T> T fromJsonString(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        }
        catch(Exception e) {
            log.error("", e);
        }
        return null;
    }

    public static void setLoggerDebug(String logger) {
        LogbackUtils.setLogLevel(logger, Level.DEBUG.toString());
    }

    public static long btcToSatoshis(BigDecimal valueBtc) {
        return valueBtc.multiply(new BigDecimal(BTC_TO_SATOSHIS)).setScale(0).longValueExact();
    }

    public static BigDecimal satoshisToBtc(long satoshis) {
        return new BigDecimal(satoshis).divide(new BigDecimal(BTC_TO_SATOSHIS));
    }

    public static Optional<RpcOutWithTx> getRpcOutWithTx(RpcTransaction tx, long index) {
        Optional<RpcOut> rpcOutResponse = Utils.findTxOutput(tx, index);
        if (!rpcOutResponse.isPresent()) {
            log.error("UTXO not found: " + tx.getTxid() + "-" + index);
            return Optional.empty();
        }
        RpcOutWithTx rpcOutWithTx = new RpcOutWithTx(rpcOutResponse.get(), tx);
        return Optional.of(rpcOutWithTx);
    }

    public static void testJsonRpcClientConnectivity(RpcClientService rpcClientService) throws Exception {
        // connect to rpc node
        if (!JSONRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass())) {
            throw new Exception("Expected rpcClient of type " + JSONRpcClientServiceImpl.class.getName());
        }
        if (!rpcClientService.testConnectivity()) {
            throw new Exception("rpcClient couldn't connect to bitcoin node");
        }
    }

    public static String getToAddressBech32(TransactionOutput out, Bech32UtilGeneric bech32Util, NetworkParameters params) {
        Script script = out.getScriptPubKey();
        if (script.isOpReturn()) {
            return null;
        }
        if (script.isSentToP2WPKH() || script.isSentToP2WSH()) {
            try {
                return bech32Util.getAddressFromScript(script, params);
            } catch(Exception e) {
                log.error("toAddress failed for bech32", e);
            }
        }
        try {
            return script.getToAddress(params).toBase58();
        } catch(Exception e) {
            log.error("unable to find toAddress", e);
        }
        return null;
    }
}
