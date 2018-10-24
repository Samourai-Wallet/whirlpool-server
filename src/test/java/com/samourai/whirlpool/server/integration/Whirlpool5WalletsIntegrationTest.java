package com.samourai.whirlpool.server.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.integration.manual.ManualMixer;
import com.samourai.whirlpool.server.integration.manual.ManualPremixer;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.IntFunction;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class Whirlpool5WalletsIntegrationTest extends WhirlpoolSimpleIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // Samourai fee %
  double swFeePct = 0.0175;
  // mix (mix) amount
  double mixAmount = 0.5;
  // Samourai fee
  long swFee = ((long) ((mixAmount * swFeePct) * 1e8) + 100000L); // add 0.001 BTC flat fee per mix
  // example utxo amount
  long selectedAmount = 75000000L;
  // mixable amount to be received
  long unitReceiveAmount = 10000000L;
  // net miner's fee for premix
  long premixFee = 900L;
  // net miner's fee for cryptomix
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
      "7d3b4f3400b94f1dc8859d88654b7d1109b6e1a1b4699d19ddfe275a612bcfc0";
  private String expectedUnsignedHexTx =
      "0100000005c710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530300000000ffffffffc710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530400000000ffffffff3b0dc9a35dd93690e23806dd60ff211cf7303b149aeeaad9598afe3638b0286d0200000000ffffffff37e23d5c8bae0e9ee43e04db76e240e797ffc28db109ca5a9ad733850753198b0500000000ffffffffbf3c3b118b4ba5238ceccedbcdaf36c1a02520937651e8e74dbd1e8feb8fb0a90600000000ffffffff0580969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e028096980000000000160014768233dc2d58b70e59e23ed6b66afdc14338be8d80969800000000001600147ae39ddd118adec5357b4a8a2d8b89ffd841adbc8096980000000000160014ad3c06940131713dca2167e299e91185d11c47268096980000000000160014eae669f72282721a410c5cc15bff402ce4f0d24600000000";

  private String expectedStrTxHash = expectedUnsignedStrTxHash;
  private String expectedHexTx =
      "01000000000105c710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530300000000ffffffffc710ff1afef85866f4a949757f4707714800de5c3406fa3ba3bdf39b508cf4530400000000ffffffff3b0dc9a35dd93690e23806dd60ff211cf7303b149aeeaad9598afe3638b0286d0200000000ffffffff37e23d5c8bae0e9ee43e04db76e240e797ffc28db109ca5a9ad733850753198b0500000000ffffffffbf3c3b118b4ba5238ceccedbcdaf36c1a02520937651e8e74dbd1e8feb8fb0a90600000000ffffffff0580969800000000001600143ff6c564f4df5837962475c97c48c3d00ec87e028096980000000000160014768233dc2d58b70e59e23ed6b66afdc14338be8d80969800000000001600147ae39ddd118adec5357b4a8a2d8b89ffd841adbc8096980000000000160014ad3c06940131713dca2167e299e91185d11c47268096980000000000160014eae669f72282721a410c5cc15bff402ce4f0d246024730440220732bea14fb6f06b871510f46560af35ff4109dbf4873d5730ec4583c1bfbf29302205ca688bcae34308ee3c4c5c81e0107c2725450b9f7d285d4af951dcd2a7f8c650121027660a18888cff932ee515c452b6be0ad322b38c4145d9314faff50d2974490940247304402204bf7a530f4adf677e847d7e0b0e847e017376497a804974742fc95772a05e0da02205ed92b047dbadc9ed7fda0acee321ad16afc476e9698b934bad822bbf9c1f5be012102596d769a5a2e30f0aec521943d9470232bd80214fa8f341b8a0bda4c15431be802483045022100b14c343cc245857e5d1c2ec85502fdde03500d88257e8b8e8c3feb534ea5e6220220513d99d40a88287d9243fab4e7f0aae145132a2a1bb09618e69f9b32e80144bc0121029f34d916939e35b46f124e33a30dbe05ec131701aefff4578614f31747a39d7b024830450221009c732ca9ad6aee39d81356e609a9d24faee41121be108564518e7684383a7a900220528c795b692b0c4fec220e15ccc8187f0a7c14c35d0bb436cfb099a6ae5069f3012103e6c2c133d6ec9b7511f78dbed8bbef3185c3c5b98b143f14a8681b3226c1a2c202483045022100e591f4afc7395e56c2de0a9ebac05ef9fdabb87a968125540ca3a4eaa8c1756c02200a60b52aa01646f6753992d485f8915ddffc7c628b4a14a591328652c2b2b324012103cae00f3d711715102a747dfd49987e26972ee86ac1b8fbf22e8fb48d0c330d1300000000";

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
  public void whirlpool_manual() throws Exception {
    Map<String, String> expectedMixables = expectedMixables();
    Map<String, String> expectedToUTXO = expectedToUTXO();

    NetworkParameters params = TestNet3Params.get();
    final int nbMixes = 5;
    Map<String, String> utxos = utxos();

    /*
     * PREMIX
     */
    ManualPremixer premixer = new ManualPremixer(params, nbMixes);
    premixer.initWallets();
    premixer.premix(utxos, swFee, selectedAmount, unitSpendAmount, unitReceiveAmount, premixFee);

    // verify premix result
    // System.out.println("mixables"+Arrays.toString(mixables.keySet().toArray()));
    // System.out.println("mixables"+Arrays.toString(mixables.values().toArray()));
    System.out.println("toUTXO" + Arrays.toString(premixer.toUTXO.keySet().toArray()));
    System.out.println("toUTXO" + Arrays.toString(premixer.toUTXO.values().toArray()));
    Assert.assertArrayEquals(
        expectedMixables.keySet().toArray(), premixer.mixables.keySet().toArray());
    Assert.assertArrayEquals(
        expectedMixables.values().toArray(), premixer.mixables.values().toArray());

    Assert.assertArrayEquals(expectedToUTXO.keySet().toArray(), premixer.toUTXO.keySet().toArray());
    Assert.assertArrayEquals(expectedToUTXO.values().toArray(), premixer.toUTXO.values().toArray());

    Assert.assertEquals(expectedMixables.size(), premixer.toPrivKeys.size());

    // if there are more mixables than needed, keep first ones
    Map<String, String> firstMixables = getFirstEntries(premixer.mixables, nbMixes);

    /*
     * MIX
     */
    ManualMixer mixer =
        new ManualMixer(
            params, nbMixes, premixer.bip47Wallets, premixer.toPrivKeys, premixer.toUTXO);

    mixer.__setDeterministPaymentCodeMatching(true);
    mixer.mix(firstMixables, premixer.biUnitSpendAmount, premixer.biUnitReceiveAmount);

    Assert.assertEquals(expectedUnsignedStrTxHash, mixer.unsignedStrTxHash);
    Assert.assertEquals(expectedUnsignedHexTx, mixer.unsignedHexTx);

    Assert.assertEquals(expectedStrTxHash, mixer.strTxHash);
    Assert.assertEquals(expectedHexTx, mixer.hexTx);
  }

  @Test
  public void whirlpool() throws Exception {
    Map<String, String> expectedMixables = expectedMixables();
    Map<String, String> expectedToUTXO = expectedToUTXO();

    NetworkParameters params = cryptoService.getNetworkParameters();
    final int nbMixes = 5;
    Map<String, String> utxos = utxos();

    /*
     * PREMIX
     */
    ManualPremixer premixer = new ManualPremixer(params, nbMixes);
    premixer.initWallets();
    Assert.assertEquals(nbMixes, premixer.bip47Wallets.size());

    premixer.premix(utxos, swFee, selectedAmount, unitSpendAmount, unitReceiveAmount, premixFee);

    // verify premix result
    Assert.assertArrayEquals(
        expectedMixables.keySet().toArray(), premixer.mixables.keySet().toArray());
    Assert.assertArrayEquals(
        expectedMixables.values().toArray(), premixer.mixables.values().toArray());

    Assert.assertArrayEquals(expectedToUTXO.keySet().toArray(), premixer.toUTXO.keySet().toArray());
    Assert.assertArrayEquals(expectedToUTXO.values().toArray(), premixer.toUTXO.values().toArray());

    Assert.assertEquals(expectedMixables.size(), premixer.toPrivKeys.size());

    // there are more mixables than needed, keep first ones
    Map<String, String> firstMixables = getFirstEntries(premixer.mixables, nbMixes);

    /*
     * MIX
     */
    List<String> mixKeys = new ArrayList<String>();
    mixKeys.addAll(firstMixables.keySet());

    List<String> mixers = new ArrayList<String>();
    mixers.addAll(firstMixables.values());

    final int NB_CLIENTS = nbMixes;

    // start mix
    long denomination = premixer.biUnitReceiveAmount.longValue();
    long minerFeeMin = mixFee;
    long minerFeeMax = mixFee * 10;
    int mustMixMin = NB_CLIENTS;
    int anonymitySetTarget = NB_CLIENTS;
    int anonymitySetMin = NB_CLIENTS;
    int anonymitySetMax = NB_CLIENTS;
    long anonymitySetAdjustTimeout = 10 * 60; // 10 minutes
    long liquidityTimeout = 60;
    Mix mix =
        __nextMix(
            denomination,
            minerFeeMin,
            minerFeeMax,
            mustMixMin,
            anonymitySetTarget,
            anonymitySetMin,
            anonymitySetMax,
            anonymitySetAdjustTimeout,
            liquidityTimeout);

    AssertMultiClientManager multiClientManager = multiClientManager(NB_CLIENTS, mix);

    // prepare inputs & outputs
    final IntFunction connectClient =
        (int i) -> {
          try {
            // send from address
            final String fromAddress = mixKeys.get(i);

            final ECKey utxoKey = premixer.toPrivKeys.get(fromAddress);
            final SegwitAddress segwitAddress = new SegwitAddress(utxoKey, params);
            final Script redeemScript = segwitAddress.segWitRedeemScript();

            String toUtxo = expectedToUTXO.get(Hex.toHexString(redeemScript.getProgram()));
            String[] utxoHashSplit = toUtxo.split("-");
            String utxoHash = utxoHashSplit[0];
            int utxoIndex = Integer.parseInt(utxoHashSplit[1]);

            final String paymentCode = mixers.get(i);
            final BIP47Wallet bip47Wallet = premixer.bip47Wallets.get(paymentCode);

            multiClientManager.connectWithMock(
                1,
                segwitAddress,
                bip47Wallet,
                0,
                null,
                utxoHash,
                utxoIndex,
                premixer.biUnitSpendAmount.longValue());
          } catch (Exception e) {
            log.error("", e);
            Assert.assertTrue(false);
          }
          return null;
        };

    // connect all clients except one, to stay in CONFIRM_INPUT
    log.info("# Connect first clients...");
    for (int i = 0; i < NB_CLIENTS - 1; i++) {
      final int clientIndice = i;
      taskExecutor.execute(() -> connectClient.apply(clientIndice));
    }
    Thread.sleep(6000);

    // connected clients should have registered their inputs...
    Thread.sleep(2000);
    Assert.assertEquals(MixStatus.CONFIRM_INPUT, mix.getMixStatus());
    Assert.assertEquals(NB_CLIENTS - 1, mix.getInputs().size());

    // connect last client
    Thread.sleep(500);
    log.info("# Connect last client...");
    taskExecutor.execute(() -> connectClient.apply(NB_CLIENTS - 1));
    Thread.sleep(5000);

    // all clients should have registered their inputs
    Assert.assertEquals(NB_CLIENTS, mix.getNbInputs());

    for (ConfirmedInput confirmedInput : mix.getInputs()) {
      RegisteredInput registeredInput = confirmedInput.getRegisteredInput();
      String txOutPointStr =
          registeredInput.getInput().getHash() + "-" + registeredInput.getInput().getIndex();
      Assert.assertTrue(premixer.toUTXO.values().contains(txOutPointStr));
    }

    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING
    Thread.sleep(7000);

    // all clients should have registered their outputs
    // assertStatusRegisterInput(mix, NB_CLIENTS, false);

    // all clients should have signed
    multiClientManager.assertMixStatusSuccess(NB_CLIENTS, false);

    // print transactions
    // Transaction unsignedTx = mix.getTx(); // TODO
    // String unsignedStrTxHash = unsignedTx.getHashAsString();
    // String unsignedHexTx = new String(Hex.encode(unsignedTx.bitcoinSerialize()));
    // System.out.println("unsignedStrTxHash = "+unsignedStrTxHash);
    // System.out.println("unsignedHexTx = "+unsignedHexTx);

    multiClientManager.assertMixTx(expectedStrTxHash, expectedHexTx);
  }

  private Map<String, String> getFirstEntries(Map<String, String> map, int limit) {
    Map<String, String> firstMixables = new HashMap<>();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      firstMixables.put(entry.getKey(), entry.getValue());
      if (firstMixables.size() == limit) {
        break;
      }
    }
    return firstMixables;
  }
}
