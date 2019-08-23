package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "server")
@Configuration
public class WhirlpoolServerConfig {

  private SamouraiFeeConfig samouraiFees;
  private boolean testMode;
  private int port;
  private boolean testnet;
  private boolean mixEnabled;
  private NetworkParameters networkParameters;
  private RpcClientConfig rpcClient;
  private RegisterInputConfig registerInput;
  private RegisterOutputConfig registerOutput;
  private SigningConfig signing;
  private RevealOutputConfig revealOutput;
  private BanConfig ban;
  private ExportConfig export;
  private PoolConfig[] pools;

  public SamouraiFeeConfig getSamouraiFees() {
    return samouraiFees;
  }

  public void setSamouraiFees(SamouraiFeeConfig samouraiFees) {
    this.samouraiFees = samouraiFees;
  }

  public boolean isTestMode() {
    return testMode;
  }

  public void setTestMode(boolean testMode) {
    this.testMode = testMode;
  }

  public boolean isMixEnabled() {
    return mixEnabled;
  }

  public void setMixEnabled(boolean mixEnabled) {
    this.mixEnabled = mixEnabled;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public boolean isTestnet() {
    return testnet;
  }

  public void setTestnet(boolean testnet) {
    this.testnet = testnet;
    NetworkParameters networkParameters = testnet ? TestNet3Params.get() : MainNetParams.get();
    this.networkParameters = networkParameters;
  }

  public NetworkParameters getNetworkParameters() {
    return networkParameters;
  }

  public RpcClientConfig getRpcClient() {
    return rpcClient;
  }

  public void setRpcClient(RpcClientConfig rpcClient) {
    this.rpcClient = rpcClient;
  }

  public RegisterInputConfig getRegisterInput() {
    return registerInput;
  }

  public void setRegisterInput(RegisterInputConfig registerInput) {
    this.registerInput = registerInput;
  }

  public RegisterOutputConfig getRegisterOutput() {
    return registerOutput;
  }

  public void setRegisterOutput(RegisterOutputConfig registerOutput) {
    this.registerOutput = registerOutput;
  }

  public SigningConfig getSigning() {
    return signing;
  }

  public void setSigning(SigningConfig signing) {
    this.signing = signing;
  }

  public RevealOutputConfig getRevealOutput() {
    return revealOutput;
  }

  public void setRevealOutput(RevealOutputConfig revealOutput) {
    this.revealOutput = revealOutput;
  }

  public BanConfig getBan() {
    return ban;
  }

  public void setBan(BanConfig ban) {
    this.ban = ban;
  }

  public ExportConfig getExport() {
    return export;
  }

  public void setExport(ExportConfig export) {
    this.export = export;
  }

  public PoolConfig[] getPools() {
    return pools;
  }

  public void setPools(PoolConfig[] pools) {
    this.pools = pools;
  }

  public static class RegisterInputConfig {
    private int minConfirmationsMustMix;
    private int minConfirmationsLiquidity;
    private int maxInputsSameHash;

    public int getMinConfirmationsMustMix() {
      return minConfirmationsMustMix;
    }

    public void setMinConfirmationsMustMix(int minConfirmationsMustMix) {
      this.minConfirmationsMustMix = minConfirmationsMustMix;
    }

    public int getMinConfirmationsLiquidity() {
      return minConfirmationsLiquidity;
    }

    public void setMinConfirmationsLiquidity(int minConfirmationsLiquidity) {
      this.minConfirmationsLiquidity = minConfirmationsLiquidity;
    }

    public int getMaxInputsSameHash() {
      return maxInputsSameHash;
    }

    public void setMaxInputsSameHash(int maxInputsSameHash) {
      this.maxInputsSameHash = maxInputsSameHash;
    }
  }

  public static class RegisterOutputConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class SigningConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class RevealOutputConfig {
    private int timeout;

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }
  }

  public static class BanConfig {
    private int blames;
    private long period;
    private long expiration;

    public int getBlames() {
      return blames;
    }

    public void setBlames(int blames) {
      this.blames = blames;
    }

    public long getPeriod() {
      return period;
    }

    public void setPeriod(long period) {
      this.period = period;
    }

    public long getExpiration() {
      return expiration;
    }

    public void setExpiration(long expiration) {
      this.expiration = expiration;
    }
  }

  public static class ExportConfig {
    private ExportItemConfig mixs;

    public ExportItemConfig getMixs() {
      return mixs;
    }

    public void setMixs(ExportItemConfig mixs) {
      this.mixs = mixs;
    }
  }

  public static class ExportItemConfig {
    private String filename;
    private String directory;

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    public String getDirectory() {
      return directory;
    }

    public void setDirectory(String directory) {
      this.directory = directory;
    }
  }

  public static class PoolConfig {
    private String id;
    private long denomination;
    private long feeValue;
    private Map<Long, Long> feeAccept;
    private long minerFeeMin;
    private long minerFeeCap;
    private long minerFeeMax;
    private int mustMixMin;
    private int anonymitySetTarget;
    private int anonymitySetMin;
    private int anonymitySetMax;
    private long anonymitySetAdjustTimeout;
    private long liquidityTimeout;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public long getDenomination() {
      return denomination;
    }

    public void setDenomination(long denomination) {
      this.denomination = denomination;
    }

    public long getFeeValue() {
      return feeValue;
    }

    public void setFeeValue(long feeValue) {
      this.feeValue = feeValue;
    }

    public Map<Long, Long> getFeeAccept() {
      return feeAccept;
    }

    public void setFeeAccept(Map<Long, Long> feeAccept) {
      this.feeAccept = feeAccept;
    }

    public long getMinerFeeMin() {
      return minerFeeMin;
    }

    public void setMinerFeeMin(long minerFeeMin) {
      this.minerFeeMin = minerFeeMin;
    }

    public long getMinerFeeCap() {
      return minerFeeCap;
    }

    public void setMinerFeeCap(long minerFeeCap) {
      this.minerFeeCap = minerFeeCap;
    }

    public long getMinerFeeMax() {
      return minerFeeMax;
    }

    public void setMinerFeeMax(long minerFeeMax) {
      this.minerFeeMax = minerFeeMax;
    }

    public int getMustMixMin() {
      return mustMixMin;
    }

    public void setMustMixMin(int mustMixMin) {
      this.mustMixMin = mustMixMin;
    }

    public int getAnonymitySetTarget() {
      return anonymitySetTarget;
    }

    public void setAnonymitySetTarget(int anonymitySetTarget) {
      this.anonymitySetTarget = anonymitySetTarget;
    }

    public int getAnonymitySetMin() {
      return anonymitySetMin;
    }

    public void setAnonymitySetMin(int anonymitySetMin) {
      this.anonymitySetMin = anonymitySetMin;
    }

    public int getAnonymitySetMax() {
      return anonymitySetMax;
    }

    public void setAnonymitySetMax(int anonymitySetMax) {
      this.anonymitySetMax = anonymitySetMax;
    }

    public long getAnonymitySetAdjustTimeout() {
      return anonymitySetAdjustTimeout;
    }

    public void setAnonymitySetAdjustTimeout(long anonymitySetAdjustTimeout) {
      this.anonymitySetAdjustTimeout = anonymitySetAdjustTimeout;
    }

    public long getLiquidityTimeout() {
      return liquidityTimeout;
    }

    public void setLiquidityTimeout(long liquidityTimeout) {
      this.liquidityTimeout = liquidityTimeout;
    }
  }

  public static class RpcClientConfig {
    @NotEmpty private String protocol;
    @NotEmpty private String host;
    @NotEmpty private int port;
    @NotEmpty private String user;
    private String password;
    private boolean mockTxBroadcast;

    public String getProtocol() {
      return protocol;
    }

    public void setProtocol(String protocol) {
      this.protocol = protocol;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public boolean isMockTxBroadcast() {
      return mockTxBroadcast;
    }

    public void setMockTxBroadcast(boolean mockTxBroadcast) {
      this.mockTxBroadcast = mockTxBroadcast;
    }
  }

  public static class SamouraiFeeConfig {
    @NotEmpty private String xpub;
    private SecretWalletConfig secretWallet;
    private Map<String, Short> feePayloadByScode = new HashMap<>(); // -32,768 to 32,767

    public String getXpub() {
      return xpub;
    }

    public void setXpub(String xpub) {
      this.xpub = xpub;
    }

    public SecretWalletConfig getSecretWallet() {
      return secretWallet;
    }

    public void setSecretWallet(SecretWalletConfig secretWallet) {
      this.secretWallet = secretWallet;
    }

    public Map<String, Short> getFeePayloadByScode() {
      return feePayloadByScode;
    }

    public void setFeePayloadByScode(Map<String, Short> feePayloadByScode) {
      this.feePayloadByScode = feePayloadByScode;
    }
  }

  public static class SecretWalletConfig {
    @NotEmpty private String words;
    @NotEmpty private String passphrase;

    public String getWords() {
      return words;
    }

    public void setWords(String words) {
      this.words = words;
    }

    public String getPassphrase() {
      return passphrase;
    }

    public void setPassphrase(String passphrase) {
      this.passphrase = passphrase;
    }
  }

  public Map<String, String> getConfigInfo() {
    Map<String, String> configInfo = new LinkedHashMap<>();
    configInfo.put("port", String.valueOf(getPort()));
    configInfo.put("testMode", String.valueOf(testMode));
    configInfo.put(
        "rpcClient",
        rpcClient.getHost() + ":" + rpcClient.getPort() + "," + networkParameters.getId());
    configInfo.put("protocolVersion", WhirlpoolProtocol.PROTOCOL_VERSION);

    String feesXpub = Utils.obfuscateString(samouraiFees.xpub, 3);
    int nbSeedWords = samouraiFees.getSecretWallet().getWords().split(" ").length;
    configInfo.put(
        "samouraiFees", "xpub=" + feesXpub + ", secretWallet=(" + nbSeedWords + " seed words)");

    configInfo.put(
        "registerInput.maxInputsSameHash", String.valueOf(registerInput.maxInputsSameHash));
    configInfo.put(
        "registerInput.minConfirmations",
        "liquidity="
            + registerInput.minConfirmationsLiquidity
            + ", mustMix="
            + registerInput.minConfirmationsMustMix);

    String timeoutInfo =
        "registerOutput="
            + String.valueOf(registerOutput.timeout)
            + ", signing="
            + String.valueOf(signing.timeout)
            + ", revealOutput="
            + String.valueOf(revealOutput.timeout);
    configInfo.put("timeouts", timeoutInfo);
    configInfo.put("export.mixs", export.mixs.directory + " -> " + export.mixs.filename);
    configInfo.put(
        "ban",
        "blames="
            + String.valueOf(ban.blames)
            + ", period="
            + ban.period
            + ", expiration="
            + ban.expiration);
    for (PoolConfig poolConfig : pools) {
      String poolInfo = "denomination=" + Utils.satoshisToBtc(poolConfig.denomination);
      poolInfo +=
          ", feeValue="
              + Utils.satoshisToBtc(poolConfig.feeValue)
              + ", feeAccept="
              + (poolConfig.feeAccept != null ? poolConfig.feeAccept : null)
              + ", anonymitySet="
              + poolConfig.anonymitySetTarget
              + "["
              + poolConfig.anonymitySetMin
              + "-"
              + poolConfig.anonymitySetMax
              + "]";
      poolInfo +=
          ", minerFee=["
              + poolConfig.minerFeeMin
              + "-"
              + poolConfig.getMinerFeeMax()
              + ", cap="
              + poolConfig.getMinerFeeCap()
              + "]";
      poolInfo += ", liquidityTimeout=" + String.valueOf(poolConfig.liquidityTimeout);
      configInfo.put("pools[" + poolConfig.id + "]", poolInfo);
    }
    for (Map.Entry<String, Short> feePayloadEntry :
        samouraiFees.getFeePayloadByScode().entrySet()) {
      String scode = Utils.obfuscateString(feePayloadEntry.getKey(), 1);
      configInfo.put("scode[" + scode + "]", "enabled");
    }
    return configInfo;
  }
}
