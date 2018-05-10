package com.samourai.whirlpool.server.to;

import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.RoundResult;

public class RoundTO {
    private String roundId;
    private int nbMustMix;
    private int nbLiquidities;
    private RoundResult roundResult;

    public RoundTO(Round round, RoundResult roundResult) {
        this.roundId = round.getRoundId();
        this.nbMustMix = round.getNbInputs() - this.nbLiquidities;
        this.nbLiquidities = (int)round.getInputs().parallelStream().filter(input -> input.isLiquidity()).count();
        this.roundResult = roundResult;
    }

    public String getRoundId() {
        return roundId;
    }

    public int getNbMustMix() {
        return nbMustMix;
    }

    public int getNbLiquidities() {
        return nbLiquidities;
    }

    public RoundResult getRoundResult() {
        return roundResult;
    }
}
