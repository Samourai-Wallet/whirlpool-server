package com.samourai.whirlpool.server.exceptions;


import com.samourai.whirlpool.server.beans.TxOutPoint;

public class UnconfirmedInputException extends Exception {
    private TxOutPoint unconfirmedOutPoint;

    public UnconfirmedInputException(String message, TxOutPoint unconfirmedOutPoint) {
        super(message);
        this.unconfirmedOutPoint = unconfirmedOutPoint;
    }

    public TxOutPoint getUnconfirmedOutPoint() {
        return unconfirmedOutPoint;
    }
}
