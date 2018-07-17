package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.protocol.v1.messages.PeersPaymentCodesResponse;
import com.samourai.whirlpool.protocol.v1.messages.RegisterInputResponse;
import com.samourai.whirlpool.protocol.v1.notifications.*;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.v1.RegisterOutputController;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.exceptions.RoundException;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.*;
import org.bitcoinj.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoundService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WebSocketService webSocketService;
    private CryptoService cryptoService;
    private BlameService blameService;
    private DbService dbService;
    private BlockchainDataService blockchainDataService;
    private RoundLimitsService roundLimitsService;
    private Bech32Util bech32Util;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    private Round currentRound;

    private boolean deterministPaymentCodeMatching; // for testing purpose only

    @Autowired
    public RoundService(CryptoService cryptoService, BlameService blameService, DbService dbService, BlockchainDataService blockchainDataService, WebSocketService webSocketService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig, RoundLimitsService roundLimitsService) {
        this.cryptoService = cryptoService;
        this.blameService = blameService;
        this.dbService = dbService;
        this.blockchainDataService = blockchainDataService;
        this.webSocketService = webSocketService;
        this.bech32Util = bech32Util;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
        roundLimitsService.setRoundService(this); // avoids circular reference
        this.roundLimitsService = roundLimitsService;

        this.deterministPaymentCodeMatching = false;

        WhirlpoolServerConfig.RoundConfig roundConfig = whirlpoolServerConfig.getRound();
        String roundId = generateRoundId();
        long denomination = whirlpoolServerConfig.getRound().getDenomination();
        long fees = roundConfig.getMinerFee();
        int minMustMix = roundConfig.getMustMixMin();
        int targetAnonymitySet = roundConfig.getAnonymitySetTarget();
        int minAnonymitySet = roundConfig.getAnonymitySetMin();
        int maxAnonymitySet = roundConfig.getAnonymitySetMax();
        long mustMixAdjustTimeout = roundConfig.getAnonymitySetAdjustTimeout();
        long liquidityTimeout = roundConfig.getLiquidityTimeout();
        Round round = new Round(roundId, denomination, fees, minMustMix, targetAnonymitySet, minAnonymitySet, maxAnonymitySet, mustMixAdjustTimeout, liquidityTimeout);
        this.__reset(round);
    }

    private String generateRoundId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public synchronized void registerInput(String roundId, String username, TxOutPoint input, byte[] pubkey, String paymentCode, byte[] signedBordereauToReply, boolean liquidity) throws IllegalInputException, RoundException, QueueInputException {
        if (log.isDebugEnabled()) {
            log.debug("registerInput "+roundId+" : "+username+" : "+input);
        }
        Round round = getRound(roundId, RoundStatus.REGISTER_INPUT);
        if (!checkInputBalance(input, round, liquidity)) {
            throw new IllegalInputException("Invalid input balance (expected: "+computeSpendAmount(round, liquidity)+", actual:"+input.getValue()+")");
        }

        RegisteredInput registeredInput = new RegisteredInput(username, input, pubkey, paymentCode, liquidity);
        if (liquidity) {
            if (!isRegisterLiquiditiesOpen(round) || isRoundFull(round)) {
                // place liquidity on queue instead of rejecting it
                queueLiquidity(round, registeredInput, signedBordereauToReply);
            }
            else {
                // register liquidity if round opened to liquidities and not full
                registerInput(round, registeredInput, signedBordereauToReply, true);
            }
        }
        else {
            /*
             * user wants to mix
             */
            registerInput(round, registeredInput, signedBordereauToReply, false);
        }
    }

    private void queueLiquidity(Round round, RegisteredInput registeredInput, byte[] signedBordereauToReply) throws IllegalInputException, RoundException {
        /*
         * liquidity placed in waiting pool
         */
        LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);
        if (liquidityPool.hasLiquidity(registeredInput.getInput())) {
            throw new IllegalInputException("Liquidity already registered for this round");
        }

        // queue liquidity for later
        RegisteredLiquidity registeredInputQueued = new RegisteredLiquidity(registeredInput, signedBordereauToReply);
        liquidityPool.registerLiquidity(registeredInputQueued);
        log.info(" • queued liquidity: " + registeredInputQueued.getRegisteredInput().getInput() + " (" + liquidityPool.getNbLiquidities() + " liquidities in pool)");

        logRoundStatus(round);
    }

    private synchronized void registerInput(Round round, RegisteredInput registeredInput, byte[] signedBordereauToReply, boolean isLiquidity) throws IllegalInputException, RoundException, QueueInputException {
        // registerInput + response
        doRegisterInput(round, registeredInput, signedBordereauToReply, isLiquidity);

        // check round limits
        roundLimitsService.onInputRegistered(round);

        // check round ready
        checkRegisterInputReady(round);
    }

    private void doRegisterInput(Round round, RegisteredInput registeredInput, byte[] signedBordereauToReply, boolean isLiquidity) throws IllegalInputException, RoundException, QueueInputException {
        TxOutPoint input = registeredInput.getInput();
        String username = registeredInput.getUsername();

        if (isRoundFull(round)) {
            throw new QueueInputException("Round is full, please wait for next round");
        }
        if (isLiquidity && !isRegisterLiquiditiesOpen(round)) {
            // should never go here...
            log.error("Unexpected exception: round is not opened to liquidities yet, but liquidity entered registerInput");
            throw new RoundException("system error");
        }
        if (round.hasInput(input)) {
            throw new IllegalInputException("Input already registered for this round");
        }

        // add immediately to round inputs
        round.registerInput(registeredInput);
        log.info(" • registered "+(isLiquidity ? "liquidity" : "mustMix")+": " + registeredInput.getInput());
        logRoundStatus(round);

        // response
        RegisterInputResponse registerInputResponse = new RegisterInputResponse();
        registerInputResponse.signedBordereau = signedBordereauToReply;
        webSocketService.sendPrivate(username, registerInputResponse);
    }

    public void addLiquidity(Round round, RegisteredLiquidity randomLiquidity) throws Exception {
        doRegisterInput(round, randomLiquidity.getRegisteredInput(), randomLiquidity.getSignedBordereau(), true);
    }

    private boolean isRegisterLiquiditiesOpen(Round round) {
        if (!round.hasMinMustMixReached()) {
            // wait to get enough mustMix before accepting liquidities
            return false;
        }
        if (!round.isAcceptLiquidities()) {
            return false;
        }
        return true;
    }

    private boolean isRoundFull(Round round) {
        return (round.getNbInputs() >= round.getMaxAnonymitySet());
    }

    private long computeSpendAmount(Round round, boolean liquidity) {
        if (liquidity) {
            // no minersFees for liquidities
            return round.getDenomination();
        }
        return round.getDenomination() + round.getFees();
    }

    private boolean checkInputBalance(TxOutPoint input, Round round, boolean liquidity) {
        // input balance should match exactly this amount, because we don't generate change
        long spendAmount = computeSpendAmount(round, liquidity);
        return (input.getValue() == spendAmount);
    }

    public boolean isRegisterInputReady(Round round) {
        if (round.getNbInputs() == 0) {
            return false;
        }
        if (!round.hasMinMustMixReached()) {
            return false;
        }
        if (round.getNbInputs() < round.getTargetAnonymitySet()) {
            return false;
        }
        return true;
    }

    public synchronized void registerOutput(String roundId, String sendAddress, String receiveAddress, String bordereau) throws Exception {
        log.info(" • registered output: " + receiveAddress);
        if (log.isDebugEnabled()) {
            log.debug("sendAddress="+sendAddress);
        }
        Round round = getRound(roundId, RoundStatus.REGISTER_OUTPUT);
        round.registerOutput(sendAddress, receiveAddress, bordereau);

        if (isRegisterOutputReady(round)) {
            validateOutputs(round);
            changeRoundStatus(roundId, RoundStatus.SIGNING);
        }
    }

    public void checkRegisterInputReady(Round round) {
        if (isRegisterInputReady(round)) {
            changeRoundStatus(round.getRoundId(), RoundStatus.REGISTER_OUTPUT);
        }
    }

    private void logRoundStatus(Round round) {
        int liquiditiesInPool = 0;
        try {
            LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);
            liquiditiesInPool = liquidityPool.getNbLiquidities();
        } catch(Exception e) {
            // no liquidityPool instanciated yet
        }
        log.info(round.getNbInputsMustMix()+"/"+round.getMinMustMix()+" mustMix, "+round.getNbInputs()+"/"+round.getTargetAnonymitySet()+" anonymitySet, "+liquiditiesInPool+" liquidities in pool");

        // update round status in database
        if (round.getNbInputsMustMix() > 0) {
            try {
                dbService.saveRound(round);
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    protected void validateOutputs(Round round) throws Exception {
        // sendAddresses and receiveAddresses should match (this verifies no user is cheating another one)
        if (!Utils.listEqualsIgnoreOrder(round.getSendAddresses(), round.getReceiveAddresses())) {
            log.error("sendAddresses doesn't match receiveAddresses. sendAddresses="+round.getSendAddresses()+"receiveAddresses="+round.getReceiveAddresses());
            throw new Exception("REGISTER_OUTPUT failed"); // TODO find and ban malicious users
        }
    }

    protected synchronized boolean isRegisterOutputReady(Round round) {
        if (!isRegisterInputReady(round)) {
            // TODO recheck inputs balances and update/ban/reopen REGISTER_INPUT or fail if input spent in the meantime
            return false;
        }
        return (round.getRegisteredBordereaux().size() == round.getNbInputs());
    }

    public synchronized void revealOutput(String roundId, String username, String bordereau) throws RoundException, IllegalInputException {
        Round round = getRound(roundId, RoundStatus.REVEAL_OUTPUT_OR_BLAME);

        // verify an output was registered with this bordereau
        if (!round.getRegisteredBordereaux().contains(bordereau)) {
            throw new IllegalInputException("Invalid bordereau");
        }
        // verify this bordereau was not already revealed (someone could try to register 2 inputs and reveal same bordereau to block round)
        if (round.getRevealedOutputUsers().contains(bordereau)) {
            log.warn("Rejecting already revealed bordereau: "+bordereau);
            throw new IllegalInputException("Bordereau already revealed");
        }
        round.addRevealedOutputUser(username);
        log.info(" • revealed output: username=" + username);

        if (isRevealOutputOrBlameReady(round)) {
            roundLimitsService.blameForRevealOutputAndResetRound(round);
        }
    }

    protected synchronized boolean isRevealOutputOrBlameReady(Round round) {
        return (round.getRevealedOutputUsers().size() == round.getRegisteredBordereaux().size());
    }

    public synchronized void registerSignature(String roundId, String username, byte[][] witness) throws Exception {
        log.info(" • registered signature: username=" + username);
        Round round = getRound(roundId, RoundStatus.SIGNING);
        Signature signature = new Signature(witness);
        round.setSignatureByUsername(username, signature);

        if (isRegisterSignaturesReady(round)) {
            Transaction tx = round.getTx();
            tx = signTransaction(tx, round);
            round.setTx(tx);

            log.info("Tx to broadcast: \n" + tx + "\nRaw: " + Utils.getRawTx(tx));
            try {
                blockchainDataService.broadcastTransaction(tx);
                changeRoundStatus(roundId, RoundStatus.SUCCESS);
            }
            catch(Exception e) {
                log.error("Unable to broadcast tx", e);
                goFail(round, FailReason.FAIL_BROADCAST);
            }
        }
    }

    protected synchronized boolean isRegisterSignaturesReady(Round round) {
        if (!isRegisterOutputReady(round)) {
            return false;
        }
        return (round.getNbSignatures() == round.getNbInputs());
    }

    public String getCurrentRoundId() {
        return currentRound.getRoundId();
    }

    public Round __getCurrentRound() {
        return currentRound;
    }

    public void changeRoundStatus(String roundId, RoundStatus roundStatus) {
        log.info("[ROUND "+roundId+"] => "+roundStatus);
        try {
            Round round = getRound(roundId);
            if (roundStatus.equals(round.getRoundStatus())) {
                // just in case...
                log.error("roundStatus inconsistency detected! (already "+roundStatus+")", new IllegalStateException());
                return;
            }

            if (roundStatus == RoundStatus.REGISTER_OUTPUT) {
                transmitPaymentCodes(round);
            }
            else if (roundStatus == RoundStatus.SIGNING) {
                try {
                    Transaction tx = computeTransaction(round);
                    round.setTx(tx);

                    log.info("Txid: "+tx.getHashAsString());
                    if (log.isDebugEnabled()) {
                        log.debug("Tx to sign: \n" + tx + "\nRaw: " + Utils.getRawTx(tx));
                    }
                } catch (Exception e) {
                    log.error("Unexpected exception on buildTransaction() for signing", e);
                    throw new RoundException("System error");
                }
            }

            // update round status
            round.setRoundStatusAndTime(roundStatus);
            try {
                dbService.saveRound(round);
            } catch(Exception e) {
                log.error("", e);
            }
            roundLimitsService.onRoundStatusChange(round);

            RoundStatusNotification roundStatusNotification = computeRoundStatusNotification();
            webSocketService.broadcast(roundStatusNotification);

            // start next round (after notifying clients for success)
            if (roundStatus == RoundStatus.SUCCESS) {
                __nextRound();
            } else if (roundStatus == RoundStatus.FAIL) {
                __nextRound();
            }
        }
        catch(RoundException e) {
            log.error("Unexpected round error", e);
            __nextRound();
        }
    }

    private void transmitPaymentCodes(Round round) throws RoundException {
        // get all paymentCodes associated to users
        Map<String,String> paymentCodesByUser = new HashMap<>();
        for (RegisteredInput registeredInput : round.getInputs()) {
            String paymentCode = registeredInput.getPaymentCode();
            String username = registeredInput.getUsername();
            paymentCodesByUser.put(username, paymentCode);
        }

        // determinist paymentCodes matching for tests reproductibility
        if (deterministPaymentCodeMatching) {
            log.warn("deterministPaymentCodeMatching is enabled (use it for tests only!)");
            // sort by paymentCode
            paymentCodesByUser = Utils.sortMapByValue(paymentCodesByUser);
        }

        // confrontate paymentCodes
        Map<String,PeersPaymentCodesResponse> peersPaymentCodesResponsesByUser = computePaymentCodesConfrontations(paymentCodesByUser);

        // transmit to users
        for (Map.Entry<String,PeersPaymentCodesResponse> peersPaymentCodesResponseEntry : peersPaymentCodesResponsesByUser.entrySet()) {
            webSocketService.sendPrivate(peersPaymentCodesResponseEntry.getKey(), peersPaymentCodesResponseEntry.getValue());
        }
    }

    protected Map<String,PeersPaymentCodesResponse> computePaymentCodesConfrontations(Map<String, String> paymentCodesByUser) {
        List<String> usernames = new ArrayList(paymentCodesByUser.keySet());
        List<String> paymentCodes = new ArrayList(paymentCodesByUser.values());

        Map<String,PeersPaymentCodesResponse> peersPaymentCodesResponsesByUser = new HashMap<>();
        for (int i=0; i<usernames.size(); i++) {
            // for each registered user...
             String username = usernames.get(i);

            // pick a paymentcode to confrontate with to compute receiveAddress
            int iFromPaymentCode = (i==paymentCodes.size()-1 ? 0 : i+1);
            String fromPaymentCode = paymentCodes.get(iFromPaymentCode);

            // pick reverse paymentcode to confrontate with to compute sendAddress (for mutual validation)
            int iToPaymentCode = (i==0 ? paymentCodes.size()-1 : i-1);
            String toPaymentCode = paymentCodes.get(iToPaymentCode);

            // send
            PeersPaymentCodesResponse confrontatePaymentCodeResponse = new PeersPaymentCodesResponse();
            confrontatePaymentCodeResponse.fromPaymentCode = fromPaymentCode;
            confrontatePaymentCodeResponse.toPaymentCode = toPaymentCode;
            peersPaymentCodesResponsesByUser.put(username, confrontatePaymentCodeResponse);
        }
        return peersPaymentCodesResponsesByUser;
    }

    public RoundStatusNotification computeRoundStatusNotification() throws RoundException {
        String roundId = getCurrentRoundId();
        Round round = getRound(roundId);
        RoundStatusNotification roundStatusNotification = null;
        switch(round.getRoundStatus()) {
            case REGISTER_INPUT:
                try {
                    byte[] publicKey = cryptoService.getPublicKey().getEncoded();
                    roundStatusNotification = new RegisterInputRoundStatusNotification(roundId, publicKey, cryptoService.getNetworkParameters().getPaymentProtocolId(), round.getDenomination(), round.getFees());
                }
                catch(Exception e) {
                    throw new RoundException("unexpected error"); // TODO
                }
                break;
            case REGISTER_OUTPUT:
                String registerOutputUrl = computeRegisterOutputUrl();
                roundStatusNotification = new RegisterOutputRoundStatusNotification(roundId, registerOutputUrl);
                break;
            case REVEAL_OUTPUT_OR_BLAME:
                roundStatusNotification = new RevealOutputOrBlameRoundStatusNotification(roundId);
                break;
            case SIGNING:
                roundStatusNotification = new SigningRoundStatusNotification(roundId, round.getTx().bitcoinSerialize());
                break;
            case SUCCESS:
                roundStatusNotification = new SuccessRoundStatusNotification(roundId);
                break;
            case FAIL:
                roundStatusNotification = new FailRoundStatusNotification(roundId);
                break;
        }
        return roundStatusNotification;
    }

    private String computeRegisterOutputUrl() {
        String registerOutputUrl = whirlpoolServerConfig.getRegisterOutput().getUrl() + RegisterOutputController.ENDPOINT;
        return registerOutputUrl;
    }

    private Round getRound(String roundId) throws RoundException {
        //Round round = rounds.get(roundId);
        //if (round == null) {
        if (!currentRound.getRoundId().equals(roundId)) {
            throw new RoundException("Invalid roundId");
        }
        return currentRound;
    }

    private Round getRound(String roundId, RoundStatus roundStatus) throws RoundException {
        Round round = getRound(roundId);
        if (!roundStatus.equals(round.getRoundStatus())) {
            throw new RoundException("Operation not permitted for current round status");
        }
        return round;
    }

    private Transaction computeTransaction(Round round) throws Exception {
        NetworkParameters params = cryptoService.getNetworkParameters();
        Transaction tx = new Transaction(params);
        List<TransactionInput> inputs = new ArrayList<>();
        List<TransactionOutput> outputs = new ArrayList<>();

        tx.clearOutputs();
        for (String receiveAddress : round.getReceiveAddresses()) {
            TransactionOutput txOutSpend = bech32Util.getTransactionOutput(receiveAddress, round.getDenomination(), params);
            if (txOutSpend == null) {
                throw new Exception("unable to create output for "+receiveAddress);
            }
            outputs.add(txOutSpend);
        }

        //
        // BIP69 sort outputs
        //
        Collections.sort(outputs, new BIP69OutputComparator());
        for(TransactionOutput to : outputs) {
            tx.addOutput(to);
        }

        //
        // create 1 mix tx
        //
        for (RegisteredInput registeredInput : round.getInputs()) {
            // send from bech32 input
            long spendAmount = computeSpendAmount(round, registeredInput.isLiquidity());
            TxOutPoint registeredOutPoint = registeredInput.getInput();
            TransactionOutPoint outPoint = new TransactionOutPoint(params, registeredOutPoint.getIndex(), Sha256Hash.wrap(registeredOutPoint.getHash()), Coin.valueOf(spendAmount));
            TransactionInput txInput = new TransactionInput(params, null, new byte[]{}, outPoint, Coin.valueOf(spendAmount));
            inputs.add(txInput);
        }

        //
        // BIP69 sort inputs
        //
        Collections.sort(inputs, new BIP69InputComparator());
        for(TransactionInput ti : inputs) {
            tx.addInput(ti);
        }
        return tx;
    }

    private Transaction signTransaction(Transaction tx, Round round) {
        for (RegisteredInput registeredInput : round.getInputs()) {
            Signature signature = round.getSignatureByUsername(registeredInput.getUsername());

            TxOutPoint registeredOutPoint = registeredInput.getInput();
            Integer inputIndex = Utils.findTxInput(tx, registeredOutPoint.getHash(), registeredOutPoint.getIndex());
            if (inputIndex == null) {
                throw new ScriptException("Transaction input not found");
            }

            TransactionWitness witness = Utils.witnessUnserialize(signature.witness);
            tx.setWitness(inputIndex, witness);
        }

        // check final transaction
        tx.verify();

        return tx;
    }

    public void goRevealOutputOrBlame(String roundId) {
        log.info(" • REGISTER_OUTPUT time over (round failed, blaming users who didn't register output...)");
        changeRoundStatus(roundId, RoundStatus.REVEAL_OUTPUT_OR_BLAME);
    }

    public void goFail(Round round, FailReason failReason) {
        round.setFailReason(failReason);
        changeRoundStatus(round.getRoundId(), RoundStatus.FAIL);
    }

    public synchronized void onClientDisconnect(String username) {
        // mark registeredInput offline
        for (Round round : getCurrentRounds()) {
            String roundId = round.getRoundId();
            List<RegisteredInput> registeredInputs = round.getInputs().parallelStream().filter(registeredInput -> registeredInput.getUsername().equals(username)).collect(Collectors.toList());
            if (!registeredInputs.isEmpty()) {
                if (RoundStatus.REGISTER_INPUT.equals(round.getRoundStatus())) {
                    // round not started yet => remove input as round isn't started yet
                    registeredInputs.forEach(registeredInput -> {
                        log.info(" • [" + roundId + "] unregistered " + (registeredInput.isLiquidity() ? "liquidity" : "mustMix") + ", username=" + registeredInput.getUsername());
                        round.unregisterInput(registeredInput);
                    });
                }
                else {
                    // round already started => mark input as offline
                    registeredInputs.forEach(registeredInput -> {
                        log.info(" • [" + roundId + "] offlined " + (registeredInput.isLiquidity() ? "liquidity" : "mustMix") + ", username=" + registeredInput.getUsername());
                        registeredInput.setOffline(true);
                    });
                }
            }
        }
    }

    private List<Round> getCurrentRounds() {
        List<Round> currentRounds = new ArrayList<>();
        currentRounds.add(__getCurrentRound());
        return currentRounds;
    }

    public void __reset(String roundId) {
        Round copyRound = new Round(roundId, this.currentRound);
        __reset(copyRound);
    }

    public void __nextRound() {
        String roundId = generateRoundId();
        __reset(roundId);
    }

    public void __reset(Round round) {
        if (this.currentRound != null) {
            roundLimitsService.unmanage(round);
        }

        log.info("[NEW ROUND "+round.getRoundId()+"]");
        logRoundStatus(round);
        this.currentRound = round;
        // TODO disconnect all clients (except liquidities?)
        roundLimitsService.manage(round);
    }

    public RoundLimitsService __getRoundLimitsService() {
        return roundLimitsService;
    }

    public void __setUseDeterministPaymentCodeMatching(boolean useDeterministPaymentCodeMatching) {
        this.deterministPaymentCodeMatching = useDeterministPaymentCodeMatching;
    }
}
