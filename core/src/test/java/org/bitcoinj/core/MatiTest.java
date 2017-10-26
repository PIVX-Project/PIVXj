package org.bitcoinj.core;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bitcoinj.core.listeners.*;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.LevelDBBlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import sun.applet.Main;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by furszy on 6/23/17.
 */
public class MatiTest {

    @Test
    public void connectTestnetPeer(){
        NetworkParameters networkParameters = TestNet3Params.get();
        Context context = new Context(networkParameters);
        VersionMessage versionMessage = new VersionMessage(networkParameters, 0);
        versionMessage.relayTxesBeforeFilter=false;
        Peer peer = new Peer(networkParameters,versionMessage,new PeerAddress("localhost",51472),null);
        peer.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                System.out.println("left: "+blocksLeft);
            }
        });
        peer.addConnectedEventListener(new PeerConnectedEventListener() {
            @Override
            public void onPeerConnected(Peer peer, int peerCount) {
                System.out.println("connected");
            }
        });
        peer.startBlockChainDownload();
    }

    @Test
    public void connectPeer(){
        NetworkParameters networkParameters = MainNetParams.get();
        Context context = new Context(networkParameters);
        VersionMessage versionMessage = new VersionMessage(networkParameters, 0);
        versionMessage.relayTxesBeforeFilter=false;
        Peer peer = new Peer(networkParameters,versionMessage,new PeerAddress("localhost",51472),null);
        peer.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                System.out.println("left: "+blocksLeft);
            }
        });
        peer.addConnectedEventListener(new PeerConnectedEventListener() {
            @Override
            public void onPeerConnected(Peer peer, int peerCount) {
                System.out.println("connected");
            }
        });
    }

    @Test
    public void accountDetailsTest(){

        NetworkParameters networkParameters = MainNetParams.get();
        String words = "bronze crop radio roof bright tomorrow jump hover drift habit title pistol shine piano truly afraid atom salon easily fork toward action ancient clerk";
        List<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(words.split(" ")));
        DeterministicSeed seed = new DeterministicSeed(list, null, "", System.currentTimeMillis());
        Wallet wallet = Wallet.fromSeed(
                networkParameters,
                seed,
                DeterministicKeyChain.KeyChainType.BIP44_PIVX_ONLY
        );

        System.out.println("Wallet version: "+wallet.getVersion());

        DeterministicKeyChain deterministicKeyChain = wallet.getActiveKeyChain();
        DeterministicKey account = deterministicKeyChain.getWatchingKey();
        System.out.println("path: "+account.getPath());
        System.out.println("xpub: "+account.serializePubB58(networkParameters));
        System.out.println("xpriv: "+account.serializePrivB58(networkParameters));

        List<String> mnemonic = deterministicKeyChain.getSeed().getMnemonicCode();
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : mnemonic) {
            stringBuilder.append(s).append(" ");
        }
        System.out.println("words: "+stringBuilder.toString());

        System.out.println("printing some addresses..");
        DeterministicKey addressKey1 = wallet.freshReceiveKey();
        System.out.println("address path: "+addressKey1.getPath()+" "+addressKey1.toAddress(networkParameters).toBase58());
        DeterministicKey addressKey2 = wallet.freshReceiveKey();
        System.out.println("address path: "+addressKey2.getPath()+" "+addressKey2.toAddress(networkParameters).toBase58());
        DeterministicKey addressKey3 = wallet.freshReceiveKey();
        System.out.println("address path: "+addressKey3.getPath()+" "+addressKey3.toAddress(networkParameters).toBase58());
        DeterministicKey addressKey4 = wallet.freshReceiveKey();
        System.out.println("address path: "+addressKey4.getPath()+" "+addressKey4.toAddress(networkParameters).toBase58());

        Protos.Wallet protoWallet = new WalletProtobufSerializer().walletToProto(wallet);
        System.out.println("Proto wallet version: " + protoWallet.getVersion());

        InputStream is = null;
        try {
            is = new ByteArrayInputStream(protoWallet.toByteArray());
            final Wallet wallet2 = new WalletProtobufSerializer().readWallet(is, true, null);

            System.out.println("Restore wallet working");

        } catch (UnreadableWalletException e) {
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public Wallet restore(NetworkParameters networkParameters) throws IOException {
        String filename = "1.01_pivx-wallet-backup_org.pivx.production-2017-07-26 (2)";
        String password = "12345678";


        File file = new File(filename);
        final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
        final StringBuilder cipherText = new StringBuilder();
        copy(cipherIn, cipherText, 10000000);
        cipherIn.close();

        final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
        final InputStream is = new ByteArrayInputStream(plainText);

        Wallet wallet = restoreWalletFromProtobufOrBase58(is, networkParameters, 10000000);
        return wallet;
    }

    @Test
    public void restoreFromBackup() throws IOException {
        NetworkParameters networkParameters = MainNetParams.get();

        Wallet wallet = restore(networkParameters);

        String seed = wallet.getKeyChainSeed().toHexString();
        String watchingKey = wallet.getWatchingKey().serializePubB58(networkParameters);
        System.out.println("seed: "+seed);
        System.out.println("watching key: "+watchingKey);
        System.out.println("mnemonic code: "+Arrays.toString(wallet.getActiveKeyChain().getMnemonicCode().toArray()));


        System.out.println("#############################################");

        List<String> mnemonicTwo = wallet.getActiveKeyChain().getMnemonicCode();
        DeterministicSeed seedBase = new DeterministicSeed(mnemonicTwo, null, "", 0);
        Wallet wallet2 = Wallet.fromSeed(
                networkParameters,
                seedBase,
                DeterministicKeyChain.KeyChainType.BIP32
        );

        String seed2 = wallet2.getKeyChainSeed().toHexString();
        String watchingKey2 = wallet2.getWatchingKey().serializePubB58(networkParameters);
        System.out.println("seed2: "+seed2);
        System.out.println("watching2 key: "+watchingKey2);
        System.out.println("mnemonic2 code: "+Arrays.toString(wallet2.getActiveKeyChain().getMnemonicCode().toArray()));

        assert seed.equals(seed2) : "seeds are not equals";
        assert watchingKey2.equals(watchingKey) : "key are not the same";
    }

    @Test
    public void restoreFromMnemonic(){

        NetworkParameters networkParameters = MainNetParams.get();

        String mnemonic = "predict, will, purchase, figure, actor, laptop, marine, blind, upper, valve, carry, coyote, side, that, home, purchase, tide, mixture, hospital, front, fun, sniff, draft, execute";
        mnemonic = mnemonic.trim().replace(",","");
        List<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(mnemonic.split(" ")));
        DeterministicSeed seed = new DeterministicSeed(list, null, "", 0);
        Wallet wallet = Wallet.fromSeed(
                networkParameters,
                seed,
                DeterministicKeyChain.KeyChainType.BIP32
        );

        DeterministicKey xpubKey = wallet.getWatchingKey();
        String xpub = xpubKey.serializePubB58(networkParameters);
        System.out.println("xpub: "+xpub);
        System.out.println("key path: "+xpubKey.getPathAsString());
        System.out.println("chaincode: "+ Hex.toHexString(xpubKey.getChainCode()));
        System.out.println("first address: "+wallet.freshReceiveAddress().toBase58());
    }

    @Test
    public void watchOnlyTest(){

        NetworkParameters networkParameters = MainNetParams.get();

        String words = "bronze crop radio roof bright tomorrow jump hover drift habit title pistol shine piano truly afraid atom salon easily fork toward action ancient clerk";
        List<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(words.split(" ")));
        DeterministicSeed seed = new DeterministicSeed(list, null, "", System.currentTimeMillis());
        Wallet wallet = Wallet.fromSeed(
                networkParameters,
                seed,
                DeterministicKeyChain.KeyChainType.BIP44_PIVX_ONLY
        );

        DeterministicKey xpubKey = wallet.getWatchingKey();
        String xpub = xpubKey.serializePubB58(networkParameters);
        System.out.println("xpub: "+xpub);
        System.out.println("key path: "+xpubKey.getPathAsString());
        System.out.println("chaincode: "+ Hex.toHexString(xpubKey.getChainCode()));
        System.out.println("first address: "+wallet.freshReceiveAddress().toBase58());

        Wallet watchingWallet = Wallet.fromWatchingKeyB58(networkParameters,xpub,0, DeterministicKeyChain.KeyChainType.BIP44_PIVX_ONLY);
        DeterministicKey xpubKey2 = watchingWallet.getWatchingKey();
        System.out.println("Watching key:  "+xpubKey2.serializePubB58(networkParameters));
        System.out.println("key path: "+xpubKey2.getPathAsString());
        System.out.println("Watching first address: "+watchingWallet.freshReceiveAddress().toBase58());


        for (int i = 0; i < 10; i++) {
            assert watchingWallet.freshReceiveAddress().toBase58().equals(wallet.freshReceiveAddress().toBase58()):"address doesn't match";
        }

    }

    @Test
    public void test() throws BlockStoreException {
        NetworkParameters networkParameters = TestNet3Params.get();
        Context context = Context.getOrCreate(networkParameters);
        String hexBlock = "04000000582c73cb7d33b1a2b2269d6b00ca1f6cfa32a36bcdf7c7f85e3a3da30ed421c51ed3c5a492ad97faf6d12b806a089c26215c08bdbaa599daad45dc1908b0d4d5af4c83597093001b0000000075ff318975ff3189f97be5115f5976571afd276fa0c37215605ddbd61d37e2f40201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff06037613030101ffffffff010000000000000000000000000001000000017a35864e454019b1192a5b7a58355edeb264923bcb8461313c677387258ecc67010000004847304402206a3c6a655e858d74b566885e72f7b7f91258f16c944b4f6c74df10f3fc0ec7860220350426a3b1318958402756b483ee703c590b034d61841a79eb56e59244550f8b01ffffffff04000000000000000000409277e45d000000232102af62f4bc029ec391b71074dd222c771f0ad17b38256c901af13adb0075cd8d74ac78f089005d000000232102af62f4bc029ec391b71074dd222c771f0ad17b38256c901af13adb0075cd8d74ac40defce3000000001976a9142f28328985dfa52fa9213a6ead9c2583e2f7251588ac00000000473045022100e077082775a52855eaa6e11464b38993e1be0096d41fbff0497187d60a07b007022070300f34df7cd43e1cfb895c5179a281c9505396c8c769857ad8f3783efd4201";
        byte[] payload = Hex.decode(hexBlock);


        BitcoinSerializer serializer = new BitcoinSerializer(networkParameters,true);
        Block block = serializer.makeBlock(payload);

        System.out.println(block);


        LevelDBBlockStore levelDBBlockStore = new LevelDBBlockStore(context,new File("blockstore.dat"));

        //StoredBlock storedBlock0 = new StoredBlock(block,block.getWork(),201590);

        //levelDBBlockStore.put(storedBlock0);

        StoredBlock storedBlock = levelDBBlockStore.get(block.getHash());

        System.out.println("##############");
        System.out.println(storedBlock);

    }

    /**
     * Minimum entropy
     */
    private static final int SEED_ENTROPY_EXTRA = 256;
    private static final int ENTROPY_SIZE_DEBUG = -1;

    public static List<String> generateMnemonic(int entropyBitsSize){
        byte[] entropy;
        if (ENTROPY_SIZE_DEBUG > 0){
            entropy = new byte[ENTROPY_SIZE_DEBUG];
        }else {
            entropy = new byte[entropyBitsSize / 8];
        }
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(entropy);
        return bytesToMnemonic(entropy);
    }

    public static List<String> bytesToMnemonic(byte[] bytes){
        List<String> mnemonic;
        try{
            mnemonic = MnemonicCode.INSTANCE.toMnemonic(bytes);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e); // should not happen, we have 16 bytes of entropy
        }
        return mnemonic;
    }


    @Test
    public void connect() throws BlockStoreException, IOException, InsufficientMoneyException {

        BriefLogFormatter.init();

        NetworkParameters networkParameters = MainNetParams.get();
        Context context = new Context(networkParameters);
        //File dir = new File("dir");
        //dir.mkdir();
        File walletFile = new File("wallet2.dat");
        Wallet wallet;
        if (!walletFile.exists()){
            walletFile.createNewFile();
            wallet = restore(networkParameters);
        }else {
            try {
                wallet = Wallet.loadFromFile(walletFile);
            } catch (UnreadableWalletException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
        wallet.saveToFile(walletFile);
        wallet.autosaveToFile(walletFile, 20, TimeUnit.SECONDS, new WalletFiles.Listener() {
            @Override
            public void onBeforeAutoSave(File tempFile) {

            }

            @Override
            public void onAfterAutoSave(File newlySavedFile) {

            }
        });
        BlockStore spvBlockStore = new LevelDBBlockStore(context,new File("blockstore.dat")); //new LevelDBBlockStore(context,new File("blockstore.dat"));
        //InputStream inputStream = new FileInputStream(new File("checkpoints"));
        //CheckpointManager.checkpoint(networkParameters, inputStream, spvBlockStore, System.currentTimeMillis());
        final BlockChain blockChain = new BlockChain(context,wallet,spvBlockStore);
        PeerGroup peerGroup = new PeerGroup(networkParameters,blockChain);
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
        peerGroup.setDownloadTxDependencies(0);
        peerGroup.addWallet(wallet);
        peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
            }
        });
        peerGroup.setMaxPeersToDiscoverCount(1);
        peerGroup.startBlockChainDownload(new AbstractPeerDataEventListener(){
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                //System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
            }
        });
        peerGroup.startAsync();
        peerGroup.downloadBlockChain();

        System.out.println(wallet.toString());
        System.out.println("blockchain height: "+blockChain.chainHead.getHeight());

        while (true){

        }

    }

    public Transaction buildTx(Wallet wallet,NetworkParameters networkParameters,Address to,Coin amount) throws InsufficientMoneyException {
        SendRequest sendRequest = SendRequest.to(to,amount);
        sendRequest.useInstantSend = true;
        wallet.completeTx(sendRequest);
        return sendRequest.tx;
    }

    @Test
    public void decodeTx(){
        Context context = new Context(MainNetParams.get());
        byte[] txBytes = Utils.HEX.decode("01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff5e04ffff001d01044c55552e532e204e657773202620576f726c64205265706f7274204a616e203238203230313620576974682048697320416273656e63652c205472756d7020446f6d696e6174657320416e6f7468657220446562617465ffffffff0100ba1dd205000000434104c10e83b2703ccf322f7dbd62dd5855ac7c10bd055814ce121ba32607d573b8810c02c0582aed05b4deb9c4b77b26d92428c61256cd42774babea0a073b2ed0c9ac00000000");
        Transaction tx = new Transaction(MainNetParams.get(),txBytes);

        TransactionInput transactionInput = tx.getInput(0);
        System.out.println("Input: \nsequence "+transactionInput.getSequenceNumber());
        System.out.println(Hex.toHexString(transactionInput.getScriptBytes()));
        System.out.println("\n\n");
        TransactionOutput transactionOutput = tx.getOutput(0);
        System.out.println("Output:");
        System.out.println(transactionOutput.getScriptPubKey());

        byte[] pubKey = Utils.HEX.decode("04c10e83b2703ccf322f7dbd62dd5855ac7c10bd055814ce121ba32607d573b8810c02c0582aed05b4deb9c4b77b26d92428c61256cd42774babea0a073b2ed0c9");
        ScriptBuilder scriptBuilder = new ScriptBuilder().addChunk(new ScriptChunk(65,pubKey)).op(ScriptOpCodes.OP_CHECKSIG);
        Script script = scriptBuilder.build();
        System.out.println(script);

        //TransactionOutput transactionOutput1 = new TransactionOutput(MainNetParams.get(),tx,)
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void chmod(final File path, final int mode) {
        try {
            final Class fileUtils = Class.forName("android.os.FileUtils");
            final Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
            setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
        }
        catch (final Exception x) {
            System.out.println("problem using undocumented chmod api "+ x);
        }
    }

    public static final long copy(final Reader reader, final StringBuilder builder) throws IOException
    {
        return copy(reader, builder, 0);
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

    public static Wallet restoreWalletFromProtobufOrBase58(final InputStream is, final NetworkParameters expectedNetworkParameters,long backupMaxChars) throws IOException
    {
        is.mark((int) backupMaxChars);

        try
        {
            return restoreWalletFromProtobuf(is, expectedNetworkParameters);
        }
        catch (final IOException x)
        {
            try
            {
                is.reset();
                return restorePrivateKeysFromBase58(is, expectedNetworkParameters,backupMaxChars);
            }
            catch (final IOException x2)
            {
                throw new IOException("cannot read protobuf (" + x.getMessage() + ") or base58 (" + x2.getMessage() + ")", x);
            }
        }
    }

    public static Wallet restoreWalletFromProtobuf(final InputStream is, final NetworkParameters expectedNetworkParameters) throws IOException {
        try {
            final Wallet wallet = new WalletProtobufSerializer().readWallet(is, true, null);
            if (!wallet.getParams().equals(expectedNetworkParameters))
                throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
            if (!wallet.isConsistent())
                throw new IOException("inconsistent wallet backup");

            return wallet;
        } catch (final UnreadableWalletException x) {
            throw new IOException("unreadable wallet", x);
        }
    }

    public static Wallet restorePrivateKeysFromBase58(final InputStream is, final NetworkParameters expectedNetworkParameters,long backupMaxChars) throws IOException
    {
        final BufferedReader keyReader = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));

        // create non-HD wallet
        final KeyChainGroup group = new KeyChainGroup(expectedNetworkParameters);
        group.importKeys(readKeys(keyReader, expectedNetworkParameters,backupMaxChars));
        return new Wallet(expectedNetworkParameters, group);
    }

    public static List<ECKey> readKeys(final BufferedReader in, final NetworkParameters expectedNetworkParameters,long backupMaxChars) throws IOException
    {
        try
        {
            final DateFormat format = Iso8601Format.newDateTimeFormatT();

            final List<ECKey> keys = new LinkedList<ECKey>();

            long charCount = 0;
            while (true)
            {
                final String line = in.readLine();
                if (line == null)
                    break; // eof
                charCount += line.length();
                if (charCount > backupMaxChars)
                    throw new IOException("read more than the limit of " + backupMaxChars + " characters");
                if (line.trim().isEmpty() || line.charAt(0) == '#')
                    continue; // skip comment

                final String[] parts = line.split(" ");

                final ECKey key = DumpedPrivateKey.fromBase58(expectedNetworkParameters, parts[0]).getKey();
                key.setCreationTimeSeconds(parts.length >= 2 ? format.parse(parts[1]).getTime() / TimeUnit.SECONDS.toMillis(1) : 0);  //DateUtils.SECOND_IN_MILLIS

                keys.add(key);
            }

            return keys;
        }
        catch (final AddressFormatException x)
        {
            throw new IOException("cannot read keys", x);
        }
        catch (final ParseException x)
        {
            throw new IOException("cannot read keys", x);
        }
    }

    public static class Iso8601Format extends SimpleDateFormat {
        private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

        private Iso8601Format(final String formatString) {
            super(formatString);

            setTimeZone(UTC);
        }

        public static DateFormat newTimeFormat() {
            return new Iso8601Format("HH:mm:ss");
        }

        public static DateFormat newDateFormat() {
            return new Iso8601Format("yyyy-MM-dd");
        }

        public static DateFormat newDateTimeFormat() {
            return new Iso8601Format("yyyy-MM-dd HH:mm:ss");
        }

        public static String formatDateTime(final Date date) {
            return newDateTimeFormat().format(date);
        }

        public static Date parseDateTime(final String source) throws ParseException {
            return newDateTimeFormat().parse(source);
        }

        public static DateFormat newDateTimeFormatT() {
            return new Iso8601Format("yyyy-MM-dd'T'HH:mm:ss'Z'");
        }

        public static String formatDateTimeT(final Date date) {
            return newDateTimeFormatT().format(date);
        }

        public static Date parseDateTimeT(final String source) throws ParseException {
            return newDateTimeFormatT().parse(source);
        }
    }
}
