package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.services.PoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;

@RestController
public class PoolsController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;

  @Autowired
  public PoolsController(PoolService poolService) {
    this.poolService = poolService;
  }

  @RequestMapping(value = WhirlpoolProtocol.ENDPOINT_POOLS, method = RequestMethod.GET)
  public PoolsResponse pools() {
    PoolsResponse poolsResponse = new PoolsResponse();
    poolsResponse.pools = poolService.getPools().parallelStream().map(pool -> computePoolInfo(pool)).toArray((i) -> new PoolInfo[i]);
    return poolsResponse;
  }

  private PoolInfo computePoolInfo(Pool pool) {
    Mix currentMix = pool.getCurrentMix();
    PoolInfo poolInfo = new PoolInfo();
    poolInfo.poolId = pool.getPoolId();
    poolInfo.denomination = pool.getDenomination();
    poolInfo.minerFeeMin = pool.getMinerFeeMin();
    poolInfo.minerFeeMax = pool.getMinerFeeMax();
    poolInfo.minAnonymitySet = pool.getMinAnonymitySet();
    poolInfo.mixAnonymitySet = currentMix.getTargetAnonymitySet();
    poolInfo.mixStatus = currentMix.getMixStatus();
    poolInfo.elapsedTime = currentMix.getElapsedTime();
    int nbInputs = currentMix.getNbInputs();
    poolInfo.mixNbConnected = nbInputs + currentMix.getNbInputsLiquidities();
    poolInfo.mixNbRegistered = nbInputs;
    return poolInfo;
  }
}