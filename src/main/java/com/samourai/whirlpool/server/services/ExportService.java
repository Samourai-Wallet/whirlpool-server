package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WhirlpoolServerConfig serverConfig;

  private ExportHandler<MixCsv> exportMixs;

  private ExportHandler<ActivityCsv> exportActivity;

  public ExportService(WhirlpoolServerConfig serverConfig) throws Exception {
    this.serverConfig = serverConfig;

    // init exports
    exportMixs =
        new ExportHandler<>(serverConfig.getExport().getMixs(), MixCsv.class, MixCsv.HEADERS);
    exportActivity =
        new ExportHandler<>(
            serverConfig.getExport().getActivity(), ActivityCsv.class, ActivityCsv.HEADERS);
  }

  public void exportMix(Mix mix) {
    try {
      MixTO mixTO = mix.__getMixTO().get();
      MixCsv mixCSV = new MixCsv(mixTO);
      exportMixs.write(mixCSV);
    } catch (Exception e) {
      log.error("unable to export mix", e);
    }
  }

  public void exportActivity(ActivityCsv activityCsv) {
    try {
      exportActivity.write(activityCsv);
    } catch (Exception e) {
      log.error("unable to export activity", e);
    }
  }
}
