package org.pivxj.zerocoin;

import com.google.common.collect.Lists;
import org.pivxj.core.*;
import org.pivxj.net.ClientConnectionManager;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class PeergroupUtil extends PeerGroup {

    public Peer peerUtil;

    public PeergroupUtil(NetworkParameters params) throws UnknownHostException {
        super(params);
        VersionMessage ver = getVersionMessage().duplicate();
        ver.bestHeight = chain == null ? 0 : chain.getBestChainHeight();
        ver.time = Utils.currentTimeSeconds();

        peerUtil = createPeer(new PeerAddress(InetAddress.getByName("127.0.0.1")),ver);
    }

    public PeergroupUtil(Context context) {
        super(context);
    }

    public PeergroupUtil(NetworkParameters params, @Nullable AbstractBlockChain chain) {
        super(params, chain);
    }

    public PeergroupUtil(Context context, @Nullable AbstractBlockChain chain) {
        super(context, chain);
    }

    public PeergroupUtil(NetworkParameters params, @Nullable AbstractBlockChain chain, ClientConnectionManager connectionManager) {
        super(params, chain, connectionManager);
    }

    public PeergroupUtil(Context context, @Nullable AbstractBlockChain chain, ClientConnectionManager connectionManager) {
        super(context, chain, connectionManager);
    }

    @Override
    public List<Peer> getConnectedPeers() {
        return Lists.newArrayList(peerUtil);
    }
}
