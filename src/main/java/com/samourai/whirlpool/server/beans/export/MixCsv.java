package com.samourai.whirlpool.server.beans.export;

import com.opencsv.bean.CsvBindByPosition;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.persistence.to.MixTO;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.sql.Timestamp;

public class MixCsv {

    public static final String[] HEADERS = new String[]{"id", "created", "updated", "poolId", "mixId", "denomination", "anonymitySet", "nbMustMix", "nbLiquidities", "mixStatus", "failReason", "txid", "rawTx"};

    @CsvBindByPosition(position = 0)
    private Long id;

    @CsvBindByPosition(position = 1)
    private Timestamp created;

    @CsvBindByPosition(position = 2)
    private Timestamp updated;

    //

    @CsvBindByPosition(position = 3)
    private String poolId;

    @CsvBindByPosition(position = 4)
    private String mixId;

    @CsvBindByPosition(position = 5)
    private long denomination;

    @CsvBindByPosition(position = 6)
    private int anonymitySet;

    @CsvBindByPosition(position = 7)
    private int nbMustMix;

    @CsvBindByPosition(position = 8)
    private int nbLiquidities;

    @CsvBindByPosition(position = 9)
    @Enumerated(EnumType.STRING)
    private MixStatus mixStatus;

    @CsvBindByPosition(position = 10)
    @Enumerated(EnumType.STRING)
    private FailReason failReason;

    //

    @CsvBindByPosition(position = 11)
    private String txid;

    @CsvBindByPosition(position = 12)
    private String rawTx;

    public MixCsv(MixTO to) {
        this.id = to.getId();
        this.created = to.getCreated();
        this.updated = to.getUpdated();

        this.poolId = to.getPoolId();
        this.mixId = to.getMixId();
        this.denomination = to.getDenomination();
        this.anonymitySet = to.getAnonymitySet();
        this.nbMustMix = to.getNbMustMix();
        this.nbLiquidities = to.getNbLiquidities();
        this.mixStatus = to.getMixStatus();
        this.failReason = to.getFailReason();

        if (to.getMixLog() != null) {
            this.txid = to.getMixLog().getTxid();
            this.rawTx = to.getMixLog().getRawTx();
        }
    }

    public Long getId() {
        return id;
    }

    public Timestamp getCreated() {
        return created;
    }

    public Timestamp getUpdated() {
        return updated;
    }

    public String getPoolId() {
        return poolId;
    }

    public String getMixId() {
        return mixId;
    }

    public long getDenomination() {
        return denomination;
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

    public String getTxid() {
        return txid;
    }

    public String getRawTx() {
        return rawTx;
    }
}
