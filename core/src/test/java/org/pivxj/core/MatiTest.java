package org.pivxj.core;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.hashengineering.crypto.Hash9;
import org.junit.Assert;
import org.pivxj.core.listeners.*;
import org.pivxj.crypto.*;
import org.pivxj.net.discovery.DnsDiscovery;
import org.pivxj.net.discovery.PeerDiscovery;
import org.pivxj.net.discovery.PeerDiscoveryException;
import org.pivxj.params.MainNetParams;
import org.pivxj.params.TestNet3Params;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptChunk;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.store.BlockStore;
import org.pivxj.store.BlockStoreException;
import org.pivxj.store.LevelDBBlockStore;
import org.pivxj.utils.BriefLogFormatter;
import org.pivxj.wallet.*;
import org.junit.Test;
import org.pivxj.zerocoin.GenWitMessage;
import org.pivxj.zerocoin.LibZerocoin;
import org.pivxj.zerocoin.PubcoinsMessage;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.util.encoders.Hex;
import sun.applet.Main;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;

/**
 * Created by furszy on 6/23/17.
 */
public class MatiTest {

    @Test
    public void connectAndGetPubcoinsdata(){

        final NetworkParameters params = MainNetParams.get();
        PeerGroup peerGroup = new PeerGroup(params);
        peerGroup.start();
        Peer peer = peerGroup.connectTo(new InetSocketAddress(7776));

        peer.addOnGetDataResponseEventListener(new OnGetDataResponseEventListener() {
            @Override
            public void onResponseReceived(PubcoinsMessage pubcoinsMessage) {
                System.out.println("size: " + pubcoinsMessage.getList().size());
            }
        });

        peer.addConnectedEventListener(new PeerConnectedEventListener() {
            @Override
            public void onPeerConnected(Peer peer, int i) {
                System.out.println("Peer connected, seinding getdata");
                //GetDataMessage getDataMessage = new GetDataMessage(params);
                //getDataMessage.addPubcoinsBlockHash(
                //        Sha256Hash.wrap("ed56ff1e1abd88e5dce0b52f8951970c86d94ec526c0007bf2bad4ca10ba3ac4")
                //);
                GenWitMessage genWitMessage = new GenWitMessage(
                        params,
                        1245475,
                        LibZerocoin.CoinDenomination.ZQ_ONE,
                        1, 0.001, (long) (Math.random() * Long.MAX_VALUE)
                );
                BigInteger bnValue = new BigInteger("49562934426197246361341302937888166535958820127560563046688232084681663408334057925442609971010765290980273589195421234926681824529604731044735511851429332795902473030281542943546216458161997423614193390428379820294894150716196038837775465627137407450997323837703190642031432725966841831797669052225113685451");
                genWitMessage.insert(bnValue);
                peer.sendMessage(genWitMessage);
            }
        });

        while (true){
            try {
                TimeUnit.SECONDS.sleep(90);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            break;
        }
    }

    @Test
    public void hash() throws UnsupportedEncodingException {
        byte[] bytes = "add".getBytes("UTF-8");
        byte[] res = Hash9.digest(bytes);
        System.out.println(Hex.toHexString(res));
    }

    @Test
    public void connectTestnetPeer(){
        NetworkParameters networkParameters = TestNet3Params.get();
        Context context = new Context(networkParameters);
        VersionMessage versionMessage = new VersionMessage(networkParameters, 0);
        versionMessage.relayTxesBeforeFilter=false;
        Peer peer = new Peer(networkParameters,versionMessage,new PeerAddress("warrows.fr",51474),null);
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

    @Test
    public void publicKeyToAddress(){

        NetworkParameters params = MainNetParams.get();
        String pubKey = "0244c064ee0b6bd3763736ad3ba60bf1d5a9c72d26b57ee13f4658476d6c6e1bec";
        DeterministicKey key = DeterministicKey.deserialize(params,Hex.decode(pubKey));
        //Address address = Address.fromP2SHHash(params,);

        System.out.printf("address: "+key.toAddress(params).toBase58());

    }

    public Wallet restore(NetworkParameters networkParameters) throws IOException {
        String filename = "2.0.32_pivx-wallet-backup_org.pivx.production-2017-11-20";
        //"1.01_pivx-wallet-backup_org.pivx.production-2017-07-26 (2)";
        String password = "123";//"12345678";


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
                DeterministicKeyChain.KeyChainType.BIP44_PIVX_ONLY
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
    public void syncWatchingKey() throws IOException, BlockStoreException {
        NetworkParameters params = TestNet3Params.get();
        //String pubKey = "ToEA6nz3Vu6K2cvMHSRBTTipz7iyMt1fs9tFcZPDm4usjo1VrHQabxkBVEyvDo2HtxNmABF6wFxKkYmgCkeMmdtbTCWvHeqQLxdjsX1ceL72zMo";
        //Wallet watchingWallet = Wallet.fromWatchingKeyB58(params,pubKey,0, DeterministicKeyChain.KeyChainType.BIP44_PIVX_ONLY);
        //DeterministicKey xpubKey2 = watchingWallet.getWatchingKey();


        Wallet watchingWallet = new Wallet(params);
        BriefLogFormatter.init();

        FileHandler fh = new FileHandler("MyLogFile.log");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("");
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);

        Context context = new Context(params);
        //File dir = new File("dir");
        //dir.mkdir();
        File walletFile = new File("wallet_testnet_3.dat");
        watchingWallet.saveToFile(walletFile);
        watchingWallet.autosaveToFile(walletFile, 20, TimeUnit.SECONDS, new WalletFiles.Listener() {
            @Override
            public void onBeforeAutoSave(File tempFile) {

            }

            @Override
            public void onAfterAutoSave(File newlySavedFile) {

            }
        });
        BlockStore spvBlockStore = new LevelDBBlockStore(context,new File("watching_blockstore_2.dat")); //new LevelDBBlockStore(context,new File("blockstore.dat"));
        //InputStream inputStream = new FileInputStream(new File("checkpoints"));
        //CheckpointManager.checkpoint(networkParameters, inputStream, spvBlockStore, System.currentTimeMillis());
        final BlockChain blockChain = new BlockChain(context,watchingWallet,spvBlockStore);
        PeerGroup peerGroup = new PeerGroup(params,blockChain);
        //peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
        peerGroup.addPeerDiscovery(new PeerDiscovery() {
            @Override
            public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                return new InetSocketAddress[]{
                        //new InetSocketAddress("202.5.21.31",51474),
                        new InetSocketAddress("warrows.fr",51474)
                        //new InetSocketAddress("localhost",51474)
                        //new InetSocketAddress("88.198.192.110",51474)
                };
            }

            @Override
            public void shutdown() {

            }
        });
        peerGroup.setDownloadTxDependencies(0);
        peerGroup.addWallet(watchingWallet);
        peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                //System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
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

        System.out.println(watchingWallet.toString());
        System.out.println("blockchain height: "+blockChain.chainHead.getHeight());
        System.out.println();

        // #########################

        String password = "mati";
        // Encrypt
        watchingWallet.encrypt(password);

        Assert.assertTrue("Wallet not encrypted", watchingWallet.isEncrypted());

        logger.info("wallet encrypted ");

        // decrypt
        watchingWallet.decrypt(password);

        logger.info("Wallet decrypted");
        Assert.assertTrue("Wallet not decrypted" , !watchingWallet.isEncrypted());

        while (true){

        }


    }

    @Test
    public void testEncryption(){

        NetworkParameters params = TestNet3Params.get();
        Context.getOrCreate(params);

        Wallet wallet = new Wallet(params);



    }

    @Test
    public void test() throws BlockStoreException {
        NetworkParameters networkParameters = TestNet3Params.get();
        Context context = Context.getOrCreate(networkParameters);
        String hexBlock = "04000000582c73cb7d33b1a2b2269d6b00ca1f6cfa32a36bcdf7c7f85e3a3da30ed421c51ed3c5a492ad97faf6d12b806a089c26215c08bdbaa599daad45dc1908b0d4d5af4c83597093001b0000000075ff318975ff3189f97be5115f5976571afd276fa0c37215605ddbd61d37e2f40201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff06037613030101ffffffff010000000000000000000000000001000000017a35864e454019b1192a5b7a58355edeb264923bcb8461313c677387258ecc67010000004847304402206a3c6a655e858d74b566885e72f7b7f91258f16c944b4f6c74df10f3fc0ec7860220350426a3b1318958402756b483ee703c590b034d61841a79eb56e59244550f8b01ffffffff04000000000000000000409277e45d000000232102af62f4bc029ec391b71074dd222c771f0ad17b38256c901af13adb0075cd8d74ac78f089005d000000232102af62f4bc029ec391b71074dd222c771f0ad17b38256c901af13adb0075cd8d74ac40defce3000000001976a9142f28328985dfa52fa9213a6ead9c2583e2f7251588ac00000000473045022100e077082775a52855eaa6e11464b38993e1be0096d41fbff0497187d60a07b007022070300f34df7cd43e1cfb895c5179a281c9505396c8c769857ad8f3783efd4201";
        byte[] payload = Hex.decode(hexBlock);


        BitcoinSerializer serializer = new BitcoinSerializer(networkParameters,false);
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
    public void zerocoinTransactionTest(){

        NetworkParameters params = MainNetParams.get();
        String zerocoinTxHex = "473044022018d79ecf0de1c1b6a6b2869d574c0c4f1517613e48bdd7661e632c48c0cecd4b02203c840273520b8cc15792202a288b964fc3b5986fceb87d8f39b7c82a73987c5b012102bbc42e2dcfe0aa8e9341254a6dd138915727dab4e1c89a3a196a161c2fbe49c6";
        byte[] btes = Hex.decode(zerocoinTxHex);
        byte[] hash = Hex.decode("b45e049f36e3f0f39f0b30f30f5f75a68632b92e4b4242769c78b82a55a24322");
        BitcoinSerializer serializer = new BitcoinSerializer(params,false);
        Transaction transaction = serializer.makeTransaction(btes,0,btes.length,hash);

        System.out.println(transaction.toString());
    }

    @Test
    public void testConversion(){

        byte[] payload = Hex.decode("ffffff");
        long res = Utils.readUint32(payload,0);
        System.out.println("Res: "+res);
    }

    @Test
    public void parseTxHexTest(){




    }

    @Test
    public void getZerocoinSpendTx(){
        BriefLogFormatter.init();

        final NetworkParameters params = MainNetParams.get();
        Context.getOrCreate(params);


        PeerGroup peerGroup = new PeerGroup(params);
        peerGroup.addConnectedEventListener(new PeerConnectedEventListener() {
            @Override
            public void onPeerConnected(Peer peer, int peerCount) {
                System.out.println("connected");
                // request the tx
                Sha256Hash txHash = Sha256Hash.wrap("50f7c828374f73649730740574422786f52d88450d9c39af09decaba24644922");
                InventoryItem item = new InventoryItem(InventoryItem.Type.Transaction,txHash);

                GetDataMessage getdata = new GetDataMessage(params);
                getdata.addItem(item);
                peer.sendMessage(getdata);
            }
        });

        peerGroup.addGetDataEventListener(new GetDataEventListener() {
            @Nullable
            @Override
            public List<Message> getData(Peer peer, GetDataMessage m) {
                System.out.println(m);
                return null;
            }
        });
        peerGroup.start();
        Peer peer = peerGroup.connectToLocalHost();

        while (true){

        }

    }


    public Wallet createWallet(NetworkParameters networkParameters,File walletFile) throws IOException {
        Wallet wallet;
        if (!walletFile.exists()){
            wallet = new Wallet(networkParameters);
        }else {
            try {
                wallet = Wallet.loadFromFile(walletFile);
            } catch (UnreadableWalletException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
        return wallet;
    }

    @Test
    public void parseZerocoinSpend(){

        try {

            NetworkParameters params = MainNetParams.get();
            Context.getOrCreate(params);

            byte[] encoded = Files.readAllBytes(Paths.get("zc_transaction.txt"));
            String text = new String(encoded, "UTF-8");
            System.out.println(text);
            byte[] zcTransaction = Hex.decode(text);
            // hash
            byte[] transactionHash = Sha256Hash.wrap("50f7c828374f73649730740574422786f52d88450d9c39af09decaba24644922").getReversedBytes();
            // Serialized
            BitcoinSerializer bitcoinSerializer = new BitcoinSerializer(params,true);
            Transaction transaction = bitcoinSerializer.makeTransaction(zcTransaction,0,zcTransaction.length,transactionHash);
            System.out.println(transaction.toString());

            assert transaction.getHashAsString().equals("50f7c828374f73649730740574422786f52d88450d9c39af09decaba24644922") : "Hash is not the same";

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    public void newEcKeyWallet() throws IOException {
        NetworkParameters params = MainNetParams.get();
        Context.getOrCreate(params);
        File walletFile = new File("wallet_new_eckey.dat");
        Wallet wallet = createWallet(params,walletFile);
        wallet.saveToFile(walletFile);
        wallet.autosaveToFile(walletFile, 20, TimeUnit.SECONDS, new WalletFiles.Listener() {
            @Override
            public void onBeforeAutoSave(File tempFile) {

            }

            @Override
            public void onAfterAutoSave(File newlySavedFile) {

            }
        });

        for (ECKey key : wallet.getIssuedReceiveKeys()) {
            System.out.println(key.toStringWithPrivate(params));
        }
    }

    @Test
    public void signMultiSigTransaction(){

        NetworkParameters params = MainNetParams.get();

        // First key, my key
        ECKey key1 = new ECKey(Hex.decode("57148e68f6e774204b8c0ba6921a9342d601413ca3cccc7453112f025410a7ad"),Hex.decode("023910b54c9ee1ab2570efc5ef25b93139cd81c25780b35ea2b9a088b5d3557ae7"));


        // Use the redeem script we have saved somewhere to start building the transaction
        String redeemScriptStr = "5221023910b54c9ee1ab2570efc5ef25b93139cd81c25780b35ea2b9a088b5d3557ae72103a93f64c2d1d2581826c85217eda6f97f45e899897e78bc385d5a7600e2d6252c52ae";
        Script redeemScript = new Script(Hex.decode(redeemScriptStr));

        // Start building the transaction by adding the unspent inputs we want to use
        // The data is taken from blockchain.info, and can be found here: https://blockchain.info/rawtx/ca1884b8f2e0ba88249a86ec5ddca04f937f12d4fac299af41a9b51643302077
        Transaction spendTx = new Transaction(params);
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        // The output that we want to redeem..
        scriptBuilder.data(new String("a91492363c63a03bdd9d532c81a2defb9f2dfdea121587").getBytes()); // Script of this output
        // tx hash
        String txHash = "f0bb04058d212955a3d479ea498e901fb004013c6ca863a1c07d820c1735a8ae";
        TransactionInput input = spendTx.addInput(Sha256Hash.wrap(txHash), 1, scriptBuilder.build());

        // Add outputs to the person receiving pivx
        Address receiverAddress = Address.fromBase58(params, "D9VYtFMfhcCdRtWrY3m7Dc17yeSrn9R94k");
        Coin charge = Coin.valueOf(190000); // 0.019 mPIV
        Script outputScript = ScriptBuilder.createOutputScript(receiverAddress);
        spendTx.addOutput(charge, outputScript);

        // Sign the first part of the transaction using private key #1
        Sha256Hash sighash = spendTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature ecdsaSignature = key1.sign(sighash);
        TransactionSignature transactionSignarture = new TransactionSignature(ecdsaSignature, Transaction.SigHash.ALL, false);

        // Create p2sh multisig input script
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(Arrays.asList(transactionSignarture), redeemScript);

        // Add the script signature to the input
        input.setScriptSig(inputScript);
        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));
    }

    @Test
    public void signWithSecondKey(){
        NetworkParameters params = MainNetParams.get();
        String txStr= "0100000001aea835170c827dc0a163a86c3c0104b01f908e49ea79d4a35529218d0504bbf0010000009200483045022100a11fac3165ef51adc12324d281384f7528effe83627340e3370e9c7edf2cb4d502205c24923f16d5c0a607bd137de31a1df552adcc265644fac5bab5079ed410796d01475221023910b54c9ee1ab2570efc5ef25b93139cd81c25780b35ea2b9a088b5d3557ae72103a93f64c2d1d2581826c85217eda6f97f45e899897e78bc385d5a7600e2d6252c52aeffffffff0130e60200000000001976a9142fbed053e4cd6a6d78a36da33cd797fc7572d53c88ac00000000";
        Transaction spendTx = new Transaction(params,Hex.decode(txStr));

        // Get the input chunks
        Script inputScript = spendTx.getInput(0).getScriptSig();
        List<ScriptChunk> scriptChunks = inputScript.getChunks();

        // Create a list of all signatures. Start by extracting the existing ones from the list of script schunks.
        // The last signature in the script chunk list is the redeemScript
        List<TransactionSignature> signatureList = new ArrayList<TransactionSignature>();
        Iterator<ScriptChunk> iterator = scriptChunks.iterator();
        Script redeemScript = null;

        while (iterator.hasNext())
        {
            ScriptChunk chunk = iterator.next();

            if (iterator.hasNext() && chunk.opcode != 0)
            {
                TransactionSignature transactionSignarture = TransactionSignature.decodeFromBitcoin(chunk.data, false);
                signatureList.add(transactionSignarture);
            } else
            {
                redeemScript = new Script(chunk.data);
            }
        }

        // Create the sighash using the redeem script
        Sha256Hash sighash = spendTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature secondSignature;

        // Take out the key and sign the signhash
        ECKey key2 = new ECKey(Hex.decode("e9cd2c5daff035f1692e72c5d3c6ef9518d8a317e92cbe178bd5d1019fbf1cc9"),Hex.decode("03a93f64c2d1d2581826c85217eda6f97f45e899897e78bc385d5a7600e2d6252c"));
        secondSignature = key2.sign(sighash);

        // Add the second signature to the signature list
        TransactionSignature transactionSignarture = new TransactionSignature(secondSignature, Transaction.SigHash.ALL, false);
        signatureList.add(transactionSignarture);

        // Rebuild p2sh multisig input script
        inputScript = ScriptBuilder.createP2SHMultiSigInputScript(signatureList, redeemScript);
        spendTx.getInput(0).setScriptSig(inputScript);

        System.out.println(Hex.toHexString(spendTx.bitcoinSerialize()));

    }

    private static AtomicBoolean flag = new AtomicBoolean(false);

    @Test
    public void multiSig() throws IOException, BlockStoreException {
        BriefLogFormatter.init();
        final NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);
        File walletFile = new File("wallet_4_multi_sig.dat");
        Wallet wallet = createWallet(params,walletFile);


        // Add watched address
        wallet.addWatchedAddress(Address.fromBase58(params,"6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1"));
        Script redeemScript = new Script(Hex.decode("5221023c90f28fe64a6b7165c5b323e42c9e6f40822e0509c8bdc0db9c4ef8436efbc721030609b122a03279486e92893726c444267973a7b039fa02487f352dcad9b32c5352ae"));
        Script p2sh = ScriptBuilder.createP2SHOutputScript(redeemScript);
        wallet.addWatchedScripts(Lists.newArrayList(redeemScript,p2sh));

        BlockStore spvBlockStore = new LevelDBBlockStore(context,new File("blockstore_multi_sig1.dat")); //new LevelDBBlockStore(context,new File("blockstore.dat"));
        //InputStream inputStream = new FileInputStream(new File("checkpoints"));

        try {
            String filename = "checkpoints";
            String suffix = params instanceof MainNetParams ? "":"-testnet";
            final InputStream checkpointsInputStream =  new FileInputStream(new File(filename)); //context.openAssestsStream(filename+suffix);
            CheckpointManager.checkpoint(params, checkpointsInputStream, spvBlockStore, 1519731317);

        }catch (final IOException x) {
            x.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }

        final BlockChain blockChain = new BlockChain(context,wallet,spvBlockStore);
        final PeerGroup peerGroup = new PeerGroup(params,blockChain);

        peerGroup.addPeerDiscovery(new PeerDiscovery() {
            @Override
            public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                return new InetSocketAddress[]{
                        //new InetSocketAddress("202.5.21.31",51474),
                        new InetSocketAddress("185.101.98.175",8443)
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
                //System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
            }
        });
        peerGroup.setMaxPeersToDiscoverCount(1);

        peerGroup.addConnectedEventListener(new PeerConnectedEventListener() {
            @Override
            public void onPeerConnected(Peer peer, int peerCount) {

            }
        });

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

        System.out.println(wallet.toString(false,true,false,blockChain));
        System.out.println("blockchain height: "+blockChain.chainHead.getHeight());


        System.out.println("########################");
        System.out.println("########################");
        System.out.println("########################");

        for (TransactionOutput transactionOutput : wallet.getUnspents()) {
            System.out.println("---------------");
            System.out.println(transactionOutput.getOutPointFor().toString());
            System.out.println("---------------");
        }

        while (true){
        }

    }

    @Test
    public void checkTx() throws IOException {
        BriefLogFormatter.init();
        final NetworkParameters params = MainNetParams.get();
        Context context = Context.getOrCreate(params);
        File walletFile = new File("wallet_4_multi_sig.dat");
        Wallet wallet = createWallet(params,walletFile);


        // Add watched address
        wallet.addWatchedAddress(Address.fromBase58(params,"6XhmA6QewRiLVaakw19RHS5Y5TMSGzFte1"));
        Script redeemScript = new Script(Hex.decode("5221023c90f28fe64a6b7165c5b323e42c9e6f40822e0509c8bdc0db9c4ef8436efbc721030609b122a03279486e92893726c444267973a7b039fa02487f352dcad9b32c5352ae"));
        Script p2sh = ScriptBuilder.createP2SHOutputScript(redeemScript);
        wallet.addWatchedScripts(Lists.newArrayList(redeemScript,p2sh));

        System.out.println(wallet.toString(false,true,false,null));
        System.out.println("########################");
        System.out.println("########################");
        System.out.println("########################");
        System.out.println(Arrays.toString(wallet.getUnspents().toArray()));
    }

    @Test
    public void connect() throws BlockStoreException, IOException, InsufficientMoneyException {

        BriefLogFormatter.init();

        FileHandler fh = new FileHandler("MyLogFile.log");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("");
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);

        NetworkParameters networkParameters = MainNetParams.get();
        Context context = new Context(networkParameters);
        //File dir = new File("dir");
        //dir.mkdir();
        File walletFile = new File("wallet_2.dat");
        Wallet wallet = createWallet(networkParameters,walletFile);
        //Wallet wallet = restore(networkParameters);
        System.out.println("Wallet keys: "+Arrays.toString(wallet.getWatchedAddresses().toArray()));
        wallet.saveToFile(walletFile);
        wallet.autosaveToFile(walletFile, 20, TimeUnit.SECONDS, new WalletFiles.Listener() {
            @Override
            public void onBeforeAutoSave(File tempFile) {

            }

            @Override
            public void onAfterAutoSave(File newlySavedFile) {

            }
        });
        // add the zspend
        wallet.addWatchedAddress(Address.fromBase58(networkParameters,"DFuEgkFtkXrMQfFoZXckm88LuqzAAukGK3"));

        BlockStore spvBlockStore = new LevelDBBlockStore(context,new File("blockstore_m3.dat")); //new LevelDBBlockStore(context,new File("blockstore.dat"));
        //InputStream inputStream = new FileInputStream(new File("checkpoints"));
        //CheckpointManager.checkpoint(networkParameters, inputStream, spvBlockStore, System.currentTimeMillis());
        final BlockChain blockChain = new BlockChain(context,wallet,spvBlockStore);
        PeerGroup peerGroup = new PeerGroup(networkParameters,blockChain);
        //peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
        peerGroup.addPeerDiscovery(new PeerDiscovery() {
            @Override
            public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                return new InetSocketAddress[]{
                        //new InetSocketAddress("202.5.21.31",51474),
                        new InetSocketAddress("185.101.98.175",8443)
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
                //System.out.println("block left: "+blocksLeft+", hash: "+block.getHash().toString());
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

        System.out.println(wallet.toString(false,true,false,blockChain));
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

    @Test
    public void verifyScript(){

        Script redeemScript = new Script(Hex.decode("5221023c90f28fe64a6b7165c5b323e42c9e6f40822e0509c8bdc0db9c4ef8436efbc721030609b122a03279486e92893726c444267973a7b039fa02487f352dcad9b32c5352ae"));

        System.out.println("redeemScript: "+redeemScript.toString());

        // Hash of the redeem script
        byte[] hash = Utils.sha256hash160(redeemScript.getProgram());
        String hashedRedeemScriptHex = Hex.toHexString(hash);

        System.out.println(hashedRedeemScriptHex);

        assert hashedRedeemScriptHex.equals("bdddbfdf7f8cc7ab7d42361324d036004204cf83") : "Fatal error" ;


    }

    @Test
    public void decodeMultiSigAndCheckvalidity(){
        NetworkParameters param = MainNetParams.get();
        Context.getOrCreate(param);
        Transaction tx = new Transaction(
                param,
                Hex.decode("0100000001105f6bb76efbca8be0a35674c8ece30b48772d8315d6e2f6d30234b6d0cd873101000000db00483045022100ff5fdac77de16e4f4761c7be657bf4eecb773add010bb475f05109d9ffbe4e4a02207b52af3d16aec0c4c9257175aaef1511b9cc6e790bf6223dda79e57d3d80a58f01483045022100b81b7ac2e1db8542415de6d2fa2293226e3b3dedb7b0a55943f6bfdefb25018f02203f00a0509bf1fe8235b84fd0343e02e508ae466db391fcba3740f4202a944e290147522102eed43149a2d0d681ceec269ff64f0380ce011f3d42fdaf47b0cc9b9ff0944c802103fb8ea7eb154134e796d68cf5ac24aff7f9e0c89be91315338acc6b995b8e174552aeffffffff0278ff73ad020000001976a914b4d8da98ed0efa48ed3c453415d7c5eea19b1c9988ac78ff73ad020000001976a9145fd67df219b31729a2137a4a5a512298520fce0e88ac00000000")
        );

        // Change redeem script..
        Script scriptSig = tx.getInput(0).getScriptSig();
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        for (ScriptChunk scriptChunk : scriptSig.getChunks()) {
            System.out.println("Chunk: "+scriptChunk);
            // Check if it's the redeem script
            if (scriptChunk.opcode == 71){
                // redeem script
                scriptBuilder.data(Hex.decode("5221023c90f28fe64a6b7165c5b323e42c9e6f40822e0509c8bdc0db9c4ef8436efbc721030609b122a03279486e92893726c444267973a7b039fa02487f352dcad9b32c5352ae"));
            }else {
                scriptBuilder.addChunk(scriptChunk);
            }
        }

        Script newScript = scriptBuilder.build();
        tx.getInput(0).setScriptSig(newScript);


        System.out.println("new script: "+newScript.toString());


        System.out.println(tx);

        Transaction txToRedeem = new Transaction(
                param,
                Hex.decode("01000000010ea11e99335a551e5b21d673d024347b32b843cfdeb90efa5681d8745e9437f2010000006a47304402201d7e0e552384ce0ff7d6ab386e3274b4be13c0f111048983712558044986e216022055bc59664fb6ecbc80476c94d05999efa221e1e4ca7b984e20eb00aad254d5ea012103ae5432f9a8bb7183c80390a83bbfb67b9b281a1aa180bbd793275611750b446dffffffff02c08df262020000001976a9149b49c6d2da780f5fa1e969398539d749b68d8e9d88ac0026e85a0500000017a914bdddbfdf7f8cc7ab7d42361324d036004204cf838700000000")
        );
        tx.getInput(0).connect(txToRedeem,null);
        System.out.println(
                "####################\n"+tx);
        //tx.getInput(0).verify(new TransactionOutput(param,null,Hex.decode(),0));
        tx.getInput(0).verify();
    }

    @Test
    public void testDecodeAndVerify(){
        NetworkParameters param = MainNetParams.get();
        Context.getOrCreate(param);
        Transaction tx = new Transaction(
                param,
                Hex.decode("0100000001105f6bb76efbca8be0a35674c8ece30b48772d8315d6e2f6d30234b6d0cd87310100000091004730440220086ed6fe550915bc5d81f29de3c940145078ef1f41e2e128e1b1010f0eac9e7602202ea850bb112bf02a87ee6e058859138930727072d0f9771b8edc5dfa22ac912e01475221023c90f28fe64a6b7165c5b323e42c9e6f40822e0509c8bdc0db9c4ef8436efbc721030609b122a03279486e92893726c444267973a7b039fa02487f352dcad9b32c5352aeffffffff0278ff73ad020000001976a914b4d8da98ed0efa48ed3c453415d7c5eea19b1c9988ac78ff73ad020000001976a9145fd67df219b31729a2137a4a5a512298520fce0e88ac00000000")
        );

        Transaction txToRedeem = new Transaction(
                param,
                Hex.decode("01000000010ea11e99335a551e5b21d673d024347b32b843cfdeb90efa5681d8745e9437f2010000006a47304402201d7e0e552384ce0ff7d6ab386e3274b4be13c0f111048983712558044986e216022055bc59664fb6ecbc80476c94d05999efa221e1e4ca7b984e20eb00aad254d5ea012103ae5432f9a8bb7183c80390a83bbfb67b9b281a1aa180bbd793275611750b446dffffffff02c08df262020000001976a9149b49c6d2da780f5fa1e969398539d749b68d8e9d88ac0026e85a0500000017a914bdddbfdf7f8cc7ab7d42361324d036004204cf838700000000")
        );
        tx.getInput(0).connect(txToRedeem,null);
        System.out.println(
                "####################\n"+tx);
        //tx.getInput(0).verify(new TransactionOutput(param,null,Hex.decode(),0));
        tx.getInput(0).verify();

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
