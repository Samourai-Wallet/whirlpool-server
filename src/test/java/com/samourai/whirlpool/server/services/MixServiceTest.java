package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class MixServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void isRegisterInputReady_noLiquidity() throws Exception {
        MixService spyMixService = Mockito.spy(mixService);
        long denomination = 200000000;
        long minerFeeMin = 100;
        long minerFeeMax = 10000;
        int mustMixMin = 1;
        int anonymitySetTarget = 2;
        int anonymitySetMin = 2;
        int anonymitySetMax = 2;
        long anonymitySetAdjustTimeout = 10 * 60;
        long liquidityTimeout = 60;
        Mix mix = __nextMix(denomination, minerFeeMin, minerFeeMax, mustMixMin, anonymitySetTarget, anonymitySetMin, anonymitySetMax, anonymitySetAdjustTimeout, liquidityTimeout);

        // 0 mustMix => false
        Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

        // 1 mustMix => false
        mix.registerInput(new RegisteredInput("mustMix1", generateInput(), null, null, false));
        Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

        // 2 mustMix => true
        mix.registerInput(new RegisteredInput("mustMix2", generateInput(), null, null, false));
        Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
    }

    @Test
    public void isRegisterInputReady_withLiquidityBefore() throws Exception {
        MixService spyMixService = Mockito.spy(mixService);
        long denomination = 200000000;
        long minerFeeMin = 100;
        long minerFeeMax = 10000;
        int mustMixMin = 1;
        int anonymitySetTarget = 2;
        int anonymitySetMin = 2;
        int anonymitySetMax = 2;
        long anonymitySetAdjustTimeout = 10 * 60;
        long liquidityTimeout = 60;
        Mix mix = __nextMix(denomination, minerFeeMin, minerFeeMax, mustMixMin, anonymitySetTarget, anonymitySetMin, anonymitySetMax, anonymitySetAdjustTimeout, liquidityTimeout);

        // 0 liquidity => false
        Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

        // 1 liquidity => false
        mix.registerInput(new RegisteredInput("liquidity1", generateInput(), null, null, true));
        Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

        // 2 liquidity => false : minMustMix not reached
        mix.registerInput(new RegisteredInput("liquidity2", generateInput(), null, null, true));
        Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

        // 1 mustMix => true : minMustMix reached
        mix.registerInput(new RegisteredInput("mustMix1", generateInput(), null, null, false));
        Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
    }

    private List<TxOutPoint> generateInputsList(int nb) {
        List<TxOutPoint> inputs = new ArrayList<>();
        while(inputs.size() < nb) {
            TxOutPoint txOutPoint = generateInput();
            inputs.add(txOutPoint);
        }
        return inputs;
    }

    private TxOutPoint generateInput() {
        TxOutPoint txOutPoint = new TxOutPoint(Utils.getRandomString(65), 0, 99999);
        return txOutPoint;
    }
}