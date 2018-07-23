package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.context.ZerocoinContext;
import org.pivxj.core.*;
import org.pivxj.crypto.DeterministicKey;
import org.pivxj.wallet.*;

import static org.pivxj.wallet.DeterministicKeyChain.KeyChainType.BIP44_PIV;

public class MultiWallet{

    private Wallet pivWallet;
    private ZWallet zWallet;

    public MultiWallet(NetworkParameters params, ZerocoinContext zContext, DeterministicSeed seed){
        KeyChainGroup keyChainGroupPiv = new KeyChainGroup(params, seed, BIP44_PIV);

        pivWallet = new Wallet(params,keyChainGroupPiv);
        zWallet = new ZWallet(params, zContext, seed);
    }

    ////////////////////////// Basic /////////////////////////////////

    public void addPeergroup(PeerGroup peerGroup){
        peerGroup.addWallet(pivWallet);
        zWallet.addPeergroup(peerGroup);
    }

    public void removePeergroup(PeerGroup peerGroup){
        peerGroup.removeWallet(pivWallet);
        zWallet.removePeergroup(peerGroup);
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
        }

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

    public void completeSend(SendRequest sendRequest) throws InsufficientMoneyException {
        pivWallet.completeTx(sendRequest);
    }


    //////////////////////////// Zerocoin /////////////////////////////////////////////

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
}
