package com.samourai.whirlpool.server.services;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.RSAPublicKeySpec;

@Service
public class CryptoService {
    private AsymmetricCipherKeyPair keyPair;
    private PublicKey publicKey;
    private NetworkParameters networkParameters;

    public CryptoService(AsymmetricCipherKeyPair keyPair, NetworkParameters networkParameters) throws Exception {
        this.keyPair = keyPair;
        this.publicKey = computePublicKey((RSAKeyParameters)keyPair.getPublic());

        this.networkParameters = networkParameters;

        // initialize bitcoinj context
        new Context(networkParameters);
    }

    private PublicKey computePublicKey(RSAKeyParameters pubKey) throws Exception {
        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(pubKey.getModulus(), pubKey.getExponent());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(rsaPublicKeySpec);
    }

    public boolean verifyMessageSignature(byte[] pubkeyHex, String message, String signatureBase64) {
        ECKey ecKeyFromPubkey;
        try {
            ecKeyFromPubkey = ECKey.fromPublicOnly(pubkeyHex);
        } catch (Exception e) {
            return false;
        }
        try {
            ecKeyFromPubkey.verifyMessage(message, signatureBase64);
        } catch (SignatureException e) {
            return false;
        }
        return true;
    }

    public byte[] signBlindedOutput(byte[] blindedOutput) {
        // sign blinded output
        RSAEngine engine = new RSAEngine();
        engine.init(false, this.keyPair.getPrivate());
        return engine.processBlock(blindedOutput, 0, blindedOutput.length);
    }

    public boolean verifyUnblindedSignedBordereau(String revealedBordereau, byte[] unblindedSignedBordereau) {
        PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), 32);
        signer.init(false, this.keyPair.getPublic());

        byte[] data = revealedBordereau.getBytes();
        signer.update(data, 0, data.length);
        return signer.verifySignature(unblindedSignedBordereau);
    }

    public PublicKey getPublicKey() throws Exception {
        return publicKey;
    }

    public NetworkParameters getNetworkParameters() {
        return networkParameters;
    }
}
