package host.furszy.zerocoinj.wallet;

import com.google.common.collect.Lists;
import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.context.ZerocoinContext;
import com.zerocoinj.core.exceptions.InvalidSerialException;
import com.zerocoinj.utils.JniBridgeWrapper;
import host.furszy.zerocoinj.MultiWalletFiles;
import host.furszy.zerocoinj.WalletFilesInterface;
import host.furszy.zerocoinj.protocol.AccValueMessage;
import host.furszy.zerocoinj.store.AccStoreException;
import host.furszy.zerocoinj.wallet.files.Listener;
import org.pivxj.core.*;
import org.pivxj.core.listeners.*;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.store.BlockStoreException;
import org.pivxj.utils.Pair;
import org.pivxj.wallet.*;
import org.pivxj.wallet.exceptions.RequestFailedErrorcodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

import static org.pivxj.core.Utils.toDenomination;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_ZPIV;

public class ZWallet {

    private Logger logger = LoggerFactory.getLogger(ZWallet.class);

    private final static int SPEND_THRESHOLD_LIMIT = 60 * 24 * 30; // 30 days threshold

    private ZerocoinContext zContext;
    private NetworkParameters params;
    private MultiWallet parent;
    private Wallet zPivWallet;

    // Last calculated block..
    int lastWitnessCalculationHeight = 0;
    int passedBlocks = -1;

    private ExecutorService executorService;

    private NewBestBlockListener blocksEvent = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            // Every 50 blocks calculate the witness value for the coins that this wallet has..
            if (passedBlocks == -1 || passedBlocks > 50){
                // TODO: Request the witness calculation here..
                System.out.println("Request witness calculation..");
                lastWitnessCalculationHeight = zPivWallet.getLastBlockSeenHeight();
                passedBlocks = 0;
            }
        }
    };

    private TransactionReceivedInBlockListener txEvent = new TransactionReceivedInBlockListener(){

        @Override
        public void receiveFromBlock(final Transaction tx, final StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            if (zPivWallet.isTransactionRelevant(tx)){
                if (blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                tryToSyncTxIfIsRelevant(tx, block);
                            }catch (Exception e){
                                logger.error("Exception on tryToSyncTxIfIsRelevant", e);
                            }
                        }
                    });
                }

            }
        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, final StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            final Transaction tx = zPivWallet.getTransaction(txHash);
            if (tx == null) return false; // False positive that was broadcast to us and ignored by us because it was irrelevant to our keys.

            if (blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            tryToSyncTxIfIsRelevant(tx, block);
                        }catch (Exception e){
                            logger.error("Exception on tryToSyncTxIfIsRelevant", e);
                        }
                    }
                });
            }
            return true;
        }
    };

    public ZWallet(NetworkParameters params, ZerocoinContext zContext, MultiWallet parent , DeterministicSeed seed, int lookaheadSize) {
        this.parent = parent;
        this.params = params;
        this.zContext = zContext;
        KeyChainGroup keyChainGroupZpiv = new KeyChainGroup(params, seed, BIP44_ZPIV);
        if (lookaheadSize > 0)
            keyChainGroupZpiv.setLookaheadSize(lookaheadSize);
        zPivWallet = new Wallet(params,keyChainGroupZpiv);
        this.lastWitnessCalculationHeight = zPivWallet.getLastBlockSeenHeight();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public ZWallet(ZerocoinContext zContext, MultiWallet parent, Wallet zPivWallet) {
        this.parent = parent;
        this.zContext = zContext;
        this.zPivWallet = zPivWallet;
        this.params = zPivWallet.getParams();
        this.lastWitnessCalculationHeight = zPivWallet.getLastBlockSeenHeight();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    private void setupEvents(){
        if (Context.get().blockChain != null) {
            Context.get().blockChain.addNewBestBlockListener(blocksEvent);
            Context.get().blockChain.addTransactionReceivedListener(txEvent);
        }
    }

    public void addPeergroup(PeerGroup peerGroup) {
        peerGroup.addWallet(zPivWallet);
        setupEvents();
    }

    public void removePeergroup(PeerGroup peerGroup) {
        peerGroup.removeWallet(zPivWallet);
    }

    public void commitTx(Transaction tx) {
        zPivWallet.commitTx(tx);
    }


    private void tryToSyncTxIfIsRelevant(Transaction tx, StoredBlock block) {
        // If tx is mint then request the accValue that occurred right before this block..
        List<TransactionOutput> mints = new ArrayList<>();
        for (TransactionOutput output : tx.getOutputs()) {
            if (output.isZcMint()){
                mints.add(output);
                break;
            }
        }
        if (!mints.isEmpty()){

            for (TransactionOutput mint : mints) {
                try {
                    int height = block.getHeight();
                    //int rev = (height % 10) + 10;
                    int rev = (height % 10);

                    logger.info("#@#@#@ Trying to request the accumulator value for height: " + height + ", rev height: " + rev);

                    BigInteger commValue = mint.getScriptPubKey().getCommitmentValue();
                    ZCoin zCoin = zPivWallet.getZcoin(commValue);

                    CoinDenomination denom;
                    if (zCoin == null){
                        logger.info("zCoin in mint {} not found for this wallet",commValue.toString(16));
                        denom = toDenomination(mint.getValue().value);
                    }else
                        denom = zCoin.getCoinDenomination();

                    StoredBlock storedBlock = block;
                    for (int i = 0; i < rev; i++) {
                        storedBlock = storedBlock.getPrev(
                                Context.get().blockChain.getBlockStore()
                        );
                    }
                    requestAccValue(storedBlock, denom, storedBlock.getHeight());
                }catch (Exception e){
                    logger.warn("Error trying to get the accValue", e);
                }
            }
        }
    }

    private void requestAccValue(StoredBlock storedBlock, CoinDenomination denom, int height){
        Sha256Hash accChecksum = storedBlock.getHeader().getAccumulator();
        int pos = denom.ordinal() - 1;
        // This is in bytes and not bits, that is why this is 4 and not 32
        long nChecksum = Utils.readUint32BE(accChecksum.getBytes(), 4 * pos);
        List<Peer> peers = Context.get().peerGroup.getConnectedPeers();
        peers.get(new Random().nextInt(peers.size()))
                .sendAccValueRequest(
                        nChecksum,
                        new AccValueMessage(params, height, denom)
                );
    }

    public Transaction createMint(Transaction tx, Coin amount) throws InsufficientMoneyException {
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
            // Double check just for in case
            if (!zCoin.validate()){
                throw new Error("##### ERROR Invalid zCoin, \n " + zCoin.toJsonString());
            }
            System.out.println("### Minting using zCoin: " + zCoin.toJsonString());
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

    public Map<CoinDenomination, HashSet<ZCoin>> getAllMintedZCoins(){
        // Get all of the denom in the wallet
        Pair<Map<CoinDenomination, HashSet<TransactionOutput>>, Map<CoinDenomination, Integer>> pair =
                orderUnspents(zPivWallet.getUnspents(), true);

        Map<CoinDenomination, HashSet<ZCoin>> map = new HashMap<>();
        for (Map.Entry<CoinDenomination, HashSet<TransactionOutput>> entry : pair.getFirst().entrySet()) {
            HashSet set = new HashSet();
            for (TransactionOutput output : entry.getValue()) {
                set.add(zPivWallet.loadZcoin(output));
            }
            map.put(entry.getKey(), set);
        }
        return map;
    }

    public Transaction createSpend(Transaction tx, Coin amount, boolean mintChange) throws InsufficientMoneyException {

        try {
            /**
             * First calculate all of the possibilities
             *
             */
            int amountExact = (int) (amount.longValue() / 100000000);
            List<PSolution> solutions = ZCoinSelection.calculateAllPossibleSolutionsFor(amountExact);

            /**
             * Now get all of the possible solutions based on the denominations that are in the wallet
             */
            // Get all of the denom in the wallet
            Pair<Map<CoinDenomination, HashSet<TransactionOutput>>, Map<CoinDenomination, Integer>> pair =
                    orderUnspents(zPivWallet.getUnspents(), true);

            Map<CoinDenomination, Integer> walletAvailableDenominations = pair.getSecond();
            // Now check all of the possibilities against the coins that the wallet has
            List<PSolution> possibleSolutions = new ArrayList<>();
            for (PSolution solution : solutions) {
                boolean ok = true;
                for (Map.Entry<CoinDenomination, Integer> entry : solution.getNeededDenominations().entrySet()) {
                    if (walletAvailableDenominations.containsKey(entry.getKey())) {
                        int availableQuantity = walletAvailableDenominations.get(entry.getKey());
                        if (availableQuantity < entry.getValue()) {
                            ok = false;
                            break;
                        }
                    }else {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    possibleSolutions.add(solution);
                }
            }

            // Outputs ordered by age
            Map<CoinDenomination, HashSet<TransactionOutput>> orderedUnspents = pair.getFirst();
            Map<CoinDenomination, HashSet<TransactionOutput>> orderedUnspentsByAge = pair.getFirst();
            // order outputs by age now
            for (Map.Entry<CoinDenomination, HashSet<TransactionOutput>> coinDenominationSetEntry : orderedUnspents.entrySet()) {
                Set<TransactionOutput> set = coinDenominationSetEntry.getValue();
                List<TransactionOutput> list = new ArrayList<>(set);
                Collections.sort(list, new Comparator<TransactionOutput>() {
                    @Override
                    public int compare(TransactionOutput o1, TransactionOutput o2) {
                        return Integer.compare(
                                o2.getParentTransactionDepthInBlocks(),
                                o1.getParentTransactionDepthInBlocks()
                        );
                    }
                });
                orderedUnspentsByAge.put(coinDenominationSetEntry.getKey(), new HashSet<>(list));
            }


            // Now the wallet should choose the best solution of all of them.. based on oldest criteria and some randomness
            // So, for each possible solution the wallet will fill it up with coins

            // Necesito formar un score por cada grupo de elementos basado en cuan viejo es la suma de sus zCoins
            List<SelectSolution> scoringPerSolution = new ArrayList<>();
            for (PSolution possibleSolution : possibleSolutions) {
                SelectSolution selectSolution = new SelectSolution(possibleSolution);

                // now start adding outputs
                Map<CoinDenomination, Integer> neededDen = possibleSolution.getNeededDenominations();
                for (Map.Entry<CoinDenomination, Integer> entry : neededDen.entrySet()) {
                    HashSet<TransactionOutput> outputs = orderedUnspentsByAge.get(entry.getKey());
                    // add as many as are needed
                    Iterator<TransactionOutput> it = outputs.iterator();
                    for (int i = 0; i < entry.getValue(); i++) {
                        selectSolution.addOutput(it.next());
                    }
                }
                scoringPerSolution.add(selectSolution);
            }

            // Now that we have all of the solutions with a scoring, let's select the best option!
            SelectSolution bestSolution = null;
            for (SelectSolution selectSolution : scoringPerSolution) {
                if (bestSolution == null) {
                    bestSolution = selectSolution;
                } else {
                    if (bestSolution.getScoring() < selectSolution.getScoring()) {
                        bestSolution = selectSolution;
                    }
                }
            }

            if (bestSolution == null){
                throw new InsufficientMoneyException(amount);
            }

            // Now use the best solution
            tx = completeSpendOutput(tx, bestSolution.getOutpus(), amount, mintChange);
            Coin sumTotal = tx.getInputSum();
            if (sumTotal.equals(amount) || sumTotal.isGreaterThan(amount)) {
                tx.verify();
                return tx;
            } else {
                logger.error("€€€€€€€€€€€€ FAIL ");
            }

            // TODO: Remove what comes after this.. if this fails it's because there is no money..

            /**
             * The spend flow is:
             * 1) Order unspent by depth.
             * 2) Use the older unspents
             * 3) Use the remaining spends in decreasing order.
             */
            // First order the unspents
            // Now get the older zPIV and add it to this tx first
            Set<TransactionOutput> oldZpiv = new HashSet<>();
            for (Set<TransactionOutput> set : orderedUnspents.values()) {
                for (TransactionOutput output : set) {
                    if (output.getParentTransactionDepthInBlocks() >= SPEND_THRESHOLD_LIMIT) {
                        oldZpiv.add(output);
                    }
                }
            }

            if (!oldZpiv.isEmpty()) {
                // Now add them and check if the tx is completed
                tx = completeSpendOutput(tx, oldZpiv, amount, mintChange);
            }

            Coin sum = tx.getInputSum();
            if (sum.equals(amount) || sum.isGreaterThan(amount)) {
                tx.verify();
                return tx;
            }

            Coin coinsToAdd = amount.subtract(sum);

            // If not means that we need to add more..
            for (Map.Entry<CoinDenomination, HashSet<TransactionOutput>> entry : orderedUnspents.entrySet()) {
                // Now add them and check if the tx is completed
                tx = completeSpendOutput(tx, entry.getValue(), coinsToAdd, mintChange);

                sum = tx.getInputSum();
                if (sum.equals(amount) || sum.isGreaterThan(amount)) {
                    try {
                        // Verify
                        tx.verify();
                    }catch (Exception e){
                        logger.error("Exception on the verification, tx: " + tx);
                        throw e;
                    }
                    return tx;
                }
            }

            sum = tx.getInputSum();
            // Check if we are good now or throw exception
            if (sum.isZero() || (!sum.equals(amount) && sum.isLessThan(amount)))
                throw new InsufficientMoneyException(amount.subtract(sum));

            // Verify
            tx.verify();

            return tx;

        }catch (Exception e){
            throw e;
        }
    }

    private Transaction completeSpendOutput(Transaction tx, Set<TransactionOutput> outputs, Coin amount, boolean mintChange) {
        // order by depth first
        outputs = new HashSet<>(orderByDepth(outputs));
        // Now start, start from the bigger den and decrease it until the value is completed
        Coin temp = Coin.valueOf(amount.value);
        for (TransactionOutput out : outputs) {
            if (temp.isZero() || temp.isNegative()) break;
            // Check if this output is already included in the tx
            if (isSelectable(tx, out)) {
                tx.addInput(out);
                temp = temp.subtract(out.getValue());
            }
        }

        // Now complete the change address
        Coin finalAmount = tx.getInputSum();
        if (!finalAmount.equals(amount) && finalAmount.isGreaterThan(amount)){
            Coin change = finalAmount.subtract(amount);
            if (!mintChange || change.compareTo(Coin.COIN) == 0) {
                tx.addOutput(change, parent.getCurrentReceiveAddress());
            }else {
                try {
                    logger.info("Minting change..");
                    Coin coinMinusMintFee = change.minus(Coin.COIN);
                    logger.info("Trying to mint changeMinusFee: " + coinMinusMintFee.toFriendlyString());
                    tx = createMint(tx, coinMinusMintFee);
                    Coin changeToRegularAddress = Coin.COIN.minus(
                            CoinDefinition.MIN_ZEROCOIN_MINT_FEE.multiply(tx.getAmountOfMints()
                            )
                    );
                    logger.info("Trying to mint changeToRegularAddress: " + changeToRegularAddress.toFriendlyString());
                    tx.addOutput(changeToRegularAddress, parent.freshReceiveAddress());
                } catch (InsufficientMoneyException e){
                    logger.error("InsufficientMoneyException on mint change --- this should not happen", e);
                    throw new IllegalStateException(e);
                }
            }
        }
        return tx;
    }

    private boolean isSelectable(Transaction tx, TransactionOutput out) {
        boolean found = false;
        for (TransactionInput transactionInput : tx.getInputs()) {
            if (transactionInput.getOutpoint().equals(out.getOutPointFor())){
                found = true;
            }
        }
        return !found;
    }

    private Pair<Map<CoinDenomination, HashSet<TransactionOutput>>, Map<CoinDenomination, Integer>> orderUnspents(List<TransactionOutput> unspents, boolean filterSpendable) {
        Map<CoinDenomination,HashSet<TransactionOutput>> orderedUnspents = new HashMap<>();
        Map<CoinDenomination, Integer> quatityOfEachDenom = new HashMap<>();
        int lastSeenBlockHeight = zPivWallet.getLastBlockSeenHeight();
        for (TransactionOutput output : unspents) {
            if (output.isZcMint()){
                if (filterSpendable){
                    if (!isSpendable(output, lastSeenBlockHeight)) continue;
                }
                CoinDenomination den = toDenomination(output.getValue().value);
                if (orderedUnspents.containsKey(den)){
                    orderedUnspents.get(den).add(output);
                    // Quantity
                    quatityOfEachDenom.put(
                            den,
                            quatityOfEachDenom.remove(den) + 1
                    );
                }else {
                    HashSet<TransactionOutput> outSet = new HashSet<>();
                    outSet.add(output);
                    orderedUnspents.put(
                            den,
                            outSet
                    );
                    // Quantity
                    quatityOfEachDenom.put(den, 1);
                }
            }
        }
        // Now order by depth
        Map<CoinDenomination,HashSet<TransactionOutput>> finalOrderedMap = new HashMap<>();
        for (Map.Entry<CoinDenomination, HashSet<TransactionOutput>> entry : orderedUnspents.entrySet()) {
            // order it by depth, older first
            finalOrderedMap.put(entry.getKey(), new HashSet<>(orderByDepth(entry.getValue())));
        }

        // Now by denomination
        Map<CoinDenomination, HashSet<TransactionOutput>> treeMap = new TreeMap<>(
                new Comparator<CoinDenomination>() {

                    @Override
                    public int compare(CoinDenomination o1, CoinDenomination o2) {
                        return o2.compareTo(o1);
                    }

                });
        treeMap.putAll(finalOrderedMap);
        return new Pair<>(treeMap, quatityOfEachDenom);
    }

    private List<TransactionOutput> orderByDepth(Collection<TransactionOutput> list){
        List<TransactionOutput> set = new ArrayList(list);
        Collections.sort(set, new Comparator<TransactionOutput>() {
            @Override
            public int compare(TransactionOutput o1, TransactionOutput o2) {
                return Integer.compare(o1.getParentTransactionDepthInBlocks(), o2.getParentTransactionDepthInBlocks());
            }
        });
        return set;
    }

    private boolean isSpendable(TransactionOutput out, int lastSeenBlockHeight){
        // TODO: this should be improved thinking on the checkpoint block time
        return out.getParentTransaction().getConfidence().getDepthInBlocks() >= CoinDefinition.MINT_REQUIRED_CONFIRMATIONS;
    }

    public void completeTx(SendRequest request) throws InsufficientMoneyException {
        zPivWallet.completeTx(request);
    }

    public List<AmountPerDen> listAmountPerDen(){
        Map<CoinDenomination, HashSet<TransactionOutput>> orderedUnspents = orderUnspents(zPivWallet.getUnspents(), true).getFirst();
        Map<CoinDenomination,AmountPerDen> map = new HashMap<>();
        for (Map.Entry<CoinDenomination, HashSet<TransactionOutput>> entry : orderedUnspents.entrySet()) {
            AmountPerDen amountPerDen = new AmountPerDen(entry.getKey());
            for (TransactionOutput output : entry.getValue()) {
                amountPerDen.increment(output.getValue());
            }
            map.put(entry.getKey(), amountPerDen);
        }
        return new ArrayList<>(map.values());
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

    public Transaction completeSendRequestAndWaitSync(JniBridgeWrapper jniBridgeWrapper, SendRequest request, PeerGroup peerGroup, ExecutorService executor) throws CannotSpendCoinsException, RequestFailedErrorcodeException {
        try {
            Context.get().zerocoinContext.jniBridge = jniBridgeWrapper;
            if (request.tx.getOutputs().isEmpty()) throw new IllegalArgumentException("SendRequest transactions outputs cannot be null, add the outputs values before call this method");
            ZCSpendRequest spendRequest = new ZCSpendRequest(request, peerGroup);
            zPivWallet.completeSendRequest(spendRequest);
            Transaction tx = executor.submit(spendRequest).get(10, TimeUnit.MINUTES);
            logger.info("Tx created!, " + tx);
            tx = peerGroup.broadcastTransaction(tx,1,false).broadcast().get(1, TimeUnit.MINUTES);
            logger.info("Tx broadcasted!, " + tx);
            return tx;
        }catch (AccStoreException e){
            logger.info("Exception in completeSendRequestAndWaitSync", e);
            // Let's try to get the value here..
            Transaction mintTx = zPivWallet.getTransaction(e.zCoin.getParentTxId());
            if (mintTx.getAppearsInHashes() != null){
                try {
                    Sha256Hash appearedBlockHash = mintTx.getAppearsInHashes().keySet().iterator().next();
                    StoredBlock storedBlock = Context.get().blockChain.getBlockStore().get(appearedBlockHash);
                    if (storedBlock.getHeight() < e.height) throw new IllegalStateException("Stored block height cannot be higher than the base acc height..");
                    while (storedBlock.getHeight() != e.height){
                        storedBlock = storedBlock.getPrev(
                                Context.get().blockChain.getBlockStore()
                        );
                    }
                    requestAccValue(storedBlock, e.zCoin.getCoinDenomination(), e.height);

                    throw new CannotSpendCoinsException("Re calculating coins, please try again in one minute");
                } catch (BlockStoreException e1) {
                    logger.warn("Cannot obtain parent block from transaction..");
                    throw new CannotSpendCoinsException(e);
                }
            }
            throw new CannotSpendCoinsException(e);

        } catch (Exception e){
            logger.info("Exception in completeSendRequestAndWaitSync", e);
            if (e.getCause() instanceof RequestFailedErrorcodeException){
                throw (RequestFailedErrorcodeException)e.getCause();
            }else
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


    public void shutdown() {
        if (executorService != null && !executorService.isShutdown())
            executorService.shutdown();
    }
}
