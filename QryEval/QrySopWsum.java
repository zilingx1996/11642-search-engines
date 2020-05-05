import java.io.IOException;
import java.util.List;

public class QrySopWsum extends QrySop  {
    List<Double> weights;
    Double sum;

    public void setWeights(List<Double> weights) {
        this.weights = weights;
        sum = 0.0;
        for (Double w:weights) sum += w;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin (r);
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri (r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double total=0;
            for (int i = 0; i<this.args.size();i++)
                total+=((QrySop)this.args.get(i)).getDefaultScore(r,docid)*weights.get(i)/sum;
            return total;
        }
    }

    private double getScoreIndri (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double total=0;
            for (int i = 0; i<this.args.size();i++){
                if (this.args.get(i).docIteratorHasMatch(r)
                        &&this.args.get(i).docIteratorGetMatch()==this.docIteratorGetMatch())
                    total+=((QrySop)this.args.get(i)).getScore(r)*weights.get(i)/sum;
                else
                    total+=((QrySop)this.args.get(i)).getDefaultScore(r,this.docIteratorGetMatch())*weights.get(i)/sum;
            }
            return total;
        }
    }

}
