package host.furszy.zerocoinj.wallet;

import com.zerocoinj.core.CoinDenomination;
import com.zerocoinj.core.CoinSpend;
import com.zerocoinj.core.SpendType;
import com.zerocoinj.core.ZCoin;
import com.zerocoinj.core.accumulators.Accumulator;
import com.zerocoinj.core.accumulators.AccumulatorWitness;
import com.zerocoinj.core.accumulators.Accumulators;
import com.zerocoinj.core.context.ZerocoinContext;
import host.furszy.zerocoinj.protocol.GenWitMessage;
import host.furszy.zerocoinj.protocol.PubcoinsMessage;
import org.pivxj.core.*;
import org.pivxj.core.listeners.OnGetDataResponseEventListener;
import org.pivxj.script.Script;
import org.pivxj.script.ScriptBuilder;
import org.pivxj.script.ScriptOpCodes;
import org.pivxj.utils.Pair;
import org.pivxj.wallet.SendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ZCSpendRequest implements Callable<Transaction>,OnGetDataResponseEventListener {

    private static final Logger log = LoggerFactory.getLogger(ZCSpendRequest.class);

    private SendRequest sendRequest;
    private PeerGroup peerGroup;
    private ZerocoinContext zerocoinContext;
    private ConcurrentHashMap<Integer,Pair<ZCoin,GenWitMessage>> waitingRequests = new ConcurrentHashMap<>();
    private Transaction transaction;
    private final Object lock;

    public ZCSpendRequest(SendRequest sendRequest, PeerGroup peerGroup) {
        this.sendRequest = sendRequest;
        this.peerGroup = peerGroup;
        this.zerocoinContext = Context.get().zerocoinContext;
        this.lock = new ReentrantLock();
    }

    public SendRequest getSendRequest() {
        return sendRequest;
    }

    public PeerGroup getPeerGroup() {
        return peerGroup;
    }

    public void addWaitingRequest(GenWitMessage genWitMessage, ZCoin zCoin){
        if (waitingRequests.containsKey(genWitMessage.getRequestNum())) throw new IllegalArgumentException("Duplicated request num");
        waitingRequests.put(genWitMessage.getRequestNum(), new Pair<>(zCoin, genWitMessage));
    }

    @Override
    public Transaction call() throws Exception {
        if (this.peerGroup.getConnectedPeers().isEmpty()) throw new IllegalStateException("No peers online");

        Peer peer0 = this.peerGroup.getConnectedPeers().get(0);
        peer0.addOnGetDataResponseEventListener(this);

        for (Map.Entry<Integer, Pair<ZCoin, GenWitMessage>> entry : waitingRequests.entrySet()) {
            peer0.sendMessage(entry.getValue().getSecond());
        }

        log.info("Waiting for node response..");
        // Now wait for the completeness..
        synchronized (lock){
            lock.wait();
        }

        log.info("Transaction ready!");
        for (Peer peer : peerGroup.getConnectedPeers()) {
            // Remove this listener now
            peer.removeOnGetDataResponse(this);
        }
        return transaction;
    }

    @Override
    public void onResponseReceived(PubcoinsMessage pubcoinsMessage) {
        log.info("onResponseReceived: " + pubcoinsMessage.toString());
        if (! waitingRequests.containsKey((int) pubcoinsMessage.getRequestNum())) return;
        Pair<ZCoin,GenWitMessage> pair = waitingRequests.get((int) pubcoinsMessage.getRequestNum());
        GenWitMessage request = pair.getSecond();
        ZCoin coinToSpend = pair.getFirst();
        if (request != null){
            List<BigInteger> list = pubcoinsMessage.getList();
            System.out.println("amount of data received: " + list.size());
            // Create accumulator:

            // First check that my commitment is in the filtered list
            if (!list.contains(coinToSpend.getCommitment().getCommitmentValue())) {
                // TODO: Notify fail here..
                log.error("Pubcoins response list doesn't contains our commitment value.., check core sources");
                return;
            }

            // Accumulator
            Accumulator acc = new Accumulator(
                    zerocoinContext.accumulatorParams,
                    CoinDenomination.ZQ_ONE,
                    pubcoinsMessage.getAccValue()
            );

            // Now accumulate the pubcoins to the result to obtain the same witness that is created by the rpc method.
            Accumulator accWit = new Accumulator(
                    zerocoinContext.accumulatorParams,
                    CoinDenomination.ZQ_ONE,
                    pubcoinsMessage.getAccWitnessValue()

            );
            AccumulatorWitness witness = new AccumulatorWitness(accWit, coinToSpend);

            int i = 0;
            for (BigInteger bigInteger : list) {
                witness.addElementUnchecked(bigInteger);
                if (i % 100 == 0)
                    log.info("coin incremented: " + i);
                i++;
            }

            log.info("Witness: " + witness.getValue());
            if (!witness.verifyWitness(acc,coinToSpend)){
                log.error("Verify witness failed");
                return;
                //             Assert.assertTrue("Verify failed", witness.verifyWitness(acc, mintedCoin));
            }
            log.info("Valid accumulator");


            // 3) Complete the tx
            Transaction transaction = sendRequest.tx;

            // Accumulator checksum
            BigInteger accChecksum = BigInteger.valueOf(
                    Accumulators.getChecksum(acc.getValue())
            );

            CoinSpend coinSpend = new CoinSpend(
                    zerocoinContext,
                    coinToSpend,
                    acc,
                    accChecksum,
                    witness,
                    transaction.getHash(),
                    SpendType.SPEND,
                    null
            );


            if (!coinSpend.verify(acc)){
                log.error("CoinSpend not valid");
                return;
                // Assert.assertTrue("CoinSpend not valid",coinSpend.verify(acc));
            }

            transaction = add(transaction, coinSpend, coinToSpend.getCommitment().getCommitmentValue());

            // Now spend it if all of the inputs are completed
            waitingRequests.remove((int) pubcoinsMessage.getRequestNum());
            if (waitingRequests.size() == 0){
                synchronized (lock) {
                    this.transaction = transaction;
                    lock.notify();
                }
            }
        }
    }

    private Transaction add(Transaction transaction, CoinSpend coinSpend, BigInteger commitmentValue){
        // zc_spend input
        byte[] coinSpendBytes = coinSpend.bitcoinSerialize();
        log.info("Coin spend bytes length: " + coinSpendBytes.length);
        log.info("Coin spend bytes: " + Hex.toHexString(coinSpendBytes));

        Script script = new ScriptBuilder()
                .op(ScriptOpCodes.OP_ZEROCOINSPEND) // OP_ZEROCOINMINT = 0xc2 == 194
                .number(coinSpendBytes.length)
                .build();

        byte[] halfScriptProgram = script.getProgram();
        log.info("Half program: " + Hex.toHexString(halfScriptProgram));
        byte[] program = new byte[halfScriptProgram.length + coinSpendBytes.length];
        System.arraycopy(halfScriptProgram,0,program,0,halfScriptProgram.length);
        System.arraycopy(coinSpendBytes,0,program,halfScriptProgram.length,coinSpendBytes.length);

        log.info("program: " + Hex.toHexString(program));
        TransactionInput transactionInput = new TransactionInput(transaction.getParams(), transaction, program);
        //use nSequence as a shorthand lookup of denomination
        //NOTE that this should never be used in place of checking the value in the final blockchain acceptance/verification
        //of the transaction
        transactionInput.setSequenceNumber(coinSpend.getDenomination().getDenomination());

        // Remove previos fake input first
        List<TransactionInput> inputs = transaction.getInputs();
        List<TransactionInput> realInputs = new ArrayList<>();

        for (TransactionInput input : inputs) {
            if (input.getConnectedOutput().isZcMint() &&
                    !( Utils.areBigIntegersEqual(input.getConnectedOutput().getScriptPubKey().getCommitmentValue(), commitmentValue)) ){
                realInputs.add(input);
            }
        }

        realInputs.add(transactionInput);

        transaction.clearInputs();
        for (TransactionInput realInput : realInputs) {
            transaction.addInput(realInput);
        }

        System.out.println(transaction);
        System.out.println("Coin spend inputs program: " + Hex.toHexString(transaction.getInput(0).getScriptBytes()));

        return transaction;
    }
}
