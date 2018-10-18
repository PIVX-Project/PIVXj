package org.pivxj.wallet;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.zerocoinj.JniBridge;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.wallet.MultiWallet;
import host.furszy.zerocoinj.wallet.PSolution;
import host.furszy.zerocoinj.wallet.ZCoinSelection;
import org.junit.Assert;
import org.junit.Test;
import org.pivxj.core.*;
import org.pivxj.crypto.DeterministicKey;
import org.pivxj.params.MainNetParams;
import org.pivxj.params.UnitTestParams;
import org.pivxj.testing.TestWithWallet;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.net.InetAddress;
import java.util.*;

import static host.furszy.zerocoinj.wallet.ZCoinSelection.decoupleNumberInDenominations;
import static host.furszy.zerocoinj.wallet.ZCoinSelection.decoupleNumberInExactDenominations;
import static host.furszy.zerocoinj.wallet.ZCoinSelection.nextDenomInDecreasingOrder;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_PIV;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_ZPIV;

public class MultiWalletTest extends TestWithWallet {

    @Test
    public void importZpiv(){
        NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);

        String s = "{\n" +
                "    \"d\": 5000," +
                "    \"p\": \"a20560555df1f577a459de8aac6e23d9acadc847cdae9782858afd53b93f0cc0adb18ec78ee3490333e60e75f6c9cd07bfe7255c414cbb728a00b9049afaacafa0ee90761a7580045abf822acffcffde948d65f5dbe7afdbda42e929f26d4eab126f386593193e7c67f23fbd8b6db8adce07b0c12930dc8148b41cef9d9eed25\"," +
                "    \"s\": \"fb6d66e8edbb0a3d45d9ade8831008116c7b95ffc97f64d4ef121b083fae4e95\"," +
                "    \"r\": \"8f540a0d6fd4f7cc0a9c366262c5be354c549813d2a58c61b9d9696061e5fdbc\"," +
                "    \"t\": \"a5397686bf3211d8ed454c96c2b6aa09ffa82ac9fa2190ffe5d02b90956128fb\"," +
                "    \"h\": 1308251," +
                "    \"u\": false," +
                "    \"v\": 2," +
                "    \"k\": \"3081d3020101042053f48dbd77ac5f3c7cb8406bdd1d1bf990da5354128fd5146f8b7e0db2fbfae1a08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a124032200034dd7ffcc02dacd73ab9c0463dd3757fe4f6958214fa13b9e56da7377220e833f\"" +
                "  }";
        ZCoin zCoin = ZCoin.fromJson(params, context.zerocoinContext, s);
        Assert.assertTrue("Not valid coin", zCoin.validate());
    }

    @Test
    public void pivAndZpivDerivationPath(){

        NetworkParameters params = MainNetParams.get();

        DeterministicSeed seed = new DeterministicSeed(
                Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                "",
                System.currentTimeMillis()
        );

        KeyChainGroup keyChainGroupPiv = new KeyChainGroup(params, seed, BIP44_PIV);
        KeyChainGroup keyChainGroupZpiv = new KeyChainGroup(params, seed, BIP44_ZPIV);

        Wallet pivWallet = new Wallet(params,keyChainGroupPiv);
        Wallet zPivWallet = new Wallet(params,keyChainGroupZpiv);


        DeterministicKey keyPiv = pivWallet.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        Assert.assertEquals("invalid PIV path", "M/44H/119H/0H/0/0", keyPiv.getPathAsString());

        DeterministicKey keyZpiv = zPivWallet.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        System.out.println(keyZpiv.getPathAsString());
        Assert.assertEquals("Invalid zPIV path", "M/44H/37361148H/0H/0/0", keyZpiv.getPathAsString());

    }

    @Test
    public void generateZPIV(){
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(2,0));
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(2, 0));


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1,2,3,4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10,2,3,4})));
            multiWallet.commitTx(tx);

            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN,tx);

            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            // More blocks..
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);

            Transaction tx2 = multiWallet.getTransaction(tx.getHash());

            Assert.assertEquals("Invalid depth in blocks", 5, tx2.getConfidence().getDepthInBlocks());


        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Fail for: " + e.getMessage());
        }
    }

    private void loadWallet(MultiWallet multiWallet, Coin coin) {
        this.wallet = multiWallet.getPivWallet();

        sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, coin,wallet.freshReceiveAddress());
        sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);

    }


    /**
     * TODO: Create a test that
     * 1) Creates the zpiv wallet
     * 2) Serialize it.
     * 3) Deserialize it and try to validate if a previous valid zCoin is part of the wallet.
     */
    @Test
    public void recreateWalletTest(){
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);

            ZCoin myCoin = multiWallet.getZPivWallet().getWallet().getActiveKeyChain().getZcoins(1).get(0);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new WalletProtobufSerializer().walletToProto(multiWallet).build().writeTo(outputStream);
            byte[] walletBytes = outputStream.toByteArray();
            outputStream.close();

            System.out.println("Second...");

            ByteArrayInputStream inputStream = new ByteArrayInputStream(walletBytes);
            MultiWallet multiWallet2 = new WalletProtobufSerializer().readMultiWallet(inputStream, false, null);

            ZCoin myCoin2 = multiWallet2.getZPivWallet().getWallet().getActiveKeyChain().getZcoins(1).get(0);

            Assert.assertEquals("Coins are not equals", myCoin,myCoin2);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }


    @Test
    public void recreateWalletWithTransactionsTest(){
        try {

            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            wallet = multiWallet.getPivWallet();

            // Only receiving money
            for (int i = 0; i < 10; i++) {
                loadWallet(multiWallet, Coin.valueOf(10));
                for (int j = 0; j < 3; j++) {
                    // Blocks..
                    sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
                    sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
                }
                loadWallet(multiWallet, Coin.valueOf(5));
            }

            int txCount = multiWallet.getPivWallet().transactions.size();

            File walletFile = new File("recreate_wallet_test.dat");
            File tempFile = new File("recreate_wallet_temp_test.dat");
            multiWallet.saveToFile(tempFile, walletFile);

            MultiWallet multiWalletRestored;
            try (InputStream inputStream = new FileInputStream(walletFile)) {
                multiWalletRestored = new WalletProtobufSerializer().readMultiWallet(inputStream, false, null);
            }

            Assert.assertNotNull("Null restored wallet", multiWalletRestored);


            Assert.assertEquals("Not equals amount of transactions", txCount, multiWalletRestored.getPivWallet().transactions.size());


        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Test create mint, save the wallet and restore it.
     * The minted zcoin needs to have the values saved.
     */

    @Test
    public void mintSaveRestoreTest(){
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);

            ZCoin myFirstCoin = multiWallet.getZPivWallet().getWallet().getActiveKeyChain().getZcoins(1).get(0);

            // load balance
            loadWallet(multiWallet, Coin.valueOf(2, 0));
            // mint
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(1, 0));
            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx);

            // obtain the minted coin
            TransactionOutput mintOutput = null;
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isZcMint()){
                    mintOutput = output;
                }
            }
            ZCoin minteCoin = multiWallet.getZcoinAssociated(mintOutput.getScriptPubKey().getCommitmentValue());

            // Now check that both coins commitments are equals
            Assert.assertEquals("Coins are not equals", minteCoin.getCommitment(), myFirstCoin.getCommitment());

            Assert.assertSame("Minted coin denomination is not correct", minteCoin.getCoinDenomination(), CoinDenomination.ZQ_ONE);

            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            // More blocks..
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new WalletProtobufSerializer().walletToProto(multiWallet).build().writeTo(outputStream);
            byte[] walletBytes = outputStream.toByteArray();
            outputStream.close();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(walletBytes);
            MultiWallet multiWallet2 = new WalletProtobufSerializer().readMultiWallet(inputStream, false, null);

            ZCoin myCoinRestaured = multiWallet2.getZPivWallet().getWallet().getActiveKeyChain().getZcoins(1).get(0);

            Assert.assertEquals("Restored coin is not equal", minteCoin.getCommitment(), myCoinRestaured.getCommitment());
            Assert.assertSame("Restored minted coin denomination is not correct", myCoinRestaured.getCoinDenomination(), CoinDenomination.ZQ_ONE);

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void moveMintFromPendingPoolToUnspentPool(){

        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(2, 0));
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(1, 0));


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx);

            Assert.assertTrue("Mint is not in pending pool",
                    multiWallet.getZpivWallet().getTransactionPool(WalletTransaction.Pool.PENDING).containsKey(tx.getHash()));

            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            // More blocks..
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);

            Assert.assertTrue("Mint is not in unspendable pool",
                    multiWallet.getZpivWallet().getTransactionPool(WalletTransaction.Pool.UNSPENT).containsKey(tx.getHash()));

            Assert.assertEquals("Invalid value sent to me", tx.getValueSentToMe(multiWallet.getZpivWallet()), Coin.COIN);
            Assert.assertEquals("Invalid value sent from me", tx.getValueSentFromMe(multiWallet.getPivWallet()), Coin.valueOf(2,0));

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }


    }

    /**
     * Test to validate that the zPIV wallet understand mint txes and add them to the wallet balance
     */
    @Test
    public void addZpivToWalletBalanceTest() {
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(2, 0));
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(1, 0));


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx);

            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            // More blocks..
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);

            Assert.assertTrue("Zpiv balance has not been increased" , multiWallet.getZpivWallet().getBalance().isGreaterThan(Coin.ZERO));


            // Now spend it and check if the wallet notice that

            //SendRequest sendRequest = multiWallet.createSpendRequest(multiWallet.getCurrentReceiveAddress(),Coin.COIN);
            //multiWallet.spendZpiv(sendRequest)
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void spendBigAmountsTest(){
        SendRequest spendRequest = null;
        try{
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(12757, 0));

            Assert.assertEquals("Invalid zPIV balance",  Coin.valueOf(12757, 0), multiWallet.getAvailableBalance());

            Coin zPIVbalance = Coin.valueOf(12753, 0);
            SendRequest req = multiWallet.createMintRequest(zPIVbalance);


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx);

            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);

            for (int i = 0; i < 24; i++) {
                // blocks..
                sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
                sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            }

            Assert.assertEquals("Invalid zPIV balance",
                    Coin.valueOf(12753, 0),
                    multiWallet.getZpivAvailableBalance());

            // Create a spend request for 7567 PIV

            spendRequest = multiWallet.createSpendRequest(
                    multiWallet.getPivWallet().freshReceiveAddress(),
                    Coin.valueOf(7567, 0)
            );

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try{
            spendRequest.tx.verify();
        }catch (Exception e) {
            Assert.fail("tx doesn't verify or null, " + spendRequest.tx);
        }
        // Now validate the transaction
        /**
         * 12753
         */
        System.out.println(spendRequest.tx);

    }


    /**
     *  Mints two 5 zPIV denomination, 10 zPIV balance.
     *  Try to spend 8 zPIV and re mint the change.
     */
    @Test
    public void mintChangeTest(){
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(20, 0));


            Coin zPIVbalance = Coin.valueOf(5, 0);
            SendRequest req = multiWallet.createMintRequest(zPIVbalance);

            Coin zPIVbalance2 = Coin.valueOf(5, 0);
            SendRequest req2 = multiWallet.createMintRequest(zPIVbalance);


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx);

            Transaction tx2 = req2.tx;
            tx2.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            tx2.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(tx2);

            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx2);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, tx2);

            for (int i = 0; i < 24; i++) {
                // blocks..
                sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
                sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            }

            //

            SendRequest spendRequest = multiWallet.createSpendRequest(
                    multiWallet.getPivWallet().freshReceiveAddress(),
                    Coin.valueOf(8, 0),
                    true
            );

            System.out.println("TX: " + spendRequest.tx);

            Transaction retTX = spendRequest.tx;

            Assert.assertTrue("Invalid input", retTX.getInput(0).getValue().equals(zPIVbalance));
            Assert.assertTrue("Invalid input", retTX.getInput(1).getValue().equals(zPIVbalance));

            // First the mint checks
            TransactionOutput output = retTX.getOutput(0);
            if (!output.isZcMint()){
                Assert.assertTrue(output.getValue().equals(Coin.valueOf(8, 0)));
            }else {
                Assert.fail("First output is a mint?");
            }

            output = retTX.getOutput(1);
            if (output.isZcMint()){
                Assert.assertTrue(output.getValue().equals(Coin.COIN));
            }else {
                Assert.fail("Second output is not a mint?");
            }

            // Now the regular send
            output = retTX.getOutput(2);
            if (!output.isZcMint()){
                Coin change = Coin.COIN.minus(CoinDefinition.MIN_ZEROCOIN_MINT_FEE);
                Assert.assertTrue(output.getValue().equals(change));
            }else {
                Assert.fail("Third output change invalid");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Mint change failed");
        }
    }


    /**
     * // Calculate all of the possibilities to spend a certain number:
     */

    @Test
    public void coinDenominationSelectionTest(){
        // 334 funciona
        // 930 no funciona
        int numberToDecouple = 930;
        List<PSolution> listOfPossibleSolutions = ZCoinSelection.calculateAllPossibleSolutionsFor(numberToDecouple);

        for (PSolution listOfPossibleSolution : listOfPossibleSolutions) {
            System.out.println(listOfPossibleSolution);
            Assert.assertTrue("Not valid solution" , listOfPossibleSolution.isValid());
        }
    }

    @Test
    public void checkSameSeed(){
        try {
            NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);

            DeterministicSeed seedPiv = multiWallet.getPivWallet().getKeyChainSeed();
            DeterministicSeed seedZPIV = multiWallet.getZpivWallet().getKeyChainSeed();

            Assert.assertEquals("Seed is not the same", seedPiv, seedZPIV);


            // Second test with just the wallet that contains the PIVs

            MultiWallet multiWallet2 = new MultiWallet(multiWallet.getPivWallet());

            seedPiv = multiWallet2.getPivWallet().getKeyChainSeed();
            seedZPIV = multiWallet2.getZpivWallet().getKeyChainSeed();

            Assert.assertEquals("Seed second test failed,it is not the same", seedPiv, seedZPIV);


            // Third test

            Protos.Wallet protoWallet = new WalletProtobufSerializer().walletToProto(multiWallet.getPivWallet());
            byte[] bytes = protoWallet.toByteArray();

            InputStream inputStream = null;


            try{
                inputStream = new ByteArrayInputStream(bytes);

                final Wallet wallet = new WalletProtobufSerializer().readWallet(inputStream, true, null);
                if (!wallet.getParams().equals(params))
                    throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
                if (!wallet.isConsistent())
                    throw new IOException("inconsistent wallet backup");

                MultiWallet multiWallet3 = new MultiWallet(wallet);

                DeterministicSeed seedPiv1 = multiWallet3.getPivWallet().getKeyChainSeed();
                DeterministicSeed seedZPIV1 = multiWallet3.getZpivWallet().getKeyChainSeed();

                Assert.assertEquals("Seed second test failed,it is not the same", seedPiv1, seedZPIV1);
                Assert.assertEquals("Seed second test failed,it is not the same", seedPiv, seedPiv1);
                Assert.assertEquals("Seed second test failed,it is not the same", seedZPIV1, seedZPIV);

            }finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }


    @Test
    public void recoverBackupFile(){

        try {
            NetworkParameters params = MainNetParams.get();
            PARAMS = params;
            Context.getOrCreate(params);
            setUp();


            File file = new File("/Users/furszy/Downloads/old_pivx_mobile_backup_nothing_here");
            final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
            final StringBuilder cipherText = new StringBuilder();
            copy(cipherIn, cipherText, 10000000);
            cipherIn.close();

            final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), "123".toCharArray());
            final InputStream is = new ByteArrayInputStream(plainText);


            final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);
            if (!wallet.getParams().equals(params))
                throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
            if (!wallet.isConsistent())
                throw new IOException("inconsistent wallet backup");

            System.out.println(Arrays.toString(wallet.getKeyChainSeed().getMnemonicCode().toArray()));


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Test
    public void test2(){

        try {
            NetworkParameters params = MainNetParams.get();
            PARAMS = params;
            Context.getOrCreate(params);
            setUp();


            Wallet wallet = new Wallet(params);
            DeterministicSeed seed = wallet.getKeyChainSeed();

            System.out.println(Arrays.toString(seed.getMnemonicCode().toArray()));

            byte[] bytes = new WalletProtobufSerializer().walletToProto(wallet).toByteArray();

            Wallet wallet2 = new WalletProtobufSerializer().readWallet(new ByteArrayInputStream(bytes), true, null);

            System.out.println(Arrays.toString(wallet2.getKeyChainSeed().getMnemonicCode().toArray()));

            Assert.assertEquals("Key doesn't match",wallet2.getKeyChainSeed(), seed);





            File file = new File("/Users/furszy/Downloads/piv_mobile_old_backup_nothing_here");
            final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
            final StringBuilder cipherText = new StringBuilder();
            copy(cipherIn, cipherText, 10000000);
            cipherIn.close();

            final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), "123".toCharArray());
            final InputStream is = new ByteArrayInputStream(plainText);


//            final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);
//            if (!wallet.getParams().equals(params))
//                throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
//            if (!wallet.isConsistent())
//                throw new IOException("inconsistent wallet backup");
//
//            MultiWallet multiWallet3 = new MultiWallet(wallet);
//
//            DeterministicSeed seedPiv1 = multiWallet3.getPivWallet().getKeyChainSeed();
//            DeterministicSeed seedZPIV1 = multiWallet3.getZpivWallet().getKeyChainSeed();
//
//            Assert.assertEquals("Seed fourth test failed,it is not the same", seedPiv1, seedZPIV1);


        }catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    public static final long copy(final Reader reader, final StringBuilder builder, final long maxChars) throws IOException
    {
        final char[] buffer = new char[256];
        long count = 0;
        int n = 0;
        while (-1 != (n = reader.read(buffer)))
        {
            builder.append(buffer, 0, n);
            count += n;

            if (maxChars != 0 && count > maxChars)
                throw new IOException("Read more than the limit of " + maxChars + " characters");
        }
        return count;
    }

    public static final long copy(final InputStream is, final OutputStream os) throws IOException
    {
        final byte[] buffer = new byte[1024];
        long count = 0;
        int n = 0;
        while (-1 != (n = is.read(buffer)))
        {
            os.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

}
