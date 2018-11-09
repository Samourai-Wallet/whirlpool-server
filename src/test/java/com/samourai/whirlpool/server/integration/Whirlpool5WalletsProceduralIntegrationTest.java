package com.samourai.whirlpool.server.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class Whirlpool5WalletsProceduralIntegrationTest extends WhirlpoolSimpleIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Samourai fee %
  double swFeePct = 0.0175;
  // mix amount
  double mixAmount = 0.5;
  // Samourai fee
  long swFee = ((long) ((mixAmount * swFeePct) * 1e8) + 100000L); // add 0.001 BTC flat fee per mix
  // example utxo amount
  long selectedAmount = 75000000L;
  // mixable amount to be received
  long unitReceiveAmount = 10000000L;
  // net miner's fee for premix
  long premixFee = 900L;
  // net miner's fee for mix
  long mixFee = 1000L;
  // mixable amount plus own fee
  long unitSpendAmount = unitReceiveAmount + mixFee; // 10001000L;

  private Map<String, String> utxos() {
    //
    // fill in manually with the 5 addresses and utxo (hash id-tx position)
    // for 0.51 deposits on 'tx0 spend address' listed in log output above
    //
    Map<String, String> utxos = new HashMap<>();
    utxos.put(
        "tb1q9m8cc0jkjlc9zwvea5a2365u6px3yu646vgez4",
        "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-0");
    utxos.put(
        "tb1qd2rsm2r0m7uzpweyery3cd5m7dg3wr5c29fptj",
        "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-1");
    utxos.put(
        "tb1qnfhmn4vfgprfnsnz2fadfr48cquydjkz4wpfgq",
        "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-2");
    utxos.put(
        "tb1qkqqvhuqsysmla7zqr0069hs93mus09gkegl0am",
        "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-3");
    utxos.put(
        "tb1q7u72vvr64gjwk8kcv2fu2fg9yht9gckm8xt64l",
        "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-4");
    return utxos;
  }

  private String expectedUnsignedStrTxHash =
      "22c150098df9e5885366d393407ace5039f4f684cc1beb4595b616be1e24f5b4";
  private String expectedUnsignedHexTx =
      "0100000005c710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530300000000ffffffffc710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530400000000ffffffff3b0dc9a35dd93690e23806dd60ff211cf7303b149aeeaad9598afe3638b0286d0200000000ffffffff37e23d5c8bae0e9ee43e04db76e240e797ffc28db109ca5a9ad733850753198b0500000000ffffffffbf3c3b118b4ba5238ceccedbcdaf36c1a02520937651e8e74dbd1e8feb8fb0a90600000000ffffffff0580969800000000001600142677dea46953d1ee4a10eae198f2b8967e54f2cf80969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e0280969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e0280969800000000001600144b544522da84eb91ef7a14eca953a0257a132e808096980000000000160014566d060c98844da006d2e9f6e0ea9a4e308cfe1500000000";

  private String expectedStrTxHash = expectedUnsignedStrTxHash;
  private String expectedHexTx =
      "01000000000105c710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530300000000ffffffffc710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530400000000ffffffff3b0dc9a35dd93690e23806dd60ff211cf7303b149aeeaad9598afe3638b0286d0200000000ffffffff37e23d5c8bae0e9ee43e04db76e240e797ffc28db109ca5a9ad733850753198b0500000000ffffffffbf3c3b118b4ba5238ceccedbcdaf36c1a02520937651e8e74dbd1e8feb8fb0a90600000000ffffffff0580969800000000001600142677dea46953d1ee4a10eae198f2b8967e54f2cf80969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e0280969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e0280969800000000001600144b544522da84eb91ef7a14eca953a0257a132e808096980000000000160014566d060c98844da006d2e9f6e0ea9a4e308cfe150247304402205070438ae6e0aaa2c31d17c4bfb5fa7c48c4c397c7374cdae8f06a69122bdc54022048ecac6b61606abd90ab6d9095a1ba495cd16d4904d99802b180c779c98c50500121027660a18888cff932ee515c452b6be0ad322b38c4145d9314faff50d29744909402483045022100c691d3b9618d0e00d60b653284b5c5bc19fa2036e52074603e3587269b8a65f30220651fd071c5f38998e84218a0cba9681c22064829a888a4fbe2831f268c41d297012102596d769a5a2e30f0aec521943d9470232bd80214fa8f341b8a0bda4c15431be8024730440220076fe06977f2c3379a6f612c0b58b55c026bd0aaad1c8575791aa8455a7bf469022040230afecf55b4121d088516d496cb757277b4e70d5568cc3502807d8e8540ee0121029f34d916939e35b46f124e33a30dbe05ec131701aefff4578614f31747a39d7b02483045022100bba4efe3b94805dfc95d314ec5a89aca58308a42b6c3678fdbd11bd540dc2097022075564ea18e7d5a03cf7aca0c3062f703bc9ca37944d3e2564d6f5ef05817fb91012103e6c2c133d6ec9b7511f78dbed8bbef3185c3c5b98b143f14a8681b3226c1a2c202483045022100a15175b8c1aa6c4c9103f322f4f3a3cdeeedc8f5a7e5082053759a102a6bf8ba022015ac166629b8b17cf8383bdfb8ef4da4130fcb35a548b4fcbe469b25a8098a0d012103cae00f3d711715102a747dfd49987e26972ee86ac1b8fbf22e8fb48d0c330d1300000000";

  private Map<String, String> expectedMixables() {
    Map<String, String> expectedMixables = new HashMap<>();
    expectedMixables.put(
        "tb1qsejypermk34cvasm4sscqegypsntz82mp8kc53",
        "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");
    expectedMixables.put(
        "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0",
        "PM8TJimSWvmBwEYZRXFFRsfPr8RZom6d73H9igbJGH7Uus5QM7uHBP8aBK6NdsAMERkx3um686g8sLKCQ3X12SaKSz3hLykSkppjqB42hdyocGSspxpw");
    expectedMixables.put(
        "tb1qf7jsj59qa2kj6fkk6m07df74s87w7955ep3wd4",
        "PM8TJYxbvd6L93QJzC9FkvyqDzD5HbWevxEkH6RAV866b9BdLE7aHiJRseYiH1dVCsDTKDqQoxodg7ZeyAJdLTiDG4p55KLuhctH7sj8C9nGdFha4Do2");
    expectedMixables.put(
        "tb1qat2sjf6kgdzhh24c828c65eva64hd6k4zyuqu0",
        "PM8TJMEqqYnmMpDENAcdocCGiTfw8tj2GxnDRCEuY6EiMpgrn1MDMQEmBvofNQ2eYPe2N4A7zMJBNGmzvksLNms96UrNZnTpMtCmN9M2Mke8xGEZ5qUg");
    expectedMixables.put(
        "tb1q8rmdf6gm7zgd6d34jjx9gnrn6kh0jvwsw3273s",
        "PM8TJYxbvd6L93QJzC9FkvyqDzD5HbWevxEkH6RAV866b9BdLE7aHiJRseYiH1dVCsDTKDqQoxodg7ZeyAJdLTiDG4p55KLuhctH7sj8C9nGdFha4Do2");
    expectedMixables.put(
        "tb1qp4k9w9rehnyxa9s9uncywm5c64cnx6lkn6r8e2",
        "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");
    expectedMixables.put(
        "tb1qr4wdnt5re3dclch6jjygc2t0jxcazzlcqsa0h6",
        "PM8TJYxbvd6L93QJzC9FkvyqDzD5HbWevxEkH6RAV866b9BdLE7aHiJRseYiH1dVCsDTKDqQoxodg7ZeyAJdLTiDG4p55KLuhctH7sj8C9nGdFha4Do2");
    expectedMixables.put(
        "tb1q09g0w306ua6f2h3efc6kq0p3t80y9ueqrfdwl8",
        "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");
    expectedMixables.put(
        "tb1qjara0278vrsr8gvaga7jpy2c9amtgvytr44xym",
        "PM8TJimSWvmBwEYZRXFFRsfPr8RZom6d73H9igbJGH7Uus5QM7uHBP8aBK6NdsAMERkx3um686g8sLKCQ3X12SaKSz3hLykSkppjqB42hdyocGSspxpw");
    expectedMixables.put(
        "tb1ql8d53kjdag74xp8gcegke53f7g9qrzyem8ca62",
        "PM8TJimSWvmBwEYZRXFFRsfPr8RZom6d73H9igbJGH7Uus5QM7uHBP8aBK6NdsAMERkx3um686g8sLKCQ3X12SaKSz3hLykSkppjqB42hdyocGSspxpw");
    expectedMixables.put(
        "tb1qwz7sccnzxwdsf4qgneys42ga6y8mzfqq6yspe8",
        "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");
    expectedMixables.put(
        "tb1q9ag2gngkep8lys0grv85xs3sp2kjf6mdahce2j",
        "PM8TJiLyCuL3qNan1DdAa5ELEemXsSaqHPo3ZjxBXoE4YzyzDiNc5EZXvAy3fmdpSuHjauJqWeh2SJdH4pQueN8S4GkeE1Fgj7KK8esYyHdpJksTC7xb");
    expectedMixables.put(
        "tb1qn5cwkpv7dcm4vaxl5m666ax65heecrlfhhhdmd",
        "PM8TJMEqqYnmMpDENAcdocCGiTfw8tj2GxnDRCEuY6EiMpgrn1MDMQEmBvofNQ2eYPe2N4A7zMJBNGmzvksLNms96UrNZnTpMtCmN9M2Mke8xGEZ5qUg");
    expectedMixables.put(
        "tb1qa3yej37fmqegf2xhty5ugfljfq33qadc8jgsng",
        "PM8TJMEqqYnmMpDENAcdocCGiTfw8tj2GxnDRCEuY6EiMpgrn1MDMQEmBvofNQ2eYPe2N4A7zMJBNGmzvksLNms96UrNZnTpMtCmN9M2Mke8xGEZ5qUg");
    expectedMixables.put(
        "tb1qmjanjayavcg2p5rrhekmkuu8hkxyf2ggl49wm8",
        "PM8TJMEqqYnmMpDENAcdocCGiTfw8tj2GxnDRCEuY6EiMpgrn1MDMQEmBvofNQ2eYPe2N4A7zMJBNGmzvksLNms96UrNZnTpMtCmN9M2Mke8xGEZ5qUg");
    expectedMixables.put(
        "tb1qhvugzxx3l4xrcuc6jf9ep4cmktmy3e4rusajhd",
        "PM8TJiLyCuL3qNan1DdAa5ELEemXsSaqHPo3ZjxBXoE4YzyzDiNc5EZXvAy3fmdpSuHjauJqWeh2SJdH4pQueN8S4GkeE1Fgj7KK8esYyHdpJksTC7xb");
    expectedMixables.put(
        "tb1qtq93vhjn7gzue079awn4ng3ul60vm5jy82drar",
        "PM8TJiLyCuL3qNan1DdAa5ELEemXsSaqHPo3ZjxBXoE4YzyzDiNc5EZXvAy3fmdpSuHjauJqWeh2SJdH4pQueN8S4GkeE1Fgj7KK8esYyHdpJksTC7xb");
    expectedMixables.put(
        "tb1qdvsmz4rul8lzdalnudzmha7764yve0nwsn9na6",
        "PM8TJMEqqYnmMpDENAcdocCGiTfw8tj2GxnDRCEuY6EiMpgrn1MDMQEmBvofNQ2eYPe2N4A7zMJBNGmzvksLNms96UrNZnTpMtCmN9M2Mke8xGEZ5qUg");
    expectedMixables.put(
        "tb1q62vm4lhe9ztkg7l04s0rpt6pypcvnl5fjc4yrp",
        "PM8TJiLyCuL3qNan1DdAa5ELEemXsSaqHPo3ZjxBXoE4YzyzDiNc5EZXvAy3fmdpSuHjauJqWeh2SJdH4pQueN8S4GkeE1Fgj7KK8esYyHdpJksTC7xb");
    expectedMixables.put(
        "tb1qgv80v52cwekqaprvnlzr9hpnkenyfjvgnap0y7",
        "PM8TJiLyCuL3qNan1DdAa5ELEemXsSaqHPo3ZjxBXoE4YzyzDiNc5EZXvAy3fmdpSuHjauJqWeh2SJdH4pQueN8S4GkeE1Fgj7KK8esYyHdpJksTC7xb");
    expectedMixables.put(
        "tb1qafk5aqjyr5lfnvs3j7tykh5pftfwvscvar3307",
        "PM8TJimSWvmBwEYZRXFFRsfPr8RZom6d73H9igbJGH7Uus5QM7uHBP8aBK6NdsAMERkx3um686g8sLKCQ3X12SaKSz3hLykSkppjqB42hdyocGSspxpw");
    expectedMixables.put(
        "tb1qvwpzp7zl4666sl6hkx2d3w7eg9mu5qy98ph3zx",
        "PM8TJWqQcDQGTobv3ea6tZRVV1FRgchd64UBj68T6hkgNLzSaC1V1EyNLqj7uzquuiyYcKbKVWkSoNWie8VjJQiZ4EoYbZ9TomyVhQC7jjJ6MQxBtNPr");
    expectedMixables.put(
        "tb1q0kh2wk5pefnjw75cv303539ucwvquvdcm5w36c",
        "PM8TJYxbvd6L93QJzC9FkvyqDzD5HbWevxEkH6RAV866b9BdLE7aHiJRseYiH1dVCsDTKDqQoxodg7ZeyAJdLTiDG4p55KLuhctH7sj8C9nGdFha4Do2");
    expectedMixables.put(
        "tb1qns0l6u56jhhqxn5wl327zq3xast6apagm6xudc",
        "PM8TJimSWvmBwEYZRXFFRsfPr8RZom6d73H9igbJGH7Uus5QM7uHBP8aBK6NdsAMERkx3um686g8sLKCQ3X12SaKSz3hLykSkppjqB42hdyocGSspxpw");
    expectedMixables.put(
        "tb1qugn3k6l7sek8lekx2mky26rxrn0hw5d8sz6gu3",
        "PM8TJYxbvd6L93QJzC9FkvyqDzD5HbWevxEkH6RAV866b9BdLE7aHiJRseYiH1dVCsDTKDqQoxodg7ZeyAJdLTiDG4p55KLuhctH7sj8C9nGdFha4Do2");
    return expectedMixables;
  }

  private Map<String, String> expectedToUTXO() {
    Map<String, String> expectedToUTXO = new HashMap<>();
    expectedToUTXO.put(
        "001411ebbec7c48b7d42c1a4f6d808f2ac81be8503b7",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-1");
    expectedToUTXO.put(
        "0014ead509275643457baab83a8f8d532ceeab76ead5",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-5");
    expectedToUTXO.put(
        "0014c25132cfe8371e4c4ab5d046db21e18e804a7625",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-1");
    expectedToUTXO.put(
        "00149747d7abc760e033a19d477d2091582f76b4308b",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-3");
    expectedToUTXO.put(
        "001470bd0c6262339b04d4089e490aa91dd10fb12400",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-4");
    expectedToUTXO.put(
        "0014b29f3f6022ed73db4dec0112e859a80b9ce228bf",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-1");
    expectedToUTXO.put(
        "001467819b98a26a732722610c6d8b9607797f2ff340",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-7");
    expectedToUTXO.put(
        "00140d6c571479bcc86e9605e4f0476e98d571336bf6",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-2");
    expectedToUTXO.put(
        "001472c2086160abe8223dffca5c8dcc41cd4baab554",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-1");
    expectedToUTXO.put(
        "0014bb388118d1fd4c3c731a924b90d71bb2f648e6a3",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-5");
    expectedToUTXO.put(
        "00141d5cd9ae83cc5b8fe2fa94888c296f91b1d10bf8",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-2");
    expectedToUTXO.put(
        "00149d30eb059e6e375674dfa6f5ad74daa5f39c0fe9",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-3");
    expectedToUTXO.put(
        "0014430ef65158766c0e846c9fc432dc33b66644c988",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-3");
    expectedToUTXO.put(
        "00147daea75a81ca67277a98645f1a44bcc3980e31b8",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-5");
    expectedToUTXO.put(
        "00141ae19823aab78f8d0a1e6f474fb57922abe339e2",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-7");
    expectedToUTXO.put(
        "6a0400000004", "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-0");
    expectedToUTXO.put(
        "0014d299bafef92897647befac1e30af412070c9fe89",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-6");
    expectedToUTXO.put(
        "6a0400000003", "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-0");
    expectedToUTXO.put(
        "0014847590f655a50b96563805fe823b0b49f1cf93f2",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-7");
    expectedToUTXO.put(
        "00142a64f8ea17ebf6c5501bd0f96f7cf43114e26801",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-2");
    expectedToUTXO.put(
        "6a0400000002", "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-0");
    expectedToUTXO.put(
        "0014dcbb39749d6610a0d063be6dbb7387bd8c44a908",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-4");
    expectedToUTXO.put(
        "0014ec499947c9d83284a8d75929c427f248231075b8",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-6");
    expectedToUTXO.put(
        "6a0400000001", "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-0");
    expectedToUTXO.put(
        "0014f9db48da4dea3d5304e8c6516cd229f20a018899",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-6");
    expectedToUTXO.put(
        "00142f50a44d16c84ff241e81b0f4342300aad24eb6d",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-2");
    expectedToUTXO.put(
        "00147950f745fae774955e394e35603c3159de42f320",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-5");
    expectedToUTXO.put(
        "0014d08a7c707572ace8fcecbc6210e31c177bdf803e",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-7");
    expectedToUTXO.put(
        "00146b21b1547cf9fe26f7f3e345bbf7ded548ccbe6e",
        "8b1953078533d79a5aca09b18dc2ff97e740e276db043ee49e0eae8b5c3de237-2");
    expectedToUTXO.put(
        "0014866440e47bb46b86761bac218065040c26b11d5b",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-6");
    expectedToUTXO.put(
        "0014e2271b6bfe866c7fe6c656ec4568661cdf7751a7",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-6");
    expectedToUTXO.put(
        "0014bc707e65b4a14365576937b1a985e26768e07d88",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-7");
    expectedToUTXO.put(
        "00149c1ffd729a95ee034e8efc55e10226ec17ae87a8",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-4");
    expectedToUTXO.put(
        "001438f6d4e91bf090dd3635948c544c73d5aef931d0",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-3");
    expectedToUTXO.put(
        "0014580b165e53f205ccbfc5eba759a23cfe9ecdd244",
        "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-4");
    expectedToUTXO.put(
        "0014638220f85faeb5a87f57b194d8bbd94177ca0085",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-3");
    expectedToUTXO.put(
        "00144fa50950a0eaad2d26d6d6dfe6a7d581fcef1694",
        "53f48c509bf3bda33bfa06345cde00487107477f7549a9f46658f8fe1aff10c7-4");
    expectedToUTXO.put(
        "6a0400000000", "e9ffe5ec4ed4604d281bf042855a9ba57f7f01b291e4b267f33abc65cebd7b49-0");
    expectedToUTXO.put(
        "0014ea6d4e82441d3e99b21197964b5e814ad2e6430c",
        "6d28b03836fe8a59d9aaee9a143b30f71c21ff60dd0638e29036d95da3c90d3b-5");
    expectedToUTXO.put(
        "0014844b532ecfc18ff817fd26db38c30d1c211c2b0a",
        "a9b08feb8f1ebd4de7e85176932025a0c136afcddbceec8c23a54b8b113b3cbf-1");
    return expectedToUTXO;
  }

  @Test
  public void whirlpool_procedural() throws Exception {
    Map<String, String> expectedMixables = expectedMixables();
    Map<String, String> expectedToUTXO = expectedToUTXO();

    final NetworkParameters params = TestNet3Params.get();
    boolean isTestnet = formatsUtil.isTestNet(params);

    int feeIdx = 0; // address index, in prod get index from Samourai API

    final int nbMixes = 5;
    boolean shuffle = false; // don't shuffle for test predictibility

    final String BIP39_ENGLISH_SHA256 =
        "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";
    HashMap<String, HD_Wallet> wallets = new HashMap<String, HD_Wallet>();
    HashMap<String, BIP47Wallet> bip47Wallets = new HashMap<String, BIP47Wallet>();
    HashMap<String, JSONObject> payloads = new HashMap<String, JSONObject>();
    InputStream wis = HD_Wallet.class.getResourceAsStream("/en_US.txt");
    if (wis != null) {
      MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);

      List<String> words =
          Arrays.asList("all all all all all all all all all all all all".split("\\s+"));
      byte[] seed = mc.toEntropy(words);

      //
      // create 5 wallets
      //
      for (int i = 0; i < nbMixes; i++) {
        // init BIP44 wallet
        HD_Wallet hdw = new HD_Wallet(44, mc, params, seed, "all" + Integer.toString(10 + i), 1);
        // init BIP84 wallet for input
        HD_Wallet hdw84 =
            new HD_Wallet(84, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);
        // init BIP47 wallet for input
        BIP47Wallet bip47w = new BIP47Wallet(47, hdw84, 1);

        //
        // collect addresses for tx0 utxos
        //
        String tx0spendFrom =
            bech32Util.toBech32(hdw84.getAccount(0).getChain(0).getAddressAt(0), params);
        System.out.println("tx0 spend address:" + tx0spendFrom);

        //
        // collect wallet payment codes
        //
        String pcode = bip47w.getAccount(0).getPaymentCode();
        wallets.put(pcode, hdw84);
        bip47Wallets.put(pcode, bip47w);

        JSONObject payloadObj = new JSONObject();
        payloadObj.put("pcode", pcode);
        payloads.put(pcode, payloadObj);
      }
    }
    wis.close();

    System.out.println("tx0: -------------------------------------------");

    //
    // fill in manually with the 5 addresses and utxo (hash id-tx position)
    // for 0.51 deposits on 'tx0 spend address' listed in log output above
    //
    Map<String, String> utxos = utxos();

    BigInteger biSelectedAmount = BigInteger.valueOf(selectedAmount);
    BigInteger biUnitSpendAmount = BigInteger.valueOf(unitSpendAmount);
    BigInteger biUnitReceiveAmount = BigInteger.valueOf(unitReceiveAmount);
    BigInteger biSWFee = BigInteger.valueOf(swFee);
    BigInteger biChange =
        BigInteger.valueOf(selectedAmount - ((unitSpendAmount * nbMixes) + premixFee + swFee));

    HashMap<String, String> mixables = new HashMap<String, String>();

    List<HD_Wallet> _wallets = new ArrayList<HD_Wallet>();
    _wallets.addAll(wallets.values());
    List<BIP47Wallet> _bip47Wallets = new ArrayList<BIP47Wallet>();
    _bip47Wallets.addAll(bip47Wallets.values());

    HashMap<String, ECKey> toPrivKeys = new HashMap<String, ECKey>();
    HashMap<String, String> toUTXO = new HashMap<String, String>();

    //
    // tx0
    //
    for (int i = 0; i < nbMixes; i++) {
      // init BIP84 wallet for input
      HD_Wallet hdw84 = _wallets.get(i);
      // init BIP47 wallet for input
      BIP47Wallet bip47w = _bip47Wallets.get(i);

      String tx0spendFrom =
          bech32Util.toBech32(hdw84.getAccount(0).getChain(0).getAddressAt(0), params);
      ECKey ecKeySpendFrom = hdw84.getAccount(0).getChain(0).getAddressAt(0).getECKey();
      System.out.println("tx0 spend address:" + tx0spendFrom);
      String tx0change =
          bech32Util.toBech32(hdw84.getAccount(0).getChain(1).getAddressAt(0), params);
      System.out.println("tx0 change address:" + tx0change);

      String pcode = bip47w.getAccount(0).getPaymentCode();
      JSONObject payloadObj = payloads.get(pcode);
      payloadObj.put("tx0change", tx0change);
      payloadObj.put("tx0utxo", utxos.get(tx0spendFrom));
      JSONArray spendTos = new JSONArray();
      for (int j = 0; j < nbMixes; j++) {
        String toAddress =
            bech32Util.toBech32(
                hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j), params);
        toPrivKeys.put(
            toAddress,
            hdw84.getAccountAt(Integer.MAX_VALUE - 2).getChain(0).getAddressAt(j).getECKey());
        //                System.out.println("spend to:"  + toAddress + "," +
        // hdw84.getAccountAt(Integer.MAX_VALUE -
        // 2).getChain(0).getAddressAt(j).getECKey().getPrivateKeyAsWiF(params));
        spendTos.put(toAddress);
        mixables.put(toAddress, pcode);
      }
      payloadObj.put("spendTos", spendTos);
      //            System.out.println("payload:"  + payloadObj.toString());
      payloads.put(pcode, payloadObj);

      //
      // make tx:
      // 5 spendTo outputs
      // SW fee
      // change
      // OP_RETURN
      //
      List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
      Transaction tx = new Transaction(params);

      //
      // 5 spend outputs
      //
      for (int k = 0; k < spendTos.length(); k++) {
        Pair<Byte, byte[]> pair =
            Bech32Segwit.decode(isTestnet ? "tb" : "bc", (String) spendTos.get(k));
        byte[] scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());

        TransactionOutput txOutSpend =
            new TransactionOutput(
                params, null, Coin.valueOf(biUnitSpendAmount.longValue()), scriptPubKey);
        outputs.add(txOutSpend);
      }

      //
      // 1 change output
      //
      Pair<Byte, byte[]> pair = Bech32Segwit.decode(isTestnet ? "tb" : "bc", tx0change);
      byte[] _scriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
      TransactionOutput txChange =
          new TransactionOutput(params, null, Coin.valueOf(biChange.longValue()), _scriptPubKey);
      outputs.add(txChange);

      // derive fee address
      final String XPUB_FEES =
          "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
      DeterministicKey mKey = formatsUtil.createMasterPubKeyFromXPub(XPUB_FEES);
      DeterministicKey cKey =
          HDKeyDerivation.deriveChildKey(
              mKey, new ChildNumber(0, false)); // assume external/receive chain
      DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIdx, false));
      ECKey feeECKey = ECKey.fromPublicOnly(adk.getPubKey());
      String feeAddress = bech32Util.toBech32(feeECKey.getPubKey(), params);
      System.out.println("fee address:" + feeAddress);

      Script outputScript = ScriptBuilder.createP2WPKHOutputScript(feeECKey);
      TransactionOutput txSWFee =
          new TransactionOutput(
              params, null, Coin.valueOf(biSWFee.longValue()), outputScript.getProgram());
      outputs.add(txSWFee);

      // add OP_RETURN output
      byte[] idxBuf = ByteBuffer.allocate(4).putInt(feeIdx).array();
      Script op_returnOutputScript =
          new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(idxBuf).build();
      TransactionOutput txFeeIdx =
          new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
      outputs.add(txFeeIdx);

      feeIdx++; // go to next address index, in prod get index from Samourai API

      //
      //
      //
      // bech32 outputs
      //
      Collections.sort(outputs, new BIP69OutputComparator());
      for (TransactionOutput to : outputs) {
        tx.addOutput(to);
      }

      String utxo = utxos.get(tx0spendFrom);
      String[] s = utxo.split("-");

      Sha256Hash txHash = Sha256Hash.wrap(Hex.decode(s[0]));
      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params, Long.parseLong(s[1]), txHash, Coin.valueOf(biSelectedAmount.longValue()));

      final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(ecKeySpendFrom);
      tx.addSignedInput(outPoint, segwitPubkeyScript, ecKeySpendFrom);

      final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
      final String strTxHash = tx.getHashAsString();

      tx.verify();
      // System.out.println(tx);
      System.out.println("tx hash:" + strTxHash);
      System.out.println("tx hex:" + hexTx + "\n");

      for (TransactionOutput to : tx.getOutputs()) {
        toUTXO.put(Hex.toHexString(to.getScriptBytes()), strTxHash + "-" + to.getIndex());
      }
    }

    System.out.println("toUTXO = " + toUTXO);
    System.out.println("txs: -------------------------------------------");

    List<String> mix = new ArrayList<String>();
    mix.addAll(mixables.keySet());

    List<String> mixers = new ArrayList<String>();
    mixers.addAll(mixables.values());

    if (shuffle) {
      Collections.shuffle(mix);
      Collections.shuffle(mixers);
    }

    Transaction tx = new Transaction(params);
    List<TransactionInput> inputs = new ArrayList<TransactionInput>();
    List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();

    tx.clearOutputs();
    for (int i = 0; i < nbMixes; i++) {

      // send from address
      String fromAddress = mix.get(i);
      // sender BIP47 payment code
      String fromPCode = mixables.get(fromAddress);

      String toPCode = mixers.get(i);

      // sender calculates address with receiver's payment code
      PaymentAddress sendAddress =
          bip47Util.getSendAddress(
              bip47Wallets.get(fromPCode), new PaymentCode(toPCode), 0, params);
      // receiver calculates address with sender's payment code
      PaymentAddress receiveAddress =
          bip47Util.getReceiveAddress(
              bip47Wallets.get(toPCode), new PaymentCode(fromPCode), 0, params);

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
    // sort outputs
    //
    Collections.sort(outputs, new BIP69OutputComparator());
    for (TransactionOutput to : outputs) {
      tx.addOutput(to);
    }

    //
    // create 1 mix tx
    //
    Map<String, String> serializedInputs = new HashMap<>();
    for (int i = 0; i < nbMixes; i++) {

      // send from address
      String fromAddress = mix.get(i);

      final ECKey ecKey = toPrivKeys.get(fromAddress);
      final SegwitAddress segwitAddress = new SegwitAddress(ecKey, params);
      final Script redeemScript = segwitAddress.segWitRedeemScript();

      String utxo = toUTXO.get(Hex.toHexString(redeemScript.getProgram()));
      String[] s = utxo.split("-");

      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params,
              Long.parseLong(s[1]),
              Sha256Hash.wrap(Hex.decode(s[0])),
              Coin.valueOf(biUnitSpendAmount.longValue()));
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

    String unsignedStrTxHash = tx.getHashAsString();
    String unsignedHexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    System.out.println("unsignedStrTxHash = " + unsignedStrTxHash);
    System.out.println("unsignedHexTx = " + unsignedHexTx);

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

    final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    final String strTxHash = tx.getHashAsString();

    System.out.println("strTxHash = " + strTxHash);
    System.out.println("hexTx = " + hexTx);

    System.out.println(tx);

    // verify premix result
    // System.out.println("mixables"+Arrays.toString(mixables.keySet().toArray()));
    // System.out.println("mixables"+Arrays.toString(mixables.values().toArray()));
    System.out.println("toUTXO" + Arrays.toString(toUTXO.keySet().toArray()));
    System.out.println("toUTXO" + Arrays.toString(toUTXO.values().toArray()));
    Assert.assertArrayEquals(expectedMixables.keySet().toArray(), mixables.keySet().toArray());
    Assert.assertArrayEquals(expectedMixables.values().toArray(), mixables.values().toArray());

    Assert.assertArrayEquals(expectedToUTXO.keySet().toArray(), toUTXO.keySet().toArray());
    Assert.assertArrayEquals(expectedToUTXO.values().toArray(), toUTXO.values().toArray());

    Assert.assertEquals(expectedMixables.size(), toPrivKeys.size());

    // verify transaction to sign
    Assert.assertEquals(expectedUnsignedStrTxHash, unsignedStrTxHash);
    Assert.assertEquals(expectedUnsignedHexTx, unsignedHexTx);

    // verify final transaction
    Assert.assertEquals(expectedStrTxHash, strTxHash);
    Assert.assertEquals(expectedHexTx, hexTx);
  }
}
