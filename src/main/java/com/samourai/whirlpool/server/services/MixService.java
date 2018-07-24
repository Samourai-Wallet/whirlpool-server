package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.protocol.v1.messages.PeersPaymentCodesResponse;
import com.samourai.whirlpool.protocol.v1.messages.RegisterInputResponse;
import com.samourai.whirlpool.protocol.v1.notifications.*;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.websocket.RegisterOutputController;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
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
public class MixService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WebSocketService webSocketService;
    private CryptoService cryptoService;
    private BlameService blameService;
    private DbService dbService;
    private BlockchainDataService blockchainDataService;
    private MixLimitsService mixLimitsService;
    private Bech32Util bech32Util;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    private Mix currentMix;

    private boolean deterministPaymentCodeMatching; // for testing purpose only

    @Autowired
    public MixService(CryptoService cryptoService, BlameService blameService, DbService dbService, BlockchainDataService blockchainDataService, WebSocketService webSocketService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig, MixLimitsService mixLimitsService) {
        this.cryptoService = cryptoService;
        this.blameService = blameService;
        this.dbService = dbService;
        this.blockchainDataService = blockchainDataService;
        this.webSocketService = webSocketService;
        this.bech32Util = bech32Util;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
        mixLimitsService.setMixService(this); // avoids circular reference
        this.mixLimitsService = mixLimitsService;

        this.deterministPaymentCodeMatching = false;

        WhirlpoolServerConfig.MixConfig mixConfig = whirlpoolServerConfig.getMix();
        String mixId = generateMixId();
        long denomination = whirlpoolServerConfig.getMix().getDenomination();
        long fees = mixConfig.getMinerFee();
        int minMustMix = mixConfig.getMustMixMin();
        int targetAnonymitySet = mixConfig.getAnonymitySetTarget();
        int minAnonymitySet = mixConfig.getAnonymitySetMin();
        int maxAnonymitySet = mixConfig.getAnonymitySetMax();
        long mustMixAdjustTimeout = mixConfig.getAnonymitySetAdjustTimeout();
        long liquidityTimeout = mixConfig.getLiquidityTimeout();
        Mix mix = new Mix(mixId, denomination, fees, minMustMix, targetAnonymitySet, minAnonymitySet, maxAnonymitySet, mustMixAdjustTimeout, liquidityTimeout);
        this.__reset(mix);
    }

    private String generateMixId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public synchronized void registerInput(String mixId, String username, TxOutPoint input, byte[] pubkey, String paymentCode, byte[] signedBordereauToReply, boolean liquidity) throws IllegalInputException, MixException, QueueInputException {
        if (log.isDebugEnabled()) {
            log.debug("registerInput "+mixId+" : "+username+" : "+input);
        }
        Mix mix = getMix(mixId, MixStatus.REGISTER_INPUT);
        if (!checkInputBalance(input, mix, liquidity)) {
            throw new IllegalInputException("Invalid input balance (expected: "+computeSpendAmount(mix, liquidity)+", actual:"+input.getValue()+")");
        }

        RegisteredInput registeredInput = new RegisteredInput(username, input, pubkey, paymentCode, liquidity);
        if (liquidity) {
            if (!isRegisterLiquiditiesOpen(mix) || isMixFull(mix)) {
                // place liquidity on queue instead of rejecting it
                queueLiquidity(mix, registeredInput, signedBordereauToReply);
            }
            else {
                // register liquidity if mix opened to liquidities and not full
                registerInput(mix, registeredInput, signedBordereauToReply, true);
            }
        }
        else {
            /*
             * user wants to mix
             */
            registerInput(mix, registeredInput, signedBordereauToReply, false);
        }
    }

    private void queueLiquidity(Mix mix, RegisteredInput registeredInput, byte[] signedBordereauToReply) throws IllegalInputException, MixException {
        /*
         * liquidity placed in waiting pool
         */
        LiquidityPool liquidityPool = mixLimitsService.getLiquidityPool(mix);
        if (liquidityPool.hasLiquidity(registeredInput.getInput())) {
            throw new IllegalInputException("Liquidity already registered for this mix");
        }

        // queue liquidity for later
        RegisteredLiquidity registeredInputQueued = new RegisteredLiquidity(registeredInput, signedBordereauToReply);
        liquidityPool.registerLiquidity(registeredInputQueued);
        log.info(" • queued liquidity: " + registeredInputQueued.getRegisteredInput().getInput() + " (" + liquidityPool.getNbLiquidities() + " liquidities in pool)");

        logMixStatus(mix);
    }

    private synchronized void registerInput(Mix mix, RegisteredInput registeredInput, byte[] signedBordereauToReply, boolean isLiquidity) throws IllegalInputException, MixException, QueueInputException {
        // registerInput + response
        doRegisterInput(mix, registeredInput, signedBordereauToReply, isLiquidity);

        // check mix limits
        mixLimitsService.onInputRegistered(mix);

        // check mix ready
        checkRegisterInputReady(mix);
    }

    private void doRegisterInput(Mix mix, RegisteredInput registeredInput, byte[] signedBordereauToReply, boolean isLiquidity) throws IllegalInputException, MixException, QueueInputException {
        TxOutPoint input = registeredInput.getInput();
        String username = registeredInput.getUsername();

        if (isMixFull(mix)) {
            throw new QueueInputException("Mix is full, please wait for next mix");
        }
        if (isLiquidity && !isRegisterLiquiditiesOpen(mix)) {
            // should never go here...
            log.error("Unexpected exception: mix is not opened to liquidities yet, but liquidity entered registerInput");
            throw new MixException("system error");
        }
        if (mix.hasInput(input)) {
            throw new IllegalInputException("Input already registered for this mix");
        }

        // add immediately to mix inputs
        mix.registerInput(registeredInput);
        log.info(" • registered "+(isLiquidity ? "liquidity" : "mustMix")+": " + registeredInput.getInput());
        logMixStatus(mix);

        // response
        RegisterInputResponse registerInputResponse = new RegisterInputResponse();
        registerInputResponse.signedBordereau = signedBordereauToReply;
        webSocketService.sendPrivate(username, registerInputResponse);
    }

    public void addLiquidity(Mix mix, RegisteredLiquidity randomLiquidity) throws Exception {
        doRegisterInput(mix, randomLiquidity.getRegisteredInput(), randomLiquidity.getSignedBordereau(), true);
    }

    private boolean isRegisterLiquiditiesOpen(Mix mix) {
        if (!mix.hasMinMustMixReached()) {
            // wait to get enough mustMix before accepting liquidities
            return false;
        }
        if (!mix.isAcceptLiquidities()) {
            return false;
        }
        return true;
    }

    private boolean isMixFull(Mix mix) {
        return (mix.getNbInputs() >= mix.getMaxAnonymitySet());
    }

    private long computeSpendAmount(Mix mix, boolean liquidity) {
        if (liquidity) {
            // no minersFees for liquidities
            return mix.getDenomination();
        }
        return mix.getDenomination() + mix.getFees();
    }

    private boolean checkInputBalance(TxOutPoint input, Mix mix, boolean liquidity) {
        // input balance should match exactly this amount, because we don't generate change
        long spendAmount = computeSpendAmount(mix, liquidity);
        return (input.getValue() == spendAmount);
    }

    public boolean isRegisterInputReady(Mix mix) {
        if (mix.getNbInputs() == 0) {
            return false;
        }
        if (!mix.hasMinMustMixReached()) {
            return false;
        }
        if (mix.getNbInputs() < mix.getTargetAnonymitySet()) {
            return false;
        }
        return true;
    }

    public synchronized void registerOutput(String mixId, String sendAddress, String receiveAddress, String bordereau) throws Exception {
        log.info(" • registered output: " + receiveAddress);
        if (log.isDebugEnabled()) {
            log.debug("sendAddress="+sendAddress);
        }
        Mix mix = getMix(mixId, MixStatus.REGISTER_OUTPUT);
        mix.registerOutput(sendAddress, receiveAddress, bordereau);

        if (isRegisterOutputReady(mix)) {
            validateOutputs(mix);
            changeMixStatus(mixId, MixStatus.SIGNING);
        }
    }

    public void checkRegisterInputReady(Mix mix) {
        if (isRegisterInputReady(mix)) {
            changeMixStatus(mix.getMixId(), MixStatus.REGISTER_OUTPUT);
        }
    }

    private void logMixStatus(Mix mix) {
        int liquiditiesInPool = 0;
        try {
            LiquidityPool liquidityPool = mixLimitsService.getLiquidityPool(mix);
            liquiditiesInPool = liquidityPool.getNbLiquidities();
        } catch(Exception e) {
            // no liquidityPool instanciated yet
        }
        log.info(mix.getNbInputsMustMix()+"/"+ mix.getMinMustMix()+" mustMix, "+ mix.getNbInputs()+"/"+ mix.getTargetAnonymitySet()+" anonymitySet, "+liquiditiesInPool+" liquidities in pool");

        // update mix status in database
        if (mix.getNbInputsMustMix() > 0) {
            try {
                dbService.saveMix(mix);
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    protected void validateOutputs(Mix mix) throws Exception {
        // sendAddresses and receiveAddresses should match (this verifies no user is cheating another one)
        if (!Utils.listEqualsIgnoreOrder(mix.getSendAddresses(), mix.getReceiveAddresses())) {
            log.error("sendAddresses doesn't match receiveAddresses. sendAddresses="+ mix.getSendAddresses()+"receiveAddresses="+ mix.getReceiveAddresses());
            throw new Exception("REGISTER_OUTPUT failed"); // TODO find and ban malicious users
        }
    }

    protected synchronized boolean isRegisterOutputReady(Mix mix) {
        if (!isRegisterInputReady(mix)) {
            // TODO recheck inputs balances and update/ban/reopen REGISTER_INPUT or fail if input spent in the meantime
            return false;
        }
        return (mix.getRegisteredBordereaux().size() == mix.getNbInputs());
    }

    public synchronized void revealOutput(String mixId, String username, String bordereau) throws MixException, IllegalInputException {
        Mix mix = getMix(mixId, MixStatus.REVEAL_OUTPUT);

        // verify an output was registered with this bordereau
        if (!mix.getRegisteredBordereaux().contains(bordereau)) {
            throw new IllegalInputException("Invalid bordereau");
        }
        // verify this bordereau was not already revealed (someone could try to register 2 inputs and reveal same bordereau to block mix)
        if (mix.getRevealedOutputUsers().contains(bordereau)) {
            log.warn("Rejecting already revealed bordereau: "+bordereau);
            throw new IllegalInputException("Bordereau already revealed");
        }
        mix.addRevealedOutputUser(username);
        log.info(" • revealed output: username=" + username);

        if (isRevealOutputReady(mix)) {
            mixLimitsService.blameForRevealOutputAndResetMix(mix);
        }
    }

    protected synchronized boolean isRevealOutputReady(Mix mix) {
        return (mix.getRevealedOutputUsers().size() == mix.getRegisteredBordereaux().size());
    }

    public synchronized void registerSignature(String mixId, String username, byte[][] witness) throws Exception {
        log.info(" • registered signature: username=" + username);
        Mix mix = getMix(mixId, MixStatus.SIGNING);
        Signature signature = new Signature(witness);
        mix.setSignatureByUsername(username, signature);

        if (isRegisterSignaturesReady(mix)) {
            Transaction tx = mix.getTx();
            tx = signTransaction(tx, mix);
            mix.setTx(tx);

            log.info("Tx to broadcast: \n" + tx + "\nRaw: " + Utils.getRawTx(tx));
            try {
                blockchainDataService.broadcastTransaction(tx);
                changeMixStatus(mixId, MixStatus.SUCCESS);
            }
            catch(Exception e) {
                log.error("Unable to broadcast tx", e);
                goFail(mix, FailReason.FAIL_BROADCAST);
            }
        }
    }

    protected synchronized boolean isRegisterSignaturesReady(Mix mix) {
        if (!isRegisterOutputReady(mix)) {
            return false;
        }
        return (mix.getNbSignatures() == mix.getNbInputs());
    }

    public String getCurrentMixdId() {
        return currentMix.getMixId();
    }

    public Mix __getCurrentMix() {
        return currentMix;
    }

    public void changeMixStatus(String mixId, MixStatus mixStatus) {
        log.info("[MIX "+mixId+"] => " + mixStatus);
        try {
            Mix mix = getMix(mixId);
            if (mixStatus.equals(mix.getMixStatus())) {
                // just in case...
                log.error("mixStatus inconsistency detected! (already " + mixStatus + ")", new IllegalStateException());
                return;
            }

            if (mixStatus == MixStatus.REGISTER_OUTPUT) {
                transmitPaymentCodes(mix);
            }
            else if (mixStatus == MixStatus.SIGNING) {
                try {
                    Transaction tx = computeTransaction(mix);
                    mix.setTx(tx);

                    log.info("Txid: "+tx.getHashAsString());
                    if (log.isDebugEnabled()) {
                        log.debug("Tx to sign: \n" + tx + "\nRaw: " + Utils.getRawTx(tx));
                    }
                } catch (Exception e) {
                    log.error("Unexpected exception on buildTransaction() for signing", e);
                    throw new MixException("System error");
                }
            }

            // update mix status
            mix.setMixStatusAndTime(mixStatus);
            try {
                dbService.saveMix(mix);
            } catch(Exception e) {
                log.error("", e);
            }
            mixLimitsService.onMixStatusChange(mix);

            MixStatusNotification mixStatusNotification = computeMixStatusNotification();
            webSocketService.broadcast(mixStatusNotification);

            // start next mix (after notifying clients for success)
            if (mixStatus == MixStatus.SUCCESS) {
                __nextMix();
            } else if (mixStatus == MixStatus.FAIL) {
                __nextMix();
            }
        }
        catch(MixException e) {
            log.error("Unexpected mix error", e);
            __nextMix();
        }
    }

    private void transmitPaymentCodes(Mix mix) throws MixException {
        // get all paymentCodes associated to users
        Map<String,String> paymentCodesByUser = new HashMap<>();
        for (RegisteredInput registeredInput : mix.getInputs()) {
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

    public MixStatusNotification computeMixStatusNotification() throws MixException {
        String mixId = getCurrentMixdId();
        Mix mix = getMix(mixId);
        MixStatusNotification mixStatusNotification = null;
        switch(mix.getMixStatus()) {
            case REGISTER_INPUT:
                try {
                    byte[] publicKey = cryptoService.getPublicKey().getEncoded();
                    mixStatusNotification = new RegisterInputMixStatusNotification(mixId, publicKey, cryptoService.getNetworkParameters().getPaymentProtocolId(), mix.getDenomination(), mix.getFees());
                }
                catch(Exception e) {
                    throw new MixException("unexpected error"); // TODO
                }
                break;
            case REGISTER_OUTPUT:
                String registerOutputUrl = computeRegisterOutputUrl();
                mixStatusNotification = new RegisterOutputMixStatusNotification(mixId, registerOutputUrl);
                break;
            case REVEAL_OUTPUT:
                mixStatusNotification = new RevealOutputMixStatusNotification(mixId);
                break;
            case SIGNING:
                mixStatusNotification = new SigningMixStatusNotification(mixId, mix.getTx().bitcoinSerialize());
                break;
            case SUCCESS:
                mixStatusNotification = new SuccessMixStatusNotification(mixId);
                break;
            case FAIL:
                mixStatusNotification = new FailMixStatusNotification(mixId);
                break;
        }
        return mixStatusNotification;
    }

    private String computeRegisterOutputUrl() {
        String registerOutputUrl = whirlpoolServerConfig.getRegisterOutput().getUrl() + RegisterOutputController.ENDPOINT;
        return registerOutputUrl;
    }

    private Mix getMix(String mixId) throws MixException {
        //Mix mix = mixs.get(mixId);
        //if (mix == null) {
        if (!currentMix.getMixId().equals(mixId)) {
            throw new MixException("Invalid mixId");
        }
        return currentMix;
    }

    private Mix getMix(String mixId, MixStatus mixStatus) throws MixException {
        Mix mix = getMix(mixId);
        if (!mixStatus.equals(mix.getMixStatus())) {
            throw new MixException("Operation not permitted for current mix status");
        }
        return mix;
    }

    private Transaction computeTransaction(Mix mix) throws Exception {
        NetworkParameters params = cryptoService.getNetworkParameters();
        Transaction tx = new Transaction(params);
        List<TransactionInput> inputs = new ArrayList<>();
        List<TransactionOutput> outputs = new ArrayList<>();

        tx.clearOutputs();
        for (String receiveAddress : mix.getReceiveAddresses()) {
            TransactionOutput txOutSpend = bech32Util.getTransactionOutput(receiveAddress, mix.getDenomination(), params);
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
        for (RegisteredInput registeredInput : mix.getInputs()) {
            // send from bech32 input
            long spendAmount = computeSpendAmount(mix, registeredInput.isLiquidity());
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

    private Transaction signTransaction(Transaction tx, Mix mix) {
        for (RegisteredInput registeredInput : mix.getInputs()) {
            Signature signature = mix.getSignatureByUsername(registeredInput.getUsername());

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

    public void goRevealOutput(String mixId) {
        log.info(" • REGISTER_OUTPUT time over (mix failed, blaming users who didn't register output...)");
        changeMixStatus(mixId, MixStatus.REVEAL_OUTPUT);
    }

    public void goFail(Mix mix, FailReason failReason) {
        mix.setFailReason(failReason);
        changeMixStatus(mix.getMixId(), MixStatus.FAIL);
    }

    public synchronized void onClientDisconnect(String username) {
        // mark registeredInput offline
        for (Mix mix : getCurrentMixs()) {
            String mixId = mix.getMixId();
            List<RegisteredInput> registeredInputs = mix.getInputs().parallelStream().filter(registeredInput -> registeredInput.getUsername().equals(username)).collect(Collectors.toList());
            if (!registeredInputs.isEmpty()) {
                if (MixStatus.REGISTER_INPUT.equals(mix.getMixStatus())) {
                    // mix not started yet => remove input as mix isn't started yet
                    registeredInputs.forEach(registeredInput -> {
                        log.info(" • [" + mixId + "] unregistered " + (registeredInput.isLiquidity() ? "liquidity" : "mustMix") + ", username=" + registeredInput.getUsername());
                        mix.unregisterInput(registeredInput);
                    });
                }
                else {
                    // mix already started => mark input as offline
                    registeredInputs.forEach(registeredInput -> {
                        log.info(" • [" + mixId + "] offlined " + (registeredInput.isLiquidity() ? "liquidity" : "mustMix") + ", username=" + registeredInput.getUsername());
                        registeredInput.setOffline(true);
                    });
                }
            }
        }
    }

    private List<Mix> getCurrentMixs() {
        List<Mix> currentMixes = new ArrayList<>();
        currentMixes.add(__getCurrentMix());
        return currentMixes;
    }

    public void __reset(String mixId) {
        Mix copyMix = new Mix(mixId, this.currentMix);
        __reset(copyMix);
    }

    public void __nextMix() {
        String mixId = generateMixId();
        __reset(mixId);
    }

    public void __reset(Mix mix) {
        if (this.currentMix != null) {
            mixLimitsService.unmanage(mix);
        }

        log.info("[NEW MIX "+ mix.getMixId()+"]");
        logMixStatus(mix);
        this.currentMix = mix;
        // TODO disconnect all clients (except liquidities?)
        mixLimitsService.manage(mix);
    }

    public MixLimitsService __getMixLimitsService() {
        return mixLimitsService;
    }

    public void __setUseDeterministPaymentCodeMatching(boolean useDeterministPaymentCodeMatching) {
        this.deterministPaymentCodeMatching = useDeterministPaymentCodeMatching;
    }
}
