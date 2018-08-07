package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.MixException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class PoolService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WhirlpoolServerConfig whirlpoolServerConfig;
    private Map<String,Pool> pools;

    @Autowired
    public PoolService(WhirlpoolServerConfig whirlpoolServerConfig) {
        this.whirlpoolServerConfig = whirlpoolServerConfig;
        __reset();
    }

    public void __reset() {
        WhirlpoolServerConfig.PoolConfig[] poolConfigs = whirlpoolServerConfig.getPools();
        __reset(poolConfigs);
    }

    public void __reset(WhirlpoolServerConfig.PoolConfig[] poolConfigs) {
        pools = new HashMap<>();
        for (WhirlpoolServerConfig.PoolConfig poolConfig : poolConfigs) {
            String poolId = poolConfig.getId();
            long denomination = poolConfig.getDenomination();
            long minerFeeMin = poolConfig.getMinerFeeMin();
            long minerFeeMax = poolConfig.getMinerFeeMax();
            int minMustMix = poolConfig.getMustMixMin();
            int targetAnonymitySet = poolConfig.getAnonymitySetTarget();
            int minAnonymitySet = poolConfig.getAnonymitySetMin();
            int maxAnonymitySet = poolConfig.getAnonymitySetMax();
            long mustMixAdjustTimeout = poolConfig.getAnonymitySetAdjustTimeout();
            long liquidityTimeout = poolConfig.getLiquidityTimeout();

            Assert.notNull(poolId, "Pool configuration: poolId must not be NULL");
            Assert.isTrue(!pools.containsKey(poolId), "Pool configuration: poolId must not be duplicate");
            Pool pool = new Pool(poolId, denomination, minerFeeMin, minerFeeMax, minMustMix, targetAnonymitySet, minAnonymitySet, maxAnonymitySet, mustMixAdjustTimeout, liquidityTimeout);
            pools.put(poolId, pool);
        }
    }

    public Collection<Pool> getPools() {
        return pools.values();
    }

    public Pool getPool(String poolId) throws MixException {
        Pool pool = pools.get(poolId);
        if (pool == null) {
            throw new MixException("Pool not found");
        }
        return pool;
    }
}
