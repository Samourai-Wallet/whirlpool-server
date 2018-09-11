package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InputPool {
    private Map<String,RegisteredInput> inputsById;

    public InputPool() {
        this.inputsById = new HashMap<>();
    }

    public synchronized void register(RegisteredInput registeredInput) {
        String inputId = Utils.computeInputId(registeredInput.getInput());
        inputsById.put(inputId, registeredInput);
    }

    public synchronized RegisteredInput peekRandom() {
        RegisteredInput registeredInput = null;
        if (!inputsById.isEmpty()) {
            Map.Entry<String,RegisteredInput> entry = Utils.getRandomEntry(inputsById);
            registeredInput = entry.getValue();
            inputsById.remove(entry.getKey());
        }
        return registeredInput;
    }

    public synchronized int removeByUsername(String username) {
        List<RegisteredInput> inputsToRemove = inputsById.values().parallelStream().filter(registeredInput -> registeredInput.getUsername().equals(username)).collect(Collectors.toList());
        inputsToRemove.forEach(registeredInput -> {
            String inputId = Utils.computeInputId(registeredInput.getInput());
            inputsById.remove(inputId);
        });
        return inputsToRemove.size();
    }

    // ------------

    public boolean hasInput(TxOutPoint outPoint) {
        return inputsById.containsKey(Utils.computeInputId(outPoint)) ;
    }

    public boolean hasInput() {
        return !inputsById.isEmpty();
    }

    public int getSize() {
        return inputsById.size();
    }
}
