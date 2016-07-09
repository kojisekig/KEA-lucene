package kea;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

import com.rondhuit.commons.FileUtil;

public class KEAModelBuilder {
  
  public static final String TRAINING_DATA_DIR = "data/fao30";
  public static final String TRAINING_DATA_ENCODING = "ISO-8859-1";
  public static final String LUCENE_INDEX_DIR = "index";
  public static final String FILE_NAME = "filename";
  public static final String FIELD_NAME = "content";
  public static final String DOC_SIZE_FIELD_NAME = "size";
  public static final int KEYPHRASE_THRESHOLD = 2;

  public static void main(String[] args) throws Exception {
    System.out.printf("%s : started\n", new Date().toString());

    Map<String, Set<String>> knownKeyphrases = readKnownKeyphrases(TRAINING_DATA_DIR, TRAINING_DATA_ENCODING);
    indexing(TRAINING_DATA_DIR, TRAINING_DATA_ENCODING);
    KEAModel model = buildModel(knownKeyphrases);
    model.save("model.txt");

    System.out.printf("%s : completed\n", new Date().toString());
  }
  
  static Map<String, Set<String>> readKnownKeyphrases(String dir, String encoding) throws IOException {
    Map<String, Map<String, Integer>> knownKeyphrases = new HashMap<>();
    File indexersDir = new File(dir, "indexers");
    File[] indexerList = indexersDir.listFiles(new FileFilter(){
      @Override
      public boolean accept(File pathname) {
        return pathname.isDirectory();
      }
    });

    List<File> keyFiles = new ArrayList<>();
    for(File indexer : indexerList){
      File[] files = indexer.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".key");
        }
      });
      keyFiles.addAll(Arrays.asList(files));
    }

    for(File keyfile: keyFiles){
      InputStream is = null;
      Reader r = null;
      BufferedReader br = null;
      try{
        is = new FileInputStream(keyfile);
        r = new InputStreamReader(is, TRAINING_DATA_ENCODING);
        br = new BufferedReader(r);
        for(String keyphrase = br.readLine(); keyphrase != null; keyphrase = br.readLine()){
          incKeyphraseCount(knownKeyphrases, keyfile.getName(), keyphrase.trim());
        }
      }
      finally{
        IOUtils.closeWhileHandlingException(br);
        IOUtils.closeWhileHandlingException(r);
        IOUtils.closeWhileHandlingException(is);
      }
    }
    
    //printKnownKeyphrases(knownKeyphrases);
    
    Map<String, Set<String>> result = new HashMap<>();
    for(String file : knownKeyphrases.keySet()){
      Set<String> keyphrases = new HashSet<>();
      for(Entry<String, Integer> entry: knownKeyphrases.get(file).entrySet()){
        if(entry.getValue() >= KEYPHRASE_THRESHOLD){
          keyphrases.add(entry.getKey());
        }
      }
      result.put(file, keyphrases);
    }
    
    return result;
  }
  
  static void incKeyphraseCount(Map<String, Map<String, Integer>> knownKeyphrases, String filename, String keyphrase){
    String fn = filename.substring(0, filename.length() - 4);    // ".key".length() == 4
    Map<String, Integer> keyphraseCount = knownKeyphrases.get(fn);
    if(keyphraseCount == null){
      keyphraseCount = new HashMap<>();
      keyphraseCount.put(keyphrase, 1);
      knownKeyphrases.put(fn, keyphraseCount);
    }
    else{
      Integer count = keyphraseCount.get(keyphrase);
      if(count == null){
        keyphraseCount.put(keyphrase, 1);
      }
      else{
        keyphraseCount.put(keyphrase, ++count);
      }
    }
  }
  
  private static void printKnownKeyphrases(Map<String, Map<String, Integer>> knownKeyphrases){
    for(String file : knownKeyphrases.keySet()){
      System.out.printf("\n===== %s =====\n", file);
      for(Entry<String, Integer> entry: knownKeyphrases.get(file).entrySet()){
        System.out.printf("%s : %d\n", entry.getKey(), entry.getValue());
      }
    }
  }

  /*
   * this method presumes that documents and keyphrases files are under
   * the predetermined sub directories of the parent directory that is specified dir parameter
   */
  static void indexing(String dir, String encoding) throws IOException {
    FileUtil.deleteRecursively(LUCENE_INDEX_DIR);
    File docsDir = new File(dir, "documents");
    File[] docList = docsDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".txt");
      }
    });

    Directory indexDir = Commons.getLuceneDirectory(LUCENE_INDEX_DIR);
    Analyzer a = Commons.getKEAAnalyzer(FIELD_NAME);
    IndexWriterConfig iwc = new IndexWriterConfig(a);
    IndexWriter iw = new IndexWriter(indexDir, iwc);
    
    try{
      for(File docFile: docList){
        System.out.println(docFile.getName());
        InputStream is = null;
        Reader r = null;
        BufferedReader br = null;
        try{
          is = new FileInputStream(docFile);
          r = new InputStreamReader(is, encoding);
          br = new BufferedReader(r);
          StringBuilder sb = new StringBuilder();
          for(String line = br.readLine(); line != null; line = br.readLine()){
            sb.append(line);
          }
          String content = sb.toString();
          String filename = docFile.getName();
          String fn = filename.substring(0, filename.length() - 4);   // ".txt".length() == 4
          iw.addDocument(getDocumentWithTermVectors(fn, content));
        }
        finally{
          IOUtils.closeWhileHandlingException(br);
          IOUtils.closeWhileHandlingException(r);
          IOUtils.closeWhileHandlingException(is);
        }
      }
      iw.forceMerge(1, true);
    }
    finally{
      IOUtils.closeWhileHandlingException(iw);
    }
  }
  
  static Document getDocument(String fn, String content) throws IOException {
    Document doc = new Document();
    doc.add(new StringField(FILE_NAME, fn, Field.Store.YES));
    doc.add(new StoredField(DOC_SIZE_FIELD_NAME, Commons.getDocumentSize(content)));

    doc.add(new TextField(Commons.getFieldName(FIELD_NAME, 1), content, Field.Store.YES));
    doc.add(new TextField(Commons.getFieldName(FIELD_NAME, 2), content, Field.Store.YES));
    doc.add(new TextField(Commons.getFieldName(FIELD_NAME, 3), content, Field.Store.YES));
    return doc;
  }

  /*
   * No need to use this method for building the model. Use getDocument() method instead.
   */
  static Document getDocumentWithTermVectors(String fn, String content) throws IOException {
    Document doc = new Document();
    doc.add(new StringField(FILE_NAME, fn, Field.Store.YES));
    doc.add(new StoredField(DOC_SIZE_FIELD_NAME, Commons.getDocumentSize(content)));

    FieldType ft = new FieldType();
    ft.setStored(true);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    ft.setStoreTermVectors(true);
    doc.add(new Field(Commons.getFieldName(FIELD_NAME, 1), content, ft));
    doc.add(new Field(Commons.getFieldName(FIELD_NAME, 2), content, ft));
    doc.add(new Field(Commons.getFieldName(FIELD_NAME, 3), content, ft));
    return doc;
  }
  
  static KEAModel buildModel(Map<String, Set<String>> knownKeyphrases) throws IOException {
    
    Directory indexDir = Commons.getLuceneDirectory(LUCENE_INDEX_DIR);
    IndexReader ir = DirectoryReader.open(indexDir);
    KEAModel model = new KEAModel(ir, knownKeyphrases);

    try{
      for(int n = 1; n <= 3; n++){
        System.out.printf("%s : building %d-gram model\n", new Date().toString(), n);
        String fieldName = Commons.getFieldName(FIELD_NAME, n);
        Terms terms = MultiFields.getTerms(ir, fieldName);
        TermsEnum te = terms.iterator();
        for(BytesRef rawPhrase = te.next(); rawPhrase != null; rawPhrase = te.next()){
          String phrase = rawPhrase.utf8ToString();
          // use KEAStopFilter instead
          //if(stopWords(phrase, n)) continue;

          //System.out.printf("%s ", phrase);
          PostingsEnum de = MultiFields.getTermDocsEnum(ir, fieldName, rawPhrase);
          while(de.nextDoc() != PostingsEnum.NO_MORE_DOCS){
            int docId = de.docID();
            int freq = de.freq();
            PostingsEnum pe = MultiFields.getTermPositionsEnum(ir, fieldName, rawPhrase);
            int ret = pe.advance(docId);
            if(ret == PostingsEnum.NO_MORE_DOCS){
              System.out.printf("(NO_MORE_DOCS) %d\n", docId);
            }
            else{
              /*
              System.out.printf("%d(%d;", docId, de.freq());
              for(int i = 0; i < freq; i++){
                int pos = pe.nextPosition();
                System.out.printf(" %d", pos);
              }
              System.out.print("), ");
              */
              // get first position of the term in the doc (first occurrence)
              int pos = pe.nextPosition();
              model.add(docId, fieldName, phrase, freq, pos);
            }
          }
          //System.out.println();   // CRLF
        }
      }
    }
    finally{
      IOUtils.closeWhileHandlingException(ir);
    }
    
    return model;
  }
  
  static class KEAModel {
    public final Map<String, KEADocModel> dm;
    public final Map<String, Integer> docFreq;
    public final Map<String, Integer> docSize;
    final IndexReader reader;
    final IndexSearcher searcher;
    final int docNum;
    final Map<String, Set<String>> knownKeyphrases;

    public KEAModel(IndexReader reader, Map<String, Set<String>> knownKeyphrases){
      this.reader = reader;
      searcher = new IndexSearcher(reader);
      dm = new HashMap<>();
      docFreq = new HashMap<>();
      docSize = new HashMap<>();
      this.docNum = reader.numDocs();
      this.knownKeyphrases = knownKeyphrases;
    }
    
    public void add(int docId, String fieldName, String phrase, int freq, int pos) throws IOException {
      Document doc = searcher.doc(docId);
      String filename = doc.get(FILE_NAME);
      KEADocModel docModel = getDocModel(filename);
      docModel.setPhraseFeatures(phrase, freq, pos, docSize(docId, filename), docFreq(fieldName, phrase), docNum);
    }
    
    KEADocModel getDocModel(String filename){
      KEADocModel docModel = dm.get(filename);
      if(docModel == null){
        docModel = new KEADocModel();
        dm.put(filename, docModel);
      }
      return docModel;
    }
    
    int docSize(int docId, String filename) throws IOException {
      Integer size = docSize.get(filename);
      if(size == null){
        Document d = searcher.doc(docId);
        String s = d.get(DOC_SIZE_FIELD_NAME);
        size = Integer.parseInt(s);
        docSize.put(filename, size);
      }
      return size;
    }
    
    int docFreq(String fieldName, String phrase) throws IOException {
      Integer df = docFreq.get(phrase);
      if(df == null){
        int dfreq = reader.docFreq(new Term(fieldName, phrase));
        df = dfreq;
        docFreq.put(phrase, df);
      }
      return df;
    }
    
    public void save(String filename) throws IOException {
      PrintWriter pw = null, pwf = null;
      try{
        pw = new PrintWriter(filename);
        pwf = new PrintWriter("features-" + filename);
        for(String file: dm.keySet()){
          pw.printf("\n* file name = %s\n", file);
          pw.printf(" - doc size = %d\n", docSize.get(file));
          pw.println(" - known keyphrases");
          Set<String> knowKeyphraseSet = knownKeyphrases.get(file);
          for(String keyphrase: knowKeyphraseSet){
            pw.println(keyphrase);
          }
          
          pw.println(" - features");
          KEADocModel docModel = dm.get(file);
          for(String keyphrase: docModel.ps.keySet()){
            PhraseStats ps = docModel.ps.get(keyphrase);
            if(!ps.discard()){
              boolean class1 = knowKeyphraseSet.contains(keyphrase);
              pw.printf("%s = %d %g %g\n", keyphrase, class1 ? 1 : 0, ps.tfidf, ps.firstOccurrence);
              pwf.printf("%g %g %s\n", ps.tfidf, ps.firstOccurrence, class1);
            }
          }
        }
      }
      finally{
        IOUtils.close(pw);
        IOUtils.close(pwf);
      }
    }
  }
  
  static class KEADocModel {
    public final Map<String, PhraseStats> ps;
    
    public KEADocModel(){
      ps = new HashMap<>();
    }
    
    public void setPhraseFeatures(String phrase, int freq, int pos, int docSize, int docFreq, int docNum){
      ps.put(phrase, new PhraseStats(freq, pos, docSize, docFreq, docNum));
    }
  }
  
  static class PhraseStats {
    public final int freq;
    public final double tfidf;
    public final double firstOccurrence;
    
    public PhraseStats(int freq, int pos, int docSize, int docFreq, int docNum){
      this.freq = freq;
      tfidf = Commons.calcTfIdf(freq, docSize, docFreq, docNum);
      firstOccurrence = Commons.calcFirstOccurrence(pos, docSize);
    }
    
    public boolean discard(){
      return freq < 2;
    }
  }
    
  /*
    IndexSearcher searcher = new IndexSearcher(ir);
    Query q = new TermQuery(new Term(getFieldName(FIELD_NAME, 1), "animal"));
    TopDocs topDocs = searcher.search(q, 10);
    for(ScoreDoc scoreDoc : topDocs.scoreDocs){
      System.out.printf("%d : %f\n", scoreDoc.doc, scoreDoc.score);
    }
   */
  
}
