package com.samourai.whirlpool.server.config;

import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
    private BanConfig ban;
    private RoundConfig round;


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

    public BanConfig getBan() {
        return ban;
    }

    public void setBan(BanConfig ban) {
        this.ban = ban;
    }

    public RoundConfig getRound() {
        return round;
    }

    public void setRound(RoundConfig round) {
        this.round = round;
    }

    public static class RegisterInputConfig {
        private int minConfirmations;

        public int getMinConfirmations() {
            return minConfirmations;
        }

        public void setMinConfirmations(int minConfirmations) {
            this.minConfirmations = minConfirmations;
        }
    }

    public static class RegisterOutputConfig {
        private int timeout;
        private String url;

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
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

    public static class BanConfig {
        private int blames;

        public int getBlames() {
            return blames;
        }

        public void setBlames(int blames) {
            this.blames = blames;
        }
    }

    public static class RoundConfig {
        private long denomination;
        private long minerFee;
        private int mustMixMin;
        private int anonymitySetTarget;
        private int anonymitySetMin;
        private int anonymitySetMax;
        private long anonymitySetAdjustTimeout;
        private long liquidityTimeout;

        public long getDenomination() {
            return denomination;
        }

        public void setDenomination(long denomination) {
            this.denomination = denomination;
        }

        public long getMinerFee() {
            return minerFee;
        }

        public void setMinerFee(long minerFee) {
            this.minerFee = minerFee;
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
        @NotBlank
        private String protocol;
        @NotBlank
        private String host;
        @NotBlank
        private int port;
        @NotBlank
        private String user;
        private String password;

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
    }

    public static class SamouraiFeeConfig {
        @NotBlank
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
