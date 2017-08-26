package org.bitcoinj.core;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
    }

    @Test
    public void connect() throws BlockStoreException, IOException {
        NetworkParameters networkParameters = TestNet3Params.get();
        Context context = new Context(networkParameters);
        //context.initPivx(false, true);
        //File dir = new File("dir");
        //dir.mkdir();
        //context.initDashSync(dir.getAbsolutePath());
        Wallet wallet = new Wallet(networkParameters);
        SPVBlockStore spvBlockStore = new SPVBlockStore(networkParameters,new File("blockstore.dat"));
        //InputStream inputStream = new FileInputStream(new File("checkpoints"));
        //CheckpointManager.checkpoint(networkParameters, inputStream, spvBlockStore, System.currentTimeMillis());
        final BlockChain blockChain = new BlockChain(context,wallet,spvBlockStore);
        PeerGroup peerGroup = new PeerGroup(networkParameters,blockChain);
        peerGroup.addPeerDiscovery(new PeerDiscovery() {
            @Override
            public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                return new InetSocketAddress[]{new InetSocketAddress("localhost",51474)};
            }

            @Override
            public void shutdown() {

            }
        });
        peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                System.out.println("downloading, block left: "+blocksLeft);
            }
        });
        peerGroup.startBlockChainDownload(new PeerDataEventListener() {
            @Nullable
            @Override
            public List<Message> getData(Peer peer, GetDataMessage m) {
                return null;
            }

            @Override
            public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                System.out.println("block left: "+blocksLeft);
            }

            @Override
            public void onChainDownloadStarted(Peer peer, int blocksLeft) {
                System.out.println("onChainDownloadStarted, block left: "+blocksLeft);
            }


            @Override
            public Message onPreMessageReceived(Peer peer, Message m) {
                return m;
            }
        });
        peerGroup.startAsync();
        //peerGroup.downloadBlockChain();

        while (true){

        }

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

}
