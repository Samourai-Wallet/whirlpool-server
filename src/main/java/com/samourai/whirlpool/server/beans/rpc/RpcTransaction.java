package com.samourai.whirlpool.server.beans.rpc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

public class RpcTransaction {
  private int confirmations;

  @JsonIgnore private Transaction tx;

  public RpcTransaction(RpcRawTransactionResponse rpcRawTransaction, NetworkParameters params) {
    // parse tx with bitcoinj
    this.tx = new Transaction(params, Utils.HEX.decode(rpcRawTransaction.getHex()));
    this.confirmations = rpcRawTransaction.getConfirmations();
  }

  public int getConfirmations() {
    return confirmations;
  }

  public Transaction getTx() {
    return tx;
  }
}
