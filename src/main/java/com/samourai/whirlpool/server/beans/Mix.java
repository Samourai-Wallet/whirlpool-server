package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.v1.notifications.MixStatus;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.Transaction;

import java.sql.Timestamp;
import java.util.*;

public class Mix {
    private MixTO mixTO;
    private String mixId;
    private Timestamp timeStarted;
    private Map<MixStatus,Timestamp> timeStatus;
    private long denomination; // in satoshis
    private long fees; // in satoshis
    private int minMustMix;
    private int targetAnonymitySetInitial;
    private int targetAnonymitySet;
    private int minAnonymitySet;
    private int maxAnonymitySet;
    private long timeoutAdjustAnonymitySet; // wait X seconds for decreasing anonymitySet
    private boolean acceptLiquidities;
    private long liquidityTimeout; // wait X seconds for accepting liquidities

    private MixStatus mixStatus;
    private Map<String,RegisteredInput> inputsById;

    private List<String> sendAddresses;
    private List<String> receiveAddresses;
    private Set<String> registeredBordereaux;
    private Set<String> revealedOutputUsers;
    private Map<String,Signature> signatures;

    private Transaction tx;
    private FailReason failReason;

    public Mix(String mixId, long denomination, long fees, int minMustMix, int targetAnonymitySet, int minAnonymitySet, int maxAnonymitySet, long timeoutAdjustAnonymitySet, long liquidityTimeout) {
        this.mixTO = null;
        this.mixId = mixId;
        this.timeStarted = new Timestamp(System.currentTimeMillis());
        this.timeStatus = new HashMap<>();
        this.denomination = denomination;
        this.fees = fees;
        this.minMustMix = minMustMix;
        this.targetAnonymitySetInitial = targetAnonymitySet;
        this.targetAnonymitySet = targetAnonymitySet;
        this.minAnonymitySet = minAnonymitySet;
        this.maxAnonymitySet = maxAnonymitySet;
        this.timeoutAdjustAnonymitySet = timeoutAdjustAnonymitySet;
        this.acceptLiquidities = false;
        this.liquidityTimeout = liquidityTimeout;

        this.mixStatus = MixStatus.REGISTER_INPUT;
        this.inputsById = new HashMap<>();

        this.sendAddresses = new LinkedList<>();
        this.receiveAddresses = new LinkedList<>();
        this.registeredBordereaux = new HashSet<>();
        this.revealedOutputUsers = new HashSet<>();
        this.signatures = new HashMap<>();

        this.tx = null;
        this.failReason = null;
    }

    public Mix(String mixId, Mix copyMix) {
        this(mixId, copyMix.getDenomination(), copyMix.getFees(), copyMix.getMinMustMix(), copyMix.getTargetAnonymitySetInitial(), copyMix.getMinAnonymitySet(), copyMix.getMaxAnonymitySet(), copyMix.getTimeoutAdjustAnonymitySet(), copyMix.getLiquidityTimeout());
    }

    public MixTO computeMixTO() {
        if (mixTO == null) {
            mixTO = new MixTO();
        }
        mixTO.update(this);
        return mixTO;
    }

    public boolean hasMinMustMixReached() {
        return getNbInputsMustMix() >= getMinMustMix();
    }

    public String getMixId() {
        return mixId;
    }

    public Timestamp getTimeStarted() {
        return timeStarted;
    }

    public Map<MixStatus, Timestamp> getTimeStatus() {
        return timeStatus;
    }

    public long getDenomination() {
        return denomination;
    }

    public long getFees() {
        return fees;
    }

    public int getMinMustMix() {
        return minMustMix;
    }

    public int getTargetAnonymitySetInitial() {
        return targetAnonymitySetInitial;
    }

    public int getTargetAnonymitySet() {
        return targetAnonymitySet;
    }

    public void setTargetAnonymitySet(int targetAnonymitySet) {
        this.targetAnonymitySet = targetAnonymitySet;
    }

    public int getMinAnonymitySet() {
        return minAnonymitySet;
    }

    public int getMaxAnonymitySet() {
        return maxAnonymitySet;
    }

    public long getTimeoutAdjustAnonymitySet() {
        return timeoutAdjustAnonymitySet;
    }

    public boolean isAcceptLiquidities() {
        return acceptLiquidities;
    }

    public void setAcceptLiquidities(boolean acceptLiquidities) {
        this.acceptLiquidities = acceptLiquidities;
    }

    public long getLiquidityTimeout() {
        return liquidityTimeout;
    }

    public MixStatus getMixStatus() {
        return mixStatus;
    }

    public void setMixStatusAndTime(MixStatus mixStatus) {
        this.mixStatus = mixStatus;
        timeStatus.put(mixStatus, new Timestamp(System.currentTimeMillis()));
    }

    public Collection<RegisteredInput> getInputs() {
        return inputsById.values();
    }

    public int getNbInputs() {
        return inputsById.size();
    }

    public int getNbInputsMustMix() {
        return (int)getInputs().parallelStream().filter(input -> !input.isLiquidity()).count();
    }

    public int getNbInputsLiquidities() {
        return (int)getInputs().parallelStream().filter(input -> input.isLiquidity()).count();
    }

    public synchronized void registerInput(RegisteredInput registeredInput) throws MixException {
        String inputId = Utils.computeInputId(registeredInput.getInput());
        if (inputsById.containsKey(inputId)) {
            throw new MixException("input already registered");
        }
        inputsById.put(inputId, registeredInput);

        if (timeStatus.get(MixStatus.REGISTER_INPUT) == null && !registeredInput.isLiquidity()) {
            timeStatus.put(MixStatus.REGISTER_INPUT, new Timestamp(System.currentTimeMillis()));
        }
    }

    public synchronized void unregisterInput(RegisteredInput registeredInput) {
        String inputId = Utils.computeInputId(registeredInput.getInput());
        inputsById.remove(inputId);
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

    public void setFailReason(FailReason failReason) {
        this.failReason = failReason;
    }

    public FailReason getFailReason() {
        return failReason;
    }
}
