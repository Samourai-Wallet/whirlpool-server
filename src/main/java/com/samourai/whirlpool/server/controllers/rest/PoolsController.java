package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.services.PoolService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoolsController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;

  @Autowired
  public PoolsController(PoolService poolService) {
    this.poolService = poolService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_POOLS, method = RequestMethod.GET)
  public PoolsResponse pools() {
    PoolInfo[] pools =
        poolService
            .getPools()
            .parallelStream()
            .map(pool -> computePoolInfo(pool))
            .toArray((i) -> new PoolInfo[i]);
    PoolsResponse poolsResponse = new PoolsResponse(pools);
    return poolsResponse;
  }

  private PoolInfo computePoolInfo(Pool pool) {
    Mix currentMix = pool.getCurrentMix();

    int nbRegistered =
        currentMix.getNbConfirmingInputs()
            + pool.getMustMixQueue().getSize()
            + pool.getLiquidityQueue().getSize()
            + pool.getUnconfirmedQueue().getSize();
    int mixNbConfirmed = currentMix.getNbInputs();
    PoolInfo poolInfo =
        new PoolInfo(
            pool.getPoolId(),
            pool.getDenomination(),
            pool.getMinerFeeMin(),
            pool.getMinerFeeMax(),
            pool.getMinAnonymitySet(),
            nbRegistered,
            currentMix.getTargetAnonymitySet(),
            currentMix.getMixStatus(),
            currentMix.getElapsedTime(),
            mixNbConfirmed);
    return poolInfo;
  }

  @ExceptionHandler
  public ResponseEntity<RestErrorResponse> handleException(Exception exception) {
    return super.handleException(exception);
  }
}
