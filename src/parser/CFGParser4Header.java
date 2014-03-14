package parser;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import iitb.shared.EntryWithScore;
import iitb.shared.IntFloatPair;
import iitb.shared.RobustMath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import parser.CFGParser4Header.EnumIndex.Tags;
import parser.cfgTrainer.FeatureVector;
import parser.coOccurMethods.Co_occurrenceScores;
import parser.coOccurMethods.ConceptClassifier;
import parser.coOccurMethods.LogisticUnitGivenWords;
import parser.coOccurMethods.PrUnitGivenWord;
import catalog.Co_occurrenceStatistics;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitMultPair;
import catalog.UnitPair;
import conditionalCFG.ConditionalCFGParser;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.StringUtils;
import eval.Utils;

public class CFGParser4Header extends RuleBasedParser {
	protected Co_occurrenceStatistics coOccurStats;
	/*
	 * 
	 */
	public static String grammar =
		/*"ROOT ::- Junk_CWs_U Junk 0.2f" + "\n" +
		"ROOT ::- Junk CWs_U 0.6f" + "\n" +
		"ROOT ::- CWs_U 0.1f" + "\n" +

		"Junk_CWs_U ::= Junk CWs_U 1f"+ "\n" +
		"CWs_U ::- CWs U 0.9f" + "\n" +
		"CWs_U ::- CWs CW_Sep_U 0.1f" + "\n" +

		"CW_Sep_U ::- IN U 1f" + "\n" +

		"CWs ::- CW CWs 0.9f" + "\n" +
		"CWs ::- CW 0.1f" + "\n" +
		 */
		"ROOT ::- ROOT_ "+Lexicon.BOUNDARY_TAG + " 1f" + "\n" +
		"ROOT_ ::- Junk IN_U 1f" + "\n" +
		"ROOT_ ::- Junk 1f" + "\n" +
		"ROOT_ ::- Junk_U Junk 1f" + "\n" +
		"ROOT_ ::- UL Junk 1f" + "\n" +
		"ROOT_ ::- UL 1f" + "\n" +

		"Junk_U ::= Junk IN_U 1f"+ "\n" +

		"IN_U ::- IN UL 1f"+ "\n" +
		"IN_U ::- UL 1f"+ "\n" +

		"UL ::- U 1f"+ "\n" +
		"UL ::- U Sep_U 1f"+ "\n" +
		"UL ::- U U 1f"+ "\n" +

		"Sep_U ::- Op U 1f"+ "\n" +

		"Junk ::= Junk W 1f"+ "\n" +
		"Junk ::= W 1f"+ "\n" +

		"U ::- BU 1" + "\n" +
		"U ::- BU IN_Mult 1" + "\n" +
		"U ::- BU Mult 1" + "\n" +
		"U ::- Mult BU 1" + "\n" +
		"U ::- Mult_OF BU 1" + "\n" +
		"U ::- Mult 1" + "\n" +

		"IN_Mult ::- IN Mult 1f" + "\n" +
		"Mult_OF ::- Mult OF 1f" + "\n";
	;
	String basicUnitGrammar = 
		"BU ::- CU2 Sep_SU 1\n"+
		"BU ::- CU2 1\n"+
		"BU ::- SU 1"+ "\n" +

		"CU2 ::- SU Sep_SU 1 \n"+
		"CU2 ::- SU PER_SU 1 \n"+

		"Sep_SU ::- Op SU 1"+ "\n" +
		"PER_SU ::- PER SU 1"+ "\n" +

		"SU ::- SU_MW SU_W 1f"+ "\n" +
		"SU ::- SU_MW Number 1f"+ "\n" + // km 2 kind of parses.
		"SU ::- SU_1W 1f \n"+ // SU of single words need to have a separate token because different features are fired over the two cases.
		"SU_MW ::- SU_W 1f"+ "\n" + 
		"SU_MW ::- SU_W Op 1f"+ "\n" + 
		"SU_MW ::- SU_MW SU_W 1f"+ "\n";
	// TODO: allow a multiplier for simple units like in mg/thousand litres.
	public static class EnumIndex implements Index<String> {
		public enum Tags {SU_1W, SU_W, W, Mult, IN, OF, Op,PER, Number,Q,Boundary};
		public short allowedTags[][] = {
				{(short) Tags.SU_1W.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU_1W.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU_1W.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU_1W.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.Mult.ordinal()},
				{(short) Tags.SU_1W.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.IN.ordinal()},
				{(short) Tags.W.ordinal(),(short) Tags.OF.ordinal()},
				{(short) Tags.W.ordinal(), (short) Tags.Op.ordinal()},
				{(short) Tags.PER.ordinal(),(short) Tags.SU_1W.ordinal(),(short)Tags.W.ordinal(), (short) Tags.Op.ordinal()},
				{(short) Tags.W.ordinal(), (short) Tags.Mult.ordinal(),(short) Tags.Number.ordinal()},
				{(short) Tags.Q.ordinal()},
				{(short) Tags.Boundary.ordinal()}
		};
		TaggedToken boundaryToken;
		public EnumIndex() {
			boundaryToken = new TaggedToken(Lexicon.BOUNDARY_TAG, Tags.Boundary, Lexicon.BOUNDARY);
		}
		@Override
		public Iterator<String> iterator() {
			return null;
		}

		boolean isSimpleUnit(int tag) {
			return Tags.SU_1W.ordinal()==tag;
		}
		boolean isMultUnit(int tag) {
			return Tags.Mult.ordinal()==tag;
		}
		@Override
		public int size() {
			return Tags.values().length;
		}

		@Override
		public String get(int i) {
			return (i== Tags.Boundary.ordinal())?Lexicon.BOUNDARY_TAG:Tags.values()[i].name();
		}

		@Override
		public int indexOf(String o) {
			return (o.equals(Lexicon.BOUNDARY_TAG)?Tags.Boundary.ordinal():Tags.valueOf(o).ordinal());
		}

		@Override
		public int indexOf(String o, boolean add) {
			if (!add) return indexOf(o);
			throw new NotImplementedException();
		}

		@Override
		public List<String> objectsList() {
			List<String> objects = new Vector<String>();
			for (Tags tag : Tags.values()) {
				objects.add(tag==Tags.Boundary?Lexicon.BOUNDARY_TAG:tag.name());
			}
			return objects;
		}

		@Override
		public Collection<String> objects(int[] indices) {
			throw new NotImplementedException();
		}

		@Override
		public boolean isLocked() {
			return false;
			//throw new NotImplementedException();
		}

		@Override
		public void lock() {
			throw new NotImplementedException();
		}

		@Override
		public void unlock() {
			throw new NotImplementedException();
		}

		@Override
		public void saveToWriter(Writer out) throws IOException {
			throw new NotImplementedException();
		}

		@Override
		public void saveToFilename(String s) {
			throw new NotImplementedException();
		}

		@Override
		public boolean contains(Object o) {
			try {
				if (o.equals(Lexicon.BOUNDARY_TAG))
					return true;
				Tags.valueOf((String) o);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new NotImplementedException();
		}

		@Override
		public boolean add(String e) {
			throw new NotImplementedException();
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new NotImplementedException();
		}

		@Override
		public void clear() {
			throw new NotImplementedException();
		}

	}
	public static class StateIndex implements Index<String>  {
		EnumIndex tagIndex;
		public StateIndex(EnumIndex tagIndex) {
			this.tagIndex = tagIndex;
		}
		public enum States {ROOT,ROOT_,Junk,Junk_U,Sep_U,IN_U,U, UL,IN_Mult,Mult_OF,BU,CU2,Sep_SU,SU_MW,SU,PER_SU,Junk_QU,Q_U,SU_Q,BU_Q,CU2_Q,Q_Junk,W_Op_U,Rep_QU,Op_U};// W, Mult, IN, OF, Op,Boundary};
		@Override
		public Iterator<String> iterator() {
			return null;
		}
		boolean isMultUnit(int tag) {
			return tagIndex.isMultUnit(tag);
		}
		@Override
		public int size() {
			return States.values().length+tagIndex.size();
		}
		@Override
		public String get(int i) {
			return (i < tagIndex.size())?tagIndex.get(i):States.values()[i-tagIndex.size()].name();
		}
		public int indexOf(States state) {
			return state.ordinal()+tagIndex.size();
		}
		@Override
		public int indexOf(String o) {
			try {
				return States.valueOf(o).ordinal()+tagIndex.size();
			} catch (IllegalArgumentException e) {
				return tagIndex.indexOf(o);
			}
		}
		@Override
		public int indexOf(String o, boolean add) {
			if (!add || contains(o)) return indexOf(o);
			throw new NotImplementedException();
		}

		@Override
		public List<String> objectsList() {
			List<String> objects = tagIndex.objectsList();
			for (States tag : States.values()) {
				objects.add(tag.name());
			}
			return objects;
		}
		@Override
		public boolean contains(Object o) {
			try {
				States.valueOf((String) o);
				return true;
			} catch (IllegalArgumentException e) {
				return tagIndex.contains(o);
			}
		}

		@Override
		public Collection<String> objects(int[] indices) {
			throw new NotImplementedException();
		}

		@Override
		public boolean isLocked() {
			return false;
			//throw new NotImplementedException();
		}

		@Override
		public void lock() {
			throw new NotImplementedException();
		}

		@Override
		public void unlock() {
			throw new NotImplementedException();
		}

		@Override
		public void saveToWriter(Writer out) throws IOException {
			throw new NotImplementedException();
		}

		@Override
		public void saveToFilename(String s) {
			throw new NotImplementedException();
		}


		@Override
		public <T> T[] toArray(T[] a) {
			throw new NotImplementedException();
		}

		@Override
		public boolean add(String e) {
			throw new NotImplementedException();
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new NotImplementedException();
		}

		@Override
		public void clear() {
			throw new NotImplementedException();
		}
		public boolean isState(States state, int stateId) {
			return (stateId < tagIndex.size()?false:(state.ordinal()+tagIndex.size())==stateId);
		}
		public boolean isUnit(int state) {
			return isState(States.U,state);
		}
		public boolean isBaseUnit(int state) {
			return (isCU2(state) || tagIndex.isSimpleUnit(state)) || isUnit(state) || isState(States.SU, state);
		}
		public boolean hasUnit(int state) {
			return isBaseUnit(state) || isState(States.UL, state);
		}
		public boolean isCU2(int state) {
			return isState(States.CU2,state) || isState(States.CU2_Q,state);
		}
		public boolean isIN_U(int state) {
			return isState(States.IN_U,state);
		}
	}
	public static class WordIndex implements Index<String> {
		iitb.shared.SignatureSetIndex.Index<String> index;
		public WordIndex(iitb.shared.SignatureSetIndex.Index index) {
			this.index = index;
		}
		@Override
		public Iterator<String> iterator() {
			return null;
		}

		@Override
		public int size() {
			throw new NotImplementedException();
		}

		@Override
		public String get(int i) {
			return (i > 0?index.get(i-1):(i==-1?Lexicon.UNKNOWN_WORD:Lexicon.BOUNDARY));
		}
		public boolean isUnknown(String wrd) {
			return wrd.equals(Lexicon.UNKNOWN_WORD) || indexOf(wrd)==-1;
		}
		@Override
		public int indexOf(String o) {
			return indexOf(o,false);
		}

		@Override
		public int indexOf(String o, boolean add) {
			if (o.equals(Lexicon.BOUNDARY)) {
				return 0;
			}
			if (o.equals(Lexicon.UNKNOWN_WORD)) {
				return -1;
			}
			int id = index.indexOf(o);
			if (id >= 0) return id+1;
			if (!add) return -1;
			throw new NotImplementedException();
		}

		@Override
		public List<String> objectsList() {
			throw new NotImplementedException();
		}

		@Override
		public Collection<String> objects(int[] indices) {
			throw new NotImplementedException();
		}

		@Override
		public boolean isLocked() {
			return false;
			//throw new NotImplementedException();
		}

		@Override
		public void lock() {
			throw new NotImplementedException();
		}

		@Override
		public void unlock() {
			throw new NotImplementedException();
		}

		@Override
		public void saveToWriter(Writer out) throws IOException {
			throw new NotImplementedException();
		}

		@Override
		public void saveToFilename(String s) {
			throw new NotImplementedException();
		}

		@Override
		public boolean contains(Object o) {
			return indexOf((String) o) >= 0;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new NotImplementedException();
		}

		@Override
		public boolean add(String e) {
			throw new NotImplementedException();
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new NotImplementedException();
		}

		@Override
		public void clear() {
			throw new NotImplementedException();
		}

	}
	ConditionalCFGParser parser;
	TokenScorer tokenScorer;
	Index<String> wordIndex;
	Params params;

	public static class TagArrayIterator implements Iterator<IntTaggedWord> {
		short tagArray[];
		int word;
		short index = 0;
		short len;
		public TagArrayIterator(int word, short len) {
			this.word = word;
			this.len = len;
			index = 0;
		}
		public TagArrayIterator(int word2, short[] is) {
			this.word = word2;
			this.tagArray = is;
			len = (short) tagArray.length;
		}
		@Override
		public boolean hasNext() {
			return (index < len);
		}
		@Override
		public IntTaggedWord next() {
			index++;
			return new IntTaggedWord(word, tagArray==null?index-1:tagArray[index-1]);
		}

		@Override
		public void remove() {
		}

	}

	/*
	 * 
         1      * (normalized) ContextWord
 +      -2.8417 * (normalized) UnitBias
 +       0.4418 * (normalized) DictMatchWeight
 +      -4.1901 * (normalized) INLANG
 +      -1.1046 * (normalized) MatchLength
 +       3.6696 * (normalized) Co_occurStats
 +       0.0446 * (normalized) Subsumed
 +       2.0575 * (normalized) WithinBracket
 +       1.9767 * (normalized) AfterIN
 -       0.5267

	 */
	public static class Params {
		public int contextDiffThreshold = 2;
		public enum FTypes {ContextWord,UnitBias,DictMatchWeight,
			SINGLELetter,INLANG, MatchLength, Co_occurStats,Subsumed,
			WithinBracket,AfterIN,
			SymbolDictMatchThreshold,LemmaDictMatchThreshold,
			PercentUnkInUnit,PercenUnkInUnitThreshold, CU2Bias, MultBias,UL_Cont,PerMult}
		public static final int LargeWeight = 10000;;
		double[] weights=null;
		EntryWithScore<String> weightsIndexed[] = new EntryWithScore[]{
				new EntryWithScore<String>("ContextWord",0.5),
				new EntryWithScore<String>("UnitBias",-0.3), 
				new EntryWithScore<String>("DictMatchWeight",1),
				new EntryWithScore<String>("SINGLELetter",0),
				new EntryWithScore<String>("INLANG",-1),
				new EntryWithScore<String>("MatchLength",-0.01),
				new EntryWithScore<String>("Co_occurStats",1.5),
				new EntryWithScore<String>("Subsumed",-.07),
				new EntryWithScore<String>("WithinBracket",0.5),
				new EntryWithScore<String>("AfterIN",0.5),
				new EntryWithScore<String>("SymbolDictMatchThreshold",-0.9),
				new EntryWithScore<String>("LemmaDictMatchThreshold",-0.9),
				new EntryWithScore<String>("PercentUnkInUnit",-2),
				new EntryWithScore<String>("PercenUnkInUnitThreshold",0.5),
				new EntryWithScore<String>("CU2Bias",0.06),
				new EntryWithScore<String>("MultBias",0.29), // 27 Dec 2013, increasing to allow $m to be parsed preferentially as dollar million instead of dollar|meter
				new EntryWithScore<String>("UL_Cont", -2), /* units lists cannot be contiguous */
				new EntryWithScore<String>("PerMult",0.4)
		};
		/* 8 Nov 2013: Multbias should be less than unit bias because other new units get defined in the presence of a mult.
		 * e.g. population (million) adds population as a new unit.
		 * 
		 */
		String[][] learnedParams = null;// {{"ContextWord","3.202180785484943"},{"UnitBias","-105.3380056184916"},{"DictMatchWeight","83.61666183286786"},{"SINGLELetter","-9.866035985021725"},{"INLANG","-2.2194842396084"},{"MatchLength","-0.07966785895890988"},{"Co_occurStats","2.464134767464041"},{"Subsumed","-3.618467285251402"},{"WithinBracket","20.862932392986586"},{"AfterIN","11.400342284896563"}};
		public Params(){
			weights = new double[FTypes.values().length];
			for (EntryWithScore<String> paramWt : weightsIndexed) {
				//assert(Math.abs(weights[FTypes.valueOf(paramWt.getKey()).ordinal()] - paramWt.getScore()) < 0.001);
				weights[FTypes.valueOf(paramWt.getKey()).ordinal()] = paramWt.getScore();
			}
			//weights=learnedWeightsSVM;
		}
		public Params(String paramsString){
			this();
			String extWts[] = paramsString.split(",");
			for (int i = 0; i < extWts.length; i++) {
				String paramsWt[] = extWts[i].split("=");
				int f = FTypes.valueOf(paramsWt[0]).ordinal();
				double v = Double.parseDouble(paramsWt[1]);
				weights[f] = v;
			}
		}
		// CU2bias should be more than unitbias to prefer compound units when one side is known e.g. people per sq km
		public int numFeatures() {
			return FTypes.values().length;
		}
		public String featureName(int i) {
			return FTypes.values()[i].name();
		}
	}

	public static class Token implements HasWord {
		String wrd;
		Tags tag;
		public Token(String w) {
			this.wrd = w;
		}
		public Token(Tags in, String w) {
			this.tag = in;
			this.wrd = w;
		}
		@Override
		public String word() {
			return wrd;
		}
		@Override
		public void setWord(String word) {
			this.wrd = word;
		}
	}
	public static class TaggedToken extends Token implements HasWord, HasTag {
		String tg;
		public TaggedToken(String tag, Tags t, String w) {
			super(t, w);
			this.tg = tag;
		}
		@Override
		public String tag() {
			return tg;
		}
		@Override
		public void setTag(String tag) {
			this.tg = tag;
		}
	}
	public CFGParser4Header(Element options) throws Exception {
		this(options,null);
	}
	public CFGParser4Header(Element options, QuantityCatalog quantMatcher) throws Exception {
		super(options,quantMatcher);
		//index.add(Lexicon.BOUNDARY_TAG);
		EnumIndex tagIndex = new EnumIndex();
		StateIndex index = new StateIndex(tagIndex);
		Vector<BinaryRule> brules = new Vector<BinaryRule>();
		Vector<UnaryRule> urules = new Vector<UnaryRule>();
		initIndex(index,brules,urules);
		BinaryGrammar bg = new BinaryGrammar(index);
		for (BinaryRule brule : brules) {
			bg.addRule(brule);
		}
		bg.splitRules();
		UnaryGrammar ug = new UnaryGrammar(index);
		for (UnaryRule urule : urules) {
			ug.addRule(urule);
		}
		ug.purgeRules();
		Index<String> wordIndex = new WordIndex(this.quantityDict.tokenDict);
		coOccurStats = new Co_occurrenceStatistics(options, quantityDict);

		Co_occurrenceScores coOccurMethod = null;
		if (options != null && options.hasAttribute("co-occur-class")) {
			coOccurMethod = (Co_occurrenceScores) iitb.shared.Utils.makeClassGivenArgs("parser.coOccurMethods." + options.getAttribute("co-occur-class"), new Class[]{Co_occurrenceStatistics.class}, new Object[]{coOccurStats});
		} else {
			coOccurMethod = new ConceptClassifier(quantityDict, null);////new LogisticUnitGivenWords(coOccurStats); //new PrUnitGivenWord(coOccurStats);
		}
		if (options != null && options.hasAttribute("params")) {
			params = new Params(options.getAttribute("params"));
		} else
			params = new Params();
		tokenScorer = new TokenScorer(index, tagIndex,quantityDict,wordIndex, wordFreq,coOccurMethod,params);
		Options op = new Options();

		tmpFVec = new FeatureVector(params.numFeatures());
		op.dcTags=false;
		//op.testOptions.verbose=true;
		parser = new ConditionalCFGParser(bg, ug, tokenScorer, op, index, wordIndex, tagIndex);
	}

	private void initIndex(Index<String> index, Vector<BinaryRule> brules, Vector<UnaryRule> urules) throws IOException {
		String line;
		BufferedReader br  = new BufferedReader(new StringReader(getGrammar()));
		while ((line = br.readLine())!=null) {
			if (line.split(" ").length >= 5) {
				BinaryRule brule = new BinaryRule(line, index);
				brule.score = (float) Math.log(brule.score);
				brules.add(brule);
			} else {
				String[] fields = StringUtils.splitOnCharWithQuoting(line, ' ', '\"', '\\');
				UnaryRule urule = new UnaryRule(index.indexOf(fields[0],true), index.indexOf(fields[2],true), Math.log(Float.parseFloat(fields[3])));
				urules.add(urule);
			}
		}
	}
	protected String getGrammar() {
		return grammar+basicUnitGrammar;
	}
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr) {
		return parseHeader(hdr, null, 0);
	}

	List<UnitFeatures > bestUnits = new Vector<UnitFeatures>();
	List<UnitFeatures> bestUnits2 = new Vector<UnitFeatures>();
	FeatureVector tmpFVec;

	public List<? extends EntryWithScore<Unit>> getTopKUnits(String hdr, int k, Vector<UnitFeatures> featureList, int debugLvl) {
		return parseHeader(hdr, null, debugLvl, k, featureList);
	}
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl) {
		return parseHeader(hdr, forcedTags, debugLvl, 1, null);
	}
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl, int k, Vector<UnitFeatures> featureList) {
		return parseHeader(hdr, null, debugLvl,forcedTags, null, k, featureList);
	}

	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, ParseState hdrMatches, int debugLvl, short[][] forcedTags, UnitSpan forcedUnit, int k, Vector<UnitFeatures> featureList) {	
		return parseHeader(hdr, hdrMatches, null, debugLvl, forcedTags, forcedUnit, k, featureList);
	}

	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, ParseState hdrMatches, ParseState context, int debugLvl, short[][] forcedTags, UnitSpan forcedUnit, int k, Vector<UnitFeatures> featureList) {			
		if (debugLvl > 0) System.out.println(hdr);
		if (isURL(hdr)) return null;

		hdrMatches = getTokensWithSpan(hdr,forcedUnit,hdrMatches);
		if (hdrMatches.tokens.size()==0) return null;
		if (featureList!=null) featureList.clear();
		List<String> hdrToks = hdrMatches.tokens;
		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrMatches,forcedTags,debugLvl,featureList!=null,forcedUnit,context);
		if (parser.parse(sentence)) {
			TObjectFloatHashMap<UnitFeatures> units = new TObjectFloatHashMap<UnitFeatures>();
			List<ScoredObject<Tree>> trees = parser.getBestParses();
			/* 21 Dec 2013: disabling this because the strings like "wealth in billion us$", the 
			 * second match is "billion|USD" which has a very close score to the correct one.
			 * 
			 */ if (trees.size() < k) {
				double bestScore = (trees.size()>0?trees.get(0).score():Double.POSITIVE_INFINITY)-Double.MIN_VALUE;
				List<ScoredObject<Tree>> treesK = parser.getKBestParses(k);
				for (int r = 0; r < treesK.size(); r++) {
					if (treesK.get(r).score() < bestScore && trees.size() < k) {
						trees.add(treesK.get(r));
					}
				}
			}
			for (ScoredObject<Tree> stree : trees) {
				Tree tree = stree.object();
				Vector<Tree> unitNodes = new Vector<Tree>();
				tree.setSpans();
				getTopUnitNodes(tree, unitNodes);
				float treeScore = (float) parser.scoreBinarizedTree(tree, 0,debugLvl-1);
				if (debugLvl > 0) System.out.println(tree + " " + tree.score()+ " "+treeScore);
				FeatureVector treeFeatureVector=null;
				if (featureList!=null) {treeFeatureVector = extractTreeFeatureVector(tree, 0,tmpFVec.clear());}
				if (unitNodes.size() > 2) throw new NotImplementedException();
				if (unitNodes.size()==0) continue;
				Tree unitTree = unitNodes.get(0);
				getUnit(unitTree,hdrToks,bestUnits,hdr);
				if (unitNodes.size() > 1) getUnit(unitNodes.get(1),hdrToks,bestUnits2,hdr);
				int numUnits = unitNodes.size();
				if (bestUnits != null && bestUnits.size()>0) {
					float baseScore = (float) bestUnits.get(0).getScore();
					for (int a = 0; a < bestUnits.size(); a++) {
						Unit bestUnit = bestUnits.get(a).getKey();
						float val = (float) ((float) bestUnits.get(a).getScore()+treeScore-baseScore);
						if (numUnits==1 || bestUnits2.size()==0) {
							units.adjustOrPutValue(bestUnits.get(a), val,val);
							if (treeFeatureVector != null) addFeatureVector(featureList, bestUnits.get(a),null,treeFeatureVector,val,bestUnit);
						} else {
							float baseScore2 = (float) bestUnits2.get(0).getScore();
							for (int a2 = 0; a2 < bestUnits2.size(); a2++) {
								Unit bestUnit2 = bestUnits2.get(a2).getKey();
								float val2 = (float) bestUnits2.get(a2).getScore() - baseScore2 + val;
								Unit newUnit = new UnitPair(bestUnit, bestUnit2, UnitPair.OpType.Alt,bestUnit.getParentQuantity());
								UnitFeatures newUnitF = addFeatureVector(featureList, bestUnits.get(a),bestUnits2.get(a2),treeFeatureVector,val2, newUnit);
								units.adjustOrPutValue(newUnitF, val2, val2);
							}
						}
					}
				}
			}
			if (units.size()>0) {
				Vector<EntryWithScore<Unit>> possibleUnits = new Vector<EntryWithScore<Unit>>();
				double logNorm = 0;
				for (TObjectFloatIterator<UnitFeatures> iter = units.iterator(); iter.hasNext();) {
					iter.advance();
					if (iter.value() > 0) possibleUnits.add(new UnitSpan(iter.key().getKey(), iter.value(),iter.key().start(),iter.key().end()));
					logNorm = RobustMath.logSumExp(logNorm, iter.value());
				}
				Collections.sort(possibleUnits);
				// remove subsumed units from the top-k list?
				for (int ik = possibleUnits.size()-1; ik>0; ik--) {
					UnitSpan uspan = (UnitSpan) possibleUnits.get(ik);
					for (int j = ik-1; j >= 0; j--) {
						UnitSpan uspanj = (UnitSpan) possibleUnits.get(j);
						if (uspanj.start() <= uspan.start() && uspanj.end() >= uspan.end() 
								&& uspan.end()-uspan.start() < uspanj.end()-uspanj.start()) {
							possibleUnits.remove(ik);
							break;
						}
					}
				}
				
				if (possibleUnits.size()>k) {
					while (possibleUnits.size() > k) {
						possibleUnits.remove(possibleUnits.size()-1);
					}
				}
				if (k > 1) {
					// normalize the units to get probabilities.
					//	for (EntryWithScore<Unit> entry : possibleUnits) {
					//		entry.setScore(Math.exp(entry.getScore()-logNorm));
					//	}
				}
				if (featureList!=null) {
					for (UnitFeatures obj : featureList) {
						obj.checkCorrectness(params.weights);
					}
					Collections.sort(featureList);
					while (featureList.size()>k) {
						featureList.remove(featureList.size()-1);
					}
					if (debugLvl > 0) {
						for (UnitFeatures unitObj : featureList) {
							System.out.println(unitObj.getKey().getBaseName()+ " "+unitObj.getScore()); 
							unitObj.fvals.print(Params.FTypes.values(), params.weights);
						}
					}
				}
				return possibleUnits;
			} 
		} else {
			//	System.out.println("No unit");
		}

		return null;
	}

	protected void getTopUnitNodes(Tree tree, Vector<Tree> unitNodes) {
		getUnitNodes(tree,unitNodes,StateIndex.States.U.name());
	}
	private FeatureVector extractTreeFeatureVector(Tree tree, int start, FeatureVector fvec) {
		if (tree.isLeaf()) {
			return fvec;
		}
		if (tree.isPreTerminal()) {
			String wordStr = tree.children()[0].label().value();
			int tag = tokenScorer.tagIndex.indexOf(tree.label().value());
			int word = tokenScorer.wordIndex.indexOf(wordStr);
			IntTaggedWord iTW = new IntTaggedWord(word, tag);
			float score = tokenScorer.score(fvec,iTW, start, wordStr, null);
			return fvec;
		}
		int parent = tokenScorer.stateIndex.indexOf(tree.label().value());
		int firstChild = tokenScorer.stateIndex.indexOf(tree.children()[0].label().value());
		if (tree.numChildren() == 1) {
			extractTreeFeatureVector(tree.children()[0], start, fvec);
			return fvec;
		}
		int secondChild = tokenScorer.stateIndex.indexOf(tree.children()[1].label().value());
		BinaryRule br = new BinaryRule(parent, firstChild, secondChild);
		int sz0 = tree.children()[0].yield().size();
		extractTreeFeatureVector(tree.children()[0], start, fvec);
		extractTreeFeatureVector(tree.children()[1], start + sz0, fvec);
		tokenScorer.score(br,start,start+sz0+tree.children()[1].yield().size(),start + sz0,fvec);
		return fvec;
	}
	private UnitFeatures addFeatureVector(Vector<UnitFeatures> featureList, UnitFeatures unitObject1,UnitFeatures unitObject2, FeatureVector treeFeatureVector, float val2, Unit newUnit) {
		UnitFeatures unitObject = new UnitFeatures(newUnit, val2, unitObject1, unitObject2,unitObject2==null?unitObject1.start():Math.min(unitObject1.start(),unitObject2.start())
				,unitObject2==null?unitObject1.end():Math.max(unitObject1.end(),unitObject2.end()));
		if (treeFeatureVector != null) unitObject.addFeatures(treeFeatureVector);
		if (featureList != null) featureList.add(unitObject);
		return unitObject;
	}
	List<UnitFeatures> bestUnitsBase = new Vector<UnitFeatures>();
	List<UnitFeatures> bestUnitsMult = new Vector<UnitFeatures>();
	UnitFeatures tmpEntry = new UnitFeatures(null, 0,0,0);
	private void getUnit(Tree unitTree, List<String> hdrToks,List<UnitFeatures> bestUnitsVec,String hdr) {
		bestUnitsVec.clear();
		Tree bestUTree = getSubTree(unitTree,new String[]{"BU_Q", "BU","SU", "Op_U"});
		Tree multTree = getSubTree(unitTree,new String[]{"Mult"});
		if (bestUTree != null) {
			Vector<UnitFeatures> bestUnits[] = tokenScorer.sortedUnits[bestUTree.getSpan().getSource()][bestUTree.getSpan().getTarget()];
			if(bestUTree.getChild(0).label().value().startsWith("PER") && tokenScorer.dictionaryMatch(bestUTree.getSpan().getTarget(), bestUTree.getSpan().getTarget()) < 0.9){
				System.out.println("work in progress");
				getUnit(bestUTree.getChild(1), hdrToks,bestUnitsBase,hdr);
				Unit unit1 = this.quantityDict.multipleOneUnit();
				int start = bestUTree.getSpan().getSource();
				int endP = bestUTree.getSpan().getTarget();
				for (int a1 = 0; a1 < bestUnitsBase.size(); a1++) {
					Unit unit2 = bestUnitsBase.get(a1).getKey();
					bestUnitsVec.add(new UnitFeatures(quantityDict.newUnit(unit1,unit2,UnitPair.OpType.Ratio),bestUnitsBase.get(a1).getScore(), 
							bestUnitsBase.get(a1), null,start,endP));
					multTree = null; //reset multTree, because here multiplier is part of unit.
				}
			}
			else if (bestUTree.getChild(0).label().value().startsWith("CU2")&&tokenScorer.dictionaryMatch(bestUTree.getSpan().getSource(),bestUTree.getSpan().getTarget()) < 0.9) {
				Vector<Tree> simpleUnits = new Vector<Tree>();
				getUnitNodes(bestUTree, simpleUnits, StateIndex.States.SU.name());
				if (simpleUnits.size()<2) {
					throw new NotImplementedException("Error in parsing of Compund unit");
				}
				for (int s = 1; s < simpleUnits.size(); s++) {
					if (s == 1) { 
						getUnit(simpleUnits.get(0), hdrToks,bestUnitsBase,hdr);
					} else {
						bestUnitsBase.clear();
						bestUnitsBase.addAll(bestUnitsVec);
						bestUnitsVec.clear();
					}
					getUnit(simpleUnits.get(s), hdrToks,bestUnitsMult,hdr);
					int a = 0;
					for (int a1 = 0; a1 < bestUnitsBase.size(); a1++) {
						Unit unit1 = bestUnitsBase.get(a1).getKey();
						for (int a2 = 0; a2 < bestUnitsMult.size(); a2++) {
							Unit unit2 = bestUnitsMult.get(a2).getKey();
							int start = Math.min(bestUnitsBase.get(a1).start(),bestUnitsMult.get(a2).start());
							int end = Math.max(bestUnitsBase.get(a1).end(),bestUnitsMult.get(a2).end());
							bestUnitsVec.add(new UnitFeatures(quantityDict.newUnit(unit1,unit2,UnitPair.OpType.Ratio),newCompundScore(bestUnitsBase.get(a1).getScore(),bestUnitsMult.get(a2).getScore()), 
									bestUnitsBase.get(a1), bestUnitsMult.get(a2),start,end));
							a++;
						}
					}
				}
			} else if (bestUnits[tokenScorer.unitState] == null || bestUnits[tokenScorer.unitState].size()==0) {
				// a new base unit.
				bestUnits[tokenScorer.unitState] = new Vector<UnitFeatures>();
				int start = bestUTree.getSpan().getSource();
				int endP = bestUTree.getSpan().getTarget();
				bestUnits[tokenScorer.unitState].add(tokenScorer.newUnit(quantityDict.newUnit(hdrToks.subList(start, endP+1)), start, endP));
				//if (tokenScorer.altUnitCounts > 1) bestUnits[tokenScorer.unitState+2] = null;
				System.out.println("Created new unit "+bestUnits[tokenScorer.unitState]+ " on string "+hdr);
				bestUnitsVec.add(bestUnits[tokenScorer.unitState].get(0));
			} else {
				for (int a1 = 0; a1 < bestUnits[tokenScorer.unitState].size(); a1++) {
					bestUnitsVec.add(bestUnits[tokenScorer.unitState].get(a1));
				}
			}
		}
		UnitFeatures multUnit = null;
		float multScore = 0;
		if (multTree != null) {
			int start = multTree.getSpan().getSource();
			int end = multTree.getSpan().getTarget();
			if (tokenScorer.sortedUnits[start][end][tokenScorer.multUnitState] != null &&  tokenScorer.sortedUnits[start][end][tokenScorer.multUnitState].size() > 0)
				multUnit = tokenScorer.sortedUnits[start][end][tokenScorer.multUnitState].get(0);
			else {
				multUnit = tokenScorer.newUnit(quantityDict.newUnit(hdrToks.subList(start, end+1)), start, end);
				System.out.println("Created new mult unit "+multUnit+ " on string "+hdr);
			}
		}
		if (bestUnitsVec != null && bestUnitsVec.size()>0) {
			if (multUnit != null) {
				for (int a = 0; a < bestUnitsVec.size(); a++) {
					Unit bestUnit = bestUnitsVec.get(a).getKey();
					double score = bestUnitsVec.get(a).getScore();
					int start = Math.min(bestUnitsVec.get(a).start(),multUnit.start());
					int end = Math.max(bestUnitsVec.get(a).end(),multUnit.end());
					bestUnitsVec.set(a,new UnitFeatures(new UnitMultPair(bestUnit, multUnit.getKey()), score+multScore, multUnit, bestUnitsVec.get(a),start,end));
				} 
			}
		} else if (multUnit!=null) {
			bestUnitsVec.add(multUnit);
		} else {
			//throw new NotImplementedException("Unknown state ");
			return;
		}
	}

	protected double newCompundScore(double score, double score2) {
		// Jul 29, changing to sum to allow for training via a linear classifier.
		return score + score2;
	}

	private Tree getSubTree(Tree tree, String[] strings) {
		for (String label : strings) {
			if (tree.label().value().equals(label)) {
				return tree;
			}
		}
		for (Tree kid : tree.children()) {
			Tree subTree = getSubTree(kid, strings);
			if (subTree != null)
				return subTree;
		}
		return null;
	}
	private void getUnitNodes(Tree tree, Vector<Tree> unitNodes, String unitLabel) {
		if (tree.label().value().equals(unitLabel)) {
			unitNodes.add(tree);
			return;
		}
		for (Tree kid : tree.children()) {
			getUnitNodes(kid, unitNodes, unitLabel);
		}
	}
	@Override
	public List<? extends EntryWithScore<Unit>> parseHeaderProbabilistic(String hdr,
			List<String> explanation, int debugLvl, int k, ParseState hdrMatches[]) throws IOException {
		if (hdrMatches==null) {
			hdrMatches = new ParseState[1];
		}
		if (explanation==null) {
			explanation = new Vector<String>();
		}
		List<? extends EntryWithScore<Unit>> units = super.parseHeaderProbabilistic(hdr, explanation, debugLvl,k,hdrMatches);
		if (explanation != null && explanation.size()==1) {
			return units;
		}
		return parseHeader(hdrMatches[0].hdr, hdrMatches[0],debugLvl,null,null,k, null);
	}
	public static void main(String args[]) throws Exception {
		// ,  
		//Max. 10-min. average sustained wind Km/h
		//
		//
		// 
		//  
		//
		Vector<UnitFeatures> featureList = new Vector();
			List<? extends EntryWithScore<Unit>> unitsR = new CFGParser4Header(null).getTopKUnits("Revenue ($B)",  3, featureList,1);
			//	List<EntryWithScore<Unit>> unitsR = new CFGParser4Header(null).parseHeader("Wealth (in " + UnitSpan.StartXML + " $mil "+UnitSpan.EndXML+")",null, 2,null, 
		//new short[][]{{(short) Tags.W.ordinal()},{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}
		//,{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}}
		//		new UnitSpan("united states dollar [million]"),	1,featureList);

		//List<EntryWithScore<Unit>> unitsR = (List<EntryWithScore<Unit>>) new CFGParser4Header(null).parseHeader("Profit/(loss) before tax ( £m )", 
		//			new short[][]{{(short) Tags.W.ordinal()},{(short) Tags.W.ordinal()},{(short) Tags.Mult.ordinal()},{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()},{(short) Tags.W.ordinal()},{(short) Tags.W.ordinal()}}
			//	null
				//,1,1,featureList);

		//"billions usd", new short[][]{{(short) Tags.Mult.ordinal()},{(short) Tags.SU.ordinal()}});
		//Loading g / m ( gr / ft )"); 
		// Max. 10-min. average sustained wind Km/h
		// ("fl. oz (US)", new short[][]{{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()}}); // getting wrongly matched to kg/L
		if (unitsR != null) {
			eval.Utils.printExtractedUnits(unitsR,true);
			
		}
	}
	public double[] getParamsArray() {
		return params.weights;
	}
	public String featureName(int i) {
		return params.featureName(i);
	}
	@Override
	public List<? extends EntryWithScore<Unit>> parseCell(String unitStr,
			int k, ParseState context) throws IOException {
		return parseHeader(unitStr, null, context, 0, null,null, k, null);
	}
}
