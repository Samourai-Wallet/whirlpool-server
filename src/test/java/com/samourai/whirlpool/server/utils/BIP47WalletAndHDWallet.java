package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;

public class BIP47WalletAndHDWallet {
  private BIP47Wallet bip47Wallet;
  private HD_Wallet hdWallet;

  public BIP47WalletAndHDWallet(BIP47Wallet bip47Wallet, HD_Wallet hdWallet) {
    this.bip47Wallet = bip47Wallet;
    this.hdWallet = hdWallet;
  }

  public BIP47Wallet getBip47Wallet() {
    return bip47Wallet;
  }

  public HD_Wallet getHdWallet() {
    return hdWallet;
  }
}
