package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.exceptions.RoundException;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.Transaction;

import java.util.*;

public class Round {
    private String roundId;
    private long denomination; // in satoshis
    private long fees; // in satoshis
    private int targetMustMixInitial;
    private int targetMustMix;
    private int minMustMix;
    private long mustMixAdjustTimeout; // wait X seconds for decreasing targetMustMix
    private float liquidityRatio; // 1 = 1 liquidity for 1 mustMix

    private RoundStatus roundStatus;
    private long roundStatusTime;
    private Map<String,RegisteredInput> inputsById;

    private List<String> sendAddresses;
    private List<String> receiveAddresses;
    private Set<String> registeredBordereaux;
    private Set<String> revealedOutputUsers;
    private Map<String,Signature> signatures;

    private Transaction tx;
    private RoundResult failReason;

    public Round(String roundId, long denomination, long fees, int targetMustMix, int minMustMix, long mustMixAdjustTimeout, float liquidityRatio) {
        this.roundId = roundId;
        this.denomination = denomination;
        this.fees = fees;
        this.targetMustMixInitial = targetMustMix;
        this.targetMustMix = targetMustMix;
        this.minMustMix = minMustMix;
        this.mustMixAdjustTimeout = mustMixAdjustTimeout;
        this.liquidityRatio = liquidityRatio;

        this.roundStatus = RoundStatus.REGISTER_INPUT;
        this.roundStatusTime = System.currentTimeMillis();
        this.inputsById = new HashMap<>();

        this.sendAddresses = new LinkedList<>();
        this.receiveAddresses = new LinkedList<>();
        this.registeredBordereaux = new HashSet<>();
        this.revealedOutputUsers = new HashSet<>();
        this.signatures = new HashMap<>();

        this.tx = null;
        this.failReason = null;
    }

    public Round(String roundId, Round copyRound) {
        this(roundId, copyRound.getDenomination(), copyRound.getFees(), copyRound.getTargetMustMixInitial(), copyRound.getMinMustMix(), copyRound.getMustMixAdjustTimeout(), copyRound.liquidityRatio);
    }

    public String getRoundId() {
        return roundId;
    }

    public long getDenomination() {
        return denomination;
    }

    public long getFees() {
        return fees;
    }

    public int getTargetMustMixInitial() {
        return targetMustMixInitial;
    }

    public int getTargetMustMix() {
        return targetMustMix;
    }

    public void setTargetMustMix(int targetMustMix) {
        this.targetMustMix = targetMustMix;
    }

    public int getMinMustMix() {
        return minMustMix;
    }

    public long getMustMixAdjustTimeout() {
        return mustMixAdjustTimeout;
    }

    public int computeLiquiditiesExpected() {
        return (int)Math.ceil(getTargetMustMix() * liquidityRatio);
    }

    public RoundStatus getRoundStatus() {
        return roundStatus;
    }

    public void setRoundStatusAndTime(RoundStatus roundStatus) {
        this.roundStatus = roundStatus;
    }

    public Collection<RegisteredInput> getInputs() {
        return inputsById.values();
    }

    public int getNbInputs() {
        return inputsById.size();
    }

    public synchronized void registerInput(RegisteredInput registeredInput) throws RoundException {
        String inputId = Utils.computeInputId(registeredInput.getInput());
        if (inputsById.containsKey(inputId)) {
            throw new RoundException("input already registered");
        }
        inputsById.put(inputId, registeredInput);
    }

    public boolean hasInput(TxOutPoint outPoint) {
        return inputsById.containsKey(Utils.computeInputId(outPoint)) ;
    }

    public synchronized  void registerOutput(String sendAddress, String receiveAddress, String bordereau) {
        sendAddresses.add(sendAddress);
        receiveAddresses.add(receiveAddress);
        registeredBordereaux.add(bordereau);
    }

    public List<String> getSendAddresses() {
        return sendAddresses;
    }

    public List<String> getReceiveAddresses() {
        return receiveAddresses;
    }

    public Set<String> getRegisteredBordereaux() {
        return registeredBordereaux;
    }

    public void addRevealedOutputUser(String user) {
        revealedOutputUsers.add(user);
    }

    public Set<String> getRevealedOutputUsers() {
        return revealedOutputUsers;
    }

    public int getNbSignatures() {
        return signatures.size();
    }

    public Signature getSignatureByUsername(String username) {
        return signatures.get(username);
    }

    public void setSignatureByUsername(String username, Signature userSignature) {
        signatures.put(username, userSignature);
    }

    public void setTx(Transaction tx) {
        this.tx = tx;
    }

    public Transaction getTx() {
        return tx;
    }

    public void setFailReason(RoundResult failReason) {
        this.failReason = failReason;
    }

    public RoundResult getFailReason() {
        return failReason;
    }
}
