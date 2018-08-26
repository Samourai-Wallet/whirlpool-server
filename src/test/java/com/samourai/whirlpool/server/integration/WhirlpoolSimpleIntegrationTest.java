package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.utils.BIP47WalletAndHDWallet;
import com.samourai.whirlpool.server.utils.MultiClientManager;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.util.Date;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class WhirlpoolSimpleIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void whirlpool_manual_bip47() throws Exception {
        NetworkParameters params = cryptoService.getNetworkParameters();

        // init BIP47 wallet for input
        BIP47WalletAndHDWallet inputWallets = testUtils.generateWallet(44);
        BIP47Wallet bip47InputWallet = inputWallets.getBip47Wallet();
        HD_Wallet inputWallet = inputWallets.getHdWallet();

        // init BIP47 wallet for output
        BIP47Wallet bip47OutputWallet = testUtils.generateWallet(44).getBip47Wallet();

        PaymentCode inputPCode = new PaymentCode(bip47InputWallet.getAccount(0).getPaymentCode());
        // sender signs message with payment code notification address privkey
        ECKey inputNotifAddressECKey =  bip47InputWallet.getAccount(0).getNotificationAddress().getECKey();
        String inputPCodeMessage = inputPCode.toString() + ":" + new Date().toString();
        String inputPCodeSig = messageSignUtil.signMessage(inputNotifAddressECKey, inputPCodeMessage);
        // server validates sender's message with payment code notification address pubkey
        Assert.assertTrue(messageSignUtil.verifySignedMessage(inputPCode.notificationAddress(params).getAddressString(), inputPCodeMessage, inputPCodeSig));

        PaymentCode outputPCode = new PaymentCode(bip47OutputWallet.getAccount(0).getPaymentCode());
        // receiver signs message with payment code notification address
        ECKey outputNotifAddressECKey =  bip47OutputWallet.getAccount(0).getNotificationAddress().getECKey();
        String outputPCodeMessage = outputPCode.toString() + ":" + new Date().toString();
        String outputPCodeSig = messageSignUtil.signMessage(outputNotifAddressECKey, outputPCodeMessage);
        // server validates receiver's message with payment code notification address pubkey
        Assert.assertTrue(messageSignUtil.verifySignedMessage(outputPCode.notificationAddress(params).getAddressString(), outputPCodeMessage, outputPCodeSig));

        ECKey utxoKey = inputWallet.getAccount(0).getReceive().getAddressAt(0).getECKey();
        SegwitAddress inputP2SH_P2WPKH = new SegwitAddress(utxoKey, cryptoService.getNetworkParameters());

        Mix mix = __getCurrentMix();

        // mock TransactionOutPoint
        long inputBalance = mix.computeInputBalanceMin(false);
        TxOutPoint utxo = rpcClientService.createAndMockTxOutPoint(inputP2SH_P2WPKH, inputBalance);

        MultiClientManager multiClientManager = multiClientManager(1, mix);
        multiClientManager.connectWithMock(0, 1, inputP2SH_P2WPKH, bip47OutputWallet, 0, 1000, utxo.getHash(), (int)utxo.getIndex(), inputBalance);

        // register inputs...
        multiClientManager.assertMixStatusRegisterInput(1, false);
    }

}