import java.io.*;
import java.util.*;
public class QryIopNear extends QryIop {
    private int dist;

    public QryIopNear(int dist) {
        this.dist = dist;
        //System.out.println("Required distance "+ dist);
    }

    public void initialize(RetrievalModel r) throws IOException{
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
                Vector<Integer> firstLoc = q_0.docIteratorGetMatchPosting().positions;
                while (firstLoc.size()>0){
                    boolean nearFound = true;
                    int curr = firstLoc.remove(0);
                    for (int i=1; i<this.args.size(); i++){
                        QryIop q_i = (QryIop)this.args.get(i);
                        q_i.locIteratorAdvancePast(curr);
                        if (!(q_i.locIteratorHasMatch()&&(q_i.locIteratorGetMatch()-curr)<=this.dist)){
                            nearFound =false;
                            break;
                        }
                        curr = q_i.locIteratorGetMatch();
                    }

                    //if match
                    if (nearFound) {//if match
                        //Record match
                        positions.add(curr);
                        for (Qry q: this.args){//Increment all loc iterators
                            ((QryIop)q).locIteratorAdvance();
                            if (!((QryIop)q).locIteratorHasMatch()) firstLoc.clear();
                        }
                    }else{//if no match
                        q_0.locIteratorAdvance();//Increment q0 loc iterator
                    }
                }
                if(positions.size()!=0) this.invertedList.appendPosting(docID, positions);
                for (Qry q : this.args) ((QryIop) q).docIteratorAdvancePast(docID);//Increment all doc iterators
            } else q_0.docIteratorAdvancePast(docID);
        }
    }
}
