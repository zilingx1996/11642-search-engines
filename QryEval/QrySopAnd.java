
import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        } else {
            return this.docIteratorHasMatchAll(r);
        }
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if (r instanceof RetrievalModelRankedBoolean){
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double product=1;
        for (Qry q_i : this.args)
            product *= ((QrySop)q_i).getDefaultScore(r,docid);
        return Math.pow(product,1.0/this.args.size());
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }


    private double getScoreRankedBoolean (RetrievalModel r) throws IOException{
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            //choosing the min score from matching args
            double minScore =Double.MAX_VALUE;
            for (Qry q_i : this.args){
                if(((QrySop) q_i).getScore(r)<minScore){
                    minScore=((QrySop) q_i).getScore(r);
                }
            }
            return minScore;
        }
    }

    private double getScoreIndri (RetrievalModel r) throws IOException{
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double product=1;
            for (Qry q_i : this.args){
                if (q_i.docIteratorHasMatch(r)
                        &&q_i.docIteratorGetMatch()==this.docIteratorGetMatch())
                    product *= ((QrySop)q_i).getScore(r);
                else
                    product *= ((QrySop)q_i).getDefaultScore(r,this.docIteratorGetMatch());
            }
            return Math.pow(product,1.0/this.args.size());
        }
    }


}
