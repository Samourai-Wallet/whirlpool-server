package com.samourai.whirlpool.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static int BTC_TO_SATOSHIS = 100000000;

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

    public static AsymmetricCipherKeyPair generateKeyPair() {
        // Generate a 2048-bit RSA key pair.
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        /*new RsaKeyGenerationParameters(
                RSA_F4,
                secureRandom,
                2048,
                100)
                */
        generator.init(new RSAKeyGenerationParameters(new BigInteger("10001", 16), secureRandom, 2048, 80));
        return generator.generateKeyPair();
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

    public static RpcOut findTxOutput(RpcTransaction rpcTransaction, long utxoIndex) {
        for (RpcOut rpcOut : rpcTransaction.getOuts()) {
            if (rpcOut.getIndex() == utxoIndex) {
                return rpcOut;
            }
        }
        return null;
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

    public static void setLoggerDebug(String logger) {
        LogbackUtils.setLogLevel(logger, Level.DEBUG.toString());
    }

    public static long btcToSatoshis(BigDecimal valueBtc) {
        return valueBtc.multiply(new BigDecimal(BTC_TO_SATOSHIS)).setScale(0).longValueExact();
    }

    public static BigDecimal satoshisToBtc(long satoshis) {
        return new BigDecimal(satoshis).divide(new BigDecimal(BTC_TO_SATOSHIS));
    }
}
