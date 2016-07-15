This project, KEA-lucene, is an [Apache Lucene](https://lucene.apache.org/core/) implementation of [KEA](http://www.nzdl.org/Kea/).

# What's KEA ?

[KEA](http://www.nzdl.org/Kea/) is a program developed by the University of Waikato in New Zealand that automatically extracts key phrases (keywords) from natural language documents. KEA stands for Keyphrase Extraction Algorithm and sometimes indicates the algorithm itself that constitutes KEA program.

Key phrases assigned to documents are semantic metadata of the documents and could be described as a very short version of its summary. You can, therefore, grasp the outline of a document by going through the list of key phrases, which are metadata of document, even when you don't have time to read the whole document. Let's say you are doing a research and have limited time. There are a pile of related documents in front of you but you don't have enough time to go through everything. In such case, you can view key phrases assigned to documents first and start reading only the documents that seem relevant to your research. 

Many academic papers have key phrases assigned by their authors. Most of general documents and books, however, do not have key phrases assigned. KEA is a program to automatically extracts key phrases from such documents. KEA is a supervised machine learning program that reads in documents that have key phrases assigned by their authors and learns the features of them so that it can automatically extract key phrases from unknown documents. KEA is an algorithm that works independent of language (English, Japanese, etc.).

## Relationship between Key Phrases Extraction and Information Retrieval

Now I should mention the relationship between key phrase extraction and information retrieval as this article is about extracting key phrases from Lucene index.

First, we should note that the authors of KEA use key phrases instead of keywords. Keyword usually reminds me of an important (key) word. The authors, however, use phrase (two or more words) instead of word to emphasize that the program is able to extract not only one but two or more words from a document.

What good things would happen to information retrieval if you could extract key phrases from Lucene index? The first thing comes to my mind is query suggestion (or "auto-complete"). Lucene index by any means manages character strings by words and suggestions are made by words as well. However, continuous multiple words could be suggested at once and the value would increase more if you can automatically extract key phrases. The same applies to "Did you mean" search.

It also helps users easily select documents by displaying the key phrases of documents in place of or along side of the highlight function while displaying search results. In addition, being able to extract key phrases can be a big advantage as they can be keys for facet.

## Processing Overview of KEA

You are encouraged to read through [KEA Paper](http://www.cs.waikato.ac.nz/~ml/publications/2005/chap_Witten-et-al_Windows.pdf) as it is not particularly hard to read. Here, I will give you a minimum explanation so that you can understand how to implement KEA using Lucene library that will be described later in this article.

The processes of KEA are roughly divided into "Learning Process" and "Key Phrase Extraction Process" where the both include a common process called key phrase candidate listing. Those key phrase candidates will be mechanically listed. During the learning process, the process learns how easy (or how difficult) for a listed key phrase candidate to be a key phrase. Then, during the key phrase extraction, the process refers to the learned probabilistic model to give scores to many key phrase candidates and display them from the highest score to the lowest. During the actual key phrase extraction process, the ranked key phrase candidates that have a score lower than a certain point will be cut off from the list.

The KEA implement explained in this article utilizes Lucene to a great degree. In the learning program, we will create a Lucene index that has known (with key phrases assigned by the author) document and create a model file from that. In a program that extracts key phrases from unknown document (without key phrases), the document has to be registered in a Lucene index before extracting key phrases from it.

### Enumeration of Key Phrase Candidate 

KEA enumerates 1 to up to 3 successive words as key phrase candidates. For example, if you have a following document:

> Tokyo governor likes to go to Yugawara.

then, KEA enumerates the following 10 key phrase candidates.

* tokyo
* governor
* like
* go
* yugawara (*)
* tokyo governor (*)
* governor like
* tokyo governor like
* like to go
* go to yugawara

During key phrase candidate enumeration, phrases starting with stop word or ending with stop word will not be candidates. Therefore, independent "to", "like(s) to", or "to go" will not be listed as candidates. Also, normalization of character or word that is often performed in information retrieval and NLP is done during this step. So, Tokyo and Yugawara become "tokyo" and "yugawara" while "likes" is stemmed to become "like".

### Model Learning in KEA

KEA uses Naive Bayes classifier to learn how easy (P[yes]) or how difficult (P[no]) the data with labels (document data that has key phrases assigned by the author) of key phrase candidates will become key phrases. In particular, the following formula will be used.

> P[yes] = Y / (Y + N) * Pt[t|yes] * Pd[d|yes]  
> P[no] = N / (Y + N) * Pt[t|no] * Pd[d|no]

Here, portions "Y / (Y + N)" and "N / (Y + N)" are prior probabilities determined by key phrase candidates. If the ones with asterisks are key phrases, then the prior probabilities would become as follows.

> Y / (Y + N) = 2 / 10  
> N / (Y + N) = 8 / 10

KEA uses 2 feature values to express phrase P in document D. The first is TF*IDF and is calculated as follows.

> TF*IDF = freq(P,D) / size(D) * (-log(df(P) / N))

freq(P,D) is the number of times phrase P appears in document D, size(D) is the number of words in document, df(P) is the number of documents that include phrase P, and N is the total number of documents. We use 2 for the base of log.

The second feature value is called "first occurrence" (Otherwise known as "distance"). This is determined by dividing the position of first occurrence of phrase P in document D by size(D). For example, "tokyo" is 1/7 while "governor" is 2/7 in the previous example.

While the 2 feature values are positive real numbers, KEA discretizes continuous values and calculates posterior probability for each discrete value. The above formula Pt[t|yes] (or Pt[t|no]) is the posterior probability of TF*IDF while Pd[d|yes] (or Pd[d|no]) is the posterior probability of distance.

### Key Phrase Extraction in KEA

To extract key phrases from unknown document D, derive P[yes] and P[no] by calculating previous 2 feature values for key phrase candidates enumerated from D, use the following formula to get the scores, sort them in descending order, and cut off the ones that are below suitable value.

> score(P,D) = P[yes] / (P[yes] + P[no])

Note that you need to add 1 to df(P) and N when calculating TF*IDF because unknown document is not included in the model.


# A KEA Program using Apache Lucene

Let's make full use of Lucene library to write a KEA program ("KEA-lucene" hereinafter) based on the preceding algorithm. The program introduced here is [published on Github](https://github.com/kojisekig/kea-lucene). Note that directory names and other names are intentionally hard-coded in order to make the program easier to understand.

## Why Lucene?

Now, you might think why you use Lucene to implement KEA. Not only KEA, when you implement a natural language processing tool, you often count the number of words or documents that include a certain word, or separate a document into words. Lucene provides well-suited APIs to execute those processes. In addition, the transpose index (simply "index" hereinafter) is excellent feature as a word dictionary as well. The following is Lucene API that I specifically used for KEA-lucene.

### Analyzer

Lucene uses the [Analyzer](https://lucene.apache.org/core/6_1_0/core/org/apache/lucene/analysis/Analyzer.html) class to separate a document into words. KEA-lucene uses the [StandardTokenizer](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/standard/StandardTokenizer.html) for tokenization, the [LowerCaseFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/core/LowerCaseFilter.html) for the normalization to lower case letters, and the [ShingleFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/shingle/ShingleFilter.html) for word N-gram support. Unfortunately, the concept of stop word in KEA cannot be realized by Lucene's [StopFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/core/StopFilter.html). I, therefore, implemented an original [TokenFilter](https://lucene.apache.org/core/6_1_0/core/org/apache/lucene/analysis/TokenFilter.html) called KEAStopFilter. Finally, KEAAnalyzer was constructed as follows.

```java
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
}
```

According to the paper on KEA, a stop word list is made up of word classes: conjunctions, articles, particles, prepositions, pronouns, anomalous verbs, adjectives, and adverbs. The number of stop words here is far more than what Lucene lists. KEA-lucene added original stop words to the Lucene stop words.

Furthermore, as KEA-lucene changes N of word N-gram from 1 to 3 and add index to each field, it uses Lucene PerFieldAnalyzerWrapper as follows.

```java
 public static Analyzer getKEAAnalyzer(String fieldName){
  Map<String, Analyzer> amap = new HashMap<>();
  amap.put(Commons.getFieldName(fieldName, 1), new KEAAnalyzer(1));
  amap.put(Commons.getFieldName(fieldName, 2), new KEAAnalyzer(2));
  amap.put(Commons.getFieldName(fieldName, 3), new KEAAnalyzer(3));
  return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), amap);
 }
```

### Utilizing Lucene Index as a Word Dictionary

Lucene creates transpose index that uses a word as a key. It would be natural and reasonable to use this as a word dictionary. Process as follows to scan words in a field called "field" all the way through. Note that ir is an object of IndexReader that opens Lucene index.


```java
Terms terms = MultiFields.getTerms(ir, field);
TermsEnum te = terms.iterator();
for(BytesRef rawPhrase = te.next(); rawPhrase != null; rawPhrase = te.next()){
 String phrase = rawPhrase.utf8ToString();
 :
}
```

An index that Lucene uses is not just a list of words. It also holds statistics needed to calculate preceding 2 feature values. For example, freq(P,D) is the number of occurrence of phrase P in document D. You can use getTermDocsEnum() method of MultiFields to get the PostingsEnum object and specify document D for it, then call freq() to obtain this count. 

Also df(P) is the number of documents that have phrase P while N is the number of all documents. They are obtained as follows.

```java
int dfreq = ir.docFreq(new Term(field, phrase));
:
int n = ir.numDocs();
```

You also need a position information to calculate a distance of phrase. This can be obtained by using the getTermPositionsEnum() method of MultiFields to get the PostingsEnum object, by using advance() method to specify document, and then by using nextPosition() to obtain the position of first phrase. You can use the position obtained by nextPosition() in distance calculation without any problem because KEA-lucene places 1-gram, 2-gram, and 3-gram into separate field.

## Model Learning (Model File Output) 

KEAModelBuilder performs model learning in KEA-lucene. Note that this program [download here](https://github.com/snkim/AutomaticKeyphraseExtraction) assumes that the resulting teacher data (expand MAUI.tar.gz and then expand fao30.tar.gz there) will be located in the data/fao30/ directory.

For learning, you first count variable statistics before dividing statistics because KEA uses Naive Bayes for model learning. The division is performed by the following KeyphraseExtractor(2) program and the KEAModelBuilder program only calculates statistics. To be exact, it is not learning model but is only outputting a model file.

The model file KEAModelBuilder outputs is a space delimited text file as follows.

```bash
$ head features-model.txt 
0.000852285 0.231328 false
0.000284095 0.980489 false
0.000426143 0.0124768 false
0.000426143 0.429134 false
2.01699e-05 0.0479968 false
0.000284095 0.0160665 false
0.000426143 0.726610 false
0.000136409 0.000752663 false
0.000226198 0.379661 false
0.000284095 0.478057 false
```

One record represents one key phrase candidate. The first numeric values represents TF*IDF, the second one represents the distance, and the third column is a class to specify whether it is key phrases (true) or not (false).

The KEAModelBuilder program creates Lucene index from teacher data, consider Lucene index as word dictionary, iterate the process and calculate feature value to output the above model file.

For your information, here's the processing time. My MacBook Pro (Processor: Intel Core i7／2.3 GHz) took about 5 minutes to process. While creating Lucene index was done in a fraction of a second, outputting a model file while scanning Lucene index took more time to complete.

## Discretization of Continuous Values using R

KEA does not use a real number of feature value as is but maps it to a discrete value derived from MDLP (Minimum Description Length Principle) first. I used R to calculate MDLP as follows.

```r
data <- read.table('features-model.txt')
mdlp(data)$cutp
[[1]]
[1] 0.0003538965 0.0013242950 0.0041024750

[[2]]
[1] 0.0003553105 0.0144056500 0.0697899000
```

The result obtained here is saved with a file name "cutp-model.txt" as it will be used in the next program KeyphraseExtractor(2). 

```bash
$ cat cutp-model.txt 
0.0003538965 0.0013242950 0.0041024750
0.0003553105 0.0144056500 0.0697899000
```

MDLP calculation using R took me about 30 minutes in the same environment.

## Extracting key phrases from Lucene Index

Now, let's extract key phrases from Lucene index. There are two programs: KeyphraseExtractor and KeyphraseExtractor2. The former is an index created by KEAModelBuilder that extracts key phrases from a known file called filea0011e00.txt (this file name is hard-coded in the program) as a test. KeyphraseExtractor2, meanwhile, is a program that extracts key phrases from unknown file called document t0073e.txt in fao780 (you can expand MAUI.tar.gz above to obtain this file).

KeyphraseExtractor obtains statistics from existing Lucene index, while KeyphraseExtractor2 starts from creating Lucene index in order to obtain variable statistics from a new document. Also, KeyphraseExtractor2 is a new document that is not included in a model and, because of that, is different where 1 is added to the both denominator and numerator in the df(P) / N portion.

The both KeyphraseExtractor and KeyphraseExtractor2 specify a document that is already registered in Lucene index and calculate scores while listing key phrase candidates in the document. Therefore, you want to use TermVector of Lucene instead of iterating word dictionaries. Here, Lucene's add document function calls setStoreTermVectors(true) of FieldType.

```java
 static Document getDocumentWithTermVectors(String fn, String content) throws IOException {
  Document doc = new Document();
  doc.add(new StringField(FILE_NAME, fn, Field.Store.YES));
  doc.add(new StoredField(DOC_SIZE_FIELD_NAME, Commons.getDocumentSize(content)));

  FieldType ft = new FieldType();
  ft.setStored(true);
  ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_Poperating systemsITIONS);
  ft.setStoreTermVectors(true);
  doc.add(new Field(Commons.getFieldName(FIELD_NAME, 1), content, ft));
  doc.add(new Field(Commons.getFieldName(FIELD_NAME, 2), content, ft));
  doc.add(new Field(Commons.getFieldName(FIELD_NAME, 3), content, ft));
  return doc;
 }
```

score(P,D) is calculated using the preceding score counting formula and key phrase candidates are sorted in descending order. KEA-lucene uses PriorityQueue of Lucene for this purpose. As KEA uses discrete values and not continuous values for feature values, the paper points out an event where more than one score(P,D) has the same value among a number of key phrase candidates. In such an event, use TF*IDF to run off where PriorityQueue implemented as follows.

```java
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
```

You have to implement the process so that a low-order key phrase candidate that is a sub-phrase of higher-order key phrase candidate in PriorityQueue will be rejected.

Let's look at a result of execution. The high-order 20 key phrases extracted by executing KeyphraseExtractor are as follows. Note that 2 numeric values in parentheses represent scores and TF*IDF feature values (you can see that when the scores are the same one with higher TFIDF values are placed higher).

```shell
animal (0.311211,0.001698)
standards setting (0.102368,0.014469)
sps (0.102368,0.009091)
chains (0.102368,0.008121)
food chains (0.102368,0.008038)
food safety (0.102368,0.008004)
sps standards (0.102368,0.006865)
value chain (0.102368,0.005468)
setting process (0.102368,0.005250)
standards setting process (0.102368,0.004846)
livestock food (0.102368,0.004823)
oie (0.102368,0.004501)
poor to cope (0.102368,0.004442)
animal health (0.102368,0.004255)
animal production (0.089053,0.000425)
consultation (0.076506,0.004025)
assisting the poor (0.076506,0.002827)
sanitary and technical (0.076506,0.001615)
requirements assisting (0.076506,0.001615)
dynamics of sanitary (0.076506,0.001615)
```

On the other hand, key phrases added manually are as follows (Since more than one person adds key phrases to the fao30 data randomly, I listed phrases that have been recognized as a key phrase at least once).

```shell
$ cat $(find fao30 -name a0011e00.key)|sort|uniq
animal health
animal production
animal products
capacity building
consumers
developing countries
development policies
disease surveillance
domestic markets
empowerment
fao
food chains
food safety
food security
hygiene
information dissemination
livestock
markets
meat hygiene
mechanics
phytosanitary measures
poverty
public health
regulations
risk analysis
risk management
rural population
standards
technical aid
technology
trade
veterinary hygiene
world
```

Key phrases common to the both are as follows.

> food chains, food safety, animal health, animal production

It's far from highly accurate but most of the key phrases extracted are natural. I personally think they are on the right track for automatically extracted phrases.

The result of process that runs KeyphraseExtractor2 to extract key phrases from unknown document is as follows. It also displays 20 high-order phrases.

```shell
post harvest (0.628181,0.004842)
vegetables (0.311211,0.002956)
root crops (0.311211,0.002523)
fruits (0.311211,0.002316)
vegetables and root (0.311211,0.002161)
food losses (0.311211,0.001900)
harvest food losses (0.311211,0.001582)
post harvest food (0.311211,0.001582)
fresh produce (0.102368,0.011087)
packing (0.102368,0.007199)
decay (0.102368,0.005210)
containers (0.102368,0.004370)
prevention of post (0.089053,0.001231)
fruits vegetables (0.089053,0.000472)
handling (0.076506,0.002345)
storage (0.076506,0.002177)
tubers (0.076506,0.001889)
marketing (0.076506,0.001474)
roots (0.076506,0.001389)
potatoes (0.029596,0.003984)
```

Meanwhile manually added key phrases are as follows.

```shell
$ cat t0073e.key 
Fruits
Marketing
Packaging
Postharvest losses
Postharvest technology
Root crops
Vegetables
```

Pretty reasonable phrases are extracted here as well. It is precise enough for something like keyword suggestion for information retrieval.

# Summary

In this article, I particularly chose Lucene library to implement KEA with teacher machine learning for key phrases extraction. Since the KEA program has a simple and clear algorithm, I believe you can easily understand - from the program - how to use Lucene API to obtain variable statistics of key phrase candidates.

I'd be happy to see readers find interest in the Lucene library or expand their knowledge on the library through this article.

## Challenges 

When I wrote this article, I did not implement the following items in the first version of program because of time constraints. 

* Stemming. Should be able to use tools including Lucene Porter Stemmer.
* MDLP. R is used as described in the article. Like to be able to create an original or use non-GPL license implementation because the MDLP implement of R is GPL
* Try against non-Japanese data including text in English. Teacher data and tweak around stop words would be necessary.
* Additional feature value. In addition to the above-mentioned feature values, the most recent KEA implement seems to have feature values that are not mentioned in the paper.

We have been working on hard-coding around KEA-lucene and are planning to provide general purpose codes via [NLP4L](https://github.com/NLP4L) as appropriate.