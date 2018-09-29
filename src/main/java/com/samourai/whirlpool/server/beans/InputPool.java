package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InputPool {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String,RegisteredInput> inputsById;

    public InputPool() {
        this.inputsById = new HashMap<>();
    }

    public synchronized void register(RegisteredInput registeredInput) {
        if (!hasInput(registeredInput.getInput())) {
            String username = registeredInput.getUsername();
            if (!findByUsername(username).isPresent()) {
                String inputId = Utils.computeInputId(registeredInput.getInput());
                inputsById.put(inputId, registeredInput);
            } else {
                log.error("WEIRD: not queueing input, another one was already queued for this username:" + username); // shouldn't happen...
            }
        } else {
            log.info("not queueing input, it was already queued");
        }
    }

    public Optional<RegisteredInput> findByUsername(String username) {
        return inputsById.values().parallelStream().filter(registeredInput -> registeredInput.getUsername().equals(username)).findFirst();
    }

    public synchronized Optional<RegisteredInput> peekRandom() {
        if (!inputsById.isEmpty()) {
            Map.Entry<String,RegisteredInput> entry = Utils.getRandomEntry(inputsById);
            RegisteredInput registeredInput = entry.getValue();
            inputsById.remove(entry.getKey());
            return Optional.of(registeredInput);
        }
        return Optional.empty();
    }

    public synchronized Optional<RegisteredInput> removeByUsername(String username) {
        Optional<RegisteredInput> inputByUsername = findByUsername(username);
        if (inputByUsername.isPresent()) {
            String inputId = Utils.computeInputId(inputByUsername.get().getInput());
            inputsById.remove(inputId);
        }
        return inputByUsername;
    }

    // ------------

    public boolean hasInput(TxOutPoint outPoint) {
        return inputsById.containsKey(Utils.computeInputId(outPoint)) ;
    }

    public boolean hasInputs() {
        return !inputsById.isEmpty();
    }

    public int getSize() {
        return inputsById.size();
    }
}
