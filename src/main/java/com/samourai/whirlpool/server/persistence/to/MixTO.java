package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.FailReason;

import javax.persistence.*;

@Entity(name = "mix")
public class MixTO extends EntityTO {
    @Column(unique = true)
    private String mixId;

    private int anonymitySet;
    private int nbMustMix;
    private int nbLiquidities;

    @Enumerated(EnumType.STRING)
    private MixStatus mixStatus;

    @Enumerated(EnumType.STRING)
    private FailReason failReason;

    @OneToOne(fetch = FetchType.LAZY,
            cascade =  CascadeType.ALL,
            mappedBy = "mix")
    private MixLogTO mixLog;

    public MixTO() {
    }

    public void update(Mix mix) {
        this.mixId = mix.getMixId();
        this.anonymitySet = mix.getNbInputs();
        this.nbMustMix = mix.getNbInputsMustMix();
        this.nbLiquidities = mix.getNbInputsLiquidities();
        this.mixStatus = mix.getMixStatus();
        this.failReason = mix.getFailReason();

        if (this.mixLog == null) {
            this.mixLog = new MixLogTO();
        }
        this.mixLog.update(mix, this);
    }

    public String getMixId() {
        return mixId;
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

    public MixStatus getMixStatus() {
        return mixStatus;
    }

    public FailReason getFailReason() {
        return failReason;
    }

    public MixLogTO getMixLog() {
        return mixLog;
    }
}
