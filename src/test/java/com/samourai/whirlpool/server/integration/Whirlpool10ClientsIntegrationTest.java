package com.samourai.whirlpool.server.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class Whirlpool10ClientsIntegrationTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void whirlpool_10clients() throws Exception {
    final int NB_CLIENTS = 10;
    // start mix
    long denomination = 200000000;
    long minerFeeMin = 100;
    long minerFeeMax = 10000;
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

    // connect all clients except one, to stay in CONFIRM_INPUT
    log.info("# Connect first clients...");
    for (int i = 0; i < NB_CLIENTS - 1; i++) {
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false, 1));
    }

    // connected clients should have registered their inputs...
    multiClientManager.assertMixStatusConfirmInput(NB_CLIENTS - 1, false);

    // connect last client
    log.info("# Connect last client...");
    taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false, 1));

    // all clients should have registered their inputs
    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING

    // all clients should have registered their outputs and signed
    multiClientManager.assertMixStatusSuccess(NB_CLIENTS, false);
  }

  @Test
  public void whirlpool_10clientsMixedAmounts() throws Exception {
    final int NB_CLIENTS = 10;
    // start mix
    long denomination = 1000000;
    long minerFeeMin = 100;
    long minerFeeMax = 10000;
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
    long premixBalanceMin = mix.getPool().computePremixBalanceMin(false);

    // connect all clients except one, to stay in CONFIRM_INPUT
    log.info("# Connect first clients...");
    for (int i = 0; i < NB_CLIENTS - 1; i++) {
      long inputBalance = premixBalanceMin + (100 * i); // mixed amounts
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(1, inputBalance));
    }

    // connected clients should have registered their inputs...
    multiClientManager.assertMixStatusConfirmInput(NB_CLIENTS - 1, false);

    // connect last client
    log.info("# Connect last client...");
    taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false, 1));

    // all clients should have registered their inputs
    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING

    // all clients should have registered their outputs and signed
    multiClientManager.assertMixStatusSuccess(NB_CLIENTS, false);

    String expectedTxHash = "f3f316307d7f2309a0a2e77d98d7b82c99c0d853bf9c3c26327ef5123d93a8c0";
    String expectedTxHex =
        "0100000000010a690c98bad6b5ae35fece5201520127461bcda4e888cb8fe0f85c307a0faf0f020000000000ffffffff78289d40336201d100e38158d0099f107f5743253affa3fcd890d8c756ce4c350000000000ffffffff84c24b6c50dc4e6115b84ed08cdc9ab31ec073f44957d309d45eb0b1ac885c350000000000ffffffff58d23dc642ca0345293d74957ebbbe332e434e39488c4c550994b41ec7ecc36e0000000000fffffffff33a214026bbe3319956285e767e298864e78d9390ef8afefe7c698a5b2920860000000000ffffffffe57da6f1d1a7c1017fb7696031c34a52249142d9f46a6cf43acaa286e23041970000000000ffffffff548b2dc89e780d68cc14e9896da694681eddbeb2d98f463631cfc364989cd2c50000000000ffffffff814f352fac9332d81627a13601b3bb9748b26fa9c243322288f5d5d07b3420df0000000000ffffffff3a5c9b40c8c32e0e06b0514db54b933224745fe9ae5204d64cf1a4657ba1fbe80000000000ffffffff1ca0dff18083ff1e0304f450174b457e229c4330156ab059e34f61f5dab92af60000000000ffffffff0a40420f0000000000160014321569979fbd3db4175dd458341fa827c4cb420640420f00000000001600143bf2635e87f6617a3ad7d9b4d841c8c95344113540420f00000000001600144431ebab8be508465faf28cacc66ec62acfde15a40420f000000000016001460d068fbe3aed12a3d611035cf00fc2b785e090d40420f00000000001600146e25e9854ae989db64bd2a381cdac246f843bf6d40420f00000000001600146f27c0b5d14e02b63c29954cb780a748d08e744140420f000000000016001474d4a91bc345b548d615af1f9fb633a18d57965740420f0000000000160014b73d3362db4176f92835e5c7f6ec7d2803a19aea40420f0000000000160014c17c4d69db132ecfa5fc3c7a26f5a16583e7869e40420f0000000000160014f1edae092009f084fb2bad1b0c11beac848b93630248304502210082ad6d65b6301b430a1d84972fc45d2e101139e06e036e8d5923461f2ec3cc3402202f5222c8038f0ce4e875904d731abab2d8e46998f2391eec7cde51935d0ba67a0121025814aaf8a30051f931d1c45edcf2ef5d1b13a7d7442726fb6c1429de69e8cac2024730440220414a9e86394e4f85a431949d7a6ab00305d636a6dfc23cfea23549128cb5158302203cc4309c4fe39c100394e1c18d968257585db50aaeb96d1213de5f086af243820121030faa8ed1e84acb03d1360bd42c840f93dac64b4cf906639e0b5d2c7a053edad902473044022010381b3e7fda68d5307374a7fe99e38ff0861dde970d2846f16acea80ebf77dd02204d6c9fb2118245809d29edf4a73f1ed6da1fe3d13e3243f5465ffe1014ee74f30121020c903e02f0afb0a2e571880eba1168d255218a6563553e99975dca2cc56d9f340247304402205e41ac07719449854af67959d5afadc68c4268ed5fa1a4e2a15864e1b19e02c90220097064a4869eabd5f4b885678b608612eb278616dd92321476920b4a150259c501210357894cfcae2040c2aced1fb6312ba5f335153b17120ef65abdc25cd1bf4fc04d02483045022100edb7e9e82672513f6e84b620c9c8c93a325fc03983af49911702b26588604a440220463aebc11778a220e36758ba67b4c1249605c15ac09eab83266936007f0b219f012102394c9a7e8e77009b60757f00ce99c0869b1fc73f83c5b3b6322b5055d73228ee0247304402205519fe0b29262d905357c1173d8fd679ebba3450e47f59de7280a1161482db2102205de7c7edd95555a1235c04ef04f25abe0005e77b357a7a97497ced9d98693bea012102479dcca58c680cecbde28b988536083eb9b1781ae122de13babe8713b5d4456802483045022100d5aa71d61e202dd843cdb153646b72b9d37f030a228b806cde11afd11e6b932102201bb725d4db40ec3e58929afd8f542a3f2aa994b9d4f2ccf0af7331595458b2f6012102d36c2f276c57a4d2bfc86ddadf2a0416cc29c99b75fec86dda58a04a8cc699f80247304402200a0784f55ecb69dc128223804a1d226cc0400b9e5001e06fc54b7308a1ae916b0220226e36032607a7ac70692c1a63913b6bc4fbfd1fbe6aac1877f53ce95c7c1386012102beea7cfcbaab803335ecb4a736a78179fe8f009d60c09739a23bc9b16c5e30be0247304402204dfea57a9039840ed1dc0d421b3814d43af30191601c50a985fe3c1ccdce831602201daf11a8f2edb2655eeee097b7d74efdb51d4e531bf76b47c5c6d85245ca3226012103974351c609f1025cb4cc0e6072dea57cc7be269b556bda5d2dc22dc03f9aab7d024730440220674ea15e1de6099ac3812834f01e630889406e3fdd0129265b049e880ceed27f0220576044c6ec054947e768a8b4116c8c7a86696d3658a364f5bf774f7e2e2ecaca0121021c4f226841121670bbfb4f77c1de9c7721ecf2e068c5b69cf86e2a26a3fa5aa300000000";
    // multiClientManager.assertMixTx(expectedTxHash, expectedTxHex); // TODO determinist utxokey
    // generator
  }

  @Test
  public void whirlpool_3MustMix2Liquidities() throws Exception {
    final int NB_CLIENTS = 5;
    // start mix
    long denomination = 1000000;
    long minerFeeMin = 100;
    long minerFeeMax = 10000;
    int mustMixMin = 3;
    int anonymitySetTarget = NB_CLIENTS;
    int anonymitySetMin = NB_CLIENTS;
    int anonymitySetMax = NB_CLIENTS;
    long anonymitySetAdjustTimeout = 10 * 60; // 10 minutes
    long liquidityTimeout = 1;
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

    // connect all clients except one, to stay in CONFIRM_INPUT
    log.info("# Connect first clients...");
    for (int i = 0; i < NB_CLIENTS - 1; i++) {
      boolean liquidity = (i < mustMixMin ? false : true);
      long premixBalanceMin = mix.getPool().computePremixBalanceMin(liquidity);
      long inputBalance =
          (liquidity ? premixBalanceMin : premixBalanceMin + (100 * i)); // mixed amounts
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(1, inputBalance));
    }

    // connected clients should have registered their inputs...
    multiClientManager.assertMixStatusConfirmInput(NB_CLIENTS - 1, false);

    // connect last client
    log.info("# Connect last client...");
    taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(true, 1));

    // all clients should have registered their inputs
    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING

    // all clients should have registered their outputs and signed
    multiClientManager.assertMixStatusSuccess(NB_CLIENTS, false);

    Assert.assertEquals(mustMixMin, mix.getNbInputsMustMix());
    Assert.assertEquals((NB_CLIENTS - mustMixMin), mix.getNbInputsLiquidities());
  }
}
