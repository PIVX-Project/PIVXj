package org.pivxj.wallet;

import com.google.common.base.Charsets;
import com.zerocoinj.JniBridge;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.Commitment;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.accumulators.Accumulator;
import com.zerocoinj.core.accumulators.AccumulatorWitness;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.store.AccStoreImp;
import host.furszy.zerocoinj.wallet.MultiWallet;
import host.furszy.zerocoinj.wallet.PSolution;
import host.furszy.zerocoinj.wallet.ZCoinSelection;
import org.junit.Assert;
import org.junit.Test;
import org.pivxj.core.*;
import org.pivxj.core.listeners.AbstractPeerDataEventListener;
import org.pivxj.core.listeners.BlocksDownloadedEventListener;
import org.pivxj.core.listeners.PeerConnectedEventListener;
import org.pivxj.crypto.DeterministicKey;
import org.pivxj.net.discovery.PeerDiscovery;
import org.pivxj.net.discovery.PeerDiscoveryException;
import org.pivxj.params.MainNetParams;
import org.pivxj.params.TestNet3Params;
import org.pivxj.params.UnitTestParams;
import org.pivxj.store.LevelDBBlockStore;
import org.pivxj.testing.TestWithWallet;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        int numberToDecouple = 67;
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
    public void deserializeMerkleBlock(){
        NetworkParameters params = TestNet3Params.get();
        PARAMS = params;
        Context context = Context.getOrCreate(params);
        String hex = "05000000145881438c4c2448aba0527caeefd962287f12df92b83f16d31614a3cacbc0d71ddbda31217a15f120a293ecd5ae4f9939c6f135beccbaf1b37cd2c9a4d374d58479c95b8de6001b00000000a40cd9f2fc00a9d06d45197b89e2c89831aa507ff0947b38b614a33ec46cc0cb02000000011ddbda31217a15f120a293ecd5ae4f9939c6f135beccbaf1b37cd2c9a4d374d50100";

        FilteredBlock filteredBlock = new FilteredBlock(params, Hex.decode(hex));

    }

    @Test
    public void connect(){

        try {
            NetworkParameters params = TestNet3Params.get();
            PARAMS = params;
            Context context = Context.getOrCreate(params);
            setUp();
            context = Context.getOrCreate(params);
            context.accStore = new AccStoreImp(context, new File("/Users/furszy/Downloads/accStore"));


            final MultiWallet wallet;
            File file = new File("/Users/furszy/Downloads/testWallet_multi_light.dat");
            if (file.exists()){
                final InputStream is = new FileInputStream(file);


                wallet = new WalletProtobufSerializer().readMultiWallet(is, false, null);
                if (!wallet.getParams().equals(params))
                    throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
                if (!wallet.isConsistent())
                    throw new IOException("inconsistent wallet backup");
            }else {
                DeterministicSeed seed = new DeterministicSeed(
                        Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                        "",
                        System.currentTimeMillis()
                );
                wallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
                wallet.saveToFile(file);
            }



            wallet.autosaveToFile(file, 2, TimeUnit.SECONDS,null);
            LevelDBBlockStore spvBlockStore = new LevelDBBlockStore(context,new File("/Users/furszy/Downloads/watching_blockstore_2.dat")); //new LevelDBBlockStore(context,new File("blockstore.dat"));

            // First rollback the blockchain to the level that i want..
            spvBlockStore.rollbackTo(Sha256Hash.wrap("59328781eaaf9bc269f582ad8655b304832a5f84db0c633cca1ca1e9c66d3e12"));

            //InputStream inputStream = new FileInputStream(new File("checkpoints"));
            //CheckpointManager.checkpoint(networkParameters, inputStream, spvBlockStore, System.currentTimeMillis());
            final BlockChain blockChain = new BlockChain(context,wallet.getPivWallet(),spvBlockStore);
            blockChain.addWallet(wallet.getZpivWallet());
            final PeerGroup peerGroup = new PeerGroup(params,blockChain);
            //peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
            peerGroup.addPeerDiscovery(new PeerDiscovery() {
                @Override
                public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                    return new InetSocketAddress[]{
                            //new InetSocketAddress("202.5.21.31",51474),
                            new InetSocketAddress("localhost",51474)
                            //new InetSocketAddress("localhost",51474)
                            //new InetSocketAddress("88.198.192.110",51474)
                    };
                }

                @Override
                public void shutdown() {

                }
            });

            wallet.addPeergroup(peerGroup);
            peerGroup.setDownloadTxDependencies(0);
            peerGroup.setMinBroadcastConnections(1);
            peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
                @Override
                public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                    //System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
                }
            });

            final AtomicBoolean flag = new AtomicBoolean(false);
            peerGroup.addConnectedEventListener(new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    // tx
                    if (flag.compareAndSet(false, true)) {

//                        try {
//                            SendRequest request = wallet.createMintRequest(Coin.COIN);
//                            peerGroup.broadcastTransaction(request.tx).broadcast().get();
//                        } catch (InsufficientMoneyException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        } catch (ExecutionException e) {
//                            e.printStackTrace();
//                        }
                    }

                }
            });
            peerGroup.setMaxPeersToDiscoverCount(1);
            peerGroup.startBlockChainDownload(new AbstractPeerDataEventListener(){
                @Override
                public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                    if ((blocksLeft/10000) == 0) {
                        System.out.println("block left: " + blocksLeft + ", hash: " + block.getHash().toString());
                    }
                }
            });
            peerGroup.startAsync();
            peerGroup.downloadBlockChain();

            System.out.println(wallet.toString());
            System.out.println("blockchain height: "+blockChain.getChainHead().getHeight());

            System.out.println("------------ LEVEL DB ACC STORE -------------");

            ZCoin zCoin = null;
            for (TransactionOutput output : wallet.listZpivUnspent()) {
                if(output.isZcMint()){
                    zCoin = wallet.getZcoinAssociated(output.getScriptPubKey().getCommitmentValue());
                    int mintTxHeight = output.getParentTransaction().getConfidence().getAppearedAtChainHeight();
                    zCoin.setHeight(mintTxHeight);
                }
            }

            System.out.println("zCOin: " + zCoin.toJsonString());

            try {
                BigInteger acc = context.accStore.get(750140, CoinDenomination.ZQ_ONE);
                if (acc == null){
                    System.out.println("### Error Accumulator not found: " + acc);
                }else {
                    System.out.println("Accumulator: " + acc);
                }

            }catch (Exception e){
                e.printStackTrace();
            }

            while (true){

            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
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

    @Test
    public void test(){
        ZerocoinContext zerocoinContext = new ZerocoinContext(null);
        BigInteger witvalue = new BigInteger("24255682626070936618706355161975355105197529177549690880729713343316449198688583171187588514328031837839755484256640140747230961529497788093594550479196944797274256740192886489436781903498601274503446376270554122138549669878626701265702651686084122411081457682524402277083253599820969297373595362241321289727316431466583595197393489811540734418048980251783597769785086249381261786389229567829018212350669896744309389305988447972302558962251480927189389105705881504285419362021974500063718777388987823643048633137055228947032699501048121778196512988677330127272347302053308089380616360688998620983480408358975936957563");
        Accumulator accumulator = new Accumulator(zerocoinContext.accumulatorParams,CoinDenomination.ZQ_ONE,witvalue);
        AccumulatorWitness accumulatorWit = new AccumulatorWitness(accumulator, new ZCoin(zerocoinContext, null, new Commitment(BigInteger.ONE,BigInteger.ONE, BigInteger.ONE),CoinDenomination.ZQ_ONE, null));


        String s = "83fbc4531606be7d5c6d465eb8854a7b1708ba07100bbcfda0b889ac2913dce1e952e6a08a2e3ed90f71a2e24ebddfd614e5b8c69b0ec7d2b8254d4256024f24c65e392e524541daa9a8442398c6a14db217f1ec6766720b4b31618d4de08120b4cfc56053f7ad0b743db4d20a5e08032bb596cf8ef6ead6dfba98c632e3cb, 71ed7b2e90b07855e13ee756ea3c36660de6ce4f39ec363bcd26ecf8bd8702b29dc1003af6f983aafaee83e019b4fc32cab82572898298e7ba8b3331b1754a17ee7906a6aa02deab5185f26fa364b25e913c0a82918c7074062a5aeb8b500165f323dfb35238c9014b38f0e9dbb709a2ae8d66b5447d2e35bffba9ee5bac7fd, 643ef19db5c81444f5dd603a3130f669ad1cf7156ed250a9787f0c49e6f1207c3b95fadcce9516a48fa675d49bb51a9bb9aeae3d0037d13be6bbc6eb0a7204fdf589e9f0a8a3f33fdb7d4cbd9bbc893616d006230a7d315e0fe1808d862518707e4a2be909abcbb4d9c1c418139f2c3052189e7c3394fec205c2aaf6d190a297, 46cb9b537771fd34e0083b1587bcd651fa4ca98288518fafb3084fc0c08d120d489e184f051a9a6400e327e46899ecf4c89319bca18faf387c52bff60ec55e3d67f387aa7962216b1dece2b2e90e0005e15797ce068507249ae370eedf8914afe8f53c64988f7e30982a2034f4014d038904800a5bcc4189507b28c9091043b1, 3fd643f0e3533ae22bb4f33ff9781d3ce0a07a583789cf3c374cfe4043c50d129184643bc2af3ebab3f89568ac25a74fd63218c67541264ce6261607081c224332741484264c4f5ab479814672b49c86e028f6cbd27e20c7b39cf1a63fb893d7ccb6345e7149159031a8f5bdd6aff1828725c8307cf61699234c416ecb1da601, aa18dae50e99f9e4234576faba0e2a7ae31b1a4a7a581c701bbfd871625b6d3ae2bb73336787cf65e47a1e155cd1e36e98877f30f5b1be452a996e2b79bc57c08cf8b3fbc4d4b1d821ad63a7814ccd4873b740c728dec99d41bd8a146d848c105e00f108818f04d2dd6dd49f13d328ed2ce68a4257d63d35f7b346806215810f, d6c32292af56675994927f2d9aa14a05a3fc16ac240ced8f22aa1ec4fa86be54e3ee82d8a7ac695d6af9eaec78ae4213109f464610edd33e9f81ec47242f9773712d092dac5d0690df965bae1799ba8aabccd663d48949b34a8109cdb8682c9765f479b3713e24fbfa83694a4e0a31b59a1b5f964d88b0548179e369af2553a7, 9c139b5e46ac2c98d0a7d953f7ee57e81ea8bb2f1325e12a046af92298c8c8c2b785ae1bb995244e5ae10683b31ad31845dc774912c498d0c63bf6e907f883d013c701857f439276fe6bd6c722f612edf09253b005449e19da640f75261b4fdffea7a6249b3bb68eee33d475e2e571f9aa9ddcad955b69cc608f9f86fa1be79f, 710b27addb60b6e2587fd1ea26bf80962457d9f41f17233d4b10fa85d2601cf57ff9c53e6cf8663404e4c8c231a6a820c0734ba54d1d4eec84dd613ec84f04591800d616182d7644661e8d5c229783770a23c7163733e687662a3a877cc7b992e43f9e0e200f4e0241fa2b356e7ffad413dde41ea0a13296253e2f8ad12321d9, 1b186f7a5afe47dddab2149e5c29420e771e9fcbb2f3f51016f472b1c2fdf3e92ac83eed77c1d3cd4f505104a8545493d846a620893755df990cd3a59723a649b7fbabd8b089a2926c0e3e5afd3698a2f96443040a9e2e067eda2c0fdab3bf9af8a3bb4716abe4295b5d5c4c184d6384fdcbf1218195c6b92d78ddd26bd53a77, 57e65e382785d896573b6b00a2305716a782dcaac387edf71222faf90f69bb8135f43bbfb78ece42ddc9c2eb43ad7994b35f73892c686c8a528cc599d54af565d2032e0232f1d6d4d2d62fd3c5b4711b1de92dad7fa2214ad62d379b553bb734ac53b4842005f3736a67095d5d39a1e68b8f4ca5039aeb9a177460698df10431, 706a96c7554aebfbd69e4694b7d4457c02907dc21856c4cbbecfc2c4791e772e0838caf65cfb344fc5e880ab0789e497d53c5fba32b1dacdf72ea5711a3cf51221c01002be17e017f2fb984f498f9218d0a1aa9b56f23d0d4362d37b11c0f490f241591606d874cc88c88ac19dad83d14a243317be7dd5a04fb1d7448e78b2f7, 2a57de95192fd0afa906cb9c27a377840be11ff06083b846f4b55cc8d8305ebe32c9b20ee63896f397591e03b05d8aaa7f65ca9dd91b870acda8fb8d75989c4a7ac7ad320b43fb0e40296f7ccd53dca633ec3ac08921d4d9759bb54bc960a2887a95d120d6b97ec9bf9cbf37a32ead4684ec2aaf255e3b8260779fd54f1bd88f, cef664d14dd44bf006fbe1182cdd62de92122a78b56289ec72eae482845e73ce66b147124cd1993904ceff62bd9db602aafcc4155989baf1cbececd62f7e984d4e3a2940072c009fde3f1599b18157b82aafe0ed326481bca3dcaaddd86418a95ece67bf8ee86e7f7a8449d29fccee8d295fe17bda9a7d0ae005a041ea61bd81, c26ce859a86e0c00db6df66bc2c5d7f2831a3207e93cf0e57b3db5b155f7b5070cef5d73b1f257bb0b647866226e49329a76fc2a4f9c4fef9b12fbc91cda8c4f6979692aeeab98857638b2b3b97d64540d35f855ba1b5c5360ba1fe37f139383c420440c6f937fa28ac41c5980447de7554efc5918cbc95701c16ed528d794ed, ad12b7da5176818ba929883282447aec57be918a600a51a5b65853340451720293f889eb2c1247c32e46d94546f8ab35da871277d81a18ecdcb568c5700752cc54cceca980bb505492406b99e1c3431de6fe0a0316789be54e79ac952486e509613136f2da9c9104223668fffe9d674a045e2a49ff7924f4e5f0415a610b7e81, 3722dec8d33fd0956bf3dae86f40eceeddc1ff8ee1e1dd9146c6220f73bb4fddef756f549d96bb3c999eb696387f9efa8ae4c88cf09b7ac89a8661cba34e7f53929e7878e9fe567404b42e225b1909e874009a03bfff2076b11b9a47bcb85965963d0407489f4455603db45b5f9e052401ca4b5b89c2cd2a097c56d0e02b96af, c70a8107ee27582d6802aa345e28bd135d9b4ca6d4cc4fdb2599d1ecbb26e6ef5510bf28d133d84a3f8708333940e589d5ee5408e192f35b5832eab7380632f087a24315641617d90c67f3d4850ecebc4d6c47dc401dbeb81b5f9db44dbd60c3699fcec8b6df53783033059ac99d8921ac6800de5dacb3ad2457799fc8bca507, 4e36451a6d92f45a3cf2e634f34f0578695602130b7b0a8ef1117acd97853c882b5988187b2726bc88bcbba035a0075fa30b8c31b9e0fb9ad24c4c45ee1b042c36aa450a72d03272a1c509e78b413a73f810c6b91e678909f383a394c5cb5d1d0e3ef79ec4009798d20a6fb355fd0e3a74c6488b2f5639deee9913df05e4d49f, 1480f4f405b74bdbd2ec0b36184016a74a56b7b93b41b36b0d6a1599c8ce744b0ce6d7fc42fe402280e67d45675925255ab7c0dff35c6057abcada4891989a184bd3672e3bdbb428dfbbeb2859adf3a5bff75ca542ff2b01feb4383a4c97d70d962a52b4d70c26c248bafb4e514be6f3187ef70b1cc8abf1752854866cb4510b, 396ced5ac8bf678bb07afd24d33dd8ae2db017632d61d7e4768aa2311201095bd09f10742241bfc2f8aa0ed02f7cd9faa657e34b0e5bbbce70a56e128b0211d5b72364b604ac00ff42e6fa32c09749ae267e6bafa6affec5d5c1bd5451bdb48d97650d38708398e5fc1f1ec393420b25ae681a26b5397df941a7909587592e61, 99639956293ad2c309b375d3efbb3fc7a1d4380d00fac26e3d21d47d2d8ae109c66368362392840ed084af2476b15cea8d195d65d5a713947b5226b57cf60fc311d3b6e686860e7d1eb1771ed2a362ab65b24d482aa67b82d0210601d2747d324bc0a0302efcbfd17b7a331fad20f281d8409028855412f56a4aec9a15b1ab57, 5d59242d9267b656b465d6e0bb70756fd7b04e01019f7a905218d8ae4d7813b5e59e883505dab22ca6cba9c14107b456e1819bb6c2c6764dadafedaeeb084f8f90fba6f25af0794b7d03505b4545e1e126fc3ff5f0e315161110646d9db3fc44a6492100fbaca24b9e8f1bc799ff2cd564c20159059e11fb2f3a0cf6e54a23df, c0fc584e5da10353742d43db76a01bb6b84a24c9e99446809f809d554facea621cd858ab4e6e69c58984eb98e943f681432704d631238332a00ff027743eeba0b00e427a1157330dee890c462dec59b624970ba46d75a9c3d5a75876df9f858ae73c7d665a7db3cfb82a5a2e5a288d669ad05a228ec9856660120ddca33e7271, 3f9ba41595c41012f7f0a76b44523b0c3fc6afad43b6c431fec50a471b4fa4e51f775fc6e319ae959c1d1057033bb32e281d02815fac1a7e1f8b1bfac5731105f6fb98b7bf844483858cc2dd731df6f119a7ed720db4f80230c79efd42f72bc940e46db9b539ad7f59990a992940ba8f5697c83cf0df6301630f5760785a9c5d, 66cc686379f009106fd58e9e33fd4b65b99883f75049c1585bcc7b2b73658b66ee56949f53224e13c63c6f25ac5906f55b26992bc437bdf7ddf71023fe9ad83f7bc11921a8957021b5aea950c0b52cac02b0e051d8fac7361fe95b075ce5b591a56a1bda7c5f471ff36ed14befdde6fcb8ccd6f0728911cefc98b4eea5022bd5, 48f7ce78ee51930836d574489f4048c8cade8f3175aba80b8ba27132125aca8e47e1b469348872955b1d4c0d7b576ef8f08878199b53eca4367eefd55cf57c4990a7685a57af4403677c0cb060dd798b6a2de1042046b077d3a292d705468a41acdfc067e9cfb94e70edaf03e5588ccae4f5e5efe1f4f83942c2e3abe531ce15, e4392e3dca59c1f7a923b997f7c1559233741253704b3d4369e46bb1ac4cb9877d16d06a6c43c2fb6dcc532f228a81d49ad6caf8ecbd801854424aae195d02f372d5772459e46b1e22edb163f44045fe3d7a8a81763a597148584e60cdefe3c4c0b2315f789fd180a557ca3a5e7a8aecd2baabf72a23df8c8e796c3a53522227, 7255186f401d7a05c76ef7dcff9588b39df2ea685a2444b597bb5e493808354fcc09ce4133ecf1c71d59749867a3b494882dc9ab3d972e7a35702d226d824272a08b7c0575c35cc7bd88a564583672e1a650f81bfa8a8b223f8f4fc9fe94a0b7406ba406890084bfadd27d0d2991efab4c674985689930caf352a962597133f9, d8e046a10a465709e0fdbfbeefad031c5eb3e221215e0fcc2d5933c0bbe427bc6f19b434630e72e6eb0affaacdeabd9deac63d3dd62d796e38d4f6f77d574820cb3d183b9948edd9a700be489b56950fdacd70231e99cf87b34601e1e85904d3397a311309643a4b53e7481f0c4e6d0031d1d73be1ebe7a8439d57c549db37df, a16a105873d85a898760f8376b5042a864b4cfc7ea71840ae4b1f2fdbc292c9656982d848fc10b8662a17198fb7443be5683946e063a734b13e4e7375efc6da2455cf5c622650f4a2f963d680d8380c58364fc5668e31218fb647f127610a8910d3f31b0a8dbf9dc5b5921584de3167816324e94fad6a6289323abe15936cf93, b05d36d54edcdc9ec042b3ad5bcd04bc3412b3ea22a96abec3bd25d1893377140c03b50561c956979be61e4e1973ee7f23d5342d6578c5c2687b454c2ccada70b4225b4ec5bcc1e324e23fdc22a464606bb41a6da35b39bbafaa22b5c4eb4736b1b7815de00c59fbed2a737cf71673f9ea2cb153a36b5dfd3b36351178665ba1, 31dbde900596163ee2476457a9aa13d13e627392bcbab39a5d0b6db1dba033560c2db56bb7fa5fb93bdc560caa7464338513f2b737b0e75e1d17dda8c43a9a86540fe5b60c71379c7bc908f1a618926ea214a138b9e0487aa32df798c3b1cc9623e0bb4547f65eb7284f84508ef828cd8571046196f773b50262003206f2807, 33c936049d859de85b9672a1960ccaca18d2cbe7bf753bac6a504b6944be14a7679c686d8b62aec88d226685f26dba19be8dd8e8fb88f661375701484932a4b905d4fcc934db93d1d1313f8eaa19f5bdeef6e315ccd36ee4847ae3458999af6d0e950b6d7cf91bf85c3f64f1aa0a865a6d91fa6b4c852fbd799079ec641f532f, 5542ba23e1c1fe112c69e04585366b5d88803c42ea5b5da6e1644d6ad3fec9aac285cdccd926c7a6c1edecaeabaed6f4e5872a48067004581194aa2f421c3cb0888ad66f252d5bc7bd0950af8f7b893a6ce1a2da98089a833d5156ccc65b0f342e97c5b16ca4b3dfd8579b93e0bb348e5c221cb4d178c14daaa791949221425b, 1651caceb074dadd6447571b2603235cd0d8a60053d56ec5b196ecface68f67b5b81b8a383a581b4e22701066e1bad7957a0cfd8b6ca0336a76493b0afdc23b4acadd23e09ff8d897e1170ed80227ccfe35e5bf37a1dcbdba35e6ec902351b8ec36a17e248cbe366673acd158bdd481c87911d9493d15909ef43c43f422a4413, 674960438ea37ce6ec7dfe6acf714b37ac8477db9fb95fd047a4f7d4a7ea2caf301714acc3623659112c6a2699ad4c519149d9b8b07710d655ff9682478c1d3116ac544781fc577d9666155d853976848ec4f23e1b55bd83b2a4e52a44eb497a87c5967fb6e0a8ef98a422ebc77082e6bc765f95af86a53a313089c2b2c34c4f, b89713066985196f3694d2129aadb49192f68d270d83553b77c38329a71b0f25f66e363459471f46c139c2032ea2dd75daf5581f1167a757fff767220f3b0c236ffbea9a9677f6d694b906f086fd004ee91a95395b3e11c85c5a1be2d94e7de2b25cd7a57cbac00848640320c8968bffb17f32f084920b3dd343f755adc9b5d9";

        String[] values = s.split(", ");

        List<BigInteger> list = new ArrayList<>();
        for (String value : values) {
            list.add(new BigInteger(value, 16));
        }

        for (BigInteger bigInteger : list) {
            accumulatorWit.addElementUnchecked(bigInteger);
        }
        System.out.println("value: " + accumulatorWit.getValue());
    }

}
