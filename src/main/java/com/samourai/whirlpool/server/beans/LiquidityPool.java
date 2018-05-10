package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class LiquidityPool {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String,RegisteredLiquidity> liquiditiesById;

    public LiquidityPool() {
        this.liquiditiesById = new HashMap<>();
    }

    public synchronized void registerLiquidity(RegisteredLiquidity registeredLiquidity) {
        String inputId = Utils.computeInputId(registeredLiquidity.getRegisteredInput().getInput());
        liquiditiesById.put(inputId, registeredLiquidity);
    }

    public synchronized RegisteredLiquidity peekRandomLiquidity() {
        RegisteredLiquidity registeredLiquidity = null;
        if (!liquiditiesById.isEmpty()) {
            Map.Entry<String,RegisteredLiquidity> entry = Utils.getRandomEntry(liquiditiesById);
            registeredLiquidity = entry.getValue();
            liquiditiesById.remove(entry.getKey());
        }
        return registeredLiquidity;
    }

    // ------------

    public boolean hasLiquidity(TxOutPoint outPoint) {
        return liquiditiesById.containsKey(Utils.computeInputId(outPoint)) ;
    }

    public boolean hasLiquidity() {
        return !liquiditiesById.isEmpty();
    }
}
