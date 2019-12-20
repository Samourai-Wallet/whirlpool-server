package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import java.util.Collection;

public class MemoryWalletPersistHandler implements WhirlpoolWalletPersistHandler {
  @Override
  public IIndexHandler getIndexHandler(String key) {
    return new MemoryIndexHandler();
  }

  @Override
  public IIndexHandler getIndexHandler(String key, int defaultValue) {
    return new MemoryIndexHandler(defaultValue);
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @Override
  public void setInitialized(boolean value) {}

  @Override
  public void loadUtxoConfigs(WhirlpoolWallet whirlpoolWallet) {}

  @Override
  public WhirlpoolUtxoConfig getUtxoConfig(String utxoHash, int utxoIndex) {
    return null;
  }

  @Override
  public WhirlpoolUtxoConfig getUtxoConfig(String utxoHash) {
    return null;
  }

  @Override
  public void addUtxoConfig(String utxoHash, int utxoIndex, WhirlpoolUtxoConfig value) {}

  @Override
  public void addUtxoConfig(String utxoHash, WhirlpoolUtxoConfig value) {}

  @Override
  public void cleanUtxoConfig(Collection<WhirlpoolUtxo> knownUtxos) {}

  @Override
  public void save() throws Exception {}
}
