package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.context.ZerocoinContext;
import com.zerocoinj.core.exceptions.InvalidSerialException;
import org.pivxj.core.*;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.wallet.DeterministicSeed;
import org.pivxj.wallet.KeyChain;
import org.pivxj.wallet.KeyChainGroup;
import org.pivxj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_ZPIV;

public class ZWallet {

    private Logger logger = LoggerFactory.getLogger(ZWallet.class);

    private ZerocoinContext zContext;
    private NetworkParameters params;
    private Wallet zPivWallet;

    public ZWallet(NetworkParameters params, ZerocoinContext zContext, DeterministicSeed seed) {
        this.params = params;
        this.zContext = zContext;
        KeyChainGroup keyChainGroupZpiv = new KeyChainGroup(params, seed, BIP44_ZPIV);
        zPivWallet = new Wallet(params,keyChainGroupZpiv);
    }

    public void addPeergroup(PeerGroup peerGroup) {
        peerGroup.addWallet(zPivWallet);
    }

    public void removePeergroup(PeerGroup peerGroup) {
        peerGroup.removeWallet(zPivWallet);
    }

    public void commitTx(Transaction tx) {
        zPivWallet.commitTx(tx);
    }

    public Transaction createMint(Coin amount){
        Transaction tx = new Transaction(params);
        List<CoinDenomination> denominations = new ArrayList<>();
        Coin temp = Coin.valueOf(amount.value);
        // Easy algo, we start with the maximum amount and decreasing..
        for (CoinDenomination coinDenomination : CoinDenomination.invertedValues()) {
            if (temp.isZero()) break;
            Coin den = Coin.valueOf(coinDenomination.getDenomination(),0);
            if (den.isZero()) continue;
            if (temp.value < den.value) continue;
            long mod = temp.value % den.value;
            Coin divisibleForDenomination = temp.minus(Coin.valueOf(mod));
            for (int i = 0; i < divisibleForDenomination.divide(den); i++) {
                denominations.add(coinDenomination);
            }
            temp = Coin.valueOf(mod);
        }
        List<ZCoin> coinsToUse = zPivWallet.freshZcoins(KeyChain.KeyPurpose.RECEIVE_FUNDS, denominations.size());
        for (int i = 0; i < denominations.size(); i++) {
            ZCoin zCoin = coinsToUse.get(i);
            zCoin.setCoinDenomination(denominations.get(i));
            completeMint(tx, zCoin);
        }
        return tx;
    }

    private void completeMint(Transaction tx, ZCoin zCoin){
        byte[] encodedCommitment = zCoin.getCommitment().serialize();
        Script script = new ScriptBuilder()
                .op(ScriptOpCodes.OP_ZEROCOINMINT)
                .number(encodedCommitment.length)
                .data(encodedCommitment)
                .build();
        TransactionOutput mintOutput = new TransactionOutput(params, tx, Coin.COIN, script.getProgram());
        mintOutput.setValue(Coin.valueOf(zCoin.getCoinDenomination().getDenomination(), 0));
        tx.addOutput(mintOutput);
    }

    @Override
    public String toString() {
        return "ZWallet{" +
                "zPivWallet=" + zPivWallet +
                '}';
    }

    //////////////// Testing /////////////////////////

    public Wallet getWallet() {
        return zPivWallet;
    }
}
