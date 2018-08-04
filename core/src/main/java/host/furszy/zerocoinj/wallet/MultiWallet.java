package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.MultiWalletFiles;
import host.furszy.zerocoinj.WalletFilesInterface;
import host.furszy.zerocoinj.wallet.files.Listener;
import org.pivxj.core.*;
import org.pivxj.core.listeners.TransactionConfidenceEventListener;
import org.pivxj.crypto.DeterministicKey;
import org.pivxj.crypto.KeyCrypter;
import org.pivxj.utils.Threading;
import org.pivxj.wallet.*;
import org.pivxj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;
import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_PIV;

public class MultiWallet{

    private static final Logger log = LoggerFactory.getLogger(MultiWallet.class);

    private DeterministicSeed seed;
    private Wallet pivWallet;
    private ZWallet zWallet;

    protected volatile MultiWalletFiles vFileManager;

    protected final ReentrantLock lock;

    public MultiWallet(NetworkParameters params, ZerocoinContext zContext, DeterministicSeed seed){
        this.lock = Threading.lock("MultiWallet_1");
        this.seed = seed;
        this.pivWallet = new Wallet(params,new KeyChainGroup(params, seed, BIP44_PIV));
        this.zWallet = new ZWallet(params, zContext, seed);
    }

    public MultiWallet(DeterministicSeed seed, List<Wallet> wallets) {
        this.lock = Threading.lock("MultiWallet_1");
        ZerocoinContext zContext = Context.get().zerocoinContext;
        this.seed = seed;
        for (Wallet wallet : wallets) {
            if (wallet.getActiveKeyChain().isZerocoinPath()){
                if (zWallet != null) throw new IllegalStateException("zWallet not null");
                zWallet = new ZWallet(zContext,wallet);
            }else {
                if (pivWallet != null) throw new IllegalStateException("pivWallet not null");
                pivWallet = wallet;
            }
        }
    }

    public MultiWallet(Wallet pivWallet) {
        this.lock = Threading.lock("MultiWallet_1");
        this.pivWallet = pivWallet;
    }

    ////////////////////////// Basic /////////////////////////////////

    public List<String> getMnemonic() {
        return seed.getMnemonicCode();
    }

    public NetworkParameters getParams(){
        return pivWallet.getParams();
    }

    public void addPeergroup(PeerGroup peerGroup){
        peerGroup.addWallet(pivWallet);
        zWallet.addPeergroup(peerGroup);
    }

    public void removePeergroup(PeerGroup peerGroup){
        peerGroup.removeWallet(pivWallet);
        zWallet.removePeergroup(peerGroup);
    }

    public void addWalletFrom(BlockChain blockChain) {
        blockChain.addWallet(pivWallet);
        zWallet.addWalletFrom(blockChain);
    }

    public void commitTx(Transaction tx) {
        boolean isZcMint = false;
        for (TransactionOutput output : tx.getOutputs()) {
            if(output.isZcMint()){
                isZcMint = true;
                break;
            }
        }
        // If it's a zc_mint then commit it to both wallets
        if (isZcMint){
            pivWallet.commitTx(tx);
            zWallet.commitTx(tx);
        }else {
            pivWallet.commitTx(tx);
        }
    }

    public int getLastBlockSeenHeight() {
        return pivWallet.getLastBlockSeenHeight();
    }

    public Transaction getTransaction(Sha256Hash hash) {
        Transaction tx = pivWallet.getTransaction(hash);
        if (tx == null){
            tx = zWallet.getTramsaction(hash);
        }
        return tx;
    }

    public Coin getValueSentFromMe(Transaction transaction) {
        Coin c = transaction.getValueSentFromMe(pivWallet);
        c = c.plus(zWallet.getValueSentFromMe(transaction));
        return c;
    }

    public Coin getValueSentToMe(Transaction transaction) {
        Coin c = transaction.getValueSentToMe(pivWallet);
        c = c.plus(zWallet.getValueSentToMe(transaction));
        return c;
    }

    public Set<Transaction> listTransactions(){
        Set<Transaction> list = pivWallet.getTransactions(true);
        list.addAll(zWallet.getTransactions(true));
        return list;
    }

    public void cleanup(){
        pivWallet.cleanup();
        zWallet.cleanup();
    }

    public void reset(){
        pivWallet.reset();
        zWallet.reset();
    }

    ////////////////////////// PIV ///////////////////////////////////

    /**
     * Returns a key that has not been returned by this method before (fresh). You can think of this as being
     * a newly created key, although the notion of "create" is not really valid for a
     * {@link org.pivxj.wallet.DeterministicKeyChain}. When the parameter is
     * {@link org.pivxj.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} the returned key is suitable for being put
     * into a receive coins wizard type UI. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     */
    public DeterministicKey freshPIVKey(KeyChain.KeyPurpose purpose) {
        return pivWallet.freshKey(purpose);
    }

    public Address freshReceiveAddress() {
        return pivWallet.freshReceiveAddress();
    }

    public Address getCurrentReceiveAddress() {
        return pivWallet.currentReceiveAddress();
    }

    public List<Address> getIssuedReceiveAddresses() {
        return pivWallet.getIssuedReceiveAddresses();
    }

    public boolean isWatchingAddress(Address address){
        return pivWallet.isAddressWatched(address);
    }

    public List<Address> getWatchedAddresses(){
        return pivWallet.getWatchedAddresses();
    }

    public boolean isConsistent(){
        // TODO: Check this..
        return pivWallet.isConsistent();
    }

    public void completeSend(SendRequest sendRequest) throws InsufficientMoneyException {
        pivWallet.completeTx(sendRequest);
    }

    public void addCoinsReceivedEventListener(WalletCoinsReceivedEventListener listener, ExecutorService executor){
        pivWallet.addCoinsReceivedEventListener(executor, listener);
    }

    public void removeCoinsReceivedEventListener(WalletCoinsReceivedEventListener listener) {
        pivWallet.removeCoinsReceivedEventListener(listener);
    }

    public void addOnTransactionsConfidenceChange(ExecutorService executor, TransactionConfidenceEventListener transactionConfidenceEventListener) {
        pivWallet.addTransactionConfidenceEventListener(executor, transactionConfidenceEventListener);
        getZPivWallet().addTransactionConfidenceEventListener(executor, transactionConfidenceEventListener);
    }

    public void removeTransactionConfidenceChange(TransactionConfidenceEventListener transactionConfidenceEventListener) {
        pivWallet.removeTransactionConfidenceEventListener(transactionConfidenceEventListener);
        getZPivWallet().removeTransactionConfidenceEventListener(transactionConfidenceEventListener);
    }


    public Coin getAvailableBalance() {
        return pivWallet.getBalance(Wallet.BalanceType.AVAILABLE);
    }

    public Coin getUnspensableBalance() {
        return pivWallet.getBalance(Wallet.BalanceType.ESTIMATED).minus(pivWallet.getBalance(Wallet.BalanceType.AVAILABLE));
    }

    public List<TransactionOutput> listUnspent() {
        return pivWallet.getUnspents();
    }

    public boolean isAddressMine(Address address) {
        return pivWallet.isPubKeyHashMine(address.getHash160());
    }

    public DeterministicKey getKeyPairForAddress(Address address) {
        DeterministicKey deterministicKey = pivWallet.getActiveKeyChain().findKeyFromPubHash(address.getHash160());
        return deterministicKey;
    }

    /**
     * If the wallet doesn't contain any private key.
     * @return
     */
    public boolean isWatchOnly(){
        return pivWallet.isWatching();
    }

    public long getEarliestKeyCreationTime() {
        return pivWallet.getEarliestKeyCreationTime();
    }

    public DeterministicKey getWatchingPubKey() {
        return pivWallet.getWatchingKey();
    }

    //////////////////////////// Zerocoin /////////////////////////////////////////////

    public Coin getZpivAvailableBalance() {
        return zWallet.getBalance(Wallet.BalanceType.AVAILABLE);
    }

    public Coin getZpivUnspensableBalance() {
        return zWallet.getUnspendableBalance();
    }

    /**
     * TODO: Add fee here...
     * @param amount
     * @return
     * @throws InsufficientMoneyException
     */
    public SendRequest createMintRequest(Coin amount) throws InsufficientMoneyException {
        Transaction tx = zWallet.createMint(amount);
        SendRequest request = SendRequest.forTx(tx);
        pivWallet.completeTx(request);
        return request;
    }

    public SendRequest createSpendRequest(Address to, Coin amount) throws InsufficientMoneyException {
        Transaction tx = zWallet.createSpend(amount);
        tx.addOutput(amount, to);
        return SendRequest.forTx(tx);
    }

    public void spendZpiv(SendRequest request, PeerGroup peerGroup, ExecutorService executor) throws InsufficientMoneyException{
        zWallet.completeSendRequestAndWaitSync(request,peerGroup,executor);
    }


    //////////////////////////// Testing //////////////////////////////////////

    public Wallet getPivWallet(){
        return pivWallet;
    }

    public Wallet getZpivWallet(){
        return zWallet.getWallet();
    }

    public ZWallet getZPivWallet() {
        return zWallet;
    }

    public Date getLastBlockSeenTime() {
        return pivWallet.getLastBlockSeenTime();
    }

    public Sha256Hash getLastBlockSeenHash() {
        return pivWallet.getLastBlockSeenHash();
    }

    /** Saves the wallet first to the given temp file, then renames to the dest file. */
    public void saveToFile(File temp, File destFile) throws IOException {
        FileOutputStream stream = null;
        if (!lock.isHeldByCurrentThread() && lock.isLocked()) {
            log.info("MultiWallet, lock is held? " + lock.toString());
        }
        lock.lock();
        log.info("MultiWallet saveToFile lock");
        try {
            stream = new FileOutputStream(temp);
            saveToFileStream(stream);
            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            stream.flush();
            stream.getFD().sync();
            stream.close();
            stream = null;
            if (Utils.isWindows()) {
                // Work around an issue on Windows whereby you can't rename over existing files.
                File canonical = destFile.getCanonicalFile();
                if (canonical.exists() && !canonical.delete())
                    throw new IOException("Failed to delete canonical wallet file for replacement with autosave");
                if (temp.renameTo(canonical))
                    return;  // else fall through.
                throw new IOException("Failed to rename " + temp + " to " + canonical);
            } else if (!temp.renameTo(destFile)) {
                throw new IOException("Failed to rename " + temp + " to " + destFile);
            }
        } catch (RuntimeException e) {
            log.error("Failed whilst saving wallet", e);
            throw e;
        } catch (Exception e){
            log.error("Failed whilst saving wallet", e);
            throw e;
        }finally {
            lock.unlock();
            log.info("MultiWallet saveToFile unlock");
            if (stream != null) {
                stream.close();
            }
            if (temp.exists()) {
                log.warn("Temp file still exists after failed save.");
            }
        }
    }

    /**
     * Uses protobuf serialization to save the wallet to the given file. To learn more about this file format, see
     * {@link WalletProtobufSerializer}. Writes out first to a temporary file in the same directory and then renames
     * once written.
     */
    public void saveToFile(File f) throws IOException {
        File directory = f.getAbsoluteFile().getParentFile();
        File temp = File.createTempFile("wallet", null, directory);
        saveToFile(temp, f);
    }

    /**
     * Uses protobuf serialization to save the wallet to the given file stream. To learn more about this file format, see
     * {@link WalletProtobufSerializer}.
     */
    public void saveToFileStream(OutputStream f) throws IOException {
        if (!lock.isHeldByCurrentThread()) {
            log.info("MultiWallet, saveToFileStream lock is held? " + lock.toString());
        }
        lock.lock();
        log.info("MultiWallet saveToFileStream lock");
        try {
            new WalletProtobufSerializer().writeMultiWallet(this, f);
        } finally {
            lock.unlock();
            log.info("MultiWallet saveToFileStream unlock");
        }
    }

    /**
     * <p>Sets up the wallet to auto-save itself to the given file, using temp files with atomic renames to ensure
     * consistency. After connecting to a file, you no longer need to save the wallet manually, it will do it
     * whenever necessary. Protocol buffer serialization will be used.</p>
     *
     * <p>If delayTime is set, a background thread will be created and the wallet will only be saved to
     * disk every so many time units. If no changes have occurred for the given time period, nothing will be written.
     * In this way disk IO can be rate limited. It's a good idea to set this as otherwise the wallet can change very
     * frequently, eg if there are a lot of transactions in it or during block sync, and there will be a lot of redundant
     * writes. Note that when a new key is added, that always results in an immediate save regardless of
     * delayTime. <b>You should still save the wallet manually when your program is about to shut down as the JVM
     * will not wait for the background thread.</b></p>
     *
     * <p>An event listener can be provided. If a delay >0 was specified, it will be called on a background thread
     * with the wallet locked when an auto-save occurs. If delay is zero or you do something that always triggers
     * an immediate save, like adding a key, the event listener will be invoked on the calling threads.</p>
     *
     * @param f The destination file to save to.
     * @param delayTime How many time units to wait until saving the wallet on a background thread.
     * @param timeUnit the unit of measurement for delayTime.
     * @param eventListener callback to be informed when the auto-save thread does things, or null
     */
    public MultiWalletFiles autosaveToFile(File f, long delayTime, TimeUnit timeUnit,
                                      @Nullable Listener eventListener) {
        lock.lock();
        try {
            checkState(vFileManager == null, "Already auto saving this wallet.");
            MultiWalletFiles manager = new MultiWalletFiles(this, f, delayTime, timeUnit);
            if (eventListener != null)
                manager.setListener(eventListener);
            vFileManager = manager;

            // Add this to both wallets
            pivWallet.autosaveToFile(vFileManager, eventListener);
            zWallet.autosaveToFile(vFileManager, eventListener);
            return manager;
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Disables auto-saving, after it had been enabled with
     * {@link Wallet#autosaveToFile(java.io.File, long, java.util.concurrent.TimeUnit, host.furszy.zerocoinj.wallet.files.Listener)}
     * before. This method blocks until finished.
     * </p>
     */
    public void shutdownAutosaveAndWait() {
        lock.lock();
        try {
            WalletFilesInterface files = vFileManager;
            vFileManager = null;
            checkState(files != null, "Auto saving not enabled.");
            files.shutdownAndWait();
        } finally {
            lock.unlock();
        }
    }

    public DeterministicSeed getSeed() {
        return seed;
    }

    /**
     * Convenience wrapper around {@link Wallet#encrypt(org.pivxj.crypto.KeyCrypter,
     * org.spongycastle.crypto.params.KeyParameter)} which uses the default Scrypt key derivation algorithm and
     * parameters to derive a key from the given password.
     */
    public void encrypt(CharSequence password) {
        pivWallet.encrypt(password);
        getZpivWallet().encrypt(password);
    }

    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        pivWallet.encrypt(keyCrypter,aesKey);
        getZpivWallet().encrypt(keyCrypter,aesKey);
    }

    public void decrypt(CharSequence password) {
        pivWallet.decrypt(password);
        getZpivWallet().decrypt(password);
    }

    public void decrypt(KeyParameter keyParameter) {
        pivWallet.decrypt(keyParameter);
        getZpivWallet().decrypt(keyParameter);
    }

    public boolean isEncrypted(){
        return pivWallet.isEncrypted() && zWallet.getWallet().isEncrypted();
    }
}
