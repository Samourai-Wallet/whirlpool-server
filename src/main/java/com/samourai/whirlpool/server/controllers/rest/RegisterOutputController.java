package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.services.BlameService;
import com.samourai.whirlpool.server.services.DbService;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegisterOutputController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterOutputService registerOutputService;
  private BlameService blameService;
  private DbService dbService;
  private ExportService exportService;

  @Autowired
  public RegisterOutputController(
      RegisterOutputService registerOutputService,
      DbService dbService,
      ExportService exportService) {
    this.registerOutputService = registerOutputService;
    this.dbService = dbService;
    this.exportService = exportService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_REGISTER_OUTPUT, method = RequestMethod.POST)
  public void registerOutput(HttpServletRequest request, @RequestBody RegisterOutputRequest payload)
      throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("(<) " + WhirlpoolEndpoint.REST_REGISTER_OUTPUT);
    }

    // register output
    byte[] unblindedSignedBordereau =
        WhirlpoolProtocol.decodeBytes(payload.unblindedSignedBordereau64);
    Mix mix =
        registerOutputService.registerOutput(
            payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress);

    // log activity
    String poolId = mix.getPool().getPoolId();
    ActivityCsv activityCsv = new ActivityCsv("TX0", poolId, null, null, request);
    exportService.exportActivity(activityCsv);
  }
}
