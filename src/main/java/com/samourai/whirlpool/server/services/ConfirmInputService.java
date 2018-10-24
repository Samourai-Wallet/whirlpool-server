package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfirmInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private PoolService poolService;
  private WebSocketService webSocketService;

  @Autowired
  public ConfirmInputService(
      MixService mixService, PoolService poolService, WebSocketService webSocketService) {
    this.mixService = mixService;
    this.poolService = poolService;
    this.webSocketService = webSocketService;
  }

  public synchronized void confirmInputOrQueuePool(
      String mixId, String username, byte[] blindedBordereau)
      throws IllegalInputException, MixException {
    try {
      // add input to mix & reply confirmInputResponse
      mixService.confirmInput(mixId, username, blindedBordereau);
    } catch (QueueInputException e) {
      // input queued => re-enqueue in pool
      RegisteredInput registeredInput = e.getRegisteredInput();
      String poolId = e.getPoolId();
      poolService.registerInput(
          poolId,
          registeredInput.getUsername(),
          registeredInput.getPubkey(),
          registeredInput.isLiquidity(),
          registeredInput.getInput());
    }
  }
}
