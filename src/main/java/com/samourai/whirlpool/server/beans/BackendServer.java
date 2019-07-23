package com.samourai.whirlpool.server.beans;

public enum BackendServer {
  MAINNET(
      "https://api.samouraiwallet.com",
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion"),
  TESTNET(
      "https://api.samouraiwallet.com/test",
      "http://d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/test");

  private String backendUrlClear;
  private String backendUrlOnion;

  BackendServer(String backendUrlClear, String backendUrlOnion) {
    this.backendUrlClear = backendUrlClear;
    this.backendUrlOnion = backendUrlOnion;
  }

  public String getBackendUrl(boolean onion) {
    return onion ? backendUrlOnion : backendUrlClear;
  }

  public String getBackendUrlClear() {
    return backendUrlClear;
  }

  public String getBackendUrlOnion() {
    return backendUrlOnion;
  }

  public static BackendServer get(boolean isTestnet) {
    return isTestnet ? BackendServer.TESTNET : BackendServer.MAINNET;
  }
}
