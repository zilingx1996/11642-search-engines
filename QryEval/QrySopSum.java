import java.io.IOException;

public class QrySopSum extends QrySop {

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin (r);
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25 (r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        return 0;
    }

    private double getScoreBM25 (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double sum = 0;
            double k_3 = ((RetrievalModelBM25)r).getK_3();
            for (Qry q_i : this.args){
                if (q_i.docIteratorHasMatch(r)
                        &&q_i.docIteratorGetMatch()==this.docIteratorGetMatch())
                    sum += ((QrySop) q_i).getScore(r)*(k_3 + 1) * 1 / (k_3 + 1);
            }
            return sum;
        }
    }
}
