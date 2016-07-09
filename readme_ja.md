# KEA とは？

[KEA](http://www.nzdl.org/Kea/)はニュージーランドのワイカト大学で開発されている、自然言語で書かれた文書からキーフレーズ（キーワード）を自動抽出するプログラムである。KEAはKeyphrase Extraction Algorithmの略であり、KEAプログラムを構成するアルゴリズムそのものを指す場合もある。

文書に付加されたキーフレーズは、当該文書の意味的メタデータであり、文書の極端に短いサマリーとも言える。そのため、文書を読み込む時間がない場合にその文書のメタデータであるキーフレーズのリストをながめるだけでおおまかな内容をつかむことができる。たとえば限られた時間である調べ物をしていたとしよう。その調査に関係しそうな文書は目の前に山積みされているが、すべてに目を通している時間はない。そんなときは、文書に振られているキーフレーズをまずながめて、関係しそうな文書だけを選んで読み始めることができる。

学術論文などではその論文の著者によってキーフレーズが付加されているものが多い。しかしながら一般の文書や書籍はキーフレーズがつけられていないものがほとんどである。KEAはそのような文書から自動的にキーフレーズを抽出しようというプログラムである。KEAは著者によりキーフレーズが付加された文書を読み込んでその特徴を学習し、キーフレーズがつけられていない未知の文書からキーフレーズを自動抽出するという、教師あり機械学習プログラムである。KEAは言語（英語や日本語など）に関係なく動作可能なアルゴリズムとなっている。

## キーフレーズ抽出と情報検索の関係

本稿はLuceneインデックスからキーフレーズを抽出しようという話なので、キーフレーズ抽出と情報検索の関係についても触れておこう。

まずKEAの作者らはキーワードという言葉ではなく、キーフレーズという言葉を使っている点に注目したい。キーワードというと重要な（キー）1つの単語を連想するが、ワードではなくフレーズ（成句）という言葉を使うことで、キーとなる1つ以上の単語の連なりも文書から抽出できるということが強調されている。

Luceneインデックスからキーフレーズが抽出できたとすると、情報検索にはどんないいことがあるだろうか。まず思い浮かぶのがクエリのサジェスチョン（またの名をオートコンプリート）である。Luceneインデックスはどうしても単語単位で文字列が管理されているので、サジェスチョンも単語単位となってしまう。しかしキーとなるフレーズが自動抽出できれば連続した複数の単語が一度にサジェストでき、ぐっとありがたみが増す。もしかして検索も同様である。

また検索結果一覧を表示するときに、ハイライト機能の代わりもしくはハイライト機能と一緒に当該文書のキーフレーズを表示することでユーザの文書選択の助けとすることも考えられる。さらにはファセット（絞り込み検索）のキーとすることも考えられるので、キーフレーズが抽出できることのメリットは大きい。

## KEAの処理概要

[KEAの論文](http://www.cs.waikato.ac.nz/~ml/publications/2005/chap_Witten-et-al_Windows.pdf)は格別難しいことが書いてあるわけではないので、時間のある方にはぜひ一読をお勧めする。本稿ではのちのちLuceneライブラリを使ったKEAの実装を解説するので、それが理解できるよう最低限の説明を行うことにしよう。

KEAにおける処理は「学習過程」と「キーフレーズ抽出過程」に大別されるが、両者に共通な処理としてキーフレーズの候補をリストアップするプロセスが存在する。キーフレーズ候補は機械的にリストアップされる。そのようにリストアップされた多数のキーフレーズ候補から、学習時はキーフレーズになるなりやすさ（またはなりにくさ）を学習する。そしてキーフレーズ抽出時は学習した確率モデルを参照して、多数のキーフレーズ候補をスコアづけしてスコアの大きいものから順にキーフレーズ候補を表示する。実際のキーフレーズ抽出時はそのように順位付けされたキーフレーズ候補のリストから適当なところで足切りを行う。

本稿で解説するKEAの実装は、Luceneを大いに活用する。学習のプログラムでは、既知の（著者によりキーフレーズがつけられた）文書が入ったLuceneインデックスを作成した後そこからモデルファイルを作成する。未知の（キーフレーズがつけられていない）文書からキーフレーズを抽出するプログラムでは、その文書をLuceneインデックス登録してからキーフレーズを抽出する。

### キーフレーズ候補の列挙

KEAではキーフレーズの候補として、1つから最大3つの連続する単語を列挙する。たとえば、次のような文書があったとしよう。

> Tokyo governor likes to go to Yugawara.

すると、KEAでは次のように10個の候補となるキーフレーズを列挙する。

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

キーフレーズ候補の列挙においては、ストップワードから始まるフレーズやストップワードで終わるフレーズは候補とならない。よって、単独のtoのみならず、like(s) toやto goなどは候補としてリストアップされない。また情報検索やNLPでよく行われる文字や単語の正規化もこの段階で行われる。よって、TokyoやYugawaraはtokyoやyugawaraに、likesはステミングされてlikeになる。

### KEAにおけるモデルの学習

KEAでは単純ベイズ分類器を用いて教師ありデータ（著者によりキーフレーズがつけられた文書データ）からキーフレーズ候補におけるキーフレーズになるなりやすさ（P[yes]）となりにくさ（P[no]）を学習する。具体的には次のような式を用いる。

> P[yes] = Y / (Y + N) * Pt[t|yes] * Pd[d|yes]  
> P[no] = N / (Y + N) * Pt[t|no] * Pd[d|no]

ここで"Y / (Y + N)"や"N / (Y + N)"の部分はキーフレーズ候補から求められる事前確率である。先の例文でアスタリスクがつけられたものがキーフレーズだとすると、つぎのようになる。

> Y / (Y + N) = 2 / 10  
> N / (Y + N) = 8 / 10

KEAでは文書D中のフレーズPを表現するのに2つの特徴量を用いている。1つめはTF*IDFであり、次のように計算される。

> TF*IDF = freq(P,D) / size(D) * (-log(df(P) / N))

freq(P,D)は文書D中にフレーズPが出現する回数、size(D)は文書の単語数、df(P)はフレーズPを含む文書数、Nは全文書数である。なおlogの底は2とする。

2つめの特徴量はfirst occurrence（別名、距離）と呼ばれるものである。これはフレーズPが文書Dに最初に登場する位置をsize(D)で割ったものである。たとえば先の例でtokyoは1/7、governorは2/7となる。

2つの特徴量は正の実数となるが、KEAでは連続値を離散化した上で各離散値ごとの事後確率を計算する。前述の式のPt[t|yes]（またはPt[t|no]）はTF*IDFの事後確率、Pd[d|yes]（またはPd[d|no]）は距離の事後確率である。

### KEAにおけるキーフレーズ抽出

未知の文書Dからキーフレーズ抽出を行うには、Dから列挙したキーフレーズ候補について前述の2つの特徴量を計算してP[yes]とP[no]を求め、最終的に次の式を使ってスコアを算出して降順にソートし、適当なところで足切りする。

> score(P,D) = P[yes] / (P[yes] + P[no])

なお、未知文書はモデルに含まれていないため、TF*IDFを計算する際は、df(P)とNは1を加算する。


# Apache Luceneを使ったKEAプログラム

では前述のアルゴリズムを元に、Luceneライブラリをフル活用してKEAプログラム（KEA-luceneと呼ぶことにする）を自作してみよう。ここで紹介するプログラムは[Githubに公開](https://github.com/kojisekig/kea-lucene)している。なお、プログラムはわかりやすさを優先し、ディレクトリ名などが意図的にハードコーディングされている。

## なぜLuceneを使うのか？

ところでKEAを実装するのになぜLuceneを使うのだろうか。KEAに限らず自然言語処理のツールを実装する場合、単語の数を数えたり、ある単語を含む文書の数を数えたり、それ以前に文書を単語に区切ったりすることがよく行われる。Luceneはこういった処理を行うのによく整備されたAPIを備えている。さらにはLuceneの転置インデックス（以下単にインデックスと呼ぶ）は単語辞書としても優秀だ。特にKEA-luceneのために使用したLucene APIを以下に紹介しよう。

### Analyzer

Luceneでは、文章を単語に区切るのに[Analyzer](https://lucene.apache.org/core/6_1_0/core/org/apache/lucene/analysis/Analyzer.html)クラスを用いる。KEA-luceneでは、トークナイズのために[StandardTokenizer](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/standard/StandardTokenizer.html)を、小文字への正規化のために[LowerCaseFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/core/LowerCaseFilter.html)を、そして単語N-gramをサポートするために[ShingleFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/shingle/ShingleFilter.html)を使っている。残念ながらKEAのストップワードの考え方はLuceneの[StopFilter](https://lucene.apache.org/core/6_1_0/analyzers-common/org/apache/lucene/analysis/core/StopFilter.html)では実現できないので、KEAStopFilterという独自[TokenFilter](https://lucene.apache.org/core/6_1_0/core/org/apache/lucene/analysis/TokenFilter.html)を実装している。そして最終的に次のようにKEAAnalyzerを組み上げた。

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

KEAの論文によると、conjunctions, articles, particles, prepositions, pronouns, anomalous verbs, adjectives および adverbs の各品詞からストップワードリストを構成している。これはLuceneがリストアップしているストップワードよりもはるかに多い。KEA-luceneではLuceneのストップワードに独自にストップワードを追加した。

さらにKEA-luceneでは単語N-gramのNを1から3に変えて個別のフィールドにインデックス登録するため、LuceneのPerFieldAnalyzerWrapperを次のように使用している。

```java
  public static Analyzer getKEAAnalyzer(String fieldName){
    Map<String, Analyzer> amap = new HashMap<>();
    amap.put(Commons.getFieldName(fieldName, 1), new KEAAnalyzer(1));
    amap.put(Commons.getFieldName(fieldName, 2), new KEAAnalyzer(2));
    amap.put(Commons.getFieldName(fieldName, 3), new KEAAnalyzer(3));
    return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), amap);
  }
```

### 単語辞書としてLuceneインデックスを活用する

Luceneは単語をキーにした転置インデックスを作成する。これを単語辞書として使うのは実に自然であり理にかなっているといえるだろう。フィールドfieldの単語を最初から最後まで走査するには次のようにする。ただしirはLuceneインデックスをオープンしているIndexReaderのオブジェクトである。

```java
Terms terms = MultiFields.getTerms(ir, field);
TermsEnum te = terms.iterator();
for(BytesRef rawPhrase = te.next(); rawPhrase != null; rawPhrase = te.next()){
  String phrase = rawPhrase.utf8ToString();
  :
}
```

Luceneのインデックスはただ単に単語がリストアップされているだけではない。前述の2つの特徴量を計算するために必要となる統計量も保存されている。たとえばfreq(P,D)は文書D中にフレーズPが出現する回数であるが、これはMultiFieldsのgetTermDocsEnum()メソッドを使用して取得したPostingsEnumオブジェクトに対しnextDoc()で文書Dを特定した後、freq()を呼び出すことで取得できる。

またdf(P)はフレーズPを含む文書数、Nは全文書数であるがこれは次のようにして取得できる。

```java
int dfreq = ir.docFreq(new Term(field, phrase));
:
int n = ir.numDocs();
```

さらにはフレーズの距離を計算するためにポジション情報も必要になってくるが、これはMultiFieldsのgetTermPositionsEnum()メソッドを使ってPostingsEnumオブジェクトを取得後、advance()メソッドで文書を特定した後nextPosition()で最初のフレーズのポジションを求める。KEA-luceneでは1-gram, 2-gram, 3-gramを独立したフィールドにしているので、nextPosition()で取得したポジションはそのまま距離計算に用いて問題ない。

## モデルの学習（モデルファイルの出力）

KEA-luceneではKEAModelBuilderがモデルの学習を行っている。このプログラムは[ここからダウンロード](https://github.com/snkim/AutomaticKeyphraseExtraction)できる教師データ（MAUI.tar.gzを展開しさらにその中にあるfao30.tar.gzを展開する）をdata/fao30/ディレクトリに配置されることを前提に書かれているので注意していただきたい。

KEAではモデルの学習は単純ベイズを使っているので、学習には各種統計量をカウントした上で統計量の割り算を行う。割り算を計算しているのは後述のKeyphraseExtractor(2)プログラムであり、KEAModelBuilderプログラムでは統計量を求めているだけなので、正確にはモデルの学習というよりはモデルファイルを出力しているに過ぎない。

KEAModelBuilderが出力するモデルファイルは次のようなスペース区切りのテキストファイルである。

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

1つのレコードは1つのキーフレーズ候補を表している。最初の数値はTF*IDFで、2番目の数値は距離を表している。3列目はキーフレーズ（true）か否か（false）を表現するクラスである。

KEAModelBuilderプログラムは上記のようなモデルファイルを出力するために教師データからLuceneインデックスを作成し、Luceneインデックスを単語辞書とみなしてiterateしながら特徴量を計算しつつ、上記のモデルファイルを出力する。

参考までに処理時間を示しておこう。私のMacBook Pro（Processor 2.3 GHz Intel Core i7）では5分程度かかった。Luceneインデックスを作成するのは一瞬だが、Luceneインデックスを走査しながらモデルファイルを出力するのに少々時間がかかっている。

## Rによる連続値の離散化

KEAでは特徴量の実数値をそのまま用いるのではなく、MDLP（最小記述長原理）により求めた離散値にマッピングして用いる。ここではMDLPの計算のためにRを使って次のように求めた。

```r
data <- read.table('features-model.txt')
mdlp(data)$cutp
[[1]]
[1] 0.0003538965 0.0013242950 0.0041024750

[[2]]
[1] 0.0003553105 0.0144056500 0.0697899000
```

ここで得られた結果は、次のプログラムKeyphraseExtractor(2)で使用するので、cutp-model.txtというファイル名で保存しておく。

```bash
$ cat cutp-model.txt 
0.0003538965 0.0013242950 0.0041024750
0.0003553105 0.0144056500 0.0697899000
```

RによるMDLP計算は同じ環境で30分程度かかった。

## Luceneインデックスからのキーフレーズ抽出

ではいよいよLuceneインデックスからキーフレーズを抽出してみよう。プログラムはKeyphraseExtractorとKeyphraseExtractor2である。前者はKEAModelBuilderで作成したインデックスのうち試しに既知のファイルa0011e00.txt（このファイル名もプログラム中にハードコーディングしてある）からキーフレーズを抽出するものである。KeyphraseExtractor2の方はfao780（前述のMAUI.tar.gzを展開した中にある）内の未知の文書t0073e.txtからキーフレーズを抽出するプログラムとなっている。

KeyphraseExtractorの方は既存のLuceneインデックスから統計量を得ているのに対し、KeyphraseExtractor2の方は新規の文書から各種統計量を得るためにLuceneインデックスを作成するところから行っている。また、KeyphraseExtractor2の方はモデルに含まれない新規文書であるために、df(P) / N の計算部分で分母、分子とも1が加算されているところも異なる。

KeyphraseExtractorとKeyphraseExtractor2はともにLuceneインデックスに登録済みの文書を特定し、その文書におけるキーフレーズ候補をリストアップしながらスコアを計算する。したがって単語辞書をiterateするよりもLuceneのTermVectorを使いたい。そこでLuceneドキュメント登録は次のようにFieldTypeのsetStoreTermVectors(true)を呼んでいる。

```java
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
```

キーフレーズ候補は前述のスコア計算式によりscore(P,D)が計算され降順にソートされる。KEA-luceneではそのためにLuceneのPriorityQueueを用いている。ところで、KEAでは特徴量として連続値ではなく離散値を使っているため、score(P,D)が多くのキーフレーズ候補間で同点になる事象が論文で指摘されている。その場合はTF*IDFを使って同点決勝を行うということなので、PriorityQueueの実装は次のようになっている。

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

またPriorityQueue中下位のキーフレーズ候補が、それより上位のキーフレーズ候補のサブフレーズであった場合ははじかれるように実装する必要もある。

実行結果を見てみよう。KeyphraseExtractorを実行して抽出されたキーフレーズの上位20件は次のようである。なお、括弧内の2つの数値は、スコアとTF*IDF特徴量を表している（スコアが同点の時はTFIDF値が高い方が上位にきているのがわかるだろう）。

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

これに対し人手でつけられたキーフレーズは次のようである（fao30データは複数の人間が思い思いにキーフレーズをつけているので、一回でもキーフレーズと認識されたフレーズをここではリストアップしている）。

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

両者に共通して現れるキーフレーズは次のようになる。

> food chains, food safety, animal health, animal production

決して正解率が高いとは言えないが、抽出されたキーフレーズはフレーズとして不自然なものが少ないように見える。自動抽出されたフレーズとしてはいいセン行っているのではないだろうか。

またKeyphraseExtractor2を実行して未知の文書からキーフレーズ抽出した結果は次のようである。同じく上位20件を表示している。

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

一方で、人手でつけられたキーフレーズは以下である。

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

こちらも結構妥当なフレーズが抽出されている。情報検索のキーワードサジェスチョンなどに用いるには十分な精度といってもいいだろう。

# まとめ

本稿は教師あり機械学習でキーフレーズを抽出するKEAを、あえてLuceneライブラリを活用することで実装した。KEAプログラムはアルゴリズムが簡単明瞭なので、キーフレーズ候補の各種統計量を得るためにLucene APIをどのように使えるのか、プログラムから読み取りやすいのではないかと考えている。

読者が本稿を通じてLuceneライブラリへ興味を持ったり、ライブラリへの知識を少しでも広げていただけたなら幸いである。

## 課題

本稿を執筆した最初のプログラムのバージョンでは、以下の項目については時間の制約から実装していない。

* ステミング。LuceneのPorter Stemmerなどを使えるはずである。
* MDLP。記事にあるとおりRを使っている。RのMDLP実装はGPLなので、この部分はぜひ自作か非GPLライセンスの実装を使えるようにしたい。
* 日本語データなど英語以外のテキストに対して試す。教師データが必要なのと、ストップワード周りのチューニングが必要になるだろう。
* 追加の特徴量。最新のKEA実装は前述の2つの特徴量に追加して論文には触れられていない特徴量があるようだ。

KEA-luceneについてはいろいろハードコーディングをしているが、汎用的なものを適宜[NLP4L](https://github.com/NLP4L)を通じて提供する予定である。