package org.pivxj.wallet;

import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.wallet.MultiWallet;
import org.junit.Assert;
import org.junit.Test;
import org.pivxj.core.*;
import org.pivxj.crypto.DeterministicKey;
import org.pivxj.params.MainNetParams;
import org.pivxj.params.UnitTestParams;
import org.pivxj.testing.TestWithWallet;
import org.spongycastle.util.encoders.Hex;

import java.net.InetAddress;

import static org.pivxj.testing.FakeTxBuilder.createFakeTx;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_PIV;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_ZPIV;
import static org.pivxj.wallet.WalletTest.broadcastAndCommit;

public class MultiWalletTest extends TestWithWallet {

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

        PeerGroup peerGroup = new PeerGroup(params);
        peerGroup.addWallet(pivWallet);
        peerGroup.addWallet(zPivWallet);
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
            MultiWallet multiWallet = new MultiWallet(params, new ZerocoinContext(), seed);
            loadWallet(multiWallet, Coin.valueOf(2,0));
            SendRequest req = multiWallet.createMintRequest(Coin.valueOf(2, 0));


            Transaction tx = req.tx;
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{1,2,3,4})));
            tx.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[]{10,2,3,4})));
            multiWallet.commitTx(tx);


            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            // Confirmed tx
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN, tx);
            sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN,tx);
            // More blocks..
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
            sendMoneyToWallet(multiWallet.getPivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);


            System.out.println(multiWallet.getPivWallet());
            // TODO: add the receiving part of this.. i have to check how is this calculated
            System.out.println(multiWallet.getZPivWallet());

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

        sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, Coin.valueOf(2,0),wallet.freshReceiveAddress());
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);
        sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, Coin.valueOf(1,0),wallet.freshReceiveAddress());
        sendMoneyToWallet(multiWallet.getZpivWallet(),AbstractBlockChain.NewBlockType.BEST_CHAIN);

        System.out.println("wallet balance: " + wallet.getBalance());
        System.out.println("wallet unspendable: " + wallet.getBalance(Wallet.BalanceType.ESTIMATED));
    }


}
