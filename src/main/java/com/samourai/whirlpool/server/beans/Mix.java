package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
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

    private Pool pool;
    private int targetAnonymitySet;
    private boolean acceptLiquidities;

    private MixStatus mixStatus;
    private Map<String,RegisteredInput> inputsById;

    private List<String> receiveAddresses;
    private Set<String> registeredBordereaux;
    private Set<String> revealedOutputUsers;
    private Map<String,Signature> signatures;

    private Transaction tx;
    private FailReason failReason;

    public Mix(String mixId, Pool pool) {
        this.mixTO = null;
        this.mixId = mixId;
        this.timeStarted = new Timestamp(System.currentTimeMillis());
        this.timeStatus = new HashMap<>();

        this.pool = pool;
        this.targetAnonymitySet = pool.getTargetAnonymitySet();
        this.acceptLiquidities = false;

        this.mixStatus = MixStatus.REGISTER_INPUT;
        this.inputsById = new HashMap<>();

        this.receiveAddresses = new LinkedList<>();
        this.registeredBordereaux = new HashSet<>();
        this.revealedOutputUsers = new HashSet<>();
        this.signatures = new HashMap<>();

        this.tx = null;
        this.failReason = null;
    }

    public Mix(String mixId, Mix copyMix) {
        this(mixId, copyMix.getPool());
    }

    public MixTO computeMixTO() {
        if (mixTO == null) {
            mixTO = new MixTO();
        }
        mixTO.update(this);
        return mixTO;
    }

    public Optional<MixTO> __getMixTO() {
        return Optional.ofNullable(mixTO);
    }

    public boolean hasMinMustMixReached() {
        return getNbInputsMustMix() >= pool.getMinMustMix();
    }

    public boolean isFull() {
        return (getNbInputs() >= pool.getMaxAnonymitySet());
    }

    public boolean checkInputBalance(long inputBalance, boolean liquidity) {
        long minBalance = computeInputBalanceMin(liquidity);
        long maxBalance = computeInputBalanceMax(liquidity);
        return inputBalance >= minBalance && inputBalance <= maxBalance;
    }
    public long computeInputBalanceMin(boolean liquidity) {
        return WhirlpoolProtocol.computeInputBalanceMin(getPool().getDenomination(), liquidity, getPool().getMinerFeeMin());
    }

    public long computeInputBalanceMax(boolean liquidity) {
        return WhirlpoolProtocol.computeInputBalanceMax(getPool().getDenomination(), liquidity, getPool().getMinerFeeMax());
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

    public Pool getPool() {
        return pool;
    }

    public int getTargetAnonymitySet() {
        return targetAnonymitySet;
    }

    public void setTargetAnonymitySet(int targetAnonymitySet) {
        this.targetAnonymitySet = targetAnonymitySet;
    }

    public boolean isAcceptLiquidities() {
        return acceptLiquidities;
    }

    public void setAcceptLiquidities(boolean acceptLiquidities) {
        this.acceptLiquidities = acceptLiquidities;
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

        if (!registeredInput.isLiquidity() && getNbInputsMustMix() == 1) {
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

    public synchronized  void registerOutput(String receiveAddress, String bordereau) {
        receiveAddresses.add(receiveAddress);
        registeredBordereaux.add(bordereau);
    }

    public long getElapsedTime() {
        // return first input registration time when 1 mustMix already connected, otherwise return mix started time
        long timeStarted = getTimeStatus().getOrDefault(MixStatus.REGISTER_INPUT, getTimeStarted()).getTime();
        long elapsedTime = System.currentTimeMillis() - timeStarted;
        return elapsedTime;
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
