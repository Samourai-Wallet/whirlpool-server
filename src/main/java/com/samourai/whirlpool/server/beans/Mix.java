package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.utils.Utils;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

public class Mix {
  private MixTO mixTO;

  private String mixId;
  private AsymmetricCipherKeyPair keyPair;
  private byte[] publicKey;
  private Timestamp timeStarted;
  private Map<MixStatus, Timestamp> timeStatus;
  private ScheduledFuture scheduleRegisterOutput;

  private Pool pool;
  private int targetAnonymitySet;
  private boolean acceptLiquidities;

  private MixStatus mixStatus;
  private InputPool confirmingInputs;
  private Map<String, ConfirmedInput> inputsById;

  private Set<String> receiveAddresses;
  private Map<String, String> revealedReceiveAddressesByUsername;
  private Map<String, Signature> signatures;

  private Transaction tx;
  private FailReason failReason;

  public Mix(String mixId, Pool pool, CryptoService cryptoService) {
    this.mixTO = null;
    this.mixId = mixId;
    this.keyPair = cryptoService.generateKeyPair();
    try {
      this.publicKey = cryptoService.computePublicKey(keyPair).getEncoded();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.timeStarted = new Timestamp(System.currentTimeMillis());
    this.timeStatus = new HashMap<>();
    this.scheduleRegisterOutput = null;

    this.pool = pool;
    this.targetAnonymitySet = pool.getTargetAnonymitySet();
    this.acceptLiquidities = false;

    this.mixStatus = MixStatus.CONFIRM_INPUT;
    this.confirmingInputs = new InputPool();
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

  public String getMixId() {
    return mixId;
  }

  public AsymmetricCipherKeyPair getKeyPair() {
    return keyPair;
  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public Timestamp getTimeStarted() {
    return timeStarted;
  }

  public Map<MixStatus, Timestamp> getTimeStatus() {
    return timeStatus;
  }

  public ScheduledFuture getScheduleRegisterOutput() {
    return scheduleRegisterOutput;
  }

  public void setScheduleRegisterOutput(ScheduledFuture scheduleRegisterOutput) {
    this.scheduleRegisterOutput = scheduleRegisterOutput;
  }

  public void clearScheduleRegisterOutput() {
    if (scheduleRegisterOutput != null) {
      scheduleRegisterOutput.cancel(false);
      scheduleRegisterOutput = null;
    }
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

  public boolean hasConfirmingInput(TxOutPoint txOutPoint) {
    return confirmingInputs.hasInput(txOutPoint);
  }

  public synchronized void registerConfirmingInput(RegisteredInput registeredInput) {
    confirmingInputs.register(registeredInput);
  }

  public synchronized Optional<RegisteredInput> peekConfirmingInputByUsername(String username) {
    return confirmingInputs.removeByUsername(username);
  }

  public boolean hasPendingConfirmingInputs() {
    return confirmingInputs.hasInputs();
  }

  public int getNbConfirmingInputs() {
    return confirmingInputs.getSize();
  }

  public Collection<ConfirmedInput> getInputs() {
    return inputsById.values();
  }

  public int getNbInputs() {
    return inputsById.size();
  }

  public int getNbInputsMustMix() {
    return (int)
        getInputs()
            .parallelStream()
            .filter(input -> !input.getRegisteredInput().isLiquidity())
            .count();
  }

  public int getNbInputsLiquidities() {
    return (int)
        getInputs()
            .parallelStream()
            .filter(input -> input.getRegisteredInput().isLiquidity())
            .count();
  }

  public synchronized void registerInput(ConfirmedInput confirmedInput) throws MixException {
    String inputId = Utils.computeInputId(confirmedInput.getRegisteredInput().getInput());
    if (inputsById.containsKey(inputId)) {
      throw new MixException("input already registered");
    }
    inputsById.put(inputId, confirmedInput);

    if (!confirmedInput.getRegisteredInput().isLiquidity() && getNbInputsMustMix() == 1) {
      timeStatus.put(MixStatus.CONFIRM_INPUT, new Timestamp(System.currentTimeMillis()));
    }
  }

  public synchronized void unregisterInput(ConfirmedInput confirmedInput) {
    String inputId = Utils.computeInputId(confirmedInput.getRegisteredInput().getInput());
    inputsById.remove(inputId);
  }

  public boolean hasInput(TxOutPoint outPoint) {
    return inputsById.containsKey(Utils.computeInputId(outPoint));
  }

  public String computeInputsHash() {
    Collection<Utxo> inputs =
        getInputs()
            .parallelStream()
            .map(confirmedInput -> confirmedInput.getRegisteredInput().getInput())
            .map(input -> new Utxo(input.getHash(), input.getIndex()))
            .collect(Collectors.toList());
    return WhirlpoolProtocol.computeInputsHash(inputs);
  }

  public synchronized void registerOutput(String receiveAddress) {
    receiveAddresses.add(receiveAddress);
  }

  public long getElapsedTime() {
    // return elapsed time since first mustMix confirmed, otherwise return mix start time
    long timeStarted =
        getTimeStatus().getOrDefault(MixStatus.CONFIRM_INPUT, getTimeStarted()).getTime();
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

  public boolean isInvitationOpen(boolean liquidity) {
    return MixStatus.CONFIRM_INPUT.equals(mixStatus) && (!liquidity || acceptLiquidities);
  }
}
