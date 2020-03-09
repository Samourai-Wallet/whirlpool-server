package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfirmInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private PoolService poolService;

  @Autowired
  public ConfirmInputService(MixService mixService, PoolService poolService) {
    this.mixService = mixService;
    this.poolService = poolService;
  }

  public synchronized Optional<byte[]> confirmInputOrQueuePool(
      String mixId, String username, byte[] blindedBordereau, String userHash)
      throws NotifiableException, MixException {
    try {
      // add input to mix & reply confirmInputResponse
      return Optional.of(mixService.confirmInput(mixId, username, blindedBordereau, userHash));
    } catch (QueueInputException e) {
      // input queued => re-enqueue in pool
      RegisteredInput registeredInput = e.getRegisteredInput();
      String poolId = e.getPoolId();
      if (log.isDebugEnabled()) {
        log.debug(
            "Input queued: poolId="
                + poolId
                + ", input="
                + registeredInput.getOutPoint()
                + ", reason="
                + e.getMessage());
      }
      poolService.registerInput(
          poolId,
          registeredInput.getUsername(),
          registeredInput.isLiquidity(),
          registeredInput.getOutPoint(),
          false,
          registeredInput.getIp(),
          userHash);
      return Optional.empty();
    }
  }
}
