package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

public class Mix {
    private MixTO mixTO;

    private String mixId;
    private AsymmetricCipherKeyPair keyPair;
    private Timestamp timeStarted;
    private Map<MixStatus,Timestamp> timeStatus;

    private Pool pool;
    private int targetAnonymitySet;
    private boolean acceptLiquidities;

    private MixStatus mixStatus;
    private Map<String,RegisteredInput> inputsById;

    private Set<String> receiveAddresses;
    private Map<String,String> revealedReceiveAddressesByUsername;
    private Map<String,Signature> signatures;

    private Transaction tx;
    private FailReason failReason;

    public Mix(String mixId, Pool pool, AsymmetricCipherKeyPair keyPair) {
        this.mixTO = null;
        this.mixId = mixId;
        this.keyPair = keyPair;
        this.timeStarted = new Timestamp(System.currentTimeMillis());
        this.timeStatus = new HashMap<>();

        this.pool = pool;
        this.targetAnonymitySet = pool.getTargetAnonymitySet();
        this.acceptLiquidities = false;

        this.mixStatus = MixStatus.REGISTER_INPUT;
        this.inputsById = new HashMap<>();

        this.receiveAddresses = new HashSet<>();
        this.revealedReceiveAddressesByUsername = new HashMap<>();
        this.signatures = new HashMap<>();

        this.tx = null;
        this.failReason = null;
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

    public AsymmetricCipherKeyPair getKeyPair() {
        return keyPair;
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

    public String computeInputsHash() {
        Collection<Utxo> inputs = getInputs().parallelStream().map(input -> new Utxo(input.getInput().getHash(), input.getInput().getIndex())).collect(Collectors.toList());
        return WhirlpoolProtocol.computeInputsHash(inputs);
    }

    public synchronized void registerOutput(String receiveAddress) {
        receiveAddresses.add(receiveAddress);
    }

    public long getElapsedTime() {
        // return first input registration time when 1 mustMix already connected, otherwise return mix started time
        long timeStarted = getTimeStatus().getOrDefault(MixStatus.REGISTER_INPUT, getTimeStarted()).getTime();
        long elapsedTime = System.currentTimeMillis() - timeStarted;
        return elapsedTime;
    }

    public Set<String> getReceiveAddresses() {
        return receiveAddresses;
    }

    public boolean hasRevealedOutputUsername(String username) {
        return revealedReceiveAddressesByUsername.containsKey(username);
    }

    public boolean hasRevealedReceiveAddress(String receiveAddress) {
        return revealedReceiveAddressesByUsername.containsValue(receiveAddress);
    }

    public void addRevealedOutput(String username, String receiveAddress) {
        revealedReceiveAddressesByUsername.put(username, receiveAddress);
    }

    public int getNbRevealedOutputs() {
        return revealedReceiveAddressesByUsername.size();
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
