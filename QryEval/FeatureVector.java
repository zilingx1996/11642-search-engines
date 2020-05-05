import java.io.*;
import java.util.*;

public class FeatureVector {
    private RetrievalModelLetor model;
    private Map<String, String> queries;
    private Map<String, Map<String, Integer>> relevanceJudgement;
    private Map<String, double[]> fvs;
    private double[] min = new double[18];
    private double[] max = new double[18];
    private List<String> docs;
    private Set<Integer> featureDisable;

    public FeatureVector(RetrievalModelLetor model) {
        this.model = model;
    }

    public void setQuery(Map<String, String> queries) {
        this.queries = queries;
    }

    public void setRelevanceJudgement(Map<String, Map<String, Integer>> relevanceJudgement) {
        this.relevanceJudgement = relevanceJudgement;
    }

    public void setFeatureDisable(String str) {
        featureDisable = new HashSet<>();
        //featureDisable.add(17);
        //featureDisable.add(18);
        if (!str.equals("")) {
            String[] strs = str.split(",");
            for (String s : strs) featureDisable.add(Integer.valueOf(s.trim()));
        }
    }

    //doc - feature vector
    public void docFeatureVector(String qid) throws Exception {

        String query = queries.get(qid);
        //use QryParser.tokenizeString to stop & stem the query
        String[] qTerms = QryParser.tokenizeString(query);

        fvs = new HashMap<>();
        Arrays.fill(min, Double.MAX_VALUE);
        Arrays.fill(max, -Double.MAX_VALUE);
        QrySopScore sop = new QrySopScore();

        docs = new ArrayList<>();
        //foreach document d in the relevance judgements for training query q
        for (Map.Entry<String, Integer> entry : relevanceJudgement.get(qid).entrySet()) {
            String externalDocid = entry.getKey();
            docs.add(externalDocid);
            int docid = Idx.getInternalDocid(externalDocid);
            if (docid == -1) continue;
            double[] fv = new double[18];
            Arrays.fill(fv, Double.MIN_VALUE);

            //f1: Spam score for d (read from index).
            fv[0] = Double.parseDouble(Idx.getAttribute("spamScore", docid));

            //f2: Url depth for d(number of '/' in the rawUrl field).
            String rawUrl = Idx.getAttribute("rawUrl", docid);
            for (int i = 0; i < rawUrl.length(); i++) {
                if (rawUrl.charAt(i) == '/') fv[1]++;
            }

            //f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
            if (rawUrl.contains("wikipedia.org")) fv[2] = 1.0;
            else fv[2] = 0.0;

            //f4: PageRank score for d (read from index).
            fv[3] = (double) Float.parseFloat(Idx.getAttribute("PageRank", docid));

            //f5: BM25 score for <q, dbody>.
            fv[4] = sop.featureBM25(model.getB(), model.getK_1(), model.getK_3(), "body", docid, qTerms);

            //f6: Indri score for <q, dbody>.
            fv[5] = sop.featureIndri(model.getLambda(), model.getMu(), "body", docid, qTerms);

            //f7: Term overlap score (also called Coordination Match) for <q, dbody>.
            fv[6] = sop.getOverlapScore(docid, "body", qTerms);

            //f8: BM25 score for <q, dtitle>.
            fv[7] = sop.featureBM25(model.getB(), model.getK_1(), model.getK_3(), "title", docid, qTerms);

            //f9: Indri score for <q, dtitle>.
            fv[8] = sop.featureIndri(model.getLambda(), model.getMu(), "title", docid, qTerms);

            //f10: Term overlap score (also called Coordination Match) for <q, dtitle>.
            fv[9] = sop.getOverlapScore(docid, "title", qTerms);

            //f11: BM25 score for <q, durl>.
            fv[10] = sop.featureBM25(model.getB(), model.getK_1(), model.getK_3(), "url", docid, qTerms);

            //f12: Indri score for <q, durl>.
            fv[11] = sop.featureIndri(model.getLambda(), model.getMu(), "url", docid, qTerms);

            //f13: Term overlap score (also called Coordination Match) for <q, durl>.
            fv[12] = sop.getOverlapScore(docid, "url", qTerms);

            //f14: BM25 score for <q, dinlink>.
            fv[13] = sop.featureBM25(model.getB(), model.getK_1(), model.getK_3(), "inlink", docid, qTerms);

            //f15: Indri score for <q, dinlink>.
            fv[14] = sop.featureIndri(model.getLambda(), model.getMu(), "inlink", docid, qTerms);

            //f16: Term overlap score (also called Coordination Match) for <q, dinlink>.
            fv[15] = sop.getOverlapScore(docid, "inlink", qTerms);

            //f17: Your custom feature - use your imagination.
            fv[16] = sop.getAvgTf(docid, "body", qTerms);

            //f18: Your custom feature - use your imagination.
            fv[17] = sop.getAvgTfidf(docid, "body", qTerms);

            for (int i = 0; i < 18; i++) {
                if (fv[i] != Double.MIN_VALUE) {
                    min[i] = Math.min(fv[i], min[i]);
                    max[i] = Math.max(fv[i], max[i]);
                }
            }
            fvs.put(externalDocid, fv);
        }

    }

    public void normalize() {
        for (Map.Entry<String, double[]> entry : fvs.entrySet()) {
            String externalDocid = entry.getKey();
            double[] fv = entry.getValue();
            for (int i = 0; i < 18; i++) {
                if (fv[i] != Double.MIN_VALUE) {
                    //If the min and max are the same value, set the feature value to 0.0
                    fv[i] = min[i] == max[i] ? 0.0 : (fv[i] - min[i]) / (max[i] - min[i]);
                } else {
                    //set no match score as 0.0
                    fv[i] = 0.0;
                }
            }
            fvs.put(externalDocid, fv);
        }
    }

    public void saveToFile(String FeatureVectorsFile, String qid, String type) throws IOException {
        //write the expanded query to a file
        BufferedWriter featureVectorWrite = new BufferedWriter(new FileWriter(FeatureVectorsFile, true));

        for (Map.Entry<String, Integer> entry : relevanceJudgement.get(qid).entrySet()) {
            String externalDocid = entry.getKey();
            StringBuilder sb = new StringBuilder();

            //The first column is the score or target value of a <q, d> pair.
            // In a training file, use the relevance value obtained for this <q, d> pair from the relevance judgments ("qrels") file.
            // In a test file, this value should be 0.
            if (type.equals("train")) sb.append(String.format("%d\t", entry.getValue()));
            else sb.append(String.format("%d\t", 0));

            //The second column is the query id.
            sb.append(String.format("qid:%s\t", qid));

            double[] fv = fvs.get(externalDocid);
            for (int i = 0; i < 18; i++) {
                if (featureDisable.contains(i + 1)) continue;
                sb.append(String.format("%d:%.14f\t", i + 1, fv[i]));
            }
            sb.append(String.format("#\t%s\n", externalDocid));
            featureVectorWrite.write(sb.toString());
        }


        featureVectorWrite.close();
    }

    public List<String> getDocs() {
        return this.docs;
    }

}
