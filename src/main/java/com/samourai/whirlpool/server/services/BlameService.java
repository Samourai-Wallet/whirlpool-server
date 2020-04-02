package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlameService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private DbService dbService;
  private BanService banService;
  private ExportService exportService;

  @Autowired
  public BlameService(DbService dbService, BanService banService, ExportService exportService) {
    this.dbService = dbService;
    this.banService = banService;
    this.exportService = exportService;
  }

  public void blame(ConfirmedInput confirmedInput, BlameReason reason, Mix mix) {
    String identifier = Utils.computeBlameIdentitifer(confirmedInput);
    blame(identifier, reason, mix.getMixId(), confirmedInput.getRegisteredInput().getIp());

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv(
            "BLAME",
            mix.getPool().getPoolId(),
            confirmedInput.getRegisteredInput(),
            ImmutableMap.of("reason", reason.name()),
            null);
    exportService.exportActivity(activityCsv);
  }

  private BlameTO blame(String identifier, BlameReason reason, String mixId, String ip) {
    BlameTO blameTO = dbService.saveBlame(identifier, reason, mixId, ip);

    // notify banService
    List<BlameTO> blames = dbService.findBlames(identifier);
    banService.onBlame(identifier, blames);

    return blameTO;
  }
}
