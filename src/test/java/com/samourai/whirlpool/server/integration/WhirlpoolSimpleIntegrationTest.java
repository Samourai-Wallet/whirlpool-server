package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.utils.BIP47WalletAndHDWallet;
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

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
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

        String message = "Signed using utxo address.:" + inputP2SH_P2WPKH;
        String signature = utxoKey.signMessage(message);
        System.out.println("input address:" + inputP2SH_P2WPKH);
        System.out.println("signature:" + signature);

        // sender calculates address with receiver's payment code
        PaymentAddress sendAddress = bip47Util.getSendAddress(bip47InputWallet, outputPCode, 0, params);
        // receiver calculates address with sender's payment code
        PaymentAddress receiveAddress = bip47Util.getReceiveAddress(bip47OutputWallet, inputPCode, 0, params);

        // sender calculates from pubkey
        SegwitAddress addressFromSender = new SegwitAddress(sendAddress.getSendECKey().getPubKey(), cryptoService.getNetworkParameters());
        // receiver can calculate from privkey
        SegwitAddress addressToReceiver = new SegwitAddress(receiveAddress.getReceiveECKey(), cryptoService.getNetworkParameters());
        String outputAddress = addressToReceiver.getAddressAsString();
        Assert.assertEquals(addressFromSender.getAddressAsString(), addressToReceiver.getAddressAsString());
        System.out.println("sender calculates from pubkey:" + addressFromSender.getAddressAsString());
        System.out.println("receiver calculates from privkey:" + addressToReceiver.getAddressAsString());

        Round round = roundService.__getCurrentRound();

        // mock TransactionOutPoint
        long inputBalance = computeSpendAmount(round, false);
        TxOutPoint utxo = createAndMockTxOutPoint(inputP2SH_P2WPKH, inputBalance);

        whirlpoolClients = new WhirlpoolClient[]{createClient()};
        String paymentCode = "PM8TJgszuvoNLvuoUpMD951fLqjMDRL6km8RDxWEcvE4cjiMDXYRagW3FNRyB58Mi5UXmmZ8vo1PHsnjciEhpZn2xgHZRcGAn3UuYgjdfN4bb5KUhNAV";
        BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();
        ISimpleWhirlpoolClient keySigner = new SimpleWhirlpoolClient(utxoKey, bip47Wallet);
        whirlpoolClients[0].whirlpool(utxo.getHash(), utxo.getIndex(), paymentCode, keySigner, computeSpendAmount(round, false), false);
        Thread.sleep(3000);

        // register inputs...
        assertStatusRegisterInput(round, 1, false);
        Assert.assertTrue(round.hasInput(utxo));
        //Assert.assertEquals(utxoHash, round.getInputsByUsername(username).get(0).getHash());

        // register outputs...
        roundService.changeRoundStatus(round.getRoundId(), RoundStatus.REGISTER_OUTPUT);
        Thread.sleep(2000);
        Assert.assertEquals(RoundStatus.REGISTER_OUTPUT, round.getRoundStatus());
        Assert.assertEquals(1, round.getSendAddresses().size());
        Assert.assertEquals(1, round.getReceiveAddresses().size());
        //Assert.assertEquals(outputAddress, round.getOutputs().get(0));

        // signing...
        roundService.changeRoundStatus(round.getRoundId(), RoundStatus.SIGNING);
        Thread.sleep(2000);
        Assert.assertEquals(RoundStatus.SIGNING, round.getRoundStatus());
        Assert.assertEquals(1, round.getNbSignatures());

        // success...
        roundService.changeRoundStatus(round.getRoundId(), RoundStatus.SUCCESS);
        Thread.sleep(500);
        assertStatusSuccess(round, 1, false);
        assertClientsSuccess();
    }

}