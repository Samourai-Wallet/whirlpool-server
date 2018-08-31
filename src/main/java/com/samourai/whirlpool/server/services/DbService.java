package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.persistence.repositories.MixRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
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
    private Set<String> receiveAddresses;
    private List<BlameTO> blames;
    private MixRepository mixRepository;

    public DbService(MixRepository mixRepository) {
        this.mixRepository = mixRepository;
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

    public void registerReceiveAddress(String receiveAddress) {
        receiveAddresses.add(receiveAddress);
    }

    public boolean isReceiveAddressRegistered(String receiveAddress) {
        return receiveAddresses.contains(receiveAddress);
    }

    public void saveBlame(RegisteredInput registeredInput, BlameReason blameReason, String mixId) {
        BlameTO blameTO = new BlameTO(registeredInput, blameReason, mixId);
        blames.add(blameTO);
    }

    public void saveMix(Mix mix) {
        MixTO mixTO = mix.computeMixTO();
        mixRepository.save(mixTO);
    }

    public Iterable<MixTO> findMixs() {
        return mixRepository.findAll(new Sort(Sort.Direction.DESC, "created"));
    }

    public void __reset() {
        blindedBordereaux = new HashSet<>();
        receiveAddresses = new HashSet<>();
        blames = new ArrayList<>();
    }
}
