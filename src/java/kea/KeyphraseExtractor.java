package kea;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.PriorityQueue;

public class KeyphraseExtractor {

  public static final String LUCENE_INDEX_DIR = "index";
  public static final String FILE_NAME = "filename";
  public static final String FIELD_NAME = "content";
  public static final String DOC_SIZE_FIELD_NAME = "size";
  public static final String filename = "a0011e00";
  
  public static void main(String[] args) throws Exception {
    Discretization discr = new Discretization("cutp-model.txt");
    Model model = new Model("features-model.txt", discr);
    //model.print();
    List<KeyphraseScore> result = extractKeyphrases(model, discr);
    for(KeyphraseScore kps : result){
      System.out.println(kps.toString());
    }
  }

  static List<KeyphraseScore> extractKeyphrases(Model model, Discretization discr) throws IOException {
    
    Directory indexDir = Commons.getLuceneDirectory(LUCENE_INDEX_DIR);
    IndexReader ir = DirectoryReader.open(indexDir);
    IndexSearcher searcher = new IndexSearcher(ir);
    
    KeyphraseScorePQ pq = new KeyphraseScorePQ(100);

    try{
      for(int n = 1; n <= 3; n++){
        //System.out.printf("%s : reading %d-gram terms\n", new Date().toString(), n);
        String fieldName = Commons.getFieldName(FIELD_NAME, n);
        int docId = getDocId(searcher, filename);
        int docSize = getDocSize(searcher, docId);
        int numDocs = ir.numDocs();
        Terms terms = ir.getTermVector(docId, fieldName);
        TermsEnum te = terms.iterator();
        for(BytesRef rawPhrase = te.next(); rawPhrase != null; rawPhrase = te.next()){
          String phrase = rawPhrase.utf8ToString();
          double[] features = getFeatures(ir, fieldName, rawPhrase, docId, docSize, numDocs, false);
          //System.out.printf("%s (%g,%g)\n", phrase, features[0], features[1]);
          if(features != null){
            pq.insertWithOverflow(new KeyphraseScore(phrase, features, model, discr));
          }
        }
      }
    }
    finally{
      IOUtils.closeWhileHandlingException(ir);
    }
    
    List<KeyphraseScore> temp = new ArrayList<>();
    for(KeyphraseScore ks = pq.pop(); ks != null; ks = pq.pop()){
      temp.add(ks);
    }
    
    List<KeyphraseScore> result = new ArrayList<>();
    for(int i = temp.size() - 1; i >= 0; i--){
      KeyphraseScore kps = temp.get(i);
      if(!subphraseOf(kps, result)) result.add(kps);
    }
    
    return result;
  }
  
  static boolean subphraseOf(KeyphraseScore kps, List<KeyphraseScore> list){
    for(KeyphraseScore keyp : list){
      if(keyp.keyphrase.indexOf(kps.keyphrase) >= 0){
        //System.out.printf("* '%s' contains '%s'\n", keyp.keyphrase, kps.keyphrase);
        return true;
      }
    }
    return false;
  }
  
  static int getDocId(IndexSearcher searcher, String filename) throws IOException {
    TopDocs topDocs = searcher.search(new TermQuery(new Term(FILE_NAME, filename)), 1);
    if(topDocs.totalHits == 0) throw new RuntimeException(filename + " cannot be found in the index.");

    return topDocs.scoreDocs[0].doc;
  }
  
  static int getDocSize(IndexSearcher searcher, int docId) throws IOException {
    Document doc = searcher.doc(docId);
    return Integer.parseInt(doc.get(DOC_SIZE_FIELD_NAME));
  }
  
  static double[] getFeatures(IndexReader ir, String fieldName, BytesRef rawPhrase, int docId, int docSize, int numDocs, boolean inc)
      throws IOException {
    PostingsEnum de = MultiFields.getTermDocsEnum(ir, fieldName, rawPhrase);
    int ret = de.advance(docId);
    if(ret == PostingsEnum.NO_MORE_DOCS){
      throw new RuntimeException("no more docs...");
    }
    else{
      int freq = de.freq();
      if(freq < 2) return null;
      
      PostingsEnum pe = MultiFields.getTermPositionsEnum(ir, fieldName, rawPhrase);
      int ret2 = pe.advance(docId);
      if(ret2 == PostingsEnum.NO_MORE_DOCS){
        throw new RuntimeException("no more docs...");
      }
      else{
        double[] features = new double[2];
        int pos = pe.nextPosition();
        int docFreq = ir.docFreq(new Term(fieldName, rawPhrase));
        if(inc){
          docFreq++;
          numDocs++;
        }
        features[0] = Commons.calcTfIdf(freq, docSize, docFreq, numDocs);
        features[1] = Commons.calcFirstOccurrence(pos, docSize);
        
        return features;
      }
    }
  }
  
  public static class Discretization {
    
    private final double[] featuresTfIdf;
    private final double[] featuresDistance;
    
    public Discretization(String file) throws IOException {
      Reader r = null;
      BufferedReader br = null;
      try{
        r = new FileReader(file);
        br = new BufferedReader(r);
        
        // read cut points for tfidf
        String line = br.readLine();
        String[] values = line.trim().split("\\s");
        featuresTfIdf = new double[values.length];
        for(int i = 0; i < values.length; i++){
          featuresTfIdf[i] = Double.parseDouble(values[i]);
        }
        
        // read cut points for distance
        line = br.readLine();
        values = line.trim().split("\\s");
        featuresDistance = new double[values.length];
        for(int i = 0; i < values.length; i++){
          featuresDistance[i] = Double.parseDouble(values[i]);
        }
      }
      finally{
        IOUtils.closeWhileHandlingException(br);
        IOUtils.closeWhileHandlingException(r);
      }
    }
    
    public int discretizeTfIdf(double value){
      for(int i = 0; i < featuresTfIdf.length; i++){
        if(value < featuresTfIdf[i]) return i;
      }
      return featuresTfIdf.length;
    }
    
    public int discretizeDistance(double value){
      for(int i = 0; i < featuresDistance.length; i++){
        if(value < featuresDistance[i]) return i;
      }
      return featuresDistance.length;
    }
  }
  
  public static class Model {
    
    private final double[] probTfIdfYes;
    private final double[] probTfIdfNo;
    private final double[] probDistanceYes;
    private final double[] probDistanceNo;
    private final double priorProbYes;
    private final double priorProbNo;
    
    public Model(String file, Discretization discr) throws IOException {
      final int numTfIdf = discr.featuresTfIdf.length + 1;
      final int numDistance = discr.featuresDistance.length + 1;

      final int[] countTfIdfYes = new int[numTfIdf];
      final int[] countTfIdfNo = new int[numTfIdf];
      final int[] countDistanceYes = new int[numDistance];
      final int[] countDistanceNo = new int[numDistance];
      int countYes = 0, countNo = 0;
      
      Reader r = null;
      BufferedReader br = null;
      try{
        r = new FileReader(file);
        br = new BufferedReader(r);
        for(String line = br.readLine(); line != null; line = br.readLine()){
          String[] values = line.split("\\s");
          if(Boolean.parseBoolean(values[2])){
            countTfIdfYes[discr.discretizeTfIdf(Double.parseDouble(values[0]))]++;
            countDistanceYes[discr.discretizeDistance(Double.parseDouble(values[1]))]++;
            countYes++;
          }
          else{
            countTfIdfNo[discr.discretizeTfIdf(Double.parseDouble(values[0]))]++;
            countDistanceNo[discr.discretizeDistance(Double.parseDouble(values[1]))]++;
            countNo++;
          }
        }
      }
      finally{
        IOUtils.closeWhileHandlingException(br);
        IOUtils.closeWhileHandlingException(r);
      }
      
      priorProbYes = (double)countYes / (double)(countYes + countNo);
      priorProbNo = (double)countNo / (double)(countYes + countNo);

      probTfIdfYes = new double[numTfIdf];
      probTfIdfNo = new double[numTfIdf];
      probDistanceYes = new double[numDistance];
      probDistanceNo = new double[numDistance];
      
      for(int i = 0; i < numTfIdf; i++){
        probTfIdfYes[i] = (double)countTfIdfYes[i] / (double)countYes;
        probTfIdfNo[i] = (double)countTfIdfNo[i] / (double)countNo;
      }
      
      for(int i = 0; i < numDistance; i++){
        probDistanceYes[i] = (double)countDistanceYes[i] / (double)countYes;
        probDistanceNo[i] = (double)countDistanceNo[i] / (double)countNo;
      }
    }
    
    public void print(){
      System.out.print("tfidf | yes    = ");
      for(double p : probTfIdfYes){
        System.out.printf(" %8.6f", p);
      }
      
      System.out.print("\ntfidf | no     = ");
      for(double p : probTfIdfNo){
        System.out.printf(" %8.6f", p);
      }

      System.out.print("\ndistance | yes = ");
      for(double p : probDistanceYes){
        System.out.printf(" %8.6f", p);
      }

      System.out.print("\ndistance | no  = ");
      for(double p : probDistanceNo){
        System.out.printf(" %8.6f", p);
      }
      
      System.out.printf("\nP(yes) = %8.6f", priorProbYes);
      System.out.printf("\nP(no)  = %8.6f", priorProbNo);
    }
  }
  
  public static class KeyphraseScore {

    public final String keyphrase;
    public final double score;
    public final double tfidf;
    
    public KeyphraseScore(String keyphrase, double[] features, Model model, Discretization discr){
      this.keyphrase = keyphrase;
      this.tfidf = features[0];
      int discrTfIdf = discr.discretizeTfIdf(features[0]);
      int discrDistance = discr.discretizeDistance(features[1]);
      double probYes = model.priorProbYes * model.probTfIdfYes[discrTfIdf] * model.probDistanceYes[discrDistance];
      double probNo = model.priorProbNo * model.probTfIdfNo[discrTfIdf] * model.probDistanceNo[discrDistance];
      score = probYes / (probYes + probNo);
    }
    
    @Override
    public String toString(){
      return String.format("%s (%8.6f,%8.6f)", keyphrase, score, tfidf);
    }
  }
  
  static class KeyphraseScorePQ extends PriorityQueue<KeyphraseScore> {

    public KeyphraseScorePQ(int maxSize) {
      super(maxSize);
    }

    @Override
    public boolean lessThan(KeyphraseScore arg0, KeyphraseScore arg1) {
      if(arg0.score < arg1.score) return true;
      else if(arg0.score == arg1.score){ // tie breaker
        return arg0.tfidf < arg1.tfidf;
      }
      else return false;
    }
  }
}
