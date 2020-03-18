package com.samourai.whirlpool.server.services;

import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.*;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.BroadcastException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.utils.MessageListener;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
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
      TxUtil txUtil,
      WebSocketSessionService webSocketSessionService) {
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

    this.__reset();

    // listen websocket onDisconnect
    webSocketSessionService.addOnDisconnectListener(
        new MessageListener<String>() {
          @Override
          public void onMessage(String username) {
            onClientDisconnect(username);
          }
        });
  }

  /** Last input validations when adding it to a mix (not when queueing it) */
  private synchronized void validateOnConfirmInput(
      Mix mix, RegisteredInput registeredInput, String userHashOrNull)
      throws QueueInputException, IllegalInputException {
    Pool pool = mix.getPool();

    // check mix didn't start yet
    if (!MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus())) {
      // confirming input too late => enqueue in pool
      String poolId = mix.getPool().getPoolId();
      throw new QueueInputException("Mix already started", registeredInput, poolId);
    }
    // verify mix not full
    if (mix.isFull()) {
      throw new QueueInputException("Current mix is full", registeredInput, pool.getPoolId());
    }

    if (registeredInput.isLiquidity()) {
      // liquidity: verify liquidities open
      if (!mix.isRegisterLiquiditiesOpen()) {
        throw new IllegalInputException(
            "Current mix not opened to liquidities yet"); // should not happen
      }
      // verify minMustMix
      int mustMixSlotsAvailable = pool.getAnonymitySet() - (mix.getNbInputsLiquidities() + 1);
      if (mustMixSlotsAvailable < pool.getMinMustMix()) {
        throw new QueueInputException(
            "Current mix is full for liquidity", registeredInput, pool.getPoolId());
      }
    } else {
      // mustMix: verify minLiquidity
      int liquiditySlotsAvailable = pool.getAnonymitySet() - (mix.getNbInputsMustMix() + 1);
      if (liquiditySlotsAvailable < pool.getMinLiquidity()) {
        throw new QueueInputException(
            "Current mix is full for mustMix", registeredInput, pool.getPoolId());
      }
    }

    // verify unique userHash
    int maxInputsSameUserHash = whirlpoolServerConfig.getRegisterInput().getMaxInputsSameUserHash();
    if (userHashOrNull != null) {
      long countInputSameUserHash =
          mix.getInputs()
              .parallelStream()
              .filter(input -> input.getUserHash().equals(userHashOrNull))
              .count();
      if ((countInputSameUserHash + 1) > maxInputsSameUserHash) {
        if (log.isTraceEnabled()) {
          log.trace(
              "already "
                  + countInputSameUserHash
                  + " inputs with same userHash in "
                  + mix.getMixId()
                  + ": "
                  + userHashOrNull);
        }
        throw new QueueInputException(
            "Your wallet already registered for this mix", registeredInput, pool.getPoolId());
      }
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
      if (log.isTraceEnabled()) {
        log.trace("already " + countInputsSameHash + " inputs with same hash: " + inputHash);
      }
      throw new QueueInputException(
          "Current mix is full for inputs with same hash", registeredInput, pool.getPoolId());
    }

    // verify no input address reuse with other inputs
    String inputAddress = registeredInput.getOutPoint().getToAddress();
    if (mix.getInputByAddress(inputAddress).isPresent()) {
      throw new QueueInputException(
          "Current mix is full for inputs with same address", registeredInput, pool.getPoolId());
    }

    // verify input not already confirmed
    ConfirmedInput alreadyConfirmedInput = mix.findInput(registeredInput.getOutPoint());
    if (alreadyConfirmedInput != null) {
      // input already confirmed => reject duplicate client
      throw new IllegalInputException("Input already confirmed");
    }
  }

  public synchronized void validateForConfirmInput(Mix mix, RegisteredInput registeredInput)
      throws QueueInputException, IllegalInputException {
    String userHash = registeredInput.getLastUserHash(); // may be null
    validateOnConfirmInput(mix, registeredInput, userHash);
  }

  private synchronized void validateOnConfirmInput(Mix mix, ConfirmedInput confirmedInput)
      throws QueueInputException, IllegalInputException {
    RegisteredInput registeredInput = confirmedInput.getRegisteredInput();
    validateOnConfirmInput(mix, registeredInput, confirmedInput.getUserHash());
  }

  public synchronized byte[] confirmInput(
      String mixId, String username, byte[] blindedBordereau, String userHash)
      throws IllegalInputException, MixException, QueueInputException {
    Mix mix = getMix(mixId);

    // find confirming input
    RegisteredInput registeredInput =
        mix.removeConfirmingInputByUsername(username)
            .orElseThrow(
                () ->
                    new IllegalInputException("Confirming input not found: username=" + username));

    // set lastUserHash
    registeredInput.setLastUserHash(userHash);

    ConfirmedInput confirmedInput = new ConfirmedInput(registeredInput, blindedBordereau, userHash);

    // last input validations
    validateOnConfirmInput(mix, confirmedInput);

    // sign bordereau to reply
    byte[] signedBordereau = cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // add to mix inputs
    mix.registerInput(confirmedInput);
    log.info(
        "["
            + mixId
            + "] registered "
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
    if (!whirlpoolServerConfig.isMixEnabled()) {
      // mix disabled by server configuration
      return;
    }

    if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus()) && isRegisterInputReady(mix)) {

      // ready to go REGISTER_OUTPUT
      if (allowGracePeriod
          && GRACE_TIME_CONFIRMING_INPUTS > 0
          && mix.hasPendingConfirmingInputs()
          && mix.getNbInputs() < mix.getPool().getAnonymitySet()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Ready to go REGISTER_OUTPUT - waiting for last pending confirmations: pendingConfirmingInputs="
                  + mix.getNbConfirmingInputs()
                  + ", nbInputs="
                  + mix.getNbInputs()
                  + ", anonymitySet="
                  + mix.getPool().getAnonymitySet());
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

  public boolean isRegisterInputReady(Mix mix) {
    if (mix.getNbInputs() == 0) {
      return false;
    }
    if (!mix.hasMinMustMixAndFeeReached()) {
      return false;
    }
    if (!mix.hasMinLiquidityMixReached()) {
      return false;
    }
    if (mix.getNbInputs() < mix.getPool().getAnonymitySet()) {
      return false;
    }
    // check for inputs spent in the meantime
    if (!revalidateInputsForSpent(mix)) {
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

    // verify no output address reuse with inputs
    if (mix.getInputByAddress(receiveAddress).isPresent()) {
      throw new IllegalInputException("receiveAddress already registered as input");
    }

    log.info("[" + mix.getMixId() + "] registered output: " + receiveAddress);
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
        "["
            + mix.getMixId()
            + "] "
            + mix.getNbInputsMustMix()
            + "/"
            + mix.getPool().getMinMustMix()
            + " mustMix, "
            + mix.getNbInputsLiquidities()
            + "/"
            + mix.getPool().getMinLiquidity()
            + " liquidity, "
            + mix.getNbInputs()
            + "/"
            + mix.getPool().getAnonymitySet()
            + " anonymitySet, "
            + mix.computeMinerFeeAccumulated()
            + "/"
            + mix.getPool().getMinerFeeMix()
            + "sat (pool: "
            + liquiditiesQueued
            + " liquidities + "
            + mustMixQueued
            + " mustMixs)");
  }

  protected synchronized boolean isRegisterOutputReady(Mix mix) {
    if (!isRegisterInputReady(mix)) {
      return false;
    }

    return (mix.getReceiveAddresses().size() == mix.getNbInputs());
  }

  protected boolean revalidateInputsForSpent(Mix mix) {
    boolean mixAlreadyStarted = mix.isAlreadyStarted();
    List<ConfirmedInput> spentInputs = new ArrayList<>();

    // check for spent inputs
    for (ConfirmedInput confirmedInput : mix.getInputs()) {
      TxOutPoint outPoint = confirmedInput.getRegisteredInput().getOutPoint();
      if (!rpcClientService.isTxOutUnspent(outPoint.getHash(), outPoint.getIndex())) {
        // input was spent in meantime
        spentInputs.add(confirmedInput);
      }
    }

    if (spentInputs.isEmpty()) {
      // no input spent => valid
      return true;
    }

    // there were input spent
    for (ConfirmedInput spentInput : spentInputs) {
      log.warn(
          "Found " + spentInputs.size() + " confirmed input(s) spent in meantime!", spentInput);

      // remove spent input
      mix.unregisterInput(spentInput);

      if (mixAlreadyStarted) {
        // blame
        blameService.blame(spentInput, BlameReason.SPENT, mix.getMixId());
      }
    }
    if (mixAlreadyStarted) {
      // restart mix
      String outpointKeysToBlame = computeOutpointKeysToBlame(spentInputs);
      goFail(mix, FailReason.SPENT, outpointKeysToBlame);
    }
    return false; // not valid
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
    log.info("[" + mixId + "] " + username + " revealed output");

    if (isRevealOutputReady(mix)) {
      blameForRevealOutputAndResetMix(mix);
    }
  }

  protected synchronized boolean isRevealOutputReady(Mix mix) {
    // don't wait for the last one who didn't sign
    return (mix.getNbRevealedOutputs() == mix.getNbInputs() - 1);
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
    log.info("[" + mixId + "]  " + username + " registered signature");

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

  public synchronized void changeMixStatus(String mixId, MixStatus mixStatus) {
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

      boolean mixOver = (mixStatus == MixStatus.SUCCESS || mixStatus == MixStatus.FAIL);
      // save mix before notifying users
      if (mixOver) {
        saveMixResult(mix, mixStatus);
      }

      mixLimitsService.onMixStatusChange(mix);

      // notify users (ConfirmInputResponse was already sent when user joined mix)
      if (mixStatus != MixStatus.CONFIRM_INPUT) {
        MixStatusNotification mixStatusNotification = computeMixStatusNotification(mixId);
        sendToMixingUsers(mix, mixStatusNotification);
      }

      // start next mix
      if (mixOver) {
        onMixOver(mix);
      }
    } catch (MixException e) {
      log.error("Unexpected mix error", e);
      if (mix != null) {
        onMixOver(mix);
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
      log.warn("REGISTER_OUTPUT rejected: no current mix for inputsHash=" + inputsHash);
      // reject with generic message because we may not be responsible of this error (ie: another
      // client disconnected during the mix)
      throw new MixException("Mix failed");
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
          "["
              + mix.getMixId()
              + "] REGISTER_OUTPUT time over (mix failed, blaming users who didn't register output...)");
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
      blameService.blame(confirmedInputToBlame, BlameReason.REGISTER_OUTPUT, mixId);
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
  }

  public void goSuccess(Mix mix) {
    changeMixStatus(mix.getMixId(), MixStatus.SUCCESS);
  }

  private void onClientDisconnect(String username) {
    for (Mix mix : getCurrentMixs()) {
      Collection<ConfirmedInput> confirmedInputsToBlame = mix.onDisconnect(username);
      if (!confirmedInputsToBlame.isEmpty()) {
        confirmedInputsToBlame.forEach(
            confirmedInput -> {
              // blame
              blameService.blame(confirmedInput, BlameReason.DISCONNECT, mix.getMixId());
            });

        // restart mix
        String outpointKeysToBlame = computeOutpointKeysToBlame(confirmedInputsToBlame);
        goFail(mix, FailReason.DISCONNECT, outpointKeysToBlame);
      }
    }
  }

  private Collection<Mix> getCurrentMixs() {
    return currentMixs.values();
  }

  public void __reset() {
    currentMixs = new ConcurrentHashMap<>();
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

  private void saveMixResult(Mix mix, MixStatus mixStatus) {
    // save in database
    try {
      dbService.saveMix(mix);
    } catch (Exception e) {
      log.error("", e);
    }

    // export to CSV
    try {
      exportService.exportMix(mix);
    } catch (Exception e) {
      log.error("", e);
    }

    if (mixStatus == MixStatus.SUCCESS) {
      // save mix txid
      try {
        dbService.saveMixTxid(mix.getTx().getHashAsString(), mix.getPool().getDenomination());
      } catch (Exception e) {
        log.error("", e);
      }
    }
  }

  private void onMixOver(Mix mix) {
    // unmanage
    try {
      mixLimitsService.unmanage(mix);
    } catch (Exception e) {
      log.error("", e);
    }

    // reset lastUserHash
    poolService.resetLastUserHash(mix);

    // start new mix
    __nextMix(mix.getPool());
  }

  private synchronized void startMix(Mix mix) {
    Pool pool = mix.getPool();
    Mix currentMix = pool.getCurrentMix();
    if (currentMix != null) {
      currentMixs.remove(currentMix.getMixId());
      // TODO disconnect all clients (except liquidities?)
    }

    String mixId = mix.getMixId();
    currentMixs.put(mixId, mix);
    pool.setCurrentMix(mix);

    log.info("[" + pool.getPoolId() + "][NEW MIX " + mix.getMixId() + "]");
    logMixStatus(mix);

    // add queued mustMixs if any
    poolService.inviteToMixAll(mix, false, this);
  }

  public Predicate<Map.Entry<String, RegisteredInput>> computeFilterInputMixable(Mix mix) {
    return entry -> {
      RegisteredInput registeredInput = entry.getValue();
      try {
        validateForConfirmInput(mix, registeredInput);
        return true; // mixable
      } catch (Exception e) {
        return false; // not mixable
      }
    };
  }

  public MixLimitsService __getMixLimitsService() {
    return mixLimitsService;
  }
}
