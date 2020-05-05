import java.io.IOException;
import java.util.List;

public class QrySopWand extends QrySop {
    List<Double> weights;
    Double sum;

    public void setWeights(List<Double> weights) {
        this.weights = weights;
        sum = 0.0;
        for (Double w : weights) {
            //w = (double) Math.round(w * 10000) / 10000;
            sum += w;
        }
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double product = 1.0;
            for (int i = 0; i < this.args.size(); i++) {
                double weight = weights.get(i);
                weight = (double) Math.round(weight * 10000) / 10000;
                product *= Math.pow(((QrySop) this.args.get(i)).getDefaultScore(r, docid), weight / sum);
            }
            return product;
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            //if(this.docIteratorGetMatch()==514167) System.out.println("aaaaaaa");
            double product = 1.0;
            for (int i = 0; i < this.args.size(); i++) {
                double weight = weights.get(i);
                weight = (double) Math.round(weight * 10000) / 10000;
                if (this.args.get(i).docIteratorHasMatch(r)
                        && this.args.get(i).docIteratorGetMatch() == this.docIteratorGetMatch()) {
                    product *= Math.pow(((QrySop) this.args.get(i)).getScore(r), weight / sum);
                } else {
                    product *= Math.pow(((QrySop) this.args.get(i)).getDefaultScore(r, this.docIteratorGetMatch()), weight / sum);
                }
            }

            return product;
        }
    }


}
