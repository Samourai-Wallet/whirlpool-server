package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.UnconfirmedInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.function.Function;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class RegisterInputServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private RegisterInputService registerInputService;

    private static final int MIN_CONFIRMATIONS_MUSTMIX = 11;
    private static final int MIN_CONFIRMATIONS_LIQUIDITY = 22;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        serverConfig.getRegisterInput().setMinConfirmationsMustMix(MIN_CONFIRMATIONS_MUSTMIX);
        serverConfig.getRegisterInput().setMinConfirmationsLiquidity(MIN_CONFIRMATIONS_LIQUIDITY);
    }

    private byte[] validBlindedBordereau = null;
    final Function<Boolean,TxOutPoint> runTestValidInput = (Boolean liquidity) -> {
        TxOutPoint txOutPoint = null;
        try {
            RSAKeyParameters serverPublicKey = (RSAKeyParameters) Utils.generateKeyPair().getPublic();

            Mix mix = __getCurrentMix();
            String mixId = mix.getMixId();
            String username = "user1";

            ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
            byte[] pubkey = ecKey.getPubKey();
            String signature = ecKey.signMessage(mixId);
            
            SegwitAddress outputAddress = testUtils.createSegwitAddress();
            RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
            validBlindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

            long inputBalance = mix.computeInputBalanceMin(liquidity);
            int confirmations = liquidity ? MIN_CONFIRMATIONS_LIQUIDITY : MIN_CONFIRMATIONS_MUSTMIX;
            txOutPoint = rpcClientService.createAndMockTxOutPoint(new SegwitAddress(pubkey, cryptoService.getNetworkParameters()), inputBalance, confirmations);

            // TEST
            registerInputService.registerInput(mixId, username, pubkey, signature, validBlindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), liquidity);

        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
        return txOutPoint;
    };

    @Test
    public void registerInput_shouldRegisterMustMixWhenValid() throws Exception {
        // TEST
        TxOutPoint txOutPoint = runTestValidInput.apply(false);

        // VERIFY
        Mix mix = __getCurrentMix();
        LiquidityPool liquidityPool = mix.getPool().getLiquidityPool();

        // bordereau should be registered
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(validBlindedBordereau));

        // mustMix should be registered
        Assert.assertEquals(1, mix.getNbInputs());
        Assert.assertTrue(mix.hasInput(txOutPoint));

        // no liquidity should be queued
        Assert.assertFalse(liquidityPool.hasLiquidity());
        Assert.assertFalse(liquidityPool.hasLiquidity(txOutPoint));
    }

    @Test
    public void registerInput_shouldQueueLiquidityWhenValid() throws Exception {
        // TEST
        TxOutPoint txOutPoint = runTestValidInput.apply(true);

        // VERIFY
        Mix mix = __getCurrentMix();
        LiquidityPool liquidityPool = mix.getPool().getLiquidityPool();

        // bordereau should be registered
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(validBlindedBordereau));

        // liquidity should not be registered
        Assert.assertEquals(0, mix.getNbInputs());
        Assert.assertFalse(mix.hasInput(txOutPoint));

        // liquidity should be queued
        Assert.assertTrue(liquidityPool.hasLiquidity());
        Assert.assertTrue(liquidityPool.hasLiquidity(txOutPoint));
    }

    @Test
    public void registerInput_shouldFailWhenInvalidMixId() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();
        Mix mix = __getCurrentMix();

        String mixId = "INVALID"; // INVALID
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(MixException.class);
        thrown.expectMessage("Mix not found");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldFailWhenInvalidMixStatus() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();
        String mixId = __getCurrentMix().getMixId();

        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Mix mix = __getCurrentMix();
        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // register first input
        registerInputService.registerInput(mix.getMixId(), username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // new bordereau
        blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        // TEST
        // all mixStatus != REGISTER_INPUTS
        for (MixStatus mixStatus : MixStatus.values()) {
            if (!mixStatus.equals(MixStatus.REGISTER_INPUT)) {
                mixService.changeMixStatus(mix.getMixId(), mixStatus);
                thrown.expect(MixException.class);
                thrown.expectMessage("Operation not permitted for current mix status");
                registerInputService.registerInput(mix.getMixId(), username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);
            }
        }

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldFailWhenInvalidSignature() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "INVALID";
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid signature");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldFailWhenInvalidPubkey() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = testUtils.createSegwitAddress(); // INVALID: not related to pubkey
        String signature = ecKey.signMessage(mixId);
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid pubkey for UTXO");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldFailWhenDuplicateBordereau() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ", blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        thrown.expect(IllegalBordereauException.class);
        thrown.expectMessage("blindedBordereau already registered");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldFailWhenDuplicateInputsSameMix() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau1 = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ", blindingParams);
        byte[] blindedBordereau2 = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBZ", blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau1, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Input already registered for this mix");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau2, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(blindedBordereau1));
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau2));
    }

    @Test
    public void registerInput_shouldFailWhenBalanceTooLow() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false) - 1; // BALANCE TOO LOW
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid input balance");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
    }

    @Test
    public void registerInput_shouldFailWhenBalanceTooHigh() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);
        
        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = mix.computeInputBalanceMax(false) + 1;// BALANCE TOO HIGH
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid input balance");
        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), false);

        // VERIFY
        Assert.assertEquals(0, mix.getInputs().size());
    }

    private void doRegisterInput(int confirmations, boolean liquidity, boolean expectedSuccess) throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = ecKey.signMessage(mixId);

        SegwitAddress outputAddress = testUtils.createSegwitAddress();
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

        long inputBalance = mix.computeInputBalanceMin(false);
        // mock input with 0 confirmations
        TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance, confirmations);

        registerInputService.registerInput(mixId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), liquidity);

        // VERIFY
        if (expectedSuccess) {
            Assert.assertEquals(1, mix.getNbInputs());
            Assert.assertTrue(mix.hasInput(txOutPoint));
            Assert.assertTrue(dbService.isBlindedBordereauRegistered(blindedBordereau));
        } else {
            Assert.assertTrue(false); // exception expected before
        }
    }

    @Test
    public void registerInput_shouldFailWhenUnconfirmed() throws Exception {
        // mustMix
        thrown.expect(UnconfirmedInputException.class);
        thrown.expectMessage("Input needs at least " + MIN_CONFIRMATIONS_MUSTMIX + " confirmations");
        doRegisterInput(0, false, false);

        // liquidity
        thrown.expect(UnconfirmedInputException.class);
        thrown.expectMessage("Input needs at least " + MIN_CONFIRMATIONS_LIQUIDITY + " confirmations");
        doRegisterInput(0, true, false);
    }

    @Test
    public void registerInput_shouldFailWhenLessConfirmations() throws Exception {
        // mustMix
        thrown.expect(UnconfirmedInputException.class);
        thrown.expectMessage("Input needs at least " + MIN_CONFIRMATIONS_MUSTMIX + " confirmations");
        doRegisterInput(MIN_CONFIRMATIONS_MUSTMIX-1, false, false);

        // liquidity
        thrown.expect(UnconfirmedInputException.class);
        thrown.expectMessage("Input needs at least " + MIN_CONFIRMATIONS_LIQUIDITY + " confirmations");
        doRegisterInput(MIN_CONFIRMATIONS_LIQUIDITY-1, true, false);
    }

    @Test
    public void registerInput_shouldSuccessWhenMoreConfirmations() throws Exception {
        // mustMix
        doRegisterInput(MIN_CONFIRMATIONS_MUSTMIX+1, false, true);

        // liquidity
        doRegisterInput(MIN_CONFIRMATIONS_LIQUIDITY+1, true, true);
    }

    // TODO test noSamouraiFeesCheck for liquidities vs feesCheck for mustMix
}