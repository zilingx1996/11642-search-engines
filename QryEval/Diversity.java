import java.io.IOException;
import java.util.*;

public class Diversity {
    private Map<String,Map<Integer,Double[]>> initialRankingMap = new HashMap<>();
    // map<qid,list<query>>
    private Map<String, List<String>> queryIntentsMap;
    private Map<String, Double[]> sumMap;

    private int maxInputRankingsLength;
    private int maxResultRankingLength;
    private double lambda;
    private boolean flag = false;

    public Diversity(int maxInputRankingsLength, int maxResultRankingLength, double lambda, Map<String, List<String>> queryIntentsMap) {
        this.maxInputRankingsLength = maxInputRankingsLength;
        this.maxResultRankingLength = maxResultRankingLength;
        this.lambda = lambda;
        this.queryIntentsMap = queryIntentsMap;
        this.sumMap = new HashMap<>();
        this.initialRankingMap = new HashMap<>();
    }

    public void setInitialRankingMap(Map<String, ScoreList> initialRanking) {
        //read relevance-based document rankings for query q from the the diversity:initialRankingFile file;
        Map<String, ScoreList> queryIntentsRankingMap = new HashMap<>();

        for (Map.Entry<String, ScoreList> entry : initialRanking.entrySet()) {
            String qid = entry.getKey().trim();
            if (!qid.contains(".")) { // query
                ScoreList result = entry.getValue();
                result.sort();
                if (result.size() > maxInputRankingsLength) result.truncate(maxInputRankingsLength);

                Map<Integer,Double[]> docScores = new HashMap<>();
                int nQueryIntents = queryIntentsMap.get(qid).size();
                Double[] sums = new Double[nQueryIntents + 1];
                //System.out.println(sums.size());
                Arrays.fill(sums,0.0);
                double sumScore = 0;
                for (int i = 0; i < result.size(); i++) {
                    int docId = result.getDocid(i);
                    double score = result.getDocidScore(i);
                    if (score > 1.0) flag = true;
                    sumScore += score;

                    Double[] scores = new Double[nQueryIntents + 1];
                    Arrays.fill(scores,0.0);
                    scores[0] = score;
                    docScores.put(docId, scores);
                }
                sums[0] = sumScore;

                initialRankingMap.put(qid, docScores);
                sumMap.put(qid, sums);
            } else { // query intents
                ScoreList result = entry.getValue();
                result.sort();
                if (result.size() > maxInputRankingsLength) result.truncate(maxInputRankingsLength);
                queryIntentsRankingMap.put(qid, result);
            }
        }

        //read relevance-based document rankings for query intents q.i from the diversity:initialRankingFile file;
        for (Map.Entry<String, ScoreList> entry : queryIntentsRankingMap.entrySet()) {
            String intentsQid = entry.getKey();
            String[] strs = intentsQid.split("\\.");
            String qid = strs[0];
            int idx = Integer.parseInt(strs[1].trim());

            ScoreList result = entry.getValue();
            Map<Integer, Double[]> docScores = initialRankingMap.get(qid);
            Double[] sums = sumMap.get(qid);

            double sumScore = 0;
            for (int i = 0; i < result.size(); i++) {
                int docId = result.getDocid(i);
                if (docScores.containsKey(docId)) {
                    double score = result.getDocidScore(i);
                    sumScore += score;
                    Double[] scores = docScores.get(docId);
                    scores[idx] = score;
                    //scores.set(idx, score);
                    docScores.put(docId, scores);
                }
            }
            sums[idx] = sumScore;
            //sums.set(idx, sumScore);
            initialRankingMap.put(qid, docScores);
            sumMap.put(qid, sums);
        }
        if (flag) {
            for (Map.Entry<String, Map<Integer, Double[] >> entry : initialRankingMap.entrySet()) {
                String qid = entry.getKey();
                Map<Integer, Double[] > docScores = entry.getValue();
                Double[] sums = sumMap.get(qid);
                double max = sums[0];
                for (double sum:sums) max = Math.max(sum,max);
                for (Integer docId : docScores.keySet()) {
                    Double[] scores = docScores.get(docId);
                    for (int i = 0; i < scores.length; i++) {
                        scores[i] = scores[i]/max;
                    }
                    docScores.put(docId, scores);
                }
                initialRankingMap.put(qid, docScores);
            }
        }

    }

    public ScoreList getResult(String qid,String algorithm){
        ScoreList results = new ScoreList();
        int nQueryIntents = queryIntentsMap.get(qid).size();
        double intentWeight = 1.0 / nQueryIntents;
        int nDocs = initialRankingMap.get(qid).size();//Desired length of diversified ranking
        Map<Integer, Double[]> initialRanking = initialRankingMap.get(qid); // Initial ranking R

        if (algorithm.equalsIgnoreCase("xquad")) {
            Map<Integer, Double[]> diversifiedRanking = new HashMap<>(); //Diversified ranking S(initially empty)
            while (results.size() < nDocs) {
                double maxScore = -1;
                int maxScoreDocId = -1;
                for (Map.Entry<Integer, Double[]> entry : initialRanking.entrySet()) {
                    int docId = entry.getKey();
                    Double[] scores = entry.getValue();
                    double score = (1 - lambda) * scores[0];

                    //diversity component sum(intentweights)
                    for (int idx = 1; idx <= nQueryIntents; idx++) {//sum over every query intent
                        double qiScore = intentWeight * scores[idx];
                        for (Map.Entry<Integer,  Double[]> e : diversifiedRanking.entrySet()) {
                            Double[] tmpScores = e.getValue();
                            qiScore *= 1 - tmpScores[idx];
                        }
                        score += lambda *qiScore;
                    }
                    if (score > maxScore) {
                        maxScore = score;
                        maxScoreDocId = docId;
                    }
                }

                diversifiedRanking.put(maxScoreDocId, initialRanking.get(maxScoreDocId));
                initialRanking.remove(maxScoreDocId);
                results.add(maxScoreDocId, maxScore);
            }
        } else if (algorithm.equalsIgnoreCase("pm2")){
            double[] v = new double[nQueryIntents+1];
            Arrays.fill(v, (1.0 * maxResultRankingLength) / nQueryIntents);
            double[] qt = new double[nQueryIntents+1];
            double[] s = new double[nQueryIntents+1];
            Arrays.fill(s, 0.0);

            while (results.size() < nDocs) {

                int maxIdx = -1;
                double maxQt = -1;
                //for each qi, qt[i] vi/(2si+1)
                //for (int i = 0; i < nQueryIntents; i++) {
                for (int i = 1; i <= nQueryIntents; i++) {
                    qt[i] = v[i] / (2 * s[i] + 1);
                    if (maxQt < qt[i]) {
                        maxQt = qt[i];
                        maxIdx = i;
                    }
                }

                double maxScore = -1;
                int maxScoreDocId = -1;
                Double[] maxScoreList = null;
                for (Map.Entry<Integer,Double[]> entry : initialRanking.entrySet()) {
                    int docId = entry.getKey();
                    Double[] scores = entry.getValue();
                    double score = lambda * qt[maxIdx] * scores[maxIdx];
                    for (int i = 1; i <= nQueryIntents; i++) {
                        if (i == maxIdx) continue;
                        score += (1 - lambda) * qt[i] * scores[i];
                    }

                    if (score > maxScore) {
                        maxScore = score;
                        maxScoreDocId = docId;
                    }
                }
                maxScoreList = initialRanking.get(maxScoreDocId);
                initialRanking.remove(maxScoreDocId);
                results.add(maxScoreDocId, maxScore);

                double sum = 0;
                for (double score : maxScoreList) sum += score;
                //System.out.println(sum);
                //System.out.println(maxScoreList.get(0));
                sum -= maxScoreList[0];
                for (int i = 1; i <= nQueryIntents; i++) {
                    s[i] = sum ==0? 0: s[i]+maxScoreList[i] / sum;
                }
            }
        }
        return results;
    }


}
