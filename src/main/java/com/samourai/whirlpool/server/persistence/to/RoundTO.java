package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.FailReason;

import javax.persistence.*;

@Entity(name = "round")
public class RoundTO extends EntityTO {
    @Column(unique = true)
    private String roundId;

    private int anonymitySet;
    private int nbMustMix;
    private int nbLiquidities;

    @Enumerated(EnumType.STRING)
    private RoundStatus roundStatus;

    @Enumerated(EnumType.STRING)
    private FailReason failReason;

    @OneToOne(fetch = FetchType.LAZY,
            cascade =  CascadeType.ALL,
            mappedBy = "round")
    private RoundLogTO roundLog;

    public RoundTO() {
    }

    public void update(Round round) {
        this.roundId = round.getRoundId();
        this.anonymitySet = round.getNbInputs();
        this.nbMustMix = round.getNbInputsMustMix();
        this.nbLiquidities = round.getNbInputsLiquidities();
        this.roundStatus = round.getRoundStatus();
        this.failReason = round.getFailReason();

        if (this.roundLog == null) {
            this.roundLog = new RoundLogTO();
        }
        this.roundLog.update(round, this);
    }

    public String getRoundId() {
        return roundId;
    }

    public int getAnonymitySet() {
        return anonymitySet;
    }

    public int getNbMustMix() {
        return nbMustMix;
    }

    public int getNbLiquidities() {
        return nbLiquidities;
    }

    public RoundStatus getRoundStatus() {
        return roundStatus;
    }

    public FailReason getFailReason() {
        return failReason;
    }

    public RoundLogTO getRoundLog() {
        return roundLog;
    }
}
