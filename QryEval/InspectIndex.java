/*
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.util.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

/**
 *  A simple commandline utility for inspecting Lucene 8 indexes.
 *  Run it to see a simple usage message.
 */
public class InspectIndex {

    private static String externalIdField = new String ("externalId");

    static String usage =
	"Usage:  java " +
	System.getProperty("sun.java.command") +
	" -index INDEX_PATH\n\n" +
	"where options include\n" +
        "    -list-doc IDOCID\n" +
	"\t\t\tlist the contents of the document with internal docid\n" +
	"\t\t\tIDOCID\n" +
        "    -list-docids\tlist the external docids of each document\n" +
        "    -list-edocid IDOCID\n" +
        "\t\t\tlist the external docid of the document\n" +
        "\t\t\twith internal docid of IDOCID\n" +
        "    -list-idocid EDOCID\n" +
        "\t\t\tlist the internal docid of the document\n" +
        "\t\t\twith external docid of EDOCID\n" +
	"    -list-fields\tlist the fields in the index\n" +
	"    -list-metadata IDOCID\n" +
	"\t\t\tdisplay the metadata fields for the document\n" +
        "\t\t\twith internal docid of IDOCID\n" +
	"    -list-postings TERM FIELD\n" +
	"\t\t\tdisplay the posting list entries for\n" +
	"\t\t\tterm TERM in field FIELD\n" +
	"    -list-postings-sample TERM FIELD\n" +
	"\t\t\tdisplay the first few posting list entries for\n" +
	"\t\t\tterm TERM in field FIELD\n" +
	"    -list-stats\n" +
	"\t\t\tdisplay corpus statistics\n" +
	"    -list-terms FIELD" +
	"\tdisplay the term dictionary for field FIELD\n" +
	"    -list-termvector IDOCID\n" +
	"\t\t\tdisplay the term vectors for all fields in the document\n" +
	"\t\t\twith internal IDOCID\n" +
	"    -list-termvector-field IDOCID FIELD\n" +
	"\t\t\tdisplay the term vector for FIELD in the document\n" +
	"\t\t\twith internal IDOCID\n";

    /**
     *  Print an error mesage if the docid is not valid for the reader.
     *  @param reader An IndexReader
     *  @param docid An internal document id
     *  @return true if the docid is valid, false otherwise
     */
    public static boolean checkDocid (IndexReader reader, long docid) {

	if ((docid < 0) || (docid >= reader.numDocs ())) {
	    System.out.println ("ERROR:  " + docid + " is a bad document id.");
	    return false;
	};

	return true;
    }

    /**
     *  Get the length of the field (which includes stopwords).
     *  @param reader An IndexReader (probably a CompositeReader)
     *  @param docid An internal document id
     *  @param fieldName The name of the field
     *  @return the field length if available, otherwise -1
     *  @throws IOException Error accessing the Lucene index.
     */
    public static long getFieldLength (IndexReader reader, int docid, String fieldName)
	throws IOException {
	
	LeafReaderContext leafContext = getLeafReaderContext (reader, docid);
	int leafDocid = docid - leafContext.docBase;
	LeafReader leafReader = leafContext.reader ();
	NumericDocValues norms = leafReader.getNormValues (fieldName);
	long fieldLength = -1;
	    
	if (norms != null) {
	    norms.advanceExact (leafDocid);
	    fieldLength = norms.longValue();
	}
	    
	return fieldLength;
    }

    /**
     *  Get the context of the LeafReader that contains the specified document.
     *  @param reader An IndexReader (probably a CompositeReader)
     *  @param docid An external document id
     *  @return the LeafReaderContext that contains the document, or null
     *  @throws IOException Error accessing the Lucene index.
     */
    public static LeafReaderContext getLeafReaderContext (IndexReader reader, String docid)
	throws Exception, IOException {

	Term term = new Term (externalIdField, docid);

	if (reader.docFreq (term) > 1)
	    throw new Exception ("Multiple matches for external id " + docid);

	for (LeafReaderContext leafContext : reader.leaves()) {
	    LeafReader leafReader = leafContext.reader();
	    if (leafReader.postings (term) != null) {
		return (leafContext);
	    };
	};
	
	return null;
    }

    /**
     *  Get the context of the LeafReader that contains the specified document.
     *  @param reader An IndexReader (probably a CompositeReader)
     *  @param docid An internal document id
     *  @return the LeafReaderContext that contains the document, or null
     */
    public static LeafReaderContext getLeafReaderContext (IndexReader reader, long docid) {

	LeafReader leafReader = null;

	for (LeafReaderContext leafContext : reader.leaves()) {
	    leafReader = leafContext.reader();
	    int minDocid = leafContext.docBase;
	    int maxDocid = leafContext.docBase + leafReader.numDocs();
	    if ((docid >= minDocid) && (docid < maxDocid)) {
		return leafContext;
	    };
	};

	return null;
    }

    /**
     *  The main method for the InspectIndex application.
     *  @param args A list of commandline arguments.
     *  @throws IOException Error accessing the Lucene index.
     */
    public static void main(String[] args) throws IOException, Exception {

	IndexReader reader = null;

	//  Opening the index first simplifies the processing of the
	//  rest of the command line arguments.

	for (int i=0; i < args.length; i++) {
	    if (("-index".equals (args[i])) &&
		((i+1) < args.length)) {
		reader = DirectoryReader.open (
			     FSDirectory.open (Paths.get (args[i+1])));

		if (reader == null) {
		    System.err.println ("Error:  Can't open index " +
					args[i+1]);
		    System.exit (1);
		};

		break;
	    };
	};

	if (reader == null) {
	    System.err.println (usage);
	    System.exit (1);
	};

	//  Process the command line arguments sequentially.

	for (int i=0; i < args.length; i++) {

	    if ("-index".equals(args[i])) {

		//  Handled in the previous loop, so just skip the argument.

		i++;

	    } else if ("-list-edocid".equals(args[i])) {

	      System.out.println  ("-list-edocid:");
	      
	      if ((i+1) >= args.length) {
		System.out.println (usage);
		break;
	      };

	      Document d = reader.document (Integer.parseInt (args[i+1]));

	      System.out.println ("Internal docid --> External docid: " +
				  args[i+1] + " --> " + d.get ("externalId"));

	      i += 1;
	    } else if ("-list-idocid".equals(args[i])) {

	      System.out.println  ("-list-idocid:");
	      
	      if ((i+1) >= args.length) {
		System.out.println (usage);
		break;
	      };

	      listInternalDocid(reader, args[i+1]);

	      i += 1;
	    } else if ("-list-doc".equals(args[i])) {

		if ((i+1) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listDoc (reader, Integer.parseInt (args[i+1]));

		i += 1;

	    } else if ("-list-docids".equals(args[i])) {

	      System.out.println  ("-list-docids:");
	      
	      for (int j=0; j<reader.numDocs(); j++) {
		Document d = reader.document (j);
		System.out.println ("Internal --> external docid: " +
				    j + " --> " + d.get ("externalId"));
	      };

	    } else if ("-list-fields".equals(args[i])) {

		FieldInfos fields = FieldInfos.getMergedFieldInfos (reader);

		System.out.print ("\nNumber of fields:  ");

		if (fields == null)
		    System.out.println ("0");
		else {
		    System.out.println (fields.size());

		    for (FieldInfo f : fields) {
			System.out.println ("\t" + f.name);
		    }
		};

	    } else if ("-list-metadata".equals(args[i])) {

		if ((i+1) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listMetadata (reader, Integer.parseInt (args[i+1]));

		i += 1;

	    } else if ("-list-postings".equals(args[i])) {

		if ((i+2) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listPostings (reader, args[i+1], args [i+2],
			      Integer.MAX_VALUE);
		i += 2;

	    } else if ("-list-postings-sample".equals(args[i])) {

		if ((i+2) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listPostings (reader, args[i+1], args [i+2], 5);
		i += 2;

	    } else if ("-list-stats".equals(args[i])) {

		System.out.println ("Corpus statistics:");
		System.out.println ("\tnumdocs\t\t" + reader.numDocs());
		System.out.println ("\turl:\t" +
				    "\tnumdocs=" +
				    reader.getDocCount ("url") +
				    "\tsumTotalTF=" +
				    reader.getSumTotalTermFreq("url") +
				    "\tavglen="+
				    reader.getSumTotalTermFreq("url") /
				    (float) reader.getDocCount ("url"));

		System.out.println ("\tkeywords:" +
				    "\tnumdocs=" +
				    reader.getDocCount ("keywords") +
				    "\tsumTotalTF=" +
				    reader.getSumTotalTermFreq("keywords") +
				    "\tavglen="+
				    reader.getSumTotalTermFreq("keywords") /
				    (float) reader.getDocCount ("keywords"));

		System.out.println ("\ttitle:\t" +
				    "\tnumdocs=" +
				    reader.getDocCount ("title") +
				    "\tsumTotalTF=" +
				    reader.getSumTotalTermFreq("title") +
				    "\tavglen="+
				    reader.getSumTotalTermFreq("title") /
				    (float) reader.getDocCount ("title"));

		System.out.println ("\tbody:\t" +
				    "\tnumdocs=" +
				    reader.getDocCount ("body") +
				    "\tsumTotalTF=" +
				    reader.getSumTotalTermFreq("body") +
				    "\tavglen="+
				    reader.getSumTotalTermFreq("body") /
				    (float) reader.getDocCount ("body"));
		
		System.out.println ("\tinlink:\t" +
				    "\tnumdocs=" +
				    reader.getDocCount ("inlink") +
				    "\tsumTotalTF=" +
				    reader.getSumTotalTermFreq("inlink") +
				    "\tavglen="+
				    reader.getSumTotalTermFreq("inlink") /
				    (float) reader.getDocCount ("inlink"));

	    } else if ("-list-terms".equals(args[i])) {

		if ((i+1) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listTermDictionary (reader, args[i+1]);
		i += 1;

	    } else if ("-list-termvector".equals(args[i])) {

		if ((i+1) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listTermVectors (reader, args[i+1]);
		i += 1;

	    } else if ("-list-termvector-field".equals(args[i])) {

		if ((i+2) >= args.length) {
		    System.out.println (usage);
		    break;
		};

		listTermVectorField (reader, args[i+1], args[i+2]);
		i += 2;

	    } else
		System.err.println ("\nWarning:  Unknown argument " + args[i]
				    + " ignored.");
	};

	//  Close the index and exit gracefully.

        reader.close();
    }

    /**
     *  Displays the stored fields in a document.
     *  @param reader An IndexReader
     *  @param docid An internal document id
     *  @return void
     */
    static void listDoc (IndexReader reader, Integer docid)
	throws IOException  {

	System.out.println ("\nDocument:  docid " + docid);

	if (! checkDocid (reader, docid))
	    return;

	System.out.println (reader.document (docid).toString());
    }

    /**
     *  External to internald document id conversion.
     *  @param reader An IndexReader
     *  @param externalId An external document id
     *  @return void
     */
    static void listInternalDocid (IndexReader reader, String externalId)
	throws Exception  {

	LeafReaderContext leafContext =
	    getLeafReaderContext (reader, externalId);

	if (leafContext == null) 
	    throw new Exception ("External id " + externalId + " not found.");

	Term term = new Term (externalIdField, externalId);
	LeafReader leafReader = leafContext.reader();
	PostingsEnum postings = leafReader.postings (term);

	if (postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
	    long internalId = leafContext.docBase + postings.docID();
	    System.out.println ("External docid --> Internal docid: " +
				externalId + " --> " + internalId);
	    System.out.println ("base: " + leafContext.docBase + ", " +
				"inc: " + postings.docID());
	    return;
	};

	throw new Exception ("External id should exist, but isn't found.");
    }

    /**
     *  Display the stored fields in a document.
     *  @param docid The internal document id of the document
     *  @param reader An IndexReader
     */
    static void listMetadata (IndexReader reader, Integer docid)
	throws IOException  {

	System.out.println ("\nMetadata:  docid " + docid);

	if ((docid < 0) ||
	    (docid >= reader.numDocs ())) {
		System.out.println ("ERROR:  " +
				    docid + " is a bad document id.");
		return;
	};

	//  Iterate over the fields in this document.

	Document d = reader.document (docid);
	List<IndexableField> fields = d.getFields();

	Iterator<IndexableField> fieldIterator = fields.iterator ();

	while (fieldIterator.hasNext()) {
	    IndexableField field = fieldIterator.next();

	    if (field.fieldType().indexOptions() == IndexOptions.DOCS) {
		String fieldName = field.name();
		String[] fieldValues = d.getValues (fieldName);
		System.out.println ("  Field: " + fieldName + 
				    "  length: " + fieldValues.length);
		for (int i=0; i<fieldValues.length; i++) {
		    System.out.println ("    " + fieldValues[i]);
		}
	    }
	}
    }

    /**
     *  Displays the first n postings for a term in a field in an
     *  index (specified by reader).  Set n to MAX_VALUE to display
     *  all postings.
     *  @param reader An IndexReader
     */
    static void listPostings (IndexReader reader, String termString,
			      String field, Integer n) throws IOException  {

	System.out.println ("\nPostings:  " + termString + " " + field);

	//  Prepare to access the index.

	BytesRef termBytes = new BytesRef (termString);
	Term term = new Term (field, termBytes);

	//  Lookup the collection term frequency (ctf).

	long df = reader.docFreq (term);
	System.out.println ("\tdf:  " + df);

	long ctf = reader.totalTermFreq (term);
	System.out.println ("\tctf:  " + ctf);

	if (df < 1)
	    return;

	//  Iterate through the first n postings.  Lucene indexes have
	//  segments, so postings must be retrieved from each segment.

	long count = 0;

	for (LeafReaderContext context : reader.leaves()) {

	    PostingsEnum postings =
		context.reader().postings (term, PostingsEnum.POSITIONS);

	    while ((count < n) &&
		   (postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)) {
	    
	        int docid = context.docBase + postings.docID();
	        System.out.println ("\tdocid: " + docid);
		int tf = postings.freq();
		System.out.println("\ttf: " + tf);
		System.out.print("\tPositions: ");

		for (int j=0; j<tf; j++) {
		    int pos = postings.nextPosition();
		    System.out.print (pos + " ");
		}
			
		System.out.println ("");
	    
		count++;
	    };
	}

	return;
    }

    /**
     *  Displays the term dictionary for a field.
     *  @param reader An IndexReader
     */
    static void listTermDictionary (IndexReader reader, String fieldName)
	throws IOException {

	System.out.println ("\nTerm Dictionary:  field " + fieldName);

	//  Each index segment has its own term dictionary.  Most of
	//  our indexes will have just a single segment, so it isn't
	//  worth merging them.  (GOV2 AND CW09 HAVE MULTIPLE SEGMENTS)

	for (LeafReaderContext leafContext : reader.leaves()) {

	    if (reader.leaves().size() > 1) {
		System.out.println ("  Index segment " + leafContext.ord);
	    };
	    
	    LeafReader leaf = leafContext.reader();
	    Terms leafTerms = leaf.terms (fieldName);

	    if ((leafTerms == null) ||
		(leafTerms.size () == -1))
		System.out.println ("    The term dictionary is empty.");
	    else {
		System.out.println ("    Vocabulary size: " +
				    leafTerms.size () + " terms");
	    
		TermsEnum ithTerm = leafTerms.iterator ();
	    
		while (ithTerm.next() != null){
		    System.out.format ("      %-30s %d %d\n",
				       ithTerm.term().utf8ToString(),
				       ithTerm.docFreq (),
				       ithTerm.totalTermFreq ());
		};
	    };
	};
    };

    /**
     *  Displays the term vectors for all of the fields in a document
     *  in an index (specified by reader).
     *  @param reader An IndexReader
     */
    static void listTermVectors (IndexReader reader, String docidString)
	throws IOException {

	System.out.println ("\nTermVector:  docid " + docidString);

	int docid = Integer.parseInt (docidString);

	if (! checkDocid (reader, docid))
	    return;

	//  Iterate over the fields in this document.

	Fields fields = reader.getTermVectors (docid);

	Iterator<String> fieldIterator = fields.iterator ();

	while (fieldIterator.hasNext()) {
	    String fieldName = fieldIterator.next();
	    System.out.println ("  Field: " + fieldName);
	    System.out.println ("    Stored length: " +
				getFieldLength (reader, docid, fieldName));
	    Terms terms = fields.terms (fieldName);
	    termVectorDisplay (terms);
	};
    }

    /**
     *  Displays the term vector for a field in a document in an index
     *  (specified by reader).
     *  @param reader An IndexReader
     *  @param docidString An external document id
     *  @return void
     */
    static void listTermVectorField (IndexReader reader,
				     String docidString,
				     String field) throws IOException {

	System.out.println ("\nTermVector:  docid " +
			    docidString + ", field " + field);

	int docid = Integer.parseInt (docidString);

	if (! checkDocid (reader, docid))
	    return;

	System.out.println ("    Stored length: " +
			    getFieldLength (reader, docid, field));
	Terms terms = reader.getTermVector (docid, field);
	termVectorDisplay (terms);
    }

    /**
     *  Utility function to display a term vector.
     *  @param terms Terms to display
     *  @return void
     */
    static void termVectorDisplay (Terms terms) throws IOException {

	if ((terms == null) ||
	    (terms.size () == -1))
	    System.out.println ("    The field is not stored.");
	else {

	    //  The terms for this field are stored.

	    System.out.println ("    Vocabulary size: " +
				terms.size () + " terms");
	    
	    TermsEnum ithTerm = terms.iterator ();

	    //  Iterate over the terms in this document.  Information
	    //  about a term's occurrences (tf and positions) is
	    //  accessed via the indexing API, which returns inverted
	    //  lists that describe (only) the current document.

	    int ord = 0;

	    System.out.format ("      %10s %-19s %s positions",
			       " ",
			       "term",
			       "tf");
	    System.out.println ();

	    while (ithTerm.next() != null){
		System.out.format ("      %10d %-20s %d ",
				   ord,
				   ithTerm.term().utf8ToString(),
				   ithTerm.totalTermFreq ());
		ord ++;
		PostingsEnum currDoc =
		    ithTerm.postings (null, PostingsEnum.ALL);
		currDoc.nextDoc ();

		for (int jthPosition=0; jthPosition<ithTerm.totalTermFreq(); jthPosition++)
		    System.out.print (currDoc.nextPosition () + " ");

		System.out.println ();
	    };
	};
    }
}
