package org.pivxj.zerocoin;

import com.google.common.collect.Lists;
import com.zerocoinj.JniBridge;
import com.zerocoinj.core.*;
import com.zerocoinj.core.accumulators.Accumulator;
import com.zerocoinj.core.accumulators.AccumulatorWitness;
import com.zerocoinj.core.accumulators.Accumulators;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.protocol.GenWitMessage;
import host.furszy.zerocoinj.protocol.PubcoinsMessage;
import host.furszy.zerocoinj.wallet.InvalidSpendException;
import host.furszy.zerocoinj.wallet.MultiWallet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pivxj.core.*;
import org.pivxj.net.MessageWriteTarget;
import org.pivxj.params.MainNetParams;
import org.pivxj.params.UnitTestParams;
import org.pivxj.testing.TestWithWallet;
import org.pivxj.wallet.DeterministicSeed;
import org.pivxj.wallet.SendRequest;
import org.pivxj.wallet.Wallet;
import org.pivxj.wallet.WalletProtobufSerializer;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;


public class CoinSpendTest extends TestWithWallet {

    @Test
    public void walletZcoinDerivation() {

        NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);
        context.zerocoinContext.jniBridge = new JniBridge();

        // Setup the wallet
        DeterministicSeed seed = new DeterministicSeed(
                Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                "",
                System.currentTimeMillis()
        );
        final MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed, 140);

    }

    @Test
    public void validCoinSpendWithCppDataTest(){

        NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);
        context.zerocoinContext.jniBridge = new JniBridge();

        // Setup the wallet
        DeterministicSeed seed = new DeterministicSeed(
                Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                "",
                System.currentTimeMillis()
        );
        final MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed, 15);

        // Setup the accumulator status with random coins
        List<ZCoin> coins = multiWallet.freshZcoins(15);
        for (ZCoin coin : coins) {
            coin.setCoinDenomination(CoinDenomination.ZQ_ONE);
        }

        ZCoin myCoin = coins.get(5);

        Assert.assertEquals("Serial not valid",
                new BigInteger("112528974699127969510791029434430131089903980535436389917566704532828560273751"),
                myCoin.getSerial());
        // valid serial --> 008f9c42288d572cf96394a7ff0c6247579c8a5bdc1227e220417cb7f620721b75
        // adjusted serial --> 3973891039144036301193230988785217477463369911398361130575219525410001236311
        // adjusted serial vch (NET) --> 809c42288d572cf96394a7ff0c6247579c8a5bdc1227e220417cb7f620721b75
        // adjusted serial hex (no sirve este..)--> 8c92482d875c29f36497affc0267475c9a8b5cd21722e0214c77b6f0227b157

        System.out.println(myCoin);

        Accumulator accumulator = new Accumulator(context.zerocoinContext.accumulatorParams, myCoin.getCoinDenomination());
        AccumulatorWitness witness = new AccumulatorWitness(accumulator, myCoin);

        for (ZCoin coin : coins) {
            accumulator.accumulate(coin);
            if (!coin.equals(myCoin))
                witness.addElement(coin);
        }

        System.out.println("Accumulator: " + accumulator.getValue());
        System.out.println("Witness: " + witness.getValue());

        // build the transaction
        Transaction tx = new Transaction(params);
        tx.addOutput(Coin.COIN, multiWallet.freshReceiveAddress());
        Sha256Hash ptxHash = tx.getHash();

        CoinSpend coinSpend = new CoinSpend(
                context.zerocoinContext,
                myCoin,
                accumulator,
                BigInteger.ZERO,
                witness,
                ptxHash,
                SpendType.SPEND,
                null
        );

        if (!coinSpend.verify(accumulator)) {
            Assert.fail("CoinSpend verify failed");
        }

        if (!coinSpend.hasValidSignature()){
            Assert.fail("CoinSpend signature invalid, coinSpend: " + coinSpend);
        }

        BigInteger adjustedSerial = ZCoin.getAdjustedSerial(coinSpend.getCoinSerialNumber());
        Assert.assertTrue(
                "Not valid serial",
                Utils.areBigIntegersEqual(
                        new BigInteger("3973891039144036301193230988785217477463369911398361130575219525410001236311"),
                        adjustedSerial
                )
        );
    }

    @Test
    public void validCoinSpendWithCppData2Test(){

        NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);
        context.zerocoinContext.jniBridge = new JniBridge();

        // Setup the wallet
        DeterministicSeed seed = new DeterministicSeed(
                Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                "",
                System.currentTimeMillis()
        );
        final MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed, 15);

        // Setup the accumulator status with random coins
        List<ZCoin> coins = multiWallet.freshZcoins(15);
        for (ZCoin coin : coins) {
            coin.setCoinDenomination(CoinDenomination.ZQ_ONE);
        }

        for (int i = 0; i < coins.size(); i++) {

            ZCoin myCoin = coins.get(i);

            Accumulator accumulator = new Accumulator(context.zerocoinContext.accumulatorParams, myCoin.getCoinDenomination());
            AccumulatorWitness witness = new AccumulatorWitness(accumulator, myCoin);

            for (ZCoin coin : coins) {
                accumulator.accumulate(coin);
                if (!coin.equals(myCoin))
                    witness.addElement(coin);
            }

            // build the transaction
            Transaction tx = new Transaction(params);
            tx.addOutput(Coin.COIN, multiWallet.freshReceiveAddress());
            Sha256Hash ptxHash = tx.getHash();

            CoinSpend coinSpend = new CoinSpend(
                    context.zerocoinContext,
                    myCoin,
                    accumulator,
                    BigInteger.ZERO,
                    witness,
                    ptxHash,
                    SpendType.SPEND,
                    null
            );

            if (!coinSpend.verify(accumulator)) {
                Assert.fail("CoinSpend verify failed");
            }

            if (!coinSpend.hasValidSignature()){
                Assert.fail("CoinSpend signature invalid, coinSpend: " + coinSpend);
            }

        }
    }

    @Test
    public void test(){
        NetworkParameters params = MainNetParams.get();
        Context.getOrCreate(params);
        System.out.println(ECKey.fromPrivate(Hex.decode("8b21ddfbd2e20cc5b04606b8d57736ea390176f170cbe1216019680cfb36f0f2")).getPrivateKeyAsWiF(params));
    }


    @Test
    public void validCoinSpendTest() {

        try {

            // The test basically load the wallet with 2 new mints and fill up them until they are spendable
            // Then try to spending them getting a valid accumulator from the node.

            final NetworkParameters params = UnitTestParams.get();
            setUp();
            DeterministicSeed seed = new DeterministicSeed(
                    Hex.decode("760a00eda285a842ad99626b61faebb6e36d80decae6665ac9c5f4c17db5185858d9fed30b6cd78a7daff4e07c88bf280cfc595620a4107613b50cab42a32f9b"),
                    "",
                    System.currentTimeMillis()
            );
            final MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(new JniBridge()), seed);
            loadWallet(multiWallet, Coin.valueOf(1, 0));
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(1, 0));

            Transaction mintTx = req.tx;
            mintTx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1, 2, 3, 4})));
            mintTx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10, 2, 3, 4})));
            multiWallet.commitTx(mintTx);

            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, mintTx);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, mintTx);

            this.wallet = multiWallet.getZpivWallet();
            // Now 20 blocks depth
            for (int i = 0; i < 20; i++) {
                sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
                sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            }

            // Now create the accumulator with the coin to spend


            // Create the spend of 1 zPIV
            ExecutorService executor = Executors.newSingleThreadExecutor();
            PeergroupUtil peergroupUtil = new PeergroupUtil(params);
            peergroupUtil.addWallet(multiWallet.getZpivWallet());

            SendRequest spendRequest = multiWallet.createSpendRequest(multiWallet.getPivWallet().freshReceiveAddress(), Coin.COIN);

            BigInteger commitmentValue = spendRequest.tx.getInput(0).getConnectedOutput().getScriptPubKey().getCommitmentValue();
            final ZCoin coinToSpend = multiWallet.getZpivWallet().getZcoin(commitmentValue);

            final Peer peer = peergroupUtil.getConnectedPeers().get(0);
            peer.setWriteTarget(new MessageWriteTarget() {
                @Override
                public void writeBytes(byte[] message) throws IOException {

                    System.out.println("Sending message..");
                    System.out.println(Hex.toHexString(message));

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Accumulator calculation
                                ZerocoinContext zerocoinContext = Context.get().zerocoinContext;
                                zerocoinContext.jniBridge = new JniBridge();
                                // Accumulator
                                Accumulator acc = new Accumulator(zerocoinContext.getAccumulatorParams(), CoinDenomination.ZQ_ONE);
                                AccumulatorWitness witness = new AccumulatorWitness(acc.copy(), coinToSpend);

                                List<ZCoin> networkCoins = Lists.newArrayList(multiWallet.getZpivWallet().getZcoins(4));
                                if (!networkCoins.contains(coinToSpend)) {
                                    networkCoins.add(coinToSpend);
                                }
                                for (ZCoin coin : networkCoins) {
                                    if (coin.getCoinDenomination() != CoinDenomination.ZQ_ONE)
                                        coin.setCoinDenomination(CoinDenomination.ZQ_ONE);
                                    acc.accumulate(coin);
                                    if (!coin.equals(coinToSpend)) {
                                        witness.addElement(coin);
                                    }
                                }

                                PubcoinsMessage pubcoinsMessage = new PubcoinsMessage(
                                        params,
                                        acc.getValue(),
                                        witness.getValue(),
                                        Lists.newArrayList(coinToSpend.getCommitment().getCommitmentValue()),
                                        1
                                );

                                try {
                                    TimeUnit.SECONDS.sleep(1);
                                    peer.processPubcoins(pubcoinsMessage);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }
                    }).start();


                }

                @Override
                public void closeConnection() {

                }
            });

            Transaction spentTx = multiWallet.spendZpiv(spendRequest, peergroupUtil, executor, new JniBridge());

            // Now check
            multiWallet.getZpivWallet().maybeCommitTx(spentTx);

            // Confirm tx
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);

            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN, spentTx);

            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(), AbstractBlockChain.NewBlockType.BEST_CHAIN);

            // Check the balance, must be zero
            Assert.assertEquals("Wallet balance result is not correct", multiWallet.getZPivWallet().getBalance(Wallet.BalanceType.AVAILABLE), Coin.ZERO);

        }catch (Exception e){
            Assert.fail(e.getMessage());
        }

    }

    private void loadWallet(MultiWallet multiWallet, Coin coin) {
        this.wallet = multiWallet.getPivWallet();
        sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, Coin.valueOf(2,0),wallet.freshReceiveAddress());
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, Coin.valueOf(1,0),wallet.freshReceiveAddress());
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);

        for (int i = 0; i < 10; i++) {
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);

        }
    }

    /**
     * TODO: Un buen test puede ser armar una transacción valida de coinSpend, generar la wallet y enviarla por el notifyTx
     */

}
