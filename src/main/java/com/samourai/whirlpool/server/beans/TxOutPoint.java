package com.samourai.whirlpool.server.beans;

public class TxOutPoint {

    private String hash;
    private long index;
    private long value;
    private int confirmations;

    public TxOutPoint(String hash, long index, long value, int confirmations) {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.confirmations = confirmations;
    }

    public String getHash() {
        return hash;
    }

    public long getIndex() {
        return index;
    }

    public long getValue() {
        return value;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public boolean equals(Object obj) {
        if (obj instanceof TxOutPoint) {
            TxOutPoint o = (TxOutPoint)obj;
            return o.getHash().equals(getHash()) && o.getIndex() == getIndex() && o.getValue() == getValue();
        }
        return super.equals(obj);
    }

    public String toString() {
        return hash+"-"+index+" ("+value+"sats)";
    }
}
