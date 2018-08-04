package org.pivxj.zerocoin;

import com.google.common.collect.Lists;
import com.zerocoinj.JniBridge;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.Commitment;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.accumulators.Accumulator;
import com.zerocoinj.core.accumulators.AccumulatorWitness;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.protocol.GenWitMessage;
import host.furszy.zerocoinj.protocol.PubcoinsMessage;
import host.furszy.zerocoinj.wallet.MultiWallet;
import org.junit.Test;
import org.pivxj.core.*;
import org.pivxj.net.MessageWriteTarget;
import org.pivxj.params.UnitTestParams;
import org.pivxj.testing.TestWithWallet;
import org.pivxj.wallet.DeterministicSeed;
import org.pivxj.wallet.SendRequest;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class CoinSpendTest extends TestWithWallet {


    @Test
    public void validCoinSpendTest() throws Exception {

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
        loadWallet(multiWallet, Coin.valueOf(2,0));
        SendRequest req = multiWallet.createMintRequest(Coin.valueOf(2, 0));

        Transaction tx = req.tx;
        tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1,2,3,4})));
        tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10,2,3,4})));
        multiWallet.commitTx(tx);

        sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        // Confirmed tx
        sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN,tx);

        this.wallet = multiWallet.getZpivWallet();
        // Now 200 blocks depth
        for (int i = 0; i < 500; i++) {
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
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
                        // Accumulator calculation
                        ZerocoinContext zerocoinContext = Context.get().zerocoinContext;
                        // Accumualtor
                        Accumulator acc = new Accumulator(zerocoinContext.getAccumulatorParams(),CoinDenomination.ZQ_ONE);
                        AccumulatorWitness witness = new AccumulatorWitness(acc.copy(), coinToSpend);

                        List<ZCoin> networkCoins = Lists.newArrayList(multiWallet.getZpivWallet().getZcoins(4));
                        if (!networkCoins.contains(coinToSpend)){
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
                                10
                        );

                        try {
                            TimeUnit.SECONDS.sleep(1);
                            peer.processPubcoins(pubcoinsMessage);
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

        multiWallet.spendZpiv(spendRequest,peergroupUtil,executor);

        System.out.println(multiWallet.getZpivWallet());

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

}
