package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.utils.Utils;

import javax.persistence.*;

@Entity(name = "roundLog")
public class RoundLogTO extends EntityTO {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    private RoundTO round;

    private String txid;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String rawTx;

    public RoundLogTO() {
    }

    public void update(Round round, RoundTO roundTO) {
        this.round = roundTO;

        if (round.getTx() != null) {
            this.txid = round.getTx().getHashAsString();
            this.rawTx = Utils.getRawTx(round.getTx());
        }
    }

    public String getRawTx() {
        return rawTx;
    }

    public String getTxid() {
        return txid;
    }
}
