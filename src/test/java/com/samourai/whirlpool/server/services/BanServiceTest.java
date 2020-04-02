package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.persistence.repositories.BlameRepository;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class BanServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private BlameRepository blameRepository;
  @Autowired private BlameService blameService;
  @Autowired private BanService banService;

  private static final int EXPIRATION_MS = 1000 * 1000;

  // BLAME

  @Test
  public void blame_and_ban_mustmix() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO
    Mix mix = __getCurrentMix();

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = false; // mustmix
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);

    // not banned yet
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 1/2 => not banned yet
    blameService.blame(confirmedInput, BlameReason.DISCONNECT, mix);
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 2/2 => banned
    blameService.blame(confirmedInput, BlameReason.DISCONNECT, mix);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // inputs from same HASH are banned too
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assert.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban disabled after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assert.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assert.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
  }

  @Test
  public void blame_and_ban_liquidity() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO
    Mix mix = __getCurrentMix();

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = true; // liquidity
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);

    // not banned yet
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 1/2 => not banned yet
    blameService.blame(confirmedInput, BlameReason.DISCONNECT, mix);
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // blame 2/2 => banned
    blameService.blame(confirmedInput, BlameReason.DISCONNECT, mix);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are not banned
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assert.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // ban disabled after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assert.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assert.assertFalse(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
  }

  // PERMANENT BAN

  @Test
  public void permanent_ban_mustmix() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = false; // mustmix
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);
    String identifier = Utils.computeBlameIdentitifer(confirmedInput);

    // not banned yet
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // permanent ban
    banService.banPermanent(identifier, null, null);

    // banned
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are banned too
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assert.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());

    // ban still after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());
  }

  @Test
  public void permanent_ban_liquidity() throws Exception {
    // server.ban.blames = 2
    // server.ban.period = 1OO
    // server.ban.expiration = 1OOO

    final String UTXO_HASH = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    final long UTXO_INDEX = 2;

    boolean liquidity = true; // liquidity
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(UTXO_HASH, UTXO_INDEX, liquidity);
    String identifier = Utils.computeBlameIdentitifer(confirmedInput);

    // not banned yet
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // permanent ban
    banService.banPermanent(identifier, null, null);

    // banned
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());

    // inputs from same HASH are not
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still active before expiration
    Timestamp beforeExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS - 10000);
    Assert.assertTrue(
        banService.findActiveBan(UTXO_HASH, UTXO_INDEX, beforeExpiration).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // ban still after expiration
    Timestamp afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    afterExpiration = new Timestamp(System.currentTimeMillis() + EXPIRATION_MS + 10000);
    Assert.assertTrue(banService.findActiveBan(UTXO_HASH, UTXO_INDEX, afterExpiration).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 0).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 1).isPresent());
    Assert.assertFalse(banService.findActiveBan(UTXO_HASH, 100).isPresent());

    // other inputs are not banned
    Assert.assertFalse(banService.findActiveBan("foo", UTXO_INDEX).isPresent());
  }
}
