package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.server.services.CryptoService;
import org.aspectj.util.FileUtil;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.KeyChainGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Optional;

@Service
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private CryptoService cryptoService;
    protected Bech32Util bech32Util;

    public TestUtils(CryptoService cryptoService, Bech32Util bech32Util) {
        this.cryptoService = cryptoService;
        this.bech32Util = bech32Util;
    }

    public SegwitAddress createSegwitAddress() throws Exception {
        //BIP47WalletAndHDWallet inputWallets = generateWallet(44);
        //HD_Wallet inputWallet = inputWallets.getHdWallet();
        //ECKey utxoKey = inputWallet.getAccount(0).getReceive().getAddressAt(0).getECKey();

        KeyChainGroup kcg = new KeyChainGroup(cryptoService.getNetworkParameters());
        DeterministicKey utxoKey = kcg.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        SegwitAddress p2shp2wpkh = new SegwitAddress(utxoKey, cryptoService.getNetworkParameters());
        return p2shp2wpkh;
    }

    public BIP47WalletAndHDWallet generateWallet(int purpose, byte[] seed, String passphrase) throws Exception {
        final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";
        InputStream wis = HD_Wallet.class.getResourceAsStream("/en_US.txt");
        if (wis != null) {
            MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);

            // init BIP44 wallet for input
            HD_Wallet inputWallet = new HD_Wallet(purpose, mc, cryptoService.getNetworkParameters(), seed, passphrase, 1);
            // init BIP47 wallet for input
            BIP47Wallet bip47InputWallet = new BIP47Wallet(47, inputWallet, 1);

            wis.close();
            return new BIP47WalletAndHDWallet(bip47InputWallet, inputWallet);
        }
        throw new Exception("wis is null");
    }

    public BIP47WalletAndHDWallet generateWallet(int purpose) throws Exception {
        int nbWords = 12;
        // len == 16 (12 words), len == 24 (18 words), len == 32 (24 words)
        int len = (nbWords / 3) * 4;

        SecureRandom random = new SecureRandom();
        byte seed[] = new byte[len];
        random.nextBytes(seed);

        return generateWallet(purpose, seed, "test");
    }

    private String getMockFileName(String txid) {
        return "./src/test/resources/mocks/" + txid + ".txt";
    }

    public void writeMockRpc(String txid, String rawTxHex) throws Exception {
        String fileName = getMockFileName(txid);
        System.out.println("writing " + fileName + ": " + rawTxHex);
        Files.write(Paths.get(fileName), rawTxHex.getBytes(), StandardOpenOption.CREATE);
    }

    public Optional<String> loadMockRpc(String txid) {
        String mockFile = getMockFileName(txid);
        try {
            log.info("reading mock: " + mockFile);
            String rawTx = FileUtil.readAsString(new File(mockFile));
            return Optional.of(rawTx);
        } catch(Exception e) {
            log.info("mock not found: " + mockFile);
            return Optional.empty();
        }
    }

}
