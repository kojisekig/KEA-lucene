package kea;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public final class Commons {
  
  public static double log2(double num){
    return Math.log(num) / Math.log(2.0);
  }
  
  public static double calcTfIdf(int freq, int docSize, int docFreq, int docNum){
    return (double)freq / (double)docSize * (-1) * log2((double)docFreq / (double)docNum);
  }
  
  public static double calcFirstOccurrence(int pos, int docSize){
    return (double)pos / (double)docSize;
  }
  
  public static String getFieldName(String baseNamem, int n){
    return String.format("%s_%d", baseNamem, n);
  }
  
  public static Directory getLuceneDirectory(String dir) throws IOException {
    Path path = FileSystems.getDefault().getPath(dir);
    return FSDirectory.open(path);
  }

  // feel free to add your stop words to this list if needed
  static final String STOP_WORDS = "a,an,and,are,as,at,be,but,by,for,if,in,into,is,it,no,not,of,on,or,such,that,the,their,then,there,these,they,this,to,was,will,with" // using Lucene's stop words
      + ",after,although,because,before,even,how,lest,once,since,than,though,till,unless,until,when,what,whatever,whenever,where,whereas,wherever,whether,which,whichever,while,who,whose,why" // conjunctions
      + ",about,above,across,ago,below,beside,besides,from,off,onto,over,past,through,toward,towards,under" // prepositions
      + ",he,her,hers,herself,him,himself,his,i,its,itself,me,mine,my,myself,our,ours,ourselves,she,theirs,them,themselves,us,we,you,your,yours,yourself,yourselves" // pronouns
      + ",been,became,become,began,begin,begun,came,come,coming,did,didn't,do,don't,does,doesn't,doing,done,gave,get,getting,give,given,go,gone,got,gotten,had,have,keep,kept,knew,know,known,leave,left,let,went,were" // anomalous verbs
      + ",can,cannot,can't,could,couldn't,made,make,ought,said,say,send,sent,shall,should,shouldn't,take,taken,tell,think,thought,told,took,will,would,wouldn't";
  
  /*
  static final Set<String> stopWords = new HashSet<>();
  static final Set<String> beginStopWords = new HashSet<>();
  static final Set<String> endStopWords = new HashSet<>();
   */
  public static final CharArraySet stopWords = new CharArraySet(STOP_WORDS.length(), true);
  public static final CharArraySet beginStopWords = new CharArraySet(STOP_WORDS.length(), true);
  public static final CharArraySet endStopWords = new CharArraySet(STOP_WORDS.length(), true);
  static {
    String[] words = STOP_WORDS.split(",");
    for(String word: words){
      stopWords.add(word);
      beginStopWords.add(word + " ");
      endStopWords.add(" " + word);
    }
  }
  public static Pattern patNumbers = Pattern.compile("^\\d+$");
  
  public static boolean stopWords(String phrase, int n){
    if(n == 1){
      if(stopWords.contains(phrase)) return true;
      
      if(phrase.length() == 1) return true;
      
      Matcher m = patNumbers.matcher(phrase);
      return m.find();
    }
    else{
      assert n >= 2;
      /*
      for(String stopWord: beginStopWords){
        if(phrase.startsWith(stopWord)) return true;
      }
      for(String stopWord: endStopWords){
        if(phrase.endsWith(stopWord)) return true;
      }
      */
      
      // Let's use quicker solution...
      String words[] = phrase.split(" ");
      if(beginStopWords.contains(words[0] + " ")) return true;
      else if(endStopWords.contains(" " + words[words.length - 1])) return true;
      
      return false;
    }
  }
  
  public static Analyzer getKEAAnalyzer(String fieldName){
    Map<String, Analyzer> amap = new HashMap<>();
    amap.put(Commons.getFieldName(fieldName, 1), new KEAAnalyzer(1));
    amap.put(Commons.getFieldName(fieldName, 2), new KEAAnalyzer(2));
    amap.put(Commons.getFieldName(fieldName, 3), new KEAAnalyzer(3));
    return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), amap);
  }
  
  /*
   * Count the number of words in a document using Analyzer
   */
  public static int getDocumentSize(String text) throws IOException {
    // take into account for stop words to count the number of words in a doc
    Analyzer a = new StandardAnalyzer(CharArraySet.EMPTY_SET);
    TokenStream stream = a.tokenStream("dummy", text);
    stream.reset();
    int count = 0;
    while(stream.incrementToken()){
      count++;
    }
    stream.end();
    stream.close();
    
    return count++;
  }
}
