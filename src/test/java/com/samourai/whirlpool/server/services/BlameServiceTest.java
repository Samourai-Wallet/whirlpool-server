package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.persistence.repositories.BlameRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
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
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class BlameServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private BlameRepository blameRepository;

  @Test
  public void isBannedUTXO_last() throws Exception {
    // server.ban.blames = 3
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO

    String identifier = "foo";
    long now = System.currentTimeMillis();

    blame(identifier, now - 800000, "1");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 780000, "2");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 400000, "3");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // other period

    blame(identifier, now - 300000, "4");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // other period

    blame(identifier, now - 750000, "5");
    Assert.assertTrue(blameService.isBannedUTXO(identifier)); // period
  }

  @Test
  public void isBannedUTXO_different_identifier() throws Exception {
    // server.ban.blames = 3
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO

    String identifier = "foo";
    long now = System.currentTimeMillis();

    blame(identifier, now - 800000, "1");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 780000, "2");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 400000, "3");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // other period

    blame(identifier, now - 300000, "4");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // other period

    blame(identifier + "another", now - 750000, "55");
    Assert.assertFalse(blameService.isBannedUTXO(identifier));
    Assert.assertFalse(blameService.isBannedUTXO(identifier + "another")); // period

    blame(identifier + "another", now - 749000, "66");
    Assert.assertFalse(blameService.isBannedUTXO(identifier));
    Assert.assertFalse(blameService.isBannedUTXO(identifier + "another")); // period

    blame(identifier + "another", now - 748000, "77");
    Assert.assertFalse(blameService.isBannedUTXO(identifier));
    Assert.assertTrue(blameService.isBannedUTXO(identifier + "another")); // period

    blame(identifier, now - 748000, "77");
    Assert.assertTrue(blameService.isBannedUTXO(identifier)); // period
  }

  @Test
  public void isBannedUTXO_expired() throws Exception {
    // server.ban.blames = 3
    // server.ban.period = 1OO
    // server.ban.duration = 1OOO

    String identifier = "foo";
    long now = System.currentTimeMillis();

    blame(identifier, now - 8000000, "1");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 7800000, "2");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period

    blame(identifier, now - 7500000, "5");
    Assert.assertFalse(blameService.isBannedUTXO(identifier)); // period
  }

  private void blame(String identifier, long created, String mixid) {
    BlameTO blameTO = blameService.blame(identifier, BlameReason.DISCONNECT, mixid, "127.0.0.1");
    blameTO.__setCreated(new Timestamp(created));
    blameRepository.save(blameTO);
  }
}
