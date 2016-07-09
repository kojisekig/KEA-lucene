package kea;

import java.io.IOException;
import java.util.regex.Matcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

public class KEAAnalyzer extends Analyzer {
  
  private final int n;
  
  public KEAAnalyzer(int n){
    this.n = n;
  }

  @Override
  protected TokenStreamComponents createComponents(String fieldName) {
    Tokenizer source = new StandardTokenizer();
    TokenStream lcf = new LowerCaseFilter(source);
    if(n == 1){
      TokenStream stf = new KEAStopFilter(lcf, n, Commons.stopWords, Commons.beginStopWords, Commons.endStopWords);
      return new TokenStreamComponents(source, stf);
    }
    else{
      assert n >= 2;
      ShingleFilter shf = new ShingleFilter(lcf, n, n);
      shf.setOutputUnigrams(false);
      KEAStopFilter keasf = new KEAStopFilter(shf, n, Commons.stopWords, Commons.beginStopWords, Commons.endStopWords);
      return new TokenStreamComponents(source, keasf);
    }
  }
  
  public static class KEAStopFilter extends FilteringTokenFilter {

    private final int n;
    private final CharArraySet stopWords, beginStopWords, endStopWords;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public KEAStopFilter(TokenStream in, int n, CharArraySet stopWords, CharArraySet beginStopWords, CharArraySet endStopWords) {
      super(in);
      this.n = n;
      this.stopWords = stopWords;
      this.beginStopWords = beginStopWords;
      this.endStopWords = endStopWords;
    }

    @Override
    protected boolean accept() throws IOException {
      String phrase = termAtt.toString();
      if(n == 1){
        if(stopWords.contains(phrase)) return false;
        
        if(phrase.length() == 1) return false;
        
        Matcher m = Commons.patNumbers.matcher(phrase);
        return !m.find();
      }
      else{
        assert n >= 2;
        String words[] = phrase.split(" ");
        if(beginStopWords.contains(words[0] + " ")) return false;
        else if(endStopWords.contains(" " + words[words.length - 1])) return false;
        return true;
      }
    }
    
  }
}
