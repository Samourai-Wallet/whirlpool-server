package com.samourai.whirlpool.server.beans.rpc;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.core.*;

public class RpcTransaction {
  private String txid;
  private int confirmations;
  private List<RpcIn> ins;
  private List<RpcOut> outs;
  private Transaction tx;

  public RpcTransaction(
      RpcRawTransactionResponse rpcRawTransaction,
      NetworkParameters params,
      Bech32UtilGeneric bech32Util)
      throws Exception {
    // parse tx with bitcoinj
    this.tx = new Transaction(params, Utils.HEX.decode(rpcRawTransaction.getHex()));

    this.txid = tx.getHashAsString();
    this.confirmations = rpcRawTransaction.getConfirmations();
    this.ins = new ArrayList<>();
    this.outs = new ArrayList<>();

    for (TransactionInput in : tx.getInputs()) {
      RpcIn rpcIn = newRpcIn(in);
      ins.add(rpcIn);
    }
    for (TransactionOutput out : tx.getOutputs()) {
      RpcOut rpcOut = newRpcOut(out, txid, params, bech32Util);
      outs.add(rpcOut);
    }
  }

  private RpcOut newRpcOut(
      TransactionOutput out, String hash, NetworkParameters params, Bech32UtilGeneric bech32Util)
      throws Exception {
    long amount = out.getValue().getValue();
    String toAddress =
        com.samourai.whirlpool.server.utils.Utils.getToAddressBech32(out, bech32Util, params);
    RpcOut rpcOut =
        new RpcOut(hash, out.getIndex(), amount, out.getScriptPubKey().getProgram(), toAddress);
    return rpcOut;
  }

  private RpcIn newRpcIn(TransactionInput in) {
    TransactionOutPoint outOrigin = in.getOutpoint();
    RpcIn rpcIn =
        new RpcIn(outOrigin.getHash().toString(), outOrigin.getIndex());
    return rpcIn;
  }

  public String getTxid() {
    return txid;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public List<RpcIn> getIns() {
    return ins;
  }

  public List<RpcOut> getOuts() {
    return outs;
  }

  public Transaction getTx() {
    return tx;
  }
}
