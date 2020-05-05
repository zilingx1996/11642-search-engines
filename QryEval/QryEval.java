/*
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.4.
 *
 *  Compatible with Lucene 8.1.1.
 */

import java.io.*;
import java.util.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));

        RetrievalModel model = null;
        if (parameters.containsKey("retrievalAlgorithm")){
            model = initializeRetrievalModel(parameters);
        }

        if ((model instanceof RetrievalModelLetor)) {
            conductLearnToRank(parameters, (RetrievalModelLetor)model);
        } else {
            //  Perform experiments.
            processQueryFile(parameters, model);
        }

        //  Clean up.
        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();

        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();

        } else if (modelString.equals("bm25")) {
            //BM25:k_1=1.2
            //BM25:b=0.75
            //BM25:k_3=0
            double k_1 = Double.valueOf(parameters.get("BM25:k_1"));
            double b = Double.valueOf(parameters.get("BM25:b"));
            double k_3 = Double.valueOf(parameters.get("BM25:k_3"));
            model = new RetrievalModelBM25(k_1, b, k_3);

        } else if (modelString.equals("indri")) {
            //Indri:mu=2500
            //Indri:lambda=0.4
            double mu = Double.valueOf(parameters.get("Indri:mu"));
            double lambda = Double.valueOf(parameters.get("Indri:lambda"));
            model = new RetrievalModelIndri(mu, lambda);

        } else if (modelString.equals("letor")) {
            double k_1 = Double.valueOf(parameters.get("BM25:k_1"));
            double b = Double.valueOf(parameters.get("BM25:b"));
            double k_3 = Double.valueOf(parameters.get("BM25:k_3"));
            double mu = Double.valueOf(parameters.get("Indri:mu"));
            double lambda = Double.valueOf(parameters.get("Indri:lambda"));
            model = new RetrievalModelLetor(k_1, b, k_3, mu, lambda);

        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList results = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    results.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }

            return results;
        } else
            return null;
    }

    /**
     * Process the query file.
     *
     * @param parameters Parameters
     * @param model      A retrieval model that will guide matching and scoring
     * @throws IOException Error accessing the Lucene index.
     *                     dfsf vgdg
     */
    static void processQueryFile(Map<String, String> parameters,
                                 RetrievalModel model)
            throws IOException {

        BufferedReader input = null;
        String queryFilePath = parameters.get("queryFilePath");
        String outputPath = parameters.get("trecEvalOutputPath");
        int outputLength = Integer.parseInt(parameters.get("trecEvalOutputLength"));
        boolean flag = parameters.containsKey("diversity") &&
                        parameters.get("diversity").equalsIgnoreCase("true");

        Diversity diversity = null;
        //if (parameters.containsKey("diversity") && parameters.get("diversity").toLowerCase().equals("true"))
       if (flag){
           int maxInputRankingsLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
           int maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
           String intentsFile = parameters.get("diversity:intentsFile");
           Map<String, List<String>> queryIntentsMap = processIntentsFile(intentsFile);
           double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
           diversity = new Diversity(maxInputRankingsLength,maxResultRankingLength,lambda,queryIntentsMap);

           Map<String, ScoreList> initialRankingMap = null;
           //if (the diversity:initialRankingFile= parameter is specified)
           if (parameters.containsKey("diversity:initialRankingFile")){
               String initialRankingFile = parameters.get("diversity:initialRankingFile");
               initialRankingMap = getInitialRankingFile(initialRankingFile,maxInputRankingsLength);
           } else{
               initialRankingMap = getInitialRankingFile(queryFilePath,queryIntentsMap,model,maxInputRankingsLength);
           }
           diversity.setInitialRankingMap(initialRankingMap);
       }

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            FileWriter outputFile = new FileWriter(outputPath);
            BufferedWriter outputWrite = new BufferedWriter(outputFile);
            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {

                printMemoryUsage(false);
                System.out.println("Query " + qLine);
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }

                String qid = pair[0].trim();
                String query = pair[1];
                ScoreList results = null;
                //if (the fb= parameter is missing from the parameter file or set to false)
                if (!parameters.containsKey("fb") || parameters.get("fb").equals(false)) {
                    if(!flag) results = processQuery(query, model);//use the query to retrieve documents;
                    else{//need diversification
                        String algorithm = parameters.get("diversity:algorithm");
                        results = diversity.getResult(qid,algorithm);
                    }
                } else {
                    int fbDocs = Integer.valueOf(parameters.get("fbDocs"));//determines the number of documents to use for query expansion.
                    int fbTerms = Integer.valueOf(parameters.get("fbTerms"));//determines the number of terms that are added to the query.
                    int fbMu = Integer.valueOf(parameters.get("fbMu"));//determines the amount of smoothing used to calculate p(r|d).
                    double fbOrigWeight = Double.valueOf(parameters.get("fbOrigWeight"));//determines the weight on the original query.
                    String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");

                    //if (the fbInitialRankingFile= parameter is specified)
                    if (parameters.containsKey("fbInitialRankingFile")) {
                        //read a document ranking in trec_eval input format from the fbInitialRankingFile;
                        Map<String, ScoreList> map = processInitialRankingFile(parameters.get("fbInitialRankingFile"));
                        results = map.get(qid);
                        //System.out.println(results);
                    } else {
                        //use the query to retrieve documents;
                        results = processQuery(query, model);
                        results.sort();
                    }

                    //use the Indri query expansion algorithm (Lecture 11, slides #30-36) to produce an expanded query;
                    //map term t to p(t|I)
                    Map<String, Double> termScore = new HashMap<>();
                    //Map<String, Long> termCtf = new HashMap<>();
                    long colLen = Idx.getSumOfFieldLengths("body");

                    for (int j = 0; j < fbDocs; j++) {
                        int docId = results.getDocid(j);
                        TermVector termVector = new TermVector(docId, "body");
                        // The 0'th entry is an empty string. It indicates a stopword.
                        for (int i = 1; i < termVector.stemsLength(); i++) {
                            //the string for the i'th stem, or null if the index is invalid.
                            String stem = termVector.stemString(i);
                            //Your query expansion software should ignore any candidate expansion term that contains a period ('.') or a comma (',').
                            if (stem.contains(".") || stem.contains(",")) continue;
                            if (!termScore.containsKey(stem)) termScore.put(stem, 0.0);
                            //if (!termCtf.containsKey(stem)) termCtf.put(stem, termVector.totalStemFreq(i));
                        }
                    }

                    for (String stem : termScore.keySet()) {
                        for (int j = 0; j < fbDocs; j++) {
                            int docId = results.getDocid(j);
                            double docScore = results.getDocidScore(j);
                            long docLen = Idx.getFieldLength("body", docId);
                            TermVector termVector = new TermVector(docId, "body");
                            //Get the index of stem in the stems vector, or -1 if the stems vector does not contain the stem.
                            int i = termVector.indexOfStem(stem);
                            //the frequency of the n'th stem in the current doc, or -1 if the index is invalid.
                            int tf = (i == -1) ? 0 : termVector.stemFreq(i);
                            //ctf of the i'th stem
                            long ctf = Idx.getTotalTermFreq("body", stem);
                            //p(t|d)=(tf+mu*ctf/colLen)/(docLen+mu)= (tf+fbMu*ctf/colLen)/(docLen+fbMu);
                            double pti = ((tf + fbMu * ctf * 1.0 / colLen) * 1.0 / (docLen + fbMu)) * docScore * Math.log((1.0 * colLen) / ctf);
                            termScore.put(stem, termScore.get(stem) + pti);
                        }
                    }

                    PriorityQueue<Map.Entry<String, Double>> pq = new PriorityQueue<>((p1, p2) -> p1.getValue().compareTo(p2.getValue()));
                    for (Map.Entry<String, Double> entry : termScore.entrySet()) {
                        pq.offer(entry);
                        if (pq.size() > fbTerms) pq.poll();
                    }

                    //get expanded query
                    StringBuilder sb = new StringBuilder();
                    StringBuilder raw = new StringBuilder();
                    sb.append("#wand ( ");
                    raw.append("#wand ( ");
                    for (int i = 0; i < fbTerms; i++) {
                        Map.Entry<String, Double> entry = pq.poll();
                        double w = entry.getValue();
                        raw.append(String.format("%.4f", w)).append(" ")
                                .append(entry.getKey()).append(" ");
                        String s = entry.getKey();
                        if (s.matches("\\d+(\\.\\d+)?")) s = s + "zzzzzzzzzzzzzzzzzzzzzzz";
                        w = (double) Math.round(w * 10000) / 10000;
                        sb.append(String.format("%.4f", w)).append(" ")
                                .append(s).append(" ");
                    }
                    sb.append(")");
                    raw.append(")");
                    String rawExpandedQuery = raw.toString();
                    String expandedQuery = sb.toString();
                    //System.out.println(expandedQuery);

                    //write the expanded query to a file
                    BufferedWriter expandedQueryWrite = new BufferedWriter(new FileWriter(fbExpansionQueryFile, true));
                    expandedQueryWrite.write(qid + ": " + rawExpandedQuery + "\n");
                    expandedQueryWrite.close();

                    //create a combined query as #wand (w qoriginal + (1-w) qexpandedquery);
                    sb = new StringBuilder();
                    sb.append("#wand (")
                            .append(fbOrigWeight).append(" ")
                            .append("#and (")
                            .append(query).append(" ) ")
                            .append(1 - fbOrigWeight).append(" ")
                            .append(expandedQuery).append(" )");

                    String combinedQuery = sb.toString();
                    //System.out.println(combinedQuery);

                    //use the combined query to retrieve documents;
                    results = processQuery(combinedQuery, model);
                }

                if (results != null) {
                    results.sort();
                    outputLength = parameters.containsKey("diversity:maxResultRankingLength")?
                            Integer.parseInt(parameters.get("diversity:maxResultRankingLength")):outputLength;
                    if (results.size()>outputLength) results.truncate(outputLength);
                    printResults(qid, results, outputWrite);
                    System.out.println();
                }
            }
            outputWrite.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
    }

    //output {157:<157.1: listing beatles songs,157.2: history beatles rock band,....>}
    static Map<String,List<String>> processIntentsFile(String intentsFile) throws IOException {
        //157.1: listing beatles songs -> 157, 157.1: listing beatles songs
        //157.2: history beatles rock band -> 157, 157.2: history beatles rock band
        //157.3: albums beatles release -> 157, 157.3: albums beatles release
        //157.4: names members beatles -> 157, 157.4: names members beatles
        Map<String,List<String>> map = new HashMap<>();
        LinkedList<String> queries = new LinkedList<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            ScoreList list = new ScoreList();
            input = new BufferedReader(new FileReader(intentsFile));
            while ((qLine = input.readLine()) != null) {
                String[] strs = qLine.split("\\.");
                if (strs.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one '.'.");
                }
                String qid = strs[0].trim();
                if(!map.containsKey(qid)){
                    map.put(qid,new ArrayList<>());
                }
                map.get(qid).add(qLine);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    static Map<String,ScoreList> getInitialRankingFile(String initialRankingFile, int maxLength) throws IOException {
        Map<String,ScoreList> map = new HashMap<>();
        LinkedList<String> queries = new LinkedList<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(initialRankingFile));
            while ((qLine = input.readLine()) != null) {
                String[] strs = qLine.split(" ");
                String qid = strs[0];
                if (!map.containsKey(qid)){
                    map.put(qid,new ScoreList());
                }
                int internalId = Idx.getInternalDocid(strs[2]);
                double score = Double.parseDouble(strs[4]);
                map.get(qid).add(internalId, score);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    //read query q from the query file
    //    use query q to retrieve documents;
    //    for each of query q's intents
    //      read intent qi from the diversity:intentsFile file;
    //      use query qi to retrieve documents;
    static Map<String,ScoreList> getInitialRankingFile(String queryFilePath,Map<String, List<String>> queryIntentsMap, RetrievalModel model, int maxLength) throws IOException {
        Map<String,ScoreList> map = new HashMap<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));

            //read query q from the query file
            while ((qLine = input.readLine()) != null) {
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }

                String qid = pair[0].trim();
                String query = pair[1];

                //use query q to retrieve documents;
                ScoreList results = processQuery(query,model);
                if (results != null){
                    results.sort();
                    if (results.size()>maxLength) results.truncate(maxLength);
                }
                map.put(qid,results);

                //for each of query q's intents
                //read intent qi from the diversity:intentsFile file;
                for (String intentsQLine : queryIntentsMap.get(qid)){
                    //output {157:<157.1: listing beatles songs,157.2: history beatles rock band,....>}
                    //qLineIntents 157.1: listing beatles songs
                    String[] strs = intentsQLine.split(":");
                    String intentsQid = strs[0].trim();
                    String intentsQuery= strs[1];

                    //use query qi to retrieve documents;
                    ScoreList intentsResults = processQuery(intentsQuery,model);
                    if (intentsResults != null){
                        intentsResults.sort();
                        if (intentsResults.size()>maxLength) intentsResults.truncate(maxLength);
                    }
                    map.put(intentsQid,intentsResults);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }


    static Map<String, ScoreList> processInitialRankingFile(String fbInitialRankingFile)
            throws IOException {
        Map<String, ScoreList> map = new HashMap<>();
        LinkedList<String> queries = new LinkedList<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            ScoreList list = new ScoreList();
            input = new BufferedReader(new FileReader(fbInitialRankingFile));
            while ((qLine = input.readLine()) != null) {
                String[] strs = qLine.split(" ");
                if (queries.isEmpty()) queries.push(strs[0]);
                if (!queries.peek().equals(strs[0])) {
                    map.put(queries.peek(), list);
                    queries.push(strs[0]);
                    list = new ScoreList();
                }
                int internalId = Idx.getInternalDocid(strs[2]);
                //int internalId = Idx.getInternalDocid("GX015-38-12832629");
                //if (strs[2].equals("GX015-38-12832629")) System.out.println(internalId);
                double score = Double.parseDouble(strs[4]);
                list.add(internalId, score);
            }
            map.put(queries.peek(), list);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    static void conductLearnToRank(Map<String, String> parameters, RetrievalModelLetor model) throws Exception {
        //Read training queries and relevance judgments from input files;
        String queryFilePath = parameters.get("queryFilePath");
        String outputPath = parameters.get("trecEvalOutputPath");
        int outputLength = Integer.parseInt(parameters.get("trecEvalOutputLength"));
        String trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        String trainingQueryFile = parameters.get("letor:trainingQueryFile");
        String trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
        String svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        String svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        Double svmRankParamC = Double.valueOf(parameters.get("letor:svmRankParamC"));
        String svmRankModelFile = parameters.get("letor:svmRankModelFile");
        String testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        String testingDocumentScores = parameters.get("letor:testingDocumentScores");
        String featureDisable ="";
        if(parameters.containsKey("letor:featureDisable")){
            featureDisable = parameters.get("letor:featureDisable");
        }

        //Read training queries and relevance judgments from input files;
        Map<String, String> trainingQuery = getQuery(trainingQueryFile);//qid - query
        Map<String, Map<String, Integer>> trainRelevanceJudgement = getRelevanceJudgement(trainingQrelsFile); //qid - <docid - degree>

        FeatureVector fv = new FeatureVector(model);

        //Calculate feature vectors for training documents;
        fv.setQuery(trainingQuery);
        fv.setRelevanceJudgement(trainRelevanceJudgement);
        fv.setFeatureDisable(featureDisable);
        List<String> qIds = new ArrayList<>();
        for (String qid:trainRelevanceJudgement.keySet()) qIds.add(qid);
        Collections.sort(qIds, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.valueOf(o1)-Integer.valueOf(o2);
            }
        });
        for (String qid:qIds){
            //System.out.println(qid);
            fv.docFeatureVector(qid);
            fv.normalize();
            //Write the feature vectors to a file;
            fv.saveToFile(trainingFeatureVectorsFile,qid,"train");
        }

        //Call SVMrank to train a retrieval model;
        Process cmdProc = Runtime.getRuntime().exec(new String[]{svmRankLearnPath, "-c",
                String.valueOf(svmRankParamC), trainingFeatureVectorsFile, svmRankModelFile});
        consume(cmdProc);

        //Read test queries from an input file;
        Map<String, String> testingQuery = getQuery(queryFilePath);
        //Use BM25 to get inital rankings for test queries;
        RetrievalModel bm25 = new RetrievalModelBM25(model.getK_1(), model.getB(),model.getK_3());
        // queryId - docId - score
        Map<String, Map<String, Integer>> testRelevanceJudgement = getRelevanceJudgement(queryFilePath, bm25);

        //System.out.println(testRelevanceJudgement.size());
        //Calculate feature vectors for the top 100 ranked documents (for each query);
        fv.setQuery(testingQuery);
        fv.setRelevanceJudgement(testRelevanceJudgement);

        qIds.clear();
        for (String qid:testingQuery.keySet()) qIds.add(qid);

        Collections.sort(qIds, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.valueOf(o1)-Integer.valueOf(o2);
            }
        });
        Map<String, List<String>> docList = new HashMap<>();
        for (String qid:qIds){
            fv.docFeatureVector(qid);
            fv.normalize();
            docList.put(qid,fv.getDocs());
            //Write the feature vectors to a file;
            fv.saveToFile(testingFeatureVectorsFile,qid,"test");
        }

        //Call SVMrank to calculate scores for test documents;
        cmdProc = Runtime.getRuntime().exec(new String[]{svmRankClassifyPath,
                testingFeatureVectorsFile, svmRankModelFile, testingDocumentScores});
        consume(cmdProc);

        //Read the scores produced by SVMrank; and
        List<Double> svmScores = processDocumentScores(testingDocumentScores);

        //read in the svmrank scores and re-rank the initial ranking based on the scores
        //Write the final ranking in trec_eval input format.
        BufferedWriter outputWrite = new BufferedWriter(new FileWriter(outputPath));

        int j = 0;
        for(String qid:qIds){
            List<String> docs = docList.get(qid);
            int len = docs.size();

            ScoreList results = new ScoreList();
            for (int i = 0; i < len; i++){
                if (j<svmScores.size())
                    results.add(Idx.getInternalDocid(docs.get(i)), svmScores.get(j++));
            }

            if (results != null) {
                results.sort();
                if (results.size() > outputLength) results.truncate(outputLength);
                printResults(qid,results, outputWrite);
                System.out.println();
            }
        }

        outputWrite.close();

    }

    static List<Double> processDocumentScores(String testingDocumentScores) throws IOException {
        List<Double> scores = new ArrayList<>();
        BufferedReader input = null;
        try {
            String line = null;
            input = new BufferedReader(new FileReader(testingDocumentScores));
            while ((line = input.readLine()) != null){
                scores.add(Double.valueOf(line.trim()));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
        return scores;
    }

    static Map<String, String> getQuery(String queryFilePath) throws IOException {
        Map<String, String> map = new HashMap<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));
            while ((qLine = input.readLine()) != null) {
                String[] pair = qLine.split(":");
                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }
                String qid = pair[0].trim();
                String query = pair[1];
                if (!map.containsKey(qid)) map.put(qid, query);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    static Map<String, Map<String, Integer>> getRelevanceJudgement(String trainingQrelsFile) throws IOException {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(trainingQrelsFile));
            while ((qLine = input.readLine()) != null) {
                String[] pair = qLine.split(" ");
                if (pair.length != 4) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }
                //Column 1 is the query id.
                //Column 2 is ignored.
                //Column 3 is the document id.
                //Column 4 indicates the degree of relevance (see below).
                String qid = pair[0].trim();
                String docId = pair[2].trim();
                int degree = Integer.valueOf(pair[3].trim());
                if (!map.containsKey(qid)) map.put(qid, new HashMap<>());
                if (!map.get(qid).containsKey(docId)) map.get(qid).put(docId, degree);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    static Map<String, Map<String, Integer>> getRelevanceJudgement(String queryFilePath, RetrievalModel model) throws IOException {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        BufferedReader input = null;
        try{
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));
            while ((qLine = input.readLine()) != null) {

                printMemoryUsage(false);
                System.out.println("Query " + qLine);
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }

                String qid = pair[0].trim();
                String query = pair[1];
                ScoreList results = processQuery(query, model);
                results.sort();
                if (results.size() > 100) results.truncate(100);
                Map<String,Integer> tmp = new HashMap<>();
                for (int i = 0; i < results.size(); i++){
                    tmp.put(Idx.getExternalDocid(results.getDocid(i)),i);
                }
                map.put(qid,tmp);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
        return map;
    }

    static void consume(Process cmdProc) throws Exception {

        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    /**
     * Print the query results.
     * <p>
     * STUDENTS::
     * This is not the correct output format. You must change this method so
     * that it outputs in the format specified in the homework page, which is:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result, BufferedWriter outputWrite) throws IOException {

        //System.out.println(queryName + ":  ");
        if (result.size() < 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(queryName).append(" Q0 dummyRecord 1 0 reference\n");
            //System.out.print(sb.toString());
            outputWrite.write(sb.toString());
        } else {
            for (int i = 0; i < result.size(); i++) {
                StringBuilder sb = new StringBuilder();
                sb.append(queryName)//query name
                        .append(" Q0 ").append(Idx.getExternalDocid(result.getDocid(i)))//doc id
                        .append(" ").append(i + 1)//rank
                        .append(" ").append(result.getDocidScore(i))//score
                        .append(" reference\n");
                //System.out.print(sb.toString());
                outputWrite.write(sb.toString());
            }
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();
        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        //  Store (all) key/value parameters in a hashmap.

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        //  Confirm that some of the essential parameters are present.
        //  This list is not complete.  It is just intended to catch silly
        //  errors.

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

}
