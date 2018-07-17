package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.persistence.repositories.RoundRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.RoundTO;
import com.samourai.whirlpool.server.utils.Utils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DbService {
    private Set<String> blindedBordereaux;
    private Set<String> bordereaux;
    private List<BlameTO> blames;
    private RoundRepository roundRepository;

    public DbService(RoundRepository roundRepository) {
        this.roundRepository = roundRepository;
        __reset(); // TODO
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

    public void saveRound(Round round) {
        RoundTO roundTO = round.computeRoundTO();
        roundRepository.save(roundTO);
    }

    public Iterable<RoundTO> findRounds() {
        return roundRepository.findAll(new Sort(Sort.Direction.DESC, "created"));
    }

    public void __reset() {
        blindedBordereaux = new HashSet<>();
        bordereaux = new HashSet<>();
        blames = new ArrayList<>();
    }
}
