/**
 * Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;
import java.util.List;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            double lambda = ((RetrievalModelIndri) r).getLambda();
            double mu = ((RetrievalModelIndri) r).getMu();
            Qry q = this.args.get(0);
            double ctf = ((QryIop) q).getCtf() == 0 ? 0.5 : ((QryIop) q).getCtf();
            double doclen = Idx.getFieldLength(((QryIop) q).getField(), (int) docid);
            double collen = Idx.getSumOfFieldLengths(((QryIop) q).getField());
            return (1 - lambda) * mu * (ctf / collen) / (doclen + mu) + lambda * ctf / collen;
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't have a default score.");
        }

    }

    /**
     *  getScore for the Unranked retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the Ranked retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            //returning tf of the match from the inverted list of its only arg
            Qry q = this.args.get(0);
            return ((QryIop) q).docIteratorGetMatchPosting().tf;
        }
    }


    /**
     * getScore for the BM25 retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double b = ((RetrievalModelBM25) r).getB();
            double k_1 = ((RetrievalModelBM25) r).getK_1();
            long N = Idx.getNumDocs();
            Qry q = this.args.get(0);
            int df = ((QryIop) q).getDf();
            double tf = ((QryIop) q).docIteratorGetMatchPosting().tf;
            double doclen = Idx.getFieldLength(((QryIop) q).getField(), ((QryIop) q).docIteratorGetMatch());
            double avg_doclen = 1.0 * Idx.getSumOfFieldLengths(((QryIop) q).getField()) / (double) Idx.getDocCount(((QryIop) q).getField());
            return Math.max(0, Math.log(1.0 * (N - df + 0.5) / (df + 0.5))) * tf / (tf + k_1 * (1 - b + b * (doclen / avg_doclen)));
        }
    }

    //  featureBM25 (queryStems, docid, field):
    //    score = 0
    //    for each stem in <docid, field>
    //      if stem is a queryStem
    //         score += BM25 term score for stem
    //      end
    //    end
    //
    //  return score
    public double featureBM25(double b,double k_1,double k_3,String field,int docId,String[] qTerms) throws IOException {
        double score = 0.0;
        TermVector termVector = new TermVector(docId, field);
        if (termVector.positionsLength()==0 || termVector.stemsLength()==0) return Double.MIN_VALUE;
        long N = Idx.getNumDocs();
        double doclen = Idx.getFieldLength(field, docId);
        double avg_doclen =  Idx.getSumOfFieldLengths(field)/(double)Idx.getDocCount(field);
        for(String stem: qTerms){
            //Get the index of stem in the stems vector, or -1 if the stems vector does not contain the stem.
            int idx = termVector.indexOfStem(stem);
            if(idx!=-1){
                double tf = termVector.stemFreq(idx);
                double df = termVector.stemDf(idx);
                score += Math.max(0, Math.log(1.0 * (N - df + 0.5) / (df + 0.5))) * tf / (tf + k_1 * (1 - b + b * (doclen / avg_doclen)));
            }
        }
        return score;
    }

    /**
     * getScore for the Indri retrieval model.
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double lambda = ((RetrievalModelIndri) r).getLambda();
            double mu = ((RetrievalModelIndri) r).getMu();
            Qry q = this.args.get(0);
            double tf = ((QryIop) q).docIteratorGetMatchPosting().tf;
            double ctf = ((QryIop) q).getCtf();
            double doclen = Idx.getFieldLength(((QryIop) q).getField(), ((QryIop) q).docIteratorGetMatch());
            double collen = Idx.getSumOfFieldLengths(((QryIop) q).getField());
            return (1 - lambda) * (tf + mu * (ctf / collen)) / (doclen + mu) + lambda * ctf / collen;
        }
    }


    //For the Indri retrieval model features, if a field does not match any term of a query, the score for the field is 0.
    //  featureIndri (queryStems, docid, field):
    //    score = 1.0
    //    for each stem in <docid, field>
    //      if stem is not a queryStem
    //         return 0.0
    //      if stem is a queryStem
    //         score *= Indri term score for stem
    //      end
    //    end
    //  return score
    public double featureIndri(double lambda,double mu,String field,int docId,String[] qTerms) throws IOException {
        double score = 1.0;
        TermVector termVector = new TermVector(docId, field);
        if (termVector.positionsLength()==0 || termVector.stemsLength()==0) return Double.MIN_VALUE;
        double doclen = Idx.getFieldLength(field, docId);
        double collen = Idx.getSumOfFieldLengths(field);
        boolean match = false;
        for(String stem: qTerms){
            int idx = termVector.indexOfStem(stem);
            if (idx != -1) match = true;
            double tf = idx == -1? 0.0:termVector.stemFreq(idx);
            double ctf = Idx.getTotalTermFreq(field,stem);
            score *= (1 - lambda) * (tf + mu * (ctf / collen)) / (doclen + mu) + lambda * ctf / collen;
        }
        return match? Math.pow(score,1.0/qTerms.length):0.0;
    }


    public double getOverlapScore(int docId, String field, String[] qTerms) throws IOException{
        TermVector termVector = new TermVector(docId, field);
        //Note: If you try to instantiate a TermVector for a document field that does not exist
        // (e.g., an inlink field for a document that has no inlinks), the constructor returns an empty TermVector.
        // It is easy to recognize an empty TermVector: The positionsLength and stemsLength methods will return 0 (i.e., the field does not contain anything).
        if (termVector.positionsLength()==0 || termVector.stemsLength()==0) return Double.MIN_VALUE;
        int counter = 0;
        for (String stem : qTerms){
            //Get the index of stem in the stems vector, or -1 if the stems vector does not contain the stem.
            if(termVector.indexOfStem(stem)!=-1) counter++;
        }
        //Term overlap is defined as the percentage of query terms that match the document field.
        return (counter*1.0)/qTerms.length;
    }

    public double getAvgTf(int docId,String field,String[] qTerms) throws IOException{
        double sum = 0.0;
        TermVector termVector = new TermVector(docId, field);
        if (termVector.positionsLength()==0 || termVector.stemsLength()==0) return Double.MIN_VALUE;

        for(String stem:qTerms){
            int idx = termVector.indexOfStem(stem);
            if (idx != -1) {
                double tf = termVector.stemFreq(idx);
                sum += tf;
            }
        }
        return sum/qTerms.length;
    }

    public double getAvgTfidf(int docId,String field,String[] qTerms) throws IOException{
        double sum = 0.0;
        TermVector termVector = new TermVector(docId, field);
        if (termVector.positionsLength()==0 || termVector.stemsLength()==0) return Double.MIN_VALUE;
        long N = Idx.getNumDocs();

        for(String stem:qTerms){
            int idx = termVector.indexOfStem(stem);
            if (idx != -1) {
                double tf = termVector.stemFreq(idx);
                double df = termVector.stemDf(idx);
                double idf = Math.log((1+N)/df);//to avoid idf = 0
                sum += tf*idf;
            }
        }
        return sum/qTerms.length;
    }




    /**
     *  Initialize the query operator (and its arguments), including any
     *  internal iterators.  If the query operator is of type QryIop, it
     *  is fully evaluated, and the results are stored in an internal
     *  inverted list that may be accessed via the internal iterator.
     *  @param r A retrieval model that guides initialization
     *  @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

}
