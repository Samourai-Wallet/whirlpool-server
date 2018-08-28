package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.validation.constraints.NotEmpty;

@ConfigurationProperties(prefix = "server")
@Configuration
public class WhirlpoolServerConfig {

    private SamouraiFeeConfig samouraiFees;
    private AsymmetricCipherKeyPair keyPair = Utils.generateKeyPair(); // TODO use real keyPair
    private boolean testnet;
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

    public AsymmetricCipherKeyPair getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(AsymmetricCipherKeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public NetworkParameters getNetworkParameters() {
        return testnet ? TestNet3Params.get() : MainNetParams.get();
    }

    public void setTestnet(boolean testnet) {
        this.testnet = testnet;
    }

    public boolean isTestnet() {
        return testnet;
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

        public int getBlames() {
            return blames;
        }

        public void setBlames(int blames) {
            this.blames = blames;
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
        private long minerFeeMin;
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

        public long getMinerFeeMin() {
            return minerFeeMin;
        }

        public void setMinerFeeMin(long minerFeeMin) {
            this.minerFeeMin = minerFeeMin;
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
        @NotEmpty
        private String protocol;
        @NotEmpty
        private String host;
        @NotEmpty
        private int port;
        @NotEmpty
        private String user;
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
        @NotEmpty
        private String xpub;
        private int amount;

        public String getXpub() {
            return xpub;
        }

        public void setXpub(String xpub) {
            this.xpub = xpub;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }
}
