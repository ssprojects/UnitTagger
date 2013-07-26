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

import parser.CFGParser.EnumIndex.Tags;
import parser.CFGParser.StateIndex.States;
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

public class CFGParser extends RuleBasedParser {
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
		"Mult_OF ::- Mult OF 1f" + "\n" +

		"BU ::- CU2 Sep_SU 1\n"+
		"BU ::- CU2 1\n"+
		"BU ::- SU 1"+ "\n" +

		"CU2 ::- SU Sep_SU 1 \n"+
		"CU2 ::- SU PER_SU 1 \n"+

		"Sep_SU ::- Op SU 1"+ "\n" +
		"Sep_SU ::- PER SU 1"+ "\n" +

		"SU ::- SU_MW SU_W 1f"+ "\n" +

		"SU_MW ::- SU_W 1f"+ "\n" + 
		"SU_MW ::- SU_W Op 1f"+ "\n" + 
		"SU_MW ::- SU_MW SU_W 1f"+ "\n"
		;

	// TODO: allow a multiplier for simple units like in mg/thousand litres.
	public static class EnumIndex implements Index<String> {
		public enum Tags {SU, SU_W, W, Mult, IN, OF, Op,PER,Boundary};
		public short allowedTags[][] = {
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.Mult.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.IN.ordinal()},
				{(short) Tags.W.ordinal(),(short) Tags.OF.ordinal()},
				{(short) Tags.W.ordinal(), (short) Tags.Op.ordinal()},
				{(short) Tags.PER.ordinal(),(short) Tags.SU.ordinal(),(short)Tags.W.ordinal()},
				{(short) Tags.Boundary.ordinal()}
		};
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
		public enum States {ROOT,ROOT_,Junk,Junk_U,Sep_U,IN_U,U, UL,IN_Mult,Mult_OF,BU,CU2,Sep_SU,SU_MW, PER_SU};// W, Mult, IN, OF, Op,Boundary};
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
		public boolean isCU2(int state) {
			return isState(States.CU2,state);
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
		public enum FTypes {WithinBracket,ContextWord,UnitBias,UnitScoreBias,AfterIN,DictMatchWeight,
						SINGLELetter,INLANG, MatchLength,Subsumed,SymbolDictMatchThreshold,LemmaDictMatchThreshold,
						PercentUnkInUnit,PercenUnkInUnitThreshold, Co_occurStats, CU2Bias, MultBias};
			float weights[]=new float[]{0.5f,0.5f,-0.05f,-0.5f,0.5f,1f,
					-1f,-1.1f,0.01f,-0.05f,-0.9f,-0.9f,-2f,0.5f,0.5f,0.06f,0.05f};
			// CU2bias should be more than unitbias to prefer compound units when one side is known e.g. people per sq km
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
	public CFGParser(Element options) throws IOException, ParserConfigurationException, SAXException {
		this(options,null);
	}
	public CFGParser(Element options, QuantityCatalog quantMatcher) throws IOException, ParserConfigurationException, SAXException {
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
		tokenScorer = new TokenScorer(index, tagIndex,quantityDict,wordIndex, new WordnetFrequency(),new PrUnitGivenWord(coOccurStats));
		Options op = new Options();
		op.dcTags=false;
		//op.testOptions.verbose=true;
		parser = new ConditionalCFGParser(bg, ug, tokenScorer, op, index, wordIndex, tagIndex);
	}

	private void initIndex(Index<String> index, Vector<BinaryRule> brules, Vector<UnaryRule> urules) throws IOException {
		String line;
		BufferedReader br  = new BufferedReader(new StringReader(grammar));
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
	public List<EntryWithScore<Unit>> parseHeader(String hdr) {
		return parseHeader(hdr, null, 0);
	}
	List<EntryWithScore<Unit> > bestUnits = new ReusableVector<EntryWithScore<Unit>>();
	List<EntryWithScore<Unit> > bestUnits2 = new ReusableVector<EntryWithScore<Unit>>();
	public List<EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl) {
		//System.out.println(hdr);
		if (isURL(hdr)) return null;
		TIntArrayList brackets = new TIntArrayList();
		List<String> hdrToks = quantityDict.getTokens(hdr,brackets);
		if (hdrToks.size()==0) return null;

		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrToks, brackets,forcedTags,debugLvl);
		if (parser.parse(sentence)) {
			TObjectFloatHashMap<Unit> units = new TObjectFloatHashMap<Unit>();
			List<ScoredObject<Tree>> trees = parser.getBestParses();

			for (ScoredObject<Tree> stree : trees) {
				Tree tree = stree.object();
				Vector<Tree> unitNodes = new Vector<Tree>();
				tree.setSpans();
				getUnitNodes(tree, unitNodes,StateIndex.States.U.name());
				if (debugLvl > 0) System.out.println(tree + " " + tree.score()+ " "+parser.scoreBinarizedTree(tree, 0,debugLvl-1));

				if (unitNodes.size() > 2) throw new NotImplementedException();
				if (unitNodes.size()==0) continue;
				Tree unitTree = unitNodes.get(0);
				getUnit(unitTree,hdrToks,bestUnits);
				if (unitNodes.size() > 1) getUnit(unitNodes.get(1),hdrToks,bestUnits2);
				int numUnits = unitNodes.size();
				if (bestUnits != null && bestUnits.size()>0) {
					for (int a = 0; a < bestUnits.size(); a++) {
						Unit bestUnit = bestUnits.get(a).getKey();
						float val = (float) bestUnits.get(a).getScore();
						if (numUnits==1 || bestUnits2.size()==0)
							units.adjustOrPutValue(bestUnit, val,val);
						else {
							for (int a2 = 0; a2 < bestUnits2.size(); a2++) {
								Unit bestUnit2 = bestUnits2.get(a2).getKey();
								float val2 = (float) bestUnits2.get(a2).getScore();
								units.adjustOrPutValue(new UnitPair(bestUnit, bestUnit2, UnitPair.OpType.Alt), val*val2,val*val2);
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
				return possibleUnits;
			} 
		} else {
		//	System.out.println("No unit");
		}
		return null;
	}

	List<EntryWithScore<Unit> > bestUnitsBase = new ReusableVector<EntryWithScore<Unit>>();
	List<EntryWithScore<Unit> > bestUnitsMult = new ReusableVector<EntryWithScore<Unit>>();
	EntryWithScore<Unit> tmpEntry = new EntryWithScore<Unit>(null, 0);
	private void getUnit(Tree unitTree, List<String> hdrToks,List<EntryWithScore<Unit>> bestUnitsVec) {
		// TODO Auto-generated method stub
		IntPair pair  = unitTree.getSpan();
		int start = pair.getSource();
		int end = pair.getTarget();
		bestUnitsVec.clear();

		Tree bestUTree = getSubTree(unitTree,new String[]{"BU","SU"});
		Tree multTree = getSubTree(unitTree,new String[]{"Mult"});

		if (bestUTree != null) {
			Unit bestUnits[] = tokenScorer.bestUnit[bestUTree.getSpan().getSource()][bestUTree.getSpan().getTarget()];
			float scoreArr[] = tokenScorer.scores[bestUTree.getSpan().getSource()][bestUTree.getSpan().getTarget()];
			
			// a new compound unit.
			if (bestUTree.numChildren()==1 && bestUTree.getChild(0).label().value().equals("CU2")&&tokenScorer.dictionaryMatch(bestUTree.getSpan().getSource(),bestUTree.getSpan().getTarget()) < 0.9) {
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
						bestUnitsVec.add(tmpEntry.init(quantityDict.newUnit(unit1,unit2,UnitPair.OpType.Ratio),newCompundScore(bestUnitsBase.get(a1).getScore(),bestUnitsMult.get(a2).getScore())));
						a++;
					}
				}
			} else if (bestUnits[tokenScorer.unitState] == null) {
				// a new base unit.
				bestUnits[tokenScorer.unitState] = quantityDict.newUnit(hdrToks.subList(bestUTree.getSpan().getSource(), bestUTree.getSpan().getTarget()+1));
				if (tokenScorer.altUnitCounts > 1) bestUnits[tokenScorer.unitState+2] = null;
				System.out.println("Created new unit "+bestUnits[tokenScorer.unitState]);
				bestUnitsVec.add(tmpEntry.init(bestUnits[tokenScorer.unitState],scoreArr[tokenScorer.unitState]));
			} else {
				for (int a1 = 0; a1 < tokenScorer.altUnitCounts; a1++) {
					Unit unit1 = bestUnits[a1*2+tokenScorer.unitState];
					if (unit1==null) break;
					bestUnitsVec.add(tmpEntry.init(unit1,scoreArr[tokenScorer.unitState]));
				}
			}
		}
		Unit multUnit = null;
		float multScore = 0;
		if (multTree != null) {
			multUnit = tokenScorer.bestUnit[multTree.getSpan().getSource()][multTree.getSpan().getTarget()][tokenScorer.multUnitState];
			multScore = tokenScorer.scores[multTree.getSpan().getSource()][multTree.getSpan().getTarget()][tokenScorer.multUnitState];
		}
		if (bestUnitsVec != null && bestUnitsVec.size()>0) {
			if (multUnit != null) {
				for (int a = 0; a < bestUnitsVec.size(); a++) {
					Unit bestUnit = bestUnitsVec.get(a).getKey();
					double score = bestUnitsVec.get(a).getScore();
					bestUnitsVec.set(a,tmpEntry.init(new UnitMultPair(bestUnit, multUnit), (float)score*multScore));
				} 
			}
		} else if (multUnit!=null) {
			bestUnitsVec.add(tmpEntry.init(multUnit, multScore));
		} else {
			throw new NotImplementedException("Unknown state ");
		}
	}

	private double newCompundScore(double score, double score2) {
		// TODO Auto-generated method stub
		return score*score2;
	}

	private Tree getSubTree(Tree tree, String[] strings) {
		for (String label : strings) {
			if (tree.label().value().equals(label)) {
				return tree;
			}
		}
		/*for (Tree kid : tree.children()) {
			for (String label : strings) {
				if (kid.label().value().equals(label)) {
					return kid;
				}
			}
		}
		 */
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
	public List<EntryWithScore<Unit>> parseHeaderExplain(String hdr,
			List<String> explanation) throws IOException {
		List<EntryWithScore<Unit>> units = super.parseHeaderExplain(hdr, explanation);
		if (explanation != null && explanation.size()==1) {
			return units;
		}
		return parseHeader(hdr);
	}
	public static void main(String args[]) throws Exception {
		// ,  
		//Max. 10-min. average sustained wind Km/h
		//
		//
		// 
		//  
		//
		
		List<EntryWithScore<Unit>> unitsR = new CFGParser(null).parseHeader("duration (s)",
				null
				//new short[][]{{(short) Tags.W.ordinal()},{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}
				//,{(short) Tags.SU.ordinal()},{(short) Tags.PER.ordinal()},{(short) Tags.SU.ordinal()}}
				,2);
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
}
