package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.FailMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SuccessMixStatusNotification;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.BroadcastException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MixService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WebSocketService webSocketService;
  private CryptoService cryptoService;
  private BlameService blameService;
  private DbService dbService;
  private RpcClientService rpcClientService;
  private MixLimitsService mixLimitsService;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private PoolService poolService;
  private ExportService exportService;
  private TaskService taskService;
  private TxUtil txUtil;

  private Map<String, Mix> currentMixs;

  private static final int GRACE_TIME_CONFIRMING_INPUTS = 10000;

  @Autowired
  public MixService(
      CryptoService cryptoService,
      BlameService blameService,
      DbService dbService,
      RpcClientService rpcClientService,
      WebSocketService webSocketService,
      Bech32UtilGeneric bech32Util,
      WhirlpoolServerConfig whirlpoolServerConfig,
      MixLimitsService mixLimitsService,
      PoolService poolService,
      ExportService exportService,
      TaskService taskService,
      TxUtil txUtil) {
    this.cryptoService = cryptoService;
    this.blameService = blameService;
    this.dbService = dbService;
    this.rpcClientService = rpcClientService;
    this.webSocketService = webSocketService;
    this.bech32Util = bech32Util;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    mixLimitsService.setMixService(this); // avoids circular reference
    this.mixLimitsService = mixLimitsService;
    this.poolService = poolService;
    this.exportService = exportService;
    this.taskService = taskService;
    this.txUtil = txUtil;

    this.currentMixs = new HashMap<>();

    this.__reset();
  }

  /** Last input validations when adding it to a mix (not when queueing it) */
  private void validateOnConfirmInput(Mix mix, ConfirmedInput confirmedInput)
      throws QueueInputException, IllegalInputException {
    RegisteredInput registeredInput = confirmedInput.getRegisteredInput();

    // verify mix not full
    if (mix.isFull()) {
      throw new QueueInputException(
          "Current mix is full", registeredInput, mix.getPool().getPoolId());
    }

    // verify not already registered
    if (mix.hasInput(registeredInput.getOutPoint())) {
      throw new IllegalInputException("Input already registered for this mix");
    }

    // liquidity: verify liquidities open
    if (registeredInput.isLiquidity() && !isRegisterLiquiditiesOpen(mix)) {
      throw new IllegalInputException(
          "Current mix not opened to liquidities yet"); // should not happen
    }

    // verify max-inputs-same-hash
    String inputHash = registeredInput.getOutPoint().getHash();
    int maxInputsSameHash = whirlpoolServerConfig.getRegisterInput().getMaxInputsSameHash();
    long countInputsSameHash =
        mix.getInputs()
            .parallelStream()
            .filter(input -> input.getRegisteredInput().getOutPoint().getHash().equals(inputHash))
            .count();
    if ((countInputsSameHash + 1) > maxInputsSameHash) {
      if (log.isDebugEnabled()) {
        log.debug("already " + countInputsSameHash + " inputs with same hash: " + inputHash);
      }
      throw new QueueInputException(
          "Current mix is full for inputs with same hash",
          registeredInput,
          mix.getPool().getPoolId());
    }
  }

  public synchronized byte[] confirmInput(String mixId, String username, byte[] blindedBordereau)
      throws IllegalInputException, MixException, QueueInputException {
    Mix mix = getMix(mixId);

    // find confirming input
    RegisteredInput registeredInput =
        mix.removeConfirmingInputByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException("Confirming input not found: username=" + username));

    // check mix didn't start yet
    if (!MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus())) {
      // confirming input too late => enqueue in pool
      String poolId = mix.getPool().getPoolId();
      throw new QueueInputException("Mix already started", registeredInput, poolId);
    }

    ConfirmedInput confirmedInput = new ConfirmedInput(registeredInput, blindedBordereau);

    // last input validations
    validateOnConfirmInput(mix, confirmedInput);

    // sign bordereau to reply
    byte[] signedBordereau = cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // add to mix inputs
    mix.registerInput(confirmedInput);
    log.info(
        " • registered "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix")
            + ": "
            + registeredInput.getOutPoint());
    logMixStatus(mix);

    // reply confirmInputResponse with signedBordereau
    String signedBordereau64 = WhirlpoolProtocol.encodeBytes(signedBordereau);
    ConfirmInputResponse confirmInputResponse = new ConfirmInputResponse(mixId, signedBordereau64);
    webSocketService.sendPrivate(username, confirmInputResponse);

    // check mix limits
    mixLimitsService.onInputConfirmed(mix);

    // check mix ready
    checkConfirmInputReady(mix);
    return signedBordereau;
  }

  public void checkConfirmInputReady(Mix mix) {
    checkConfirmInputReady(mix, true);
  }

  private void checkConfirmInputReady(Mix mix, boolean allowGracePeriod) {
    if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus()) && isRegisterInputReady(mix)) {

      // ready to go REGISTER_OUTPUT
      if (allowGracePeriod
          && GRACE_TIME_CONFIRMING_INPUTS > 0
          && mix.hasPendingConfirmingInputs()
          && mix.getNbInputs() < mix.getPool().getMaxAnonymitySet()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Ready to go REGISTER_OUTPUT - waiting for last pending confirmations: pendingConfirmingInputs="
                  + mix.getNbConfirmingInputs()
                  + ", nbInputs="
                  + mix.getNbInputs()
                  + ", maxAnonymitySet="
                  + mix.getPool().getMaxAnonymitySet());
        }

        // allow grace period for pending inputs confirmations...
        ScheduledFuture scheduledRegisterOutput = mix.getScheduleRegisterOutput();
        if (scheduledRegisterOutput == null) {
          // schedule
          if (log.isDebugEnabled()) {
            log.debug("Scheduling REGISTER_OUTPUT, in " + GRACE_TIME_CONFIRMING_INPUTS + "...");
          }
          ScheduledFuture scheduledFuture =
              taskService.runOnce(
                  GRACE_TIME_CONFIRMING_INPUTS,
                  () -> {
                    if (log.isDebugEnabled()) {
                      log.debug("REGISTER_OUTPUT schedule expired.");
                    }
                    mix.clearScheduleRegisterOutput();
                    checkConfirmInputReady(mix, false);
                  });
          mix.setScheduleRegisterOutput(scheduledFuture);
        } else {
          // already scheduled
          if (log.isDebugEnabled()) {
            log.debug(
                "REGISTER_OUTPUT already scheduled, in "
                    + scheduledRegisterOutput.getDelay(TimeUnit.SECONDS)
                    + "s");
          }
        }
      } else {
        // all inputs confirmed or mix full => REGISTER_OUTPUT
        goRegisterOutput(mix);
      }
    }
  }

  private void goRegisterOutput(Mix mix) {
    mix.clearScheduleRegisterOutput();
    changeMixStatus(mix.getMixId(), MixStatus.REGISTER_OUTPUT);
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

  public synchronized void registerOutput(
      String inputsHash, byte[] unblindedSignedBordereau, String receiveAddress) throws Exception {
    Mix mix = getMixByInputsHash(inputsHash, MixStatus.REGISTER_OUTPUT);

    // verify unblindedSignedBordereau
    if (!cryptoService.verifyUnblindedSignedBordereau(
        receiveAddress, unblindedSignedBordereau, mix.getKeyPair())) {
      throw new IllegalInputException("Invalid unblindedSignedBordereau");
    }

    log.info(" • registered output: " + receiveAddress);
    mix.registerOutput(receiveAddress);

    if (isRegisterOutputReady(mix)) {
      String mixId = mix.getMixId();
      changeMixStatus(mixId, MixStatus.SIGNING);
    }
  }

  private void logMixStatus(Mix mix) {
    int liquiditiesQueued = mix.getPool().getLiquidityQueue().getSize();
    int mustMixQueued = mix.getPool().getMustMixQueue().getSize();
    log.info(
        mix.getNbInputsMustMix()
            + "/"
            + mix.getPool().getMinMustMix()
            + " mustMix, "
            + mix.getNbInputs()
            + "/"
            + mix.getTargetAnonymitySet()
            + " anonymitySet (pool: "
            + liquiditiesQueued
            + " liquidities + "
            + mustMixQueued
            + " mustMixs)");

    // update mix status in database
    if (mix.getNbInputsMustMix() > 0) {
      try {
        dbService.saveMix(mix);
      } catch (Exception e) {
        log.error("", e);
      }
    }
  }

  protected synchronized boolean isRegisterOutputReady(Mix mix) {
    if (!isRegisterInputReady(mix)) {
      // TODO recheck inputs balances and update/ban/reopen REGISTER_INPUT or fail if input spent in
      // the meantime
      return false;
    }
    return (mix.getReceiveAddresses().size() == mix.getNbInputs());
  }

  public synchronized void revealOutput(String mixId, String username, String receiveAddress)
      throws MixException, IllegalInputException {
    Mix mix = getMix(mixId, MixStatus.REVEAL_OUTPUT);

    // verify this username didn't already reveal his output
    if (mix.hasRevealedOutputUsername(username)) {
      log.warn("Rejecting already revealed username: " + username);
      throw new IllegalInputException("Output already revealed");
    }
    // verify this receiveAddress was not already revealed (someone could try to register 2 inputs
    // and reveal same receiveAddress to block mix)
    if (mix.hasRevealedReceiveAddress(receiveAddress)) {
      log.warn("Rejecting already revealed receiveAddress: " + receiveAddress);
      throw new IllegalInputException("ReceiveAddress already revealed");
    }

    // verify an output was registered with this receiveAddress
    if (!mix.getReceiveAddresses().contains(receiveAddress)) {
      throw new IllegalInputException("Invalid receiveAddress");
    }

    mix.addRevealedOutput(username, receiveAddress);
    log.info(" • revealed output: username=" + username);

    if (isRevealOutputReady(mix)) {
      blameForRevealOutputAndResetMix(mix);
    }
  }

  protected synchronized boolean isRevealOutputReady(Mix mix) {
    return (mix.getNbRevealedOutputs()
        == mix.getNbInputs()); // TODO -1 to not wait for the one who didn't sign?
  }

  public synchronized void registerSignature(String mixId, String username, String[] witness60)
      throws Exception {
    Mix mix = getMix(mixId, MixStatus.SIGNING);

    // check user
    ConfirmedInput confirmedInput =
        mix.getInputByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException("Input not found for signing username=" + username));
    if (mix.getSignedByUsername(username)) {
      throw new IllegalInputException("User already signed, username=" + username);
    }
    TxOutPoint txOutPoint = confirmedInput.getRegisteredInput().getOutPoint();

    // sign
    Transaction tx = mix.getTx();
    Integer inputIndex = txUtil.findInputIndex(tx, txOutPoint.getHash(), txOutPoint.getIndex());
    TransactionWitness witness = Utils.witnessUnserialize64(witness60);
    tx.setWitness(inputIndex, witness);

    // verify
    try {
      txUtil.verifySignInput(tx, inputIndex, txOutPoint.getValue(), txOutPoint.getScriptBytes());
    } catch (Exception e) {
      log.error("Invalid signature", e);
      throw new IllegalInputException("Invalid signature");
    }

    // signature success
    mix.setTx(tx);
    mix.setSignedByUsername(username);
    log.info(" • registered signature: username=" + username);

    if (isRegisterSignaturesReady(mix)) {
      // check final transaction
      tx.verify();

      log.info("Tx to broadcast: \n" + tx + "\nRaw: " + Utils.getRawTx(tx));
      try {
        rpcClientService.broadcastTransaction(tx);
        goSuccess(mix);
      } catch (BroadcastException e) {
        log.error("Unable to broadcast tx: ", e);
        goFail(mix, FailReason.FAIL_BROADCAST, e.getFailInfo());
      }
    }
  }

  protected synchronized boolean isRegisterSignaturesReady(Mix mix) {
    if (!isRegisterOutputReady(mix)) {
      return false;
    }
    return (mix.getNbSignatures() == mix.getNbInputs());
  }

  public void changeMixStatus(String mixId, MixStatus mixStatus) {
    log.info("[MIX " + mixId + "] => " + mixStatus);
    Mix mix = null;
    try {
      mix = getMix(mixId);
      if (mixStatus.equals(mix.getMixStatus())) {
        // just in case...
        log.error(
            "mixStatus inconsistency detected! (already " + mixStatus + ")",
            new IllegalStateException());
        return;
      }

      if (mixStatus == MixStatus.SIGNING) {
        try {
          Transaction tx = computeTransaction(mix);
          mix.setTx(tx);

          log.info("Txid: " + tx.getHashAsString());
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
      } catch (Exception e) {
        log.error("", e);
      }
      mixLimitsService.onMixStatusChange(mix);

      // notify users (ConfirmInputResponse was already sent when user joined mix)
      if (mixStatus != MixStatus.CONFIRM_INPUT) {
        MixStatusNotification mixStatusNotification = computeMixStatusNotification(mixId);
        sendToMixingUsers(mix, mixStatusNotification);
      }

      // start next mix (after notifying clients for success)
      if (mixStatus == MixStatus.SUCCESS) {
        // save mix txid
        try {
          dbService.saveMixTxid(mix.getTx().getHashAsString(), mix.getPool().getDenomination());
        } catch (Exception e) {
          log.error("", e);
        }

        __nextMix(mix.getPool());
      } else if (mixStatus == MixStatus.FAIL) {
        __nextMix(mix.getPool());
      }
    } catch (MixException e) {
      log.error("Unexpected mix error", e);
      if (mix != null) {
        __nextMix(mix.getPool());
      }
    }
  }

  private void sendToMixingUsers(Mix mix, Object payload) {
    List<String> usernames =
        mix.getInputs()
            .parallelStream()
            .map(confirmedInput -> confirmedInput.getRegisteredInput().getUsername())
            .collect(Collectors.toList());
    webSocketService.sendPrivate(usernames, payload);
  }

  private MixStatusNotification computeMixStatusNotification(String mixId) throws MixException {
    Mix mix = getMix(mixId);
    MixStatusNotification mixStatusNotification = null;
    switch (mix.getMixStatus()) {
      case REGISTER_OUTPUT:
        String inputsHash = mix.computeInputsHash();
        mixStatusNotification = new RegisterOutputMixStatusNotification(mixId, inputsHash);
        break;
      case REVEAL_OUTPUT:
        mixStatusNotification = new RevealOutputMixStatusNotification(mixId);
        break;
      case SIGNING:
        String tx64 = WhirlpoolProtocol.encodeBytes(mix.getTx().bitcoinSerialize());
        mixStatusNotification = new SigningMixStatusNotification(mixId, tx64);
        break;
      case SUCCESS:
        mixStatusNotification = new SuccessMixStatusNotification(mixId);
        break;
      case FAIL:
        mixStatusNotification = new FailMixStatusNotification(mixId);
        break;
      default:
        log.error("computeMixStatusNotification: unknown MixStatus " + mix.getMixStatus());
        break;
    }
    return mixStatusNotification;
  }

  private Mix getMix(String mixId) throws MixException {
    return getMix(mixId, null);
  }

  private Mix getMix(String mixId, MixStatus mixStatus) throws MixException {
    Mix mix = currentMixs.get(mixId);
    if (mix == null) {
      throw new MixException("Mix not found");
    }
    if (mixStatus != null && !mixStatus.equals(mix.getMixStatus())) {
      throw new MixException(
          "Operation not permitted for current mix status: expected="
              + mixStatus
              + ", actual="
              + mix.getMixStatus());
    }
    return mix;
  }

  private Mix getMixByInputsHash(String inputsHash, MixStatus mixStatus)
      throws IllegalInputException, MixException {
    List<Mix> mixsFound =
        currentMixs
            .values()
            .parallelStream()
            .filter(mix -> mix.computeInputsHash().equals(inputsHash))
            .collect(Collectors.toList());
    if (mixsFound.size() != 1) {
      throw new IllegalInputException("Mix not found for inputsHash");
    }
    Mix mix = mixsFound.get(0);
    if (mixStatus != null && !mixStatus.equals(mix.getMixStatus())) {
      throw new MixException(
          "Operation not permitted for current mix status: expected="
              + mixStatus
              + ", actual="
              + mix.getMixStatus());
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
      TransactionOutput txOutSpend =
          bech32Util.getTransactionOutput(receiveAddress, mix.getPool().getDenomination(), params);
      outputs.add(txOutSpend);
    }

    //
    // BIP69 sort outputs
    //
    Collections.sort(outputs, new BIP69OutputComparator());
    for (TransactionOutput to : outputs) {
      tx.addOutput(to);
    }

    //
    // create 1 mix tx
    //
    for (ConfirmedInput confirmedInput : mix.getInputs()) {
      RegisteredInput registeredInput = confirmedInput.getRegisteredInput();
      // send from bech32 input
      long spendAmount = registeredInput.getOutPoint().getValue();
      TxOutPoint registeredOutPoint = registeredInput.getOutPoint();
      TransactionOutPoint outPoint =
          new TransactionOutPoint(
              params,
              registeredOutPoint.getIndex(),
              Sha256Hash.wrap(registeredOutPoint.getHash()),
              Coin.valueOf(spendAmount));
      TransactionInput txInput =
          new TransactionInput(params, null, new byte[] {}, outPoint, Coin.valueOf(spendAmount));
      inputs.add(txInput);
    }

    //
    // BIP69 sort inputs
    //
    Collections.sort(inputs, new BIP69InputComparator());
    for (TransactionInput ti : inputs) {
      tx.addInput(ti);
    }
    return tx;
  }

  public void onTimeoutRegisterOutput(Mix mix) {
    if (mix.getReceiveAddresses().isEmpty()) {
      // no output registered at all => no legit user suffered, skip REVEAL_OUTPUT and immediately
      // restart round
      goFail(mix, FailReason.FAIL_REGISTER_OUTPUTS, null);
    } else {
      // we have legit output registered => go REVEAL_OUTPUT to blame the others
      log.info(
          " • REGISTER_OUTPUT time over (mix failed, blaming users who didn't register output...)");
      changeMixStatus(mix.getMixId(), MixStatus.REVEAL_OUTPUT);
    }
  }

  public void onTimeoutRevealOutput(Mix mix) {
    blameForRevealOutputAndResetMix(mix);
  }

  private void blameForRevealOutputAndResetMix(Mix mix) {
    String mixId = mix.getMixId();

    // blame users who didn't register outputs
    Set<ConfirmedInput> confirmedInputsToBlame =
        mix.getInputs()
            .parallelStream()
            .filter(
                input -> !mix.hasRevealedOutputUsername(input.getRegisteredInput().getUsername()))
            .collect(Collectors.toSet());
    for (ConfirmedInput confirmedInputToBlame : confirmedInputsToBlame) {
      blameService.blame(confirmedInputToBlame, BlameReason.NO_REGISTER_OUTPUT, mixId);
    }
    // reset mix
    String outpointKeysToBlameStr = computeOutpointKeysToBlame(confirmedInputsToBlame);
    goFail(mix, FailReason.FAIL_REGISTER_OUTPUTS, outpointKeysToBlameStr);
  }

  private String computeOutpointKeysToBlame(Collection<ConfirmedInput> confirmedInputsToBlame) {
    List<String> outpointKeysToBlame = new ArrayList<>();
    for (ConfirmedInput confirmedInputToBlame : confirmedInputsToBlame) {
      outpointKeysToBlame.add(confirmedInputToBlame.getRegisteredInput().getOutPoint().toKey());
    }
    String outpointKeysToBlameStr = StringUtils.join(outpointKeysToBlame, ";");
    return outpointKeysToBlameStr;
  }

  public void goFail(Mix mix, FailReason failReason, String failInfo) {
    mix.setFailReason(failReason);
    mix.setFailInfo(failInfo);
    changeMixStatus(mix.getMixId(), MixStatus.FAIL);

    exportService.exportMix(mix);
  }

  public void goSuccess(Mix mix) {
    changeMixStatus(mix.getMixId(), MixStatus.SUCCESS);

    exportService.exportMix(mix);
  }

  public synchronized void onClientDisconnect(String username) {
    for (Mix mix : getCurrentMixs()) {
      String mixId = mix.getMixId();

      // remove from confirming inputs
      mix.removeConfirmingInputByUsername(username)
          .ifPresent(
              confirmInput ->
                  log.info(
                      " • ["
                          + mixId
                          + "] unregistered from confirming inputs, username="
                          + username));

      // remove from confirmed inputs
      List<ConfirmedInput> confirmedInputs =
          mix.getInputs()
              .parallelStream()
              .filter(
                  confirmedInput ->
                      confirmedInput.getRegisteredInput().getUsername().equals(username))
              .collect(Collectors.toList());
      if (!confirmedInputs.isEmpty()) {
        boolean mixAlreadyStarted = !MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus());

        confirmedInputs.forEach(
            confirmedInput -> {
              log.info(
                  " • ["
                      + mixId
                      + "] unregistered "
                      + (confirmedInput.getRegisteredInput().isLiquidity()
                          ? "liquidity"
                          : "mustMix")
                      + " from registered inputs, username="
                      + username);
              mix.unregisterInput(confirmedInput);

              if (mixAlreadyStarted) {
                // blame
                blameService.blame(confirmedInput, BlameReason.DISCONNECT, mixId);
              }
            });

        if (mixAlreadyStarted) {
          // restart mix
          String outpointKeysToBlame = computeOutpointKeysToBlame(confirmedInputs);
          goFail(mix, FailReason.DISCONNECT, outpointKeysToBlame);
        }
      }
    }
  }

  private Collection<Mix> getCurrentMixs() {
    return currentMixs.values();
  }

  public void __reset() {
    currentMixs = new HashMap<>();
    mixLimitsService.__reset();
    poolService
        .getPools()
        .forEach(
            pool -> {
              __nextMix(pool);
            });
  }

  public Mix __nextMix(Pool pool) {
    String mixId = Utils.generateUniqueString();
    Mix mix = new Mix(mixId, pool, cryptoService);
    startMix(mix);
    return mix;
  }

  private synchronized void startMix(Mix mix) {
    Pool pool = mix.getPool();
    Mix currentMix = pool.getCurrentMix();
    if (currentMix != null) {
      mixLimitsService.unmanage(mix);
      currentMixs.remove(currentMix.getMixId());
      // TODO disconnect all clients (except liquidities?)
    }

    String mixId = mix.getMixId();
    currentMixs.put(mixId, mix);
    pool.setCurrentMix(mix);

    log.info("[NEW MIX " + mix.getMixId() + "]");
    logMixStatus(mix);

    // add queued mustMixs if any
    poolService.inviteAllToMix(mix, false);
  }

  public MixLimitsService __getMixLimitsService() {
    return mixLimitsService;
  }
}
