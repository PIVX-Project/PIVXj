package host.furszy.zerocoinj.wallet;

import com.google.common.collect.Lists;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.context.ZerocoinContext;
import com.zerocoinj.core.exceptions.InvalidSerialException;
import com.zerocoinj.utils.JniBridgeWrapper;
import host.furszy.zerocoinj.MultiWalletFiles;
import host.furszy.zerocoinj.WalletFilesInterface;
import host.furszy.zerocoinj.wallet.files.Listener;
import org.pivxj.core.*;
import org.pivxj.core.listeners.TransactionConfidenceEventListener;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_ZPIV;

public class ZWallet {

    private Logger logger = LoggerFactory.getLogger(ZWallet.class);

    private ZerocoinContext zContext;
    private NetworkParameters params;
    private Wallet zPivWallet;

    public ZWallet(NetworkParameters params, ZerocoinContext zContext, DeterministicSeed seed, int lookaheadSize) {
        this.params = params;
        this.zContext = zContext;
        KeyChainGroup keyChainGroupZpiv = new KeyChainGroup(params, seed, BIP44_ZPIV);
        if (lookaheadSize > 0)
            keyChainGroupZpiv.setLookaheadSize(lookaheadSize);
        zPivWallet = new Wallet(params,keyChainGroupZpiv);
    }

    public ZWallet(ZerocoinContext zContext, Wallet zPivWallet) {
        this.zContext = zContext;
        this.zPivWallet = zPivWallet;
        this.params = zPivWallet.getParams();
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

    public Transaction createMint(Coin amount) throws InsufficientMoneyException {
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
        if (!temp.isZero()){
            throw new InsufficientMoneyException();
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

    public Transaction createSpend(Coin amount) throws InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        // First order the unspents
        Map<CoinDenomination, List<TransactionOutput>> orderedUnspents = orderUnspents(zPivWallet.getUnspents());
        // Now start, start from the bigger den and decrease it until the value is completed
        Coin temp = Coin.valueOf(amount.value);
        for (CoinDenomination coinDenomination : CoinDenomination.invertedValues()) {
            if (!orderedUnspents.containsKey(coinDenomination)) continue;
            List<TransactionOutput> outputs = orderedUnspents.get(coinDenomination);
            if (temp.isZero()) break;
            Coin denValue = Coin.valueOf(coinDenomination.getDenomination(),0);
            if (denValue.isZero()) continue;
            if (temp.value < denValue.value) continue;
            long mod = temp.value % denValue.value;
            Coin divisibleForDenomination = temp.minus(Coin.valueOf(mod));
            long amountOfThisDenom = divisibleForDenomination.divide(denValue);
            for (int i = 0; i < amountOfThisDenom; i++) {
                // todo: I should get the old one here..
                TransactionOutput out = outputs.get(i);
                if (out.getParentTransaction().getConfidence().getDepthInBlocks() >= CoinDefinition.MINT_REQUIRED_CONFIRMATIONS)
                    tx.addInput(outputs.get(i));
            }
            temp = Coin.valueOf(mod);
        }
        if (!temp.isZero()){
            throw new InsufficientMoneyException();
        }
        return tx;
    }

    private Map<CoinDenomination, List<TransactionOutput>> orderUnspents(List<TransactionOutput> unspents) {
        Map<CoinDenomination,List<TransactionOutput>> orderedUnspents = new HashMap<>();
        for (TransactionOutput output : zPivWallet.getUnspents()) {
            if (output.isZcMint()){
                CoinDenomination den = CoinDenomination.fromValue((int) output.getValue().value / 100000000);
                if (orderedUnspents.containsKey(den)){
                    orderedUnspents.get(den).add(output);
                }else {
                    orderedUnspents.put(
                            den,
                            Lists.newArrayList(output)
                    );
                }
            }
        }
        Map<CoinDenomination, List<TransactionOutput>> treeMap = new TreeMap<>(
                new Comparator<CoinDenomination>() {

                    @Override
                    public int compare(CoinDenomination o1, CoinDenomination o2) {
                        return o2.compareTo(o1);
                    }

                });
        treeMap.putAll(orderedUnspents);
        return treeMap;
    }

    public void completeTx(SendRequest request) throws InsufficientMoneyException {
        zPivWallet.completeTx(request);
    }

    public void cleanup() {
        zPivWallet.cleanup();;
    }

    public void reset() {
        zPivWallet.reset();
    }

    public void addWalletFrom(BlockChain blockChain) {
        blockChain.addWallet(zPivWallet);
    }

    public Transaction completeSendRequestAndWaitSync(JniBridgeWrapper jniBridgeWrapper, SendRequest request, PeerGroup peerGroup, ExecutorService executor) throws CannotSpendCoinsException {
        try {
            Context.get().zerocoinContext.jniBridge = jniBridgeWrapper;
            if (request.tx.getOutputs().isEmpty()) throw new IllegalArgumentException("SendRequest transactions outputs cannot be null, add the outputs values before call this method");
            ZCSpendRequest spendRequest = new ZCSpendRequest(request, peerGroup);
            zPivWallet.completeSendRequest(spendRequest);
            Transaction tx = executor.submit(spendRequest).get(15, TimeUnit.MINUTES);
            logger.info("Tx created!, " + tx);
            tx = peerGroup.broadcastTransaction(tx,1,false).broadcast().get(1, TimeUnit.MINUTES);
            logger.info("Tx broadcasted!, " + tx);
            return tx;
        } catch (Exception e){
            logger.info("Exception in completeSendRequestAndWaitSync", e);
            throw new CannotSpendCoinsException(e);
        }
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

    public Transaction getTramsaction(Sha256Hash hash) {
        return zPivWallet.getTransaction(hash);
    }

    public Coin getValueSentFromMe(Transaction transaction) {
        return transaction.getValueSentFromMe(zPivWallet);
    }

    public Coin getValueSentToMe(Transaction transaction) {
        return transaction.getValueSentToMe(zPivWallet);
    }

    public Set<Transaction> getTransactions(boolean includeDeads) {
        return zPivWallet.getTransactions(includeDeads);
    }

    public void addTransactionConfidenceEventListener(ExecutorService executor, TransactionConfidenceEventListener listener) {
        zPivWallet.addTransactionConfidenceEventListener(executor, listener);
    }

    public void removeTransactionConfidenceEventListener(TransactionConfidenceEventListener eventListener) {
        zPivWallet.removeTransactionConfidenceEventListener(eventListener);
    }

    public void autosaveToFile(WalletFilesInterface vFileManager, Listener eventListener) {
        zPivWallet.autosaveToFile(vFileManager,eventListener);
    }

    public Coin getUnspendableBalance() {
        return zPivWallet.getBalance(Wallet.BalanceType.ESTIMATED).minus(zPivWallet.getBalance(Wallet.BalanceType.AVAILABLE));
    }

    public Coin getBalance(Wallet.BalanceType type) {
        return zPivWallet.getBalance(type);
    }

    public Collection<Transaction> getPendingTransactions() {
        return zPivWallet.getPendingTransactions();
    }

    public List<TransactionOutput> getUnspents() {
        return zPivWallet.getUnspents();
    }

    public ZCoin getZcoinAssociated(BigInteger commitmentValue) {
        return zPivWallet.getActiveKeyChain().getZcoinsAssociated(commitmentValue);
    }

    public ZCoin getZcoinAssociatedToSerial(BigInteger serial) {
        return zPivWallet.getActiveKeyChain().getZcoinsAssociatedToSerial(serial);
    }

    public List<ZCoin> freshZcoins(int n) {
        return zPivWallet.freshZcoins(KeyChain.KeyPurpose.RECEIVE_FUNDS,n);
    }
}
