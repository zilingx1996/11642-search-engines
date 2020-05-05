import java.io.IOException;
import java.util.*;

public class QryIopWindow extends QryIop {
    private int dist;

    public QryIopWindow(int dist) {
        this.dist = dist;
        //System.out.println("Required distance "+ dist);
    }

    public void initialize(RetrievalModel r) throws IOException {
        //Initialize doc iterators
        super.initialize(r);
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */

    protected void evaluate () throws IOException {
        //System.out.println("Entering evaluate function ");
        //  Create an empty inverted list.  If there are no query arguments,
        //  this is the final result.
        this.invertedList = new InvList (this.getField());

        if (args.size () < 2) return;

        QryIop q_0 = (QryIop)this.args.get(0);

        while (q_0.docIteratorHasMatch(null)){
            boolean matchFound = true;
            List<Integer> positions = new ArrayList<Integer>();
            int docID = q_0.docIteratorGetMatch();
            for (int i = 1; i < this.args.size(); i++){
                QryIop q_i = (QryIop)this.args.get(i);
                q_i.docIteratorAdvanceTo(docID);
                if (!(q_i.docIteratorHasMatch(null)
                        &&q_i.docIteratorGetMatch()==docID)){
                    matchFound = false;
                    break;
                }
            }
            //if find same document
            if (matchFound){
                boolean flag = true;
                while (flag){
                    //create a hashmap to store all the query and their current location
                    Map<QryIop,Integer> map = new HashMap<>();
                    for (Qry q:this.args){
                        QryIop q_i = (QryIop)q;
                        if (!q_i.locIteratorHasMatch()){
                            flag = false;
                            break;
                        } else map.put(q_i,q_i.locIteratorGetMatch());
                    }
                    //if the size of the map matches the size of args
                    //then it suggests that none of the iterator is exhausted
                    if (map.size()==this.args.size()){
                        //find the minimum location
                        int min = Collections.min(map.values());
                        //find the maximum location
                        int max = Collections.max(map.values());
                        //check if the window restriction satisfied
                        if (max-min<dist){//if satisfied
                            //record match
                            positions.add(max);
                            for (Qry q: this.args){
                                //advance all the location iterator
                                ((QryIop)q).locIteratorAdvance();
                                //if one of the location iterator is exhausted
                                //set flag to false to end loop
                                if (!((QryIop)q).locIteratorHasMatch()) {
                                    flag = false;
                                    break;
                                }
                            }
                        }else{
                            //find the query that has the minimum location
                            for (QryIop q: map.keySet()){
                                if (map.get(q)==min){
                                    //advance this iterator and continue
                                    q.locIteratorAdvance();
                                    break;
                                }
                            }
                        }
                    }
                }

                if(positions.size()!=0) this.invertedList.appendPosting(docID, positions);
                for (Qry q : this.args) ((QryIop) q).docIteratorAdvancePast(docID);//Increment all doc iterators
            } else q_0.docIteratorAdvancePast(docID);
        }
    }
}

