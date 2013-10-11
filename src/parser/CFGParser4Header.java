package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Character.Subset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import gnu.trove.TObjectIntHashMap;
import iitb.shared.EntryWithScore;
import iitb.shared.ReusableVector;

import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import catalog.Co_occurrenceStatistics;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitMultPair;
import catalog.UnitPair;
import catalog.WordnetFrequency;

import conditionalCFG.ConditionalCFGParser;

import parser.CFGParser4Header.EnumIndex.Tags;
import parser.cfgTrainer.FeatureVector;
import parser.coOccurMethods.PrUnitGivenWord;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.ExhaustivePCFGParser;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.StringUtils;

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

		"SU_MW ::- SU_W 1f"+ "\n" + 
		"SU_MW ::- SU_W Op 1f"+ "\n" + 
		"SU_MW ::- SU_MW SU_W 1f"+ "\n";
	// TODO: allow a multiplier for simple units like in mg/thousand litres.
	public static class EnumIndex implements Index<String> {
		public enum Tags {SU, SU_W, W, Mult, IN, OF, Op,PER,Q,Boundary};
		public short allowedTags[][] = {
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.Mult.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.IN.ordinal()},
				{(short) Tags.W.ordinal(),(short) Tags.OF.ordinal()},
				{(short) Tags.W.ordinal(), (short) Tags.Op.ordinal()},
				{(short) Tags.PER.ordinal(),(short) Tags.SU.ordinal(),(short)Tags.W.ordinal()},
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
			return Tags.SU.ordinal()==tag;
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
		public enum States {ROOT,ROOT_,Junk,Junk_U,Sep_U,IN_U,U, UL,IN_Mult,Mult_OF,BU,CU2,Sep_SU,SU_MW, PER_SU,Junk_QU,Q_U,SU_Q,BU_Q,CU2_Q};// W, Mult, IN, OF, Op,Boundary};
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
			return (isCU2(state) || tagIndex.isSimpleUnit(state)) || isUnit(state);
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

	public static class Params {
		public enum FTypes {ContextWord,UnitBias,DictMatchWeight,
			SINGLELetter,INLANG, MatchLength, Co_occurStats,Subsumed,
			WithinBracket,AfterIN,
			SymbolDictMatchThreshold,LemmaDictMatchThreshold,
			PercentUnkInUnit,PercenUnkInUnitThreshold, CU2Bias, MultBias};
			double[] weights=new double[]{0.5f,-0.05f,1f,
					0f,-1f,0.01f,0.5f,-0.07f,
					0.5f,0.5f,
					-0.9f,-0.9f,-2f,0.5f,0.06f,0.05f};
			// CU2bias should be more than unitbias to prefer compound units when one side is known e.g. people per sq km
			public int numFeatures() {
				return FTypes.values().length;
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
	public CFGParser4Header(Element options) throws IOException, ParserConfigurationException, SAXException {
		this(options,null);
	}
	public CFGParser4Header(Element options, QuantityCatalog quantMatcher) throws IOException, ParserConfigurationException, SAXException {
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
		tokenScorer = new TokenScorer(index, tagIndex,quantityDict,wordIndex, wordFreq,new PrUnitGivenWord(coOccurStats));
		Options op = new Options();
		params = new Params();
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
	public List<EntryWithScore<Unit>> parseHeader(String hdr) {
		return parseHeader(hdr, null, 0);
	}
	
	List<UnitFeatures > bestUnits = new Vector<UnitFeatures>();
	List<UnitFeatures> bestUnits2 = new Vector<UnitFeatures>();
	FeatureVector tmpFVec;

	public List<EntryWithScore<Unit>> getTopKUnits(String hdr, int k, Vector<UnitFeatures> featureList, int debugLvl) {
		return parseHeader(hdr, null, debugLvl, k, featureList);
	}
	public List<EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl) {
		return parseHeader(hdr, forcedTags, debugLvl, 1, null);
	}
	public List<EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl, int k, Vector<UnitFeatures> featureList) {
		return parseHeader(hdr, null, debugLvl,forcedTags, null, k, featureList);
	}
	ParseState getTokensWithSpan(String hdr, UnitSpan forcedUnit, ParseState hdrMatches) {
		if (hdrMatches != null && forcedUnit == null && hdrMatches.hdr.equalsIgnoreCase(hdr)) return hdrMatches;
		hdr = hdr.replace(UnitSpan.StartXML, UnitSpan.StartString);
		hdr = hdr.replace(UnitSpan.EndXML, UnitSpan.EndString);
		TIntArrayList unitSpanPos = null;
		TIntArrayList brackets = new TIntArrayList();
		if (forcedUnit != null) {
			unitSpanPos = new TIntArrayList();
		}
		List<String> hdrToks = quantityDict.getTokens(hdr,brackets,UnitSpan.SpecialTokens,unitSpanPos);
		if (forcedUnit != null) {
			for (int i = 0; i < unitSpanPos.size(); i++) {
				if (i %2==0) {
					forcedUnit.start = (unitSpanPos.get(i) & ((1<<16)-1));
				} else {
					forcedUnit.end = (unitSpanPos.get(i) & ((1<<16)-1))-1;
				}
			}
		}
		hdrMatches = new ParseState(hdr);
		hdrMatches.tokens = hdrToks;
		hdrMatches.brackets=brackets;
		return hdrMatches;
	}
	public List<EntryWithScore<Unit>> parseHeader(String hdr, ParseState hdrMatches, int debugLvl, short[][] forcedTags, UnitSpan forcedUnit, int k, Vector<UnitFeatures> featureList) {			
		if (debugLvl > 0) System.out.println(hdr);
		if (isURL(hdr)) return null;
		
		hdrMatches = getTokensWithSpan(hdr,forcedUnit,hdrMatches);
		if (hdrMatches.tokens.size()==0) return null;
		if (featureList!=null) featureList.clear();
		List<String> hdrToks = hdrMatches.tokens;
		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrMatches,forcedTags,debugLvl,featureList!=null,forcedUnit);
		if (parser.parse(sentence)) {
			TObjectFloatHashMap<Unit> units = new TObjectFloatHashMap<Unit>();
			List<ScoredObject<Tree>> trees = (k>=1?parser.getBestParses():parser.getKBestParses(k));
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
				getUnit(unitTree,hdrToks,bestUnits);
				if (unitNodes.size() > 1) getUnit(unitNodes.get(1),hdrToks,bestUnits2);
				int numUnits = unitNodes.size();
				if (bestUnits != null && bestUnits.size()>0) {
					float baseScore = (float) bestUnits.get(0).getScore();
					for (int a = 0; a < bestUnits.size(); a++) {
						Unit bestUnit = bestUnits.get(a).getKey();
						float val = (float) ((float) bestUnits.get(a).getScore()+treeScore-baseScore);
						if (numUnits==1 || bestUnits2.size()==0) {
							units.adjustOrPutValue(bestUnit, val,val);
							if (treeFeatureVector != null) addFeatureVector(featureList, bestUnits.get(a),null,treeFeatureVector,val,bestUnit);
						} else {
							float baseScore2 = (float) bestUnits2.get(0).getScore();
							for (int a2 = 0; a2 < bestUnits2.size(); a2++) {
								Unit bestUnit2 = bestUnits2.get(a2).getKey();
								float val2 = (float) bestUnits2.get(a2).getScore() - baseScore2 + val;
								Unit newUnit = new UnitPair(bestUnit, bestUnit2, UnitPair.OpType.Alt);
								units.adjustOrPutValue(newUnit, val2, val2);
								if (treeFeatureVector != null) addFeatureVector(featureList, bestUnits.get(a),bestUnits2.get(a2),treeFeatureVector,val2, newUnit);
							}
						}
					}
				}
			}
			if (units.size()>0) {
				Vector<EntryWithScore<Unit>> possibleUnits = new Vector<EntryWithScore<Unit>>();
				for (TObjectFloatIterator<Unit> iter = units.iterator(); iter.hasNext();) {
					iter.advance();
					possibleUnits.add(new EntryWithScore<Unit>(iter.key(), iter.value()));
				}
				Collections.sort(possibleUnits);
				if (possibleUnits.size()>k) {
					while (possibleUnits.size() > k) {
						possibleUnits.remove(possibleUnits.size()-1);
					}
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
							unitObj.fvals.print(Params.FTypes.values());
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
	private void addFeatureVector(Vector<UnitFeatures> featureList, UnitFeatures unitObject1,UnitFeatures unitObject2, FeatureVector treeFeatureVector, float val2, Unit newUnit) {
		UnitFeatures unitObject = new UnitFeatures(newUnit, val2, unitObject1, unitObject2);
		unitObject.addFeatures(treeFeatureVector);
		featureList.add(unitObject);
	}
	List<UnitFeatures> bestUnitsBase = new Vector<UnitFeatures>();
	List<UnitFeatures> bestUnitsMult = new Vector<UnitFeatures>();
	UnitFeatures tmpEntry = new UnitFeatures(null, 0);
	private void getUnit(Tree unitTree, List<String> hdrToks,List<UnitFeatures> bestUnitsVec) {
		bestUnitsVec.clear();
		Tree bestUTree = getSubTree(unitTree,new String[]{"BU_Q", "BU","SU"});
		Tree multTree = getSubTree(unitTree,new String[]{"Mult"});

		if (bestUTree != null) {
			Vector<UnitFeatures> bestUnits[] = tokenScorer.sortedUnits[bestUTree.getSpan().getSource()][bestUTree.getSpan().getTarget()];
			//float scoreArr[] = tokenScorer.scores[bestUTree.getSpan().getSource()][bestUTree.getSpan().getTarget()];

			// a new compound unit.
			if (bestUTree.numChildren()==1 && bestUTree.getChild(0).label().value().startsWith("CU2")&&tokenScorer.dictionaryMatch(bestUTree.getSpan().getSource(),bestUTree.getSpan().getTarget()) < 0.9) {
				Vector<Tree> simpleUnits = new Vector<Tree>();
				getUnitNodes(bestUTree, simpleUnits, Tags.SU.name());
				if (simpleUnits.size()!=2) {
					throw new NotImplementedException("Error in parsing of Compund unit");
				}
				getUnit(simpleUnits.get(0), hdrToks,bestUnitsBase);
				getUnit(simpleUnits.get(1), hdrToks,bestUnitsMult);
				int a = 0;
				for (int a1 = 0; a1 < bestUnitsBase.size(); a1++) {
					Unit unit1 = bestUnitsBase.get(a1).getKey();
					for (int a2 = 0; a2 < bestUnitsMult.size(); a2++) {
						Unit unit2 = bestUnitsMult.get(a2).getKey();
						bestUnitsVec.add(new UnitFeatures(quantityDict.newUnit(unit1,unit2,UnitPair.OpType.Ratio),newCompundScore(bestUnitsBase.get(a1).getScore(),bestUnitsMult.get(a2).getScore()), bestUnitsBase.get(a1), bestUnitsMult.get(a2)));
						a++;
					}
				}
			} else if (bestUnits[tokenScorer.unitState] == null || bestUnits[tokenScorer.unitState].size()==0) {
				// a new base unit.
				bestUnits[tokenScorer.unitState] = new Vector<UnitFeatures>();
				int start = bestUTree.getSpan().getSource();
				int endP = bestUTree.getSpan().getTarget();
				bestUnits[tokenScorer.unitState].add(tokenScorer.newUnit(quantityDict.newUnit(hdrToks.subList(start, endP+1)), start, endP));
				//if (tokenScorer.altUnitCounts > 1) bestUnits[tokenScorer.unitState+2] = null;
				System.out.println("Created new unit "+bestUnits[tokenScorer.unitState]);
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
			multUnit = tokenScorer.sortedUnits[start][end][tokenScorer.multUnitState].get(0);
		}
		if (bestUnitsVec != null && bestUnitsVec.size()>0) {
			if (multUnit != null) {
				for (int a = 0; a < bestUnitsVec.size(); a++) {
					Unit bestUnit = bestUnitsVec.get(a).getKey();
					double score = bestUnitsVec.get(a).getScore();
					bestUnitsVec.set(a,new UnitFeatures(new UnitMultPair(bestUnit, multUnit.getKey()), score+multScore, multUnit, bestUnitsVec.get(a)));
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
	public List<EntryWithScore<Unit>> parseHeaderProbabilistic(String hdr,
			List<String> explanation, int debugLvl, int k, ParseState hdrMatches[]) throws IOException {
		if (hdrMatches==null) {
			hdrMatches = new ParseState[1];
		}
		if (explanation==null) {
			explanation = new Vector<String>();
		}
		List<EntryWithScore<Unit>> units = super.parseHeaderProbabilistic(hdr, explanation, debugLvl,k,hdrMatches);
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
		List<EntryWithScore<Unit>> unitsR = new CFGParser4Header(null).getTopKUnits("Revenues ($ millions)",  1, featureList,1);
		/*List<EntryWithScore<Unit>> unitsR = new CFGParser4Header(null).parseHeader("Wealth (in " + UnitSpan.StartXML + " $mil "+UnitSpan.EndXML+")",null, 2,null, 
				//new short[][]{{(short) Tags.W.ordinal()},{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}
				//,{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}}
			new UnitSpan("united states dollar [million]"),
			1,featureList);
			*/
		//"billions usd", new short[][]{{(short) Tags.Mult.ordinal()},{(short) Tags.SU.ordinal()}});
		//Loading g / m ( gr / ft )"); 
		// Max. 10-min. average sustained wind Km/h
		// ("fl. oz (US)", new short[][]{{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()}}); // getting wrongly matched to kg/L
		if (unitsR != null) {
			for (EntryWithScore<Unit> unit : unitsR) {
				System.out.println(unit.getKey().getName()+ " " +unit.getScore());
			}
		}
		
	}
	public double[] getParamsArray() {
		return params.weights;
	}
}
