package com.samourai.whirlpool.server.beans;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputPool {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Map<String, RegisteredInput> inputsById;

  public InputPool() {
    this.inputsById = new ConcurrentHashMap<>();
  }

  public synchronized void register(RegisteredInput registeredInput) throws NotifiableException {
    if (!hasInput(registeredInput.getOutPoint())) {
      String username = registeredInput.getUsername();
      if (!findByUsername(username).isPresent()) {
        String inputId = Utils.computeInputId(registeredInput.getOutPoint());
        inputsById.put(inputId, registeredInput);
      } else {
        throw new NotifiableException(
            "Username already registered another input: " + username); // shouldn't happen...
      }
    } else {
      throw new NotifiableException("Input already registered: " + registeredInput.getOutPoint());
    }
  }

  public Optional<RegisteredInput> findByUsername(String username) {
    return inputsById
        .values()
        .parallelStream()
        .filter(registeredInput -> registeredInput.getUsername().equals(username))
        .findFirst();
  }

  public synchronized Optional<RegisteredInput> removeRandom(
      Predicate<Map.Entry<String, RegisteredInput>> filter) {
    List<String> eligibleInputIds =
        inputsById
            .entrySet()
            .parallelStream()
            .filter(filter)
            .map(entry -> entry.getKey())
            .collect(Collectors.toList());
    return removeRandom(eligibleInputIds);
  }

  private synchronized Optional<RegisteredInput> removeRandom(List<String> eligibleInputIds) {
    if (!eligibleInputIds.isEmpty()) {
      String randomInputId = Utils.getRandomEntry(eligibleInputIds);
      RegisteredInput registeredInput = inputsById.remove(randomInputId);
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

  public void resetLastUserHash() {
    inputsById.values().forEach(registedInput -> registedInput.setLastUserHash(null));
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
