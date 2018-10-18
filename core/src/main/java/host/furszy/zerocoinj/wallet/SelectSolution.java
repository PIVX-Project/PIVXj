package host.furszy.zerocoinj.wallet;

import org.pivxj.core.TransactionOutput;

import java.util.HashSet;
import java.util.Set;

public class SelectSolution {

    private PSolution pSolution;
    private long scoring;
    private Set<TransactionOutput> outpus = new HashSet<>();

    public SelectSolution(PSolution pSolution) {
        this.pSolution = pSolution;
    }

    private void addScoring(int score){
        this.scoring+=score;
    }

    public void addOutput(TransactionOutput output){
        this.outpus.add(output);
        this.addScoring(output.getParentTransactionDepthInBlocks());
    }

    public PSolution getpSolution() {
        return pSolution;
    }

    public long getScoring() {
        return scoring;
    }

    public Set<TransactionOutput> getOutpus() {
        return outpus;
    }

    @Override
    public String toString() {
        return "SelectSolution{" +
                "pSolution=" + pSolution +
                ", scoring=" + scoring +
                ", outpus=" + outpus +
                '}';
    }
}
