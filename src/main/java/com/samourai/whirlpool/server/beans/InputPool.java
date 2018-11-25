package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputPool {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Map<String, RegisteredInput> inputsById;

  public InputPool() {
    this.inputsById = new HashMap<>();
  }

  public synchronized void register(RegisteredInput registeredInput) {
    if (!hasInput(registeredInput.getOutPoint())) {
      String username = registeredInput.getUsername();
      if (!findByUsername(username).isPresent()) {
        String inputId = Utils.computeInputId(registeredInput.getOutPoint());
        inputsById.put(inputId, registeredInput);
      } else {
        log.error(
            "WEIRD: not queueing input, another one was already queued for this username:"
                + username); // shouldn't happen...
      }
    } else {
      log.warn("not queueing input, it was already queued: " + registeredInput.getOutPoint());
      if (log.isDebugEnabled()) { // TODO
        inputsById
            .values()
            .forEach(i -> log.debug("input: " + i.getOutPoint() + ", username=" + i.getUsername()));
      }
    }
  }

  public Optional<RegisteredInput> findByUsername(String username) {
    return inputsById
        .values()
        .parallelStream()
        .filter(registeredInput -> registeredInput.getUsername().equals(username))
        .findFirst();
  }

  public synchronized Optional<RegisteredInput> removeRandom() {
    if (!inputsById.isEmpty()) {
      Map.Entry<String, RegisteredInput> entry = Utils.getRandomEntry(inputsById);
      RegisteredInput registeredInput = entry.getValue();
      inputsById.remove(entry.getKey());
      return Optional.of(registeredInput);
    }
    return Optional.empty();
  }

  public synchronized Optional<RegisteredInput> removeByUsername(String username) {
    Optional<RegisteredInput> inputByUsername = findByUsername(username);
    if (inputByUsername.isPresent()) {
      String inputId = Utils.computeInputId(inputByUsername.get().getOutPoint());
      inputsById.remove(inputId);
    }
    return inputByUsername;
  }

  // ------------

  public boolean hasInput(TxOutPoint outPoint) {
    return inputsById.containsKey(Utils.computeInputId(outPoint));
  }

  public boolean hasInputs() {
    return !inputsById.isEmpty();
  }

  public int getSize() {
    return inputsById.size();
  }
}
