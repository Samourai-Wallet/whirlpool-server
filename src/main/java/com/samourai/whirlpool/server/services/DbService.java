package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.RoundResult;
import com.samourai.whirlpool.server.to.BlameTO;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.to.RoundTO;
import com.samourai.whirlpool.server.utils.Utils;
import org.springframework.stereotype.Service;

import java.util.*;

// TODO use mysql
@Service
public class DbService {
    private Set<String> blindedBordereaux;
    private Set<String> bordereaux;
    private List<BlameTO> blames;
    private List<RoundTO> rounds;

    public DbService() {
        __reset();
    }

    private String computeKeyBlindedBordereau(byte[] blindedBordereau) {
        return Utils.sha512Hex(blindedBordereau);
    }

    public void registerBlindedBordereau(byte[] blindedBordereau) {
        String key = computeKeyBlindedBordereau(blindedBordereau);
        blindedBordereaux.add(key);
    }

    public boolean isBlindedBordereauRegistered(byte[] blindedBordereau) {
        String key = computeKeyBlindedBordereau(blindedBordereau);
        return blindedBordereaux.contains(key);
    }

    public void registerBordereau(String bordereau) {
        bordereaux.add(bordereau);
    }

    public boolean isBordereauRegistered(String bordereau) {
        return bordereaux.contains(bordereau);
    }

    public void saveBlame(RegisteredInput registeredInput, BlameReason blameReason, String roundId) {
        BlameTO blameTO = new BlameTO(registeredInput, blameReason, roundId);
        blames.add(blameTO);
    }

    public void saveRound(Round round, RoundResult roundResult) {
        RoundTO roundTO = new RoundTO(round, roundResult);
        rounds.add(roundTO);
    }

    public void __reset() {
        blindedBordereaux = new HashSet<>();
        bordereaux = new HashSet<>();
        blames = new ArrayList<>();
        rounds = new ArrayList<>();
    }
}
