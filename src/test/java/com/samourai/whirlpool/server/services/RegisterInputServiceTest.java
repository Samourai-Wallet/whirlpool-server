package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.RoundException;
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

    @Before
    public void setUp() throws Exception {
        super.setUp();
        roundService.__reset("12345678");
    }

    private byte[] validBlindedBordereau = null;
    final Function<Boolean,TxOutPoint> runTestValidInput = (Boolean liquidity) -> {
        TxOutPoint txOutPoint = null;
        try {
            RSAKeyParameters serverPublicKey = (RSAKeyParameters) Utils.generateKeyPair().getPublic();

            String roundId = roundService.getCurrentRoundId();
            String username = "user1";

            ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
            byte[] pubkey = ecKey.getPubKey();
            String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
            String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

            SegwitAddress outputAddress = testUtils.createSegwitAddress();
            RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
            validBlindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

            Round round = roundService.__getCurrentRound();
            long inputBalance = testUtils.computeSpendAmount(round, liquidity);
            txOutPoint = testUtils.createAndMockTxOutPoint(new SegwitAddress(pubkey, cryptoService.getNetworkParameters()), inputBalance);

            // TEST
            registerInputService.registerInput(roundId, username, pubkey, signature, validBlindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, liquidity);

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
        Round round = roundService.__getCurrentRound();
        LiquidityPool liquidityPool = roundService.__getRoundLimitsService().getLiquidityPool(round);

        // bordereau should be registered
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(validBlindedBordereau));

        // mustMix should be registered
        Assert.assertEquals(1, round.getNbInputs());
        Assert.assertTrue(round.hasInput(txOutPoint));

        // no liquidity should be queued
        Assert.assertFalse(liquidityPool.hasLiquidity());
        Assert.assertFalse(liquidityPool.hasLiquidity(txOutPoint));
    }

    @Test
    public void registerInput_shouldQueueLiquidityWhenValid() throws Exception {
        // TEST
        TxOutPoint txOutPoint = runTestValidInput.apply(true);

        // VERIFY
        Round round = roundService.__getCurrentRound();
        LiquidityPool liquidityPool = roundService.__getRoundLimitsService().getLiquidityPool(round);

        // bordereau should be registered
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(validBlindedBordereau));

        // liquidity should not be registered
        Assert.assertEquals(0, round.getNbInputs());
        Assert.assertFalse(round.hasInput(txOutPoint));

        // liquidity should be queued
        Assert.assertTrue(liquidityPool.hasLiquidity());
        Assert.assertTrue(liquidityPool.hasLiquidity(txOutPoint));
    }

    @Test
    public void registerInput_shouldNotRgisterWhenInvalidPaymentCode() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "INVALID";

        SegwitAddress outputAddress = testUtils.createSegwitAddress();
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid paymentCode");
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenInvalidRoundId() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();
        Round round = roundService.__getCurrentRound();

        String roundId = "INVALID"; // INVALID
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "IO+jpbs0hCXSjEujLV9aOkYHUKxzFdpsVvImw2WvKo6XH39o7Wg0OfcCHAj9gTV1IuzbrhtdUwM+Rruo/8FgwcM=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(RoundException.class);
        thrown.expectMessage("Invalid roundId");
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenInvalidRoundStatus() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        // all roundStatus != REGISTER_INPUTS
        for (RoundStatus roundStatus : RoundStatus.values()) {
            if (!roundStatus.equals(RoundStatus.REGISTER_INPUT)) {
                roundService.changeRoundStatus(round.getRoundId(), roundStatus);
                thrown.expect(RoundException.class);
                thrown.expectMessage("Operation not permitted for current round status");
                registerInputService.registerInput(round.getRoundId(), username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);
            }
        }

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenInvalidSignature() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "INVALIDd2OoEdPE35oGVwXq+oYYzkNMwN36Ws2VMCs+ZTs10/Ctklr9ZbvLJUHEhOz3t07/igCrK3WyJNWGfGSc=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid signature");
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenInvalidPubkey() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = testUtils.createSegwitAddress(); // INVALID: not related to pubkey
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Invalid pubkey for UTXO");
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenDuplicateBordereau() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ", blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        thrown.expect(IllegalBordereauException.class);
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldNotRegisterWhenDuplicateInputsSameRound() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau1 = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ", blindingParams);
        byte[] blindedBordereau2 = clientCryptoService.blind("3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBZ", blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);

        // TEST
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau1, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        thrown.expect(IllegalInputException.class);
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau2, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
        Assert.assertTrue(dbService.isBlindedBordereauRegistered(blindedBordereau1));
        Assert.assertFalse(dbService.isBlindedBordereauRegistered(blindedBordereau2));
    }

    @Test
    public void registerInput_shouldFailWhenBalanceTooLow() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false)-1; // BALANCE TOO LOW
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);
        thrown.expect(IllegalInputException.class);
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
    }

    @Test
    public void registerInput_shouldFailWhenBalanceTooHigh() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        String outputAddress = "3Jt9MU7Lin4QyRnHQa1wN8Csfq6GM2AkBQ";
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress, blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false)+1; // BALANCE TOO HIGH
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance);
        thrown.expect(IllegalInputException.class);
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(0, round.getInputs().size());
    }

    @Test
    public void registerInput_shouldFailWhenUnconfirmed() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        SegwitAddress outputAddress = testUtils.createSegwitAddress();
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        // mock input with 0 confirmations
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance, 0);

        // TEST
        thrown.expect(IllegalInputException.class);
        thrown.expectMessage("Input needs at least 1 confirmations");
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(1, round.getNbInputs());
        Assert.assertTrue(round.hasInput(txOutPoint));

        Assert.assertTrue(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    @Test
    public void registerInput_shouldRegisterWhenMoreConfirmations() throws Exception {
        RSAKeyParameters serverPublicKey = (RSAKeyParameters)Utils.generateKeyPair().getPublic();

        String roundId = roundService.getCurrentRoundId();
        String username = "user1";

        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        SegwitAddress inputAddress = new SegwitAddress(pubkey, cryptoService.getNetworkParameters());
        String signature = "H/djpBpXE49EghQGA9aHaAz7+YtHbaxf0fWdR9gGXLHJSbSQiVHA0Kn/7IfXS08FKGUoSzELfbwsZKfGKLiK1bs=";
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";

        SegwitAddress outputAddress = testUtils.createSegwitAddress();
        RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        byte[] blindedBordereau = clientCryptoService.blind(outputAddress.toString(), blindingParams);

        Round round = roundService.__getCurrentRound();
        long inputBalance = testUtils.computeSpendAmount(round, false);
        // mock input with 2000 confirmations
        TxOutPoint txOutPoint = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance, 2000);

        // TEST
        registerInputService.registerInput(roundId, username, pubkey, signature, blindedBordereau, txOutPoint.getHash(), txOutPoint.getIndex(), paymentCode, false);

        // VERIFY
        Assert.assertEquals(1, round.getNbInputs());
        Assert.assertTrue(round.hasInput(txOutPoint));

        Assert.assertTrue(dbService.isBlindedBordereauRegistered(blindedBordereau));
    }

    // TODO test invalid paymentCode

    // TODO test noSamouraiFeesCheck for liquidities vs feesCheck for mustMix
}