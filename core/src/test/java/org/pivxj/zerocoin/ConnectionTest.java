package org.pivxj.zerocoin;

import com.google.common.base.Charsets;
import com.subgraph.orchid.encoders.Hex;
import com.zerocoinj.core.CoinSpend;
import host.furszy.zerocoinj.MultiWalletFiles;
import host.furszy.zerocoinj.wallet.MultiWallet;
import host.furszy.zerocoinj.wallet.files.Listener;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pivxj.core.*;
import org.pivxj.core.listeners.AbstractPeerDataEventListener;
import org.pivxj.core.listeners.BlocksDownloadedEventListener;
import org.pivxj.core.listeners.TransactionConfidenceEventListener;
import org.pivxj.net.discovery.PeerDiscovery;
import org.pivxj.net.discovery.PeerDiscoveryException;
import org.pivxj.params.MainNetParams;
import org.pivxj.store.BlockStore;
import org.pivxj.store.BlockStoreException;
import org.pivxj.store.LevelDBBlockStore;
import org.pivxj.utils.BriefLogFormatter;
import org.pivxj.wallet.KeyChainGroup;
import org.pivxj.wallet.UnreadableWalletException;
import org.pivxj.wallet.Wallet;
import org.pivxj.wallet.WalletProtobufSerializer;

import javax.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConnectionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void syncTest(){
        try {
            NetworkParameters params = MainNetParams.get();
            final MultiWallet multiWallet = restore(params);
            multiWallet.setKeyChainGroupLookaheadThreshold(75);
            BriefLogFormatter.init();

            Context context = new Context(params);
            File walletFile = new File("wallet_connection_test.dat");
            if (walletFile.exists()){
                walletFile.delete();
            }
            File file = new File("watching_blockstore_2.dat");
            if (file.exists()){
                if (file.isDirectory()){
                    for (File file1 : file.listFiles()) {
                        file1.delete();
                    }
                }else file.delete();
            }
            BlockStore spvBlockStore = new LevelDBBlockStore(context,file);
            try {
                String filename = "checkpoints";
                String suffix = params instanceof MainNetParams ? "":"-testnet";
                final InputStream checkpointsInputStream =  new FileInputStream(new File("/Users/furszy/Documents/pivx_wallet/dashj", filename));
                CheckpointManager.checkpoint(params, checkpointsInputStream, spvBlockStore, multiWallet.getEarliestKeyCreationTime());
            }catch (final IOException x) {
                x.printStackTrace();
            }catch (Exception e){
                e.printStackTrace();
            }
            final BlockChain blockChain = new BlockChain(context,spvBlockStore);
            multiWallet.addWalletFrom(blockChain);
            PeerGroup peerGroup = new PeerGroup(params,blockChain);
            //peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
            peerGroup.addPeerDiscovery(new PeerDiscovery() {
                @Override
                public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                    return new InetSocketAddress[]{
                            //new InetSocketAddress("202.5.21.31",51474),
                            new InetSocketAddress("localhost",7776)
                            //new InetSocketAddress("localhost",51474)
                            //new InetSocketAddress("88.198.192.110",51474)
                    };
                }

                @Override
                public void shutdown() {

                }
            });
            peerGroup.setDownloadTxDependencies(0);
            multiWallet.addPeergroup(peerGroup);
            peerGroup.setMaxPeersToDiscoverCount(1);
            peerGroup.startBlockChainDownload(new AbstractPeerDataEventListener(){
                @Override
                public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                    if ((blocksLeft/10000) == 0) {
                        System.out.println("block left: " + blocksLeft + ", hash: " + block.getHash().toString());
                    }
                }
            });

            ExecutorService executor = Executors.newSingleThreadExecutor();
            final File tempFile = tempFolder.newFile("tempFile.txt");
            multiWallet.addOnTransactionsConfidenceChange(executor, new TransactionConfidenceEventListener() {
                @Override
                public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                    //try {
                        // Check that the serialization works right..

                        System.out.println(
                                "Transaction: " + tx.getHashAsString()  + ", "+ org.spongycastle.util.encoders.Hex.toHexString(tx.unsafeBitcoinSerialize()
                                )
                        );

                        //multiWallet.saveToFile(tempFile);
                        //FileInputStream inputStream = new FileInputStream(tempFile);
                        //MultiWallet wallet1 = new WalletProtobufSerializer().readMultiWallet(inputStream, false, null);

                        //Assert.assertTrue("Not consistent wallet", wallet1.isConsistent());
//                    } catch (UnreadableWalletException e) {
//                        e.printStackTrace();
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                }
            });

            peerGroup.startAsync();
            peerGroup.downloadBlockChain();


            while (true){
                try {
                    TimeUnit.SECONDS.sleep(120);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


            } catch (IOException e) {
                e.printStackTrace();
            } catch (BlockStoreException e) {
                e.printStackTrace();
            }
    }

    public MultiWallet restore(NetworkParameters networkParameters) throws IOException {
        String filename = "3.0.0_pivx-wallet-backup_org.pivx.production-2018-08-11";
        //"1.01_pivx-wallet-backup_org.pivx.production-2017-07-26 (2)";
        String password = "123";//"12345678";


        File file = new File("/Users/furszy/Documents/pivx_wallet/dashj/core/src/test/java/org/pivxj/zerocoin", filename);
        System.out.println(file.getAbsolutePath());
        final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
        final StringBuilder cipherText = new StringBuilder();
        copy(cipherIn, cipherText, 10000000);
        cipherIn.close();

        final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
        final InputStream is = new ByteArrayInputStream(plainText);

        MultiWallet wallet = restoreWalletFromProtobufOrBase58(is, networkParameters, 10000000);
        return wallet;
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

        public static MultiWallet restoreWalletFromProtobufOrBase58(final InputStream is, final NetworkParameters expectedNetworkParameters,long backupMaxChars) throws IOException
    {
        is.mark((int) backupMaxChars);

        try
        {
            return restoreWalletFromProtobuf(is, expectedNetworkParameters);
        }
        catch (final IOException x)
        {
            throw new IOException("cannot read protobuf (" + x.getMessage() + ") or base58 (" + x.getMessage() + ")", x);

        }
    }

    public static MultiWallet restoreWalletFromProtobuf(final InputStream is, final NetworkParameters expectedNetworkParameters) throws IOException {
        try {
            final MultiWallet wallet = new WalletProtobufSerializer().readMultiWallet(is, true, null);
            if (!wallet.getParams().equals(expectedNetworkParameters))
                throw new IOException("bad wallet backup network parameters: " + wallet.getParams().getId());
            if (!wallet.isConsistent())
                throw new IOException("inconsistent wallet backup");

            return wallet;
        } catch (final UnreadableWalletException x) {
            throw new IOException("unreadable wallet", x);
        }
    }

}
