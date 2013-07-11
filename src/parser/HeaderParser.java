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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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
import iitb.shared.SignatureSetIndex.DocResult;

import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitMultPair;
import catalog.UnitPair;
import catalog.WordFrequency;
import catalog.WordnetFrequency;

import conditionalCFG.ConditionalCFGParser;
import conditionalCFG.ConditionalLexicon;

import parser.HeaderParser.EnumIndex.Tags;
import parser.HeaderParser.Params.FTypes;
import parser.HeaderParser.StateIndex.States;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.ExhaustivePCFGParser;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.parser.lexparser.UnknownWordModel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.StringUtils;

public class HeaderParser extends SimpleParser {
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

		"Sep_SU ::- Op SU 1"+ "\n" +

		"SU ::- SU_MW SU_W 1f"+ "\n" +

		"SU_MW ::- SU_W 1f"+ "\n" + 
		"SU_MW ::- SU_MW SU_W 1f"+ "\n"
		;

	// TODO: allow a multiplier for simple units like in mg/thousand litres.
	public static class EnumIndex implements Index<String> {
		public enum Tags {SU, SU_W, W, Mult, IN, OF, Op,Boundary};
		public short allowedTags[][] = {
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.Mult.ordinal()},
				{(short) Tags.SU.ordinal(),(short) Tags.SU_W.ordinal(),(short) Tags.W.ordinal(),(short) Tags.IN.ordinal()},
				{(short) Tags.W.ordinal(),(short) Tags.OF.ordinal()},
				{(short) Tags.W.ordinal(), (short) Tags.Op.ordinal()},
				{(short) Tags.Boundary.ordinal()},
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
		public enum States {ROOT,ROOT_,Junk,Junk_U,Sep_U,IN_U,U, UL,IN_Mult,Mult_OF,BU,CU2,Sep_SU,SU_MW};// W, Mult, IN, OF, Op,Boundary};
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
			SINGLELetter,INLANG, MatchLength,Subsumed,SymbolDictMatchThreshold,LemmaDictMatchThreshold,PercentUnkInUnit,PercenUnkInUnitThreshold};
			float weights[]=new float[]{0.5f,0.5f,-0.05f,-0.5f,0.5f,1f,
					-1f,-1.1f,0.01f,-0.5f,-0.9f,-0.9f,-2f,0.5f};
	}
	public static class TokenScorer implements ConditionalLexicon {
		private static final float NegInfty = -100;
		private static final float ScoreEps = 0.01f;
		private DocResult dictMatches;
		private List<Token> sentence;
		List<String> hdrToks;
		private TIntArrayList brackets;
		EnumIndex tagIndex;
		Index<String> wordIndex;
		StateIndex stateIndex;
		QuantityCatalog matcher;
		int altUnitCounts = 3;
		int numStatesWithFeatures=2;
		int multUnitState=0;
		int unitState=1;
		Params params;
		float scores[][][];
		Unit bestUnit[][][];
		int lastMatch[] = new int[1];
		TaggedToken boundaryToken;
		short forcedTags[][]=null;
		WordFrequency wordFreq;
		Vector<EntryWithScore<String[]> > freqVector = new Vector<EntryWithScore<String[]>>();

		HashSet<String> unitWords = new HashSet<String>();
		public TokenScorer(StateIndex stateIndex, EnumIndex tagIndex, QuantityCatalog matcher, Index<String> wordIndex, WordFrequency wordFreq) {
			this.tagIndex = tagIndex;
			this.wordIndex = wordIndex;
			this.matcher = matcher;
			params = new Params();
			this.stateIndex = stateIndex;
			boundaryToken = new TaggedToken(BOUNDARY_TAG, Tags.Boundary, Lexicon.BOUNDARY);
			forcedTags = null;
			this.wordFreq = wordFreq;
		}
		public List<? extends HasWord> cacheScores(List<String> hdrToks, TIntArrayList brackets, short[][] forcedTags) {
			this.hdrToks = hdrToks;
			DocResult res = matcher.subSequenceMatch(hdrToks, 0.8f);
			this.dictMatches = res;
			this.brackets = brackets;
			if (scores == null || scores.length < hdrToks.size()) {
				scores = new float[hdrToks.size()][hdrToks.size()][numStatesWithFeatures]; 
				bestUnit = new Unit[hdrToks.size()][hdrToks.size()][numStatesWithFeatures*altUnitCounts];
			} 
			this.forcedTags=forcedTags;
			float unitBias = params.weights[FTypes.UnitBias.ordinal()];
			for (int i = 0; i < hdrToks.size(); i++) {
				for (int j = i; j < hdrToks.size(); j++) {
					Arrays.fill(scores[i][j],unitBias);
					Arrays.fill(bestUnit[i][j],null);
				}
				Arrays.fill(scores[i][i], unitBias);
			}

			int maxMatchLen = 0;
			for (int h = res.numHits()-1; h >= 0; h--) {
				int id = res.hitDocId(h);
				float score = res.hitMatch(h);
				if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.SymbolMatch && score + params.weights[FTypes.SymbolDictMatchThreshold.ordinal()] < 0)
					continue;
				if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.LemmaMatch && score + params.weights[FTypes.LemmaDictMatchThreshold.ordinal()] < 0)
					continue;
				float relativeFreqInCatalog = 0;
				
				Unit unit = matcher.idToUnitMap.get(id);
				int start = res.hitPosition(h);
				int end = res.hitEndPosition(h);

				if (matcher.idToUnitMap.getType(id) != matcher.idToUnitMap.ConceptMatch) {
					maxMatchLen = Math.max(maxMatchLen, res.hitLength(h));
					//Quantity concept = matcher.idToUnitMap.getConcept(id);
					score = score*params.weights[FTypes.DictMatchWeight.ordinal()];
					score += unitBias;
					score += (res.hitLength(h)-1)*params.weights[FTypes.MatchLength.ordinal()];
					List<String> unitToks = matcher.idToUnitMap.getTokens(id);

					int startM = getMaximalTokens(res.hitMatch(h), unitToks,hdrToks,start,end,lastMatch);
					int endM=lastMatch[0];

					/*if (unitWords != null) {
						for (int s = startM; s <= endM; s++) {
							if (hdrToks.get(s).length()==1 && Character.isDigit(hdrToks.get(s).charAt(0))) continue;
							unitWords.add(hdrToks.get(s));
						}
						if (unitWords.size() > 100) {
							try {
								PrintStream os = new PrintStream(new File("expts/unitwords.txt"));
								for (String s : unitWords) {
									os.println(s);
								}
								os.flush();
								System.exit(0);
							} catch (FileNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}*/
					//if (startM==endM && hdrToks.get(startM).length()==1 && Character.isLetterOrDigit(hdrToks.get(startM).charAt(0)))
					//	score += params.weights[FTypes.SINGLELetter.ordinal()];

					//if (startM==endM && HeaderSegmenter.wordSymbolsHash.contains(hdrToks.get(startM)))
					//	score += params.weights[FTypes.INLANG.ordinal()];

					if (startM==endM) {
						float freq = 0.01f;
						// get relative frequencies 
						wordFreq.getRelativeFrequency(hdrToks.get(startM),freqVector);
						if (freqVector.size()==0) {
							relativeFreqInCatalog = matcher.getRelativeFrequency(id);
							if (relativeFreqInCatalog > Float.MIN_VALUE)
								freq = relativeFreqInCatalog;
						}
						int numMatches = 0;
						for (EntryWithScore<String[]> entry : freqVector) {
							String[] wordForms = entry.getKey();
							boolean isUnit=false;
							for (String wf : wordForms) {
								if (unit.getBaseName().equalsIgnoreCase(wf)) {
									isUnit=true;
									break;
								}
							}
							if (isUnit) {
								numMatches++;
								if (numMatches>1) {
									System.out.println("Violated uniqueness assumption of base unit");
								}
								freq += (float) entry.getScore();
							}
						}
						if (numMatches==0 && relativeFreqInCatalog < Float.MIN_VALUE) {
							System.out.println("Found no match in wordnet for "+hdrToks.get(startM) + " "+unit.getBaseName());
						}
						if (SimpleParser.wordSymbolsHash.contains(hdrToks.get(startM))) {
							if (freq > 0.5f) {
								System.out.println("Frequency not well calibrated?");
							}
						}
						score += (1-freq)*params.weights[FTypes.INLANG.ordinal()];
					}


					int state = unitState;
					if (Quantity.isUnitLess(unit.getParentQuantity())) {
						state = multUnitState;
					}

					for (int hp = res.numHits()-1; hp >= 0; hp--) {
						if (hp == h) continue;
						int idp = res.hitDocId(hp);
						if (matcher.idToUnitMap.getType(idp) != matcher.idToUnitMap.ConceptMatch) continue;
						if (res.hitEndPosition(hp) >= start || start - res.hitEndPosition(hp) >= 2) continue;
						if (matcher.idToUnitMap.get(idp).getParentQuantity()==unit.getParentQuantity()) {
							score += params.weights[FTypes.ContextWord.ordinal()]*res.hitMatch(hp);
						}
					}

					if (bestUnit[start][end][state] ==null || scores[start][end][state] < score-Float.MIN_VALUE) {
						scores[start][end][state] = score;
						bestUnit[start][end][state] = unit;
						for (int a = 1; a < altUnitCounts; a++) {
							bestUnit[start][end][a*2+state]=null;
						}
					} else if (Math.abs(scores[start][end][state]-score) < Float.MIN_VALUE) {
						for (int a = 0; a < altUnitCounts; a++) {
							if (bestUnit[start][end][a*2+state]==null || bestUnit[start][end][a*2+state]==unit) {
								bestUnit[start][end][a*2+state] = unit;
								break;
							}
						}
					}
				}
			}
			// add features corresponding to subsumed matches...
			for (int len = 1; len < maxMatchLen; len++) {
				for (int start = 0; start < scores.length-len+1; start++) {
					int end = start+len-1;
					int state = -1;
					if (bestUnit[start][end][unitState] != null)
						state = unitState;
					else if (bestUnit[start][end][multUnitState] != null)
						state = multUnitState;
					if (state==-1) continue;
					float score = scores[start][end][state];
					boolean subsumed=false;
					for (int diff = 1; diff <= maxMatchLen-len && !subsumed; diff++) {
						for (int s = start; s >= 0 && s >= start-diff; s--) {
							int e = s + len+diff-1;
							if (e >= scores[s].length) continue;
							if (scores[s][e][state] > score - ScoreEps) {
								scores[start][end][state] += params.weights[FTypes.Subsumed.ordinal()];
								subsumed = true;
								break;
							}
						}
					}
				}
			}
			sentence = new Vector<Token>();
			for (int i = 0; i < hdrToks.size(); i++) {
				String w = hdrToks.get(i);
				if (w.equalsIgnoreCase("in")) {
					sentence.add(new Token(EnumIndex.Tags.IN,w));
				} else if (w.equalsIgnoreCase("per") || w.equals("/")){
					sentence.add(new Token(EnumIndex.Tags.Op, w));
				} else if (w.equalsIgnoreCase("of")) {
					sentence.add(new Token(EnumIndex.Tags.OF, w));
				} else if (bestUnit[i][i][multUnitState] != null) {
					sentence.add(new Token(EnumIndex.Tags.Mult, w));
				} else {
					sentence.add(new Token(EnumIndex.Tags.W, w));
				}
			}
			sentence.add(boundaryToken);
			for (int pos = 0; pos < brackets.size(); pos++) {
				if (brackets.get(pos) < 0) continue; // mismatched brackets
				int start = brackets.get(pos) >> 16;
				int end = brackets.get(pos) & ((1<<16)-1);
				float bscore = params.weights[FTypes.WithinBracket.ordinal()];
				if (start==end  && hdrToks.get(start).length()==1)  {
					// do not give bracket advantage to (s) and [number]
					char ch = hdrToks.get(start).charAt(0);
					if (ch == 's'|| Character.isDigit(ch))
						continue;
				}
				if (someUnitInside(start,end,unitState)) scores[start][end][unitState] += bscore;
				if (someUnitInside(start,end,multUnitState))  scores[start][end][multUnitState] += bscore;
			}
			/*
			for (int istart = 0; istart < hdrToks.size(); istart++) {
				if (sentence.get(istart).tag==Tags.IN) {
					float bscore = params.weights[FTypes.AfterIN.ordinal()];
					int start = istart+1;
					for (int end = start; end < hdrToks.size(); end++) {
						if (bestUnit[start][end][unitState] != null) scores[start][end][unitState] += bscore;
						if (bestUnit[start][end][multUnitState] != null) scores[start][end][multUnitState] += bscore;
						if (start > istart+1 && bestUnit[start][end][unitState] == null && bestUnit[start][end][multUnitState] == null) {
							break; // influence of IN stops after a non-match.
						}
					}
				}
			}
			 */
			return sentence;
		}
		private boolean someUnitInside(int start, int end, int state) {
			for (int s = start; s <= end; s++) {
				for (int e = start; e <= end; e++) {
					if (bestUnit[s][e][state] != null) return true;
				}
			}
			return false;
		}
		private int getMaximalTokens(float score, List<String> unitToks,
				List<String> hdrToks2, int start, int end, int[] lastMatch2) {
			lastMatch2[0]=end;
			if (score > 1 - Float.MIN_VALUE)
				return start;
			for (; start <= end; start++) {
				if (unitToks.contains(hdrToks2.get(start)))
					break;
			}
			for (;start <= end; end--) {
				if (unitToks.contains(hdrToks2.get(end)))
					return start;
				lastMatch2[0]=end-1;
			}
			assert(start <= lastMatch2[0]);
			return start;
		}
		private float addOrInit(float f, float bscore) {
			return (Float.isInfinite(f)?bscore:f+bscore);
		}
		@Override
		public boolean isKnown(int word) {
			return (word >= 0);
		}

		@Override
		public boolean isKnown(String word) {
			return wordIndex.contains(word);
		}

		public float unitScoreForSpan(int start, int end) {
			float score = 0;

			return score;
		}
		@Override
		public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
			//if (sentence.get(loc) instanceof HasTag) {
			//	return new TagArrayIterator(word, tagIndex.allowedTags[(short) tagIndex.indexOf(((HasTag)sentence.get(loc)).tag())]);
			//}
			if (forcedTags != null && forcedTags.length > loc && forcedTags[loc] != null) {
				return new TagArrayIterator(word, forcedTags[loc]);
			}
			return new TagArrayIterator(word, tagIndex.allowedTags[sentence.get(loc).tag.ordinal()]);
		}

		@Override
		public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc,String featureSpec) {
			return null;
		}

		@Override
		public int numRules() {
			// TODO Auto-generated method stub
			//return 0;
			throw new NotImplementedException();
		}

		@Override
		public void initializeTraining(double numTrees) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(Collection<Tree> trees) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(Collection<Tree> trees, double weight) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(Tree tree, double weight) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(List<TaggedWord> sentence, double weight) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void train(TaggedWord tw, int loc, double weight) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void trainUnannotated(List<TaggedWord> sentence, double weight) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void finishTraining() {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
			
			float score = scoreSpan(loc,loc,tagIndex.isMultUnit(iTW.tag())?multUnitState:(tagIndex.isSimpleUnit(iTW.tag())?unitState:-1));
			//System.out.println(iTW.toString(wordIndex, tagIndex) + " "+ loc + " " + word + "  " + score);
			return score;
		}
		@Override
		public float score(BinaryRule rule, int start, int end, int split) {
			if (rule==null) return Float.NEGATIVE_INFINITY;
			//System.out.println(rule.toString(stateIndex));
			if (start+1==end || start == end) return 0; // since score already accounted for at the word-level
			end = end-1;
			if (stateIndex.isCU2(rule.parent)) {
				// ensure that the two children are from different parts of the unit taxonomy.
				if (start > split-1) return NegInfty;
				Unit bestUnitL = bestUnit[start][split-1][unitState];
				// OP symbol at split
				if (split+1 > end) return NegInfty;
				Unit bestUnitR = bestUnit[split+1][end][unitState];
				if (bestUnitL != null && bestUnitR != null && bestUnitL.getParentQuantity() == bestUnitR.getParentQuantity()) {
					return NegInfty;
				}
			} else if (stateIndex.isIN_U(rule.parent)) {
				float bscore = params.weights[FTypes.AfterIN.ordinal()];
				if (bestUnit[split][end][unitState] != null) return bscore;
			}else if (stateIndex.isState(StateIndex.States.IN_Mult, rule.parent)) {
				float bscore = params.weights[FTypes.AfterIN.ordinal()];
				if (bestUnit[split][end][multUnitState] != null) return bscore;
			} else if (stateIndex.isState(States.UL, rule.parent)) {
				// penalize unknown words in intervening units
				//
				int unitStart = split;
				if (!stateIndex.isUnit(rule.rightChild)) {
					// right child is Op_U, so ignore the op
					unitStart = split+1;
				}
				int numUnkToks = 0;
				int numW = 0;
				for (int p = unitStart; p <= end; p++) {
					if (sentence.get(p).tag==Tags.W) {
						numW++;
						if (((WordIndex) wordIndex).isUnknown(sentence.get(p).wrd))
							numUnkToks++;
					}
				}
				float fractionUnk  = ((float)numUnkToks)/Math.max(numW, 1);
				if (fractionUnk > params.weights[FTypes.PercenUnkInUnitThreshold.ordinal()])
					return fractionUnk*params.weights[FTypes.PercentUnkInUnit.ordinal()];
				return 0;
			}
			return scoreSpan(start,end,stateToScorePosition(rule.parent));
		}

		@Override
		public float score(UnaryRule rule, int start, int end) {
			if (rule==null) return Float.NEGATIVE_INFINITY;
			return 0; // conditional CFG cannot handle scores for unary rules.

		}
		private int stateToScorePosition(int state) {
			if (stateIndex.isBaseUnit(state)) {
				return unitState;
			}
			if (stateIndex.isMultUnit(state)) {
				return multUnitState;
			}
			return -1;
		}
		private float scoreSpan(int start, int end, int state) {
			if (state >=0)
				return scores[start][end][state];
			return 0;
		}
		@Override
		public void writeData(Writer w) throws IOException {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public void readData(BufferedReader in) throws IOException {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}

		@Override
		public UnknownWordModel getUnknownWordModel() {
			// TODO Auto-generated method stub
			//return null;
			throw new NotImplementedException();
		}

		@Override
		public void setUnknownWordModel(UnknownWordModel uwm) {
			// TODO Auto-generated method stub
			throw new NotImplementedException();
		}
		public double dictionaryMatch(int source, int target) {
			// TODO Auto-generated method stub
			return 0;
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
	public HeaderParser(Element options) throws IOException, ParserConfigurationException, SAXException {
		this(options,null);
	}
	public HeaderParser(Element options, QuantityCatalog quantMatcher) throws IOException, ParserConfigurationException, SAXException {
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
		tokenScorer = new TokenScorer(index, tagIndex,quantityDict,wordIndex, new WordnetFrequency());
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
		return parseHeader(hdr, null);
	}
	List<EntryWithScore<Unit> > bestUnits = new ReusableVector<EntryWithScore<Unit>>();
	List<EntryWithScore<Unit> > bestUnits2 = new ReusableVector<EntryWithScore<Unit>>();
	public List<EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags) {
		System.out.println(hdr);
		if (isURL(hdr)) return null;
		TIntArrayList brackets = new TIntArrayList();
		List<String> hdrToks = quantityDict.getTokens(hdr,brackets);
		if (hdrToks.size()==0) return null;

		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrToks, brackets,forcedTags);
		if (parser.parse(sentence)) {
			TObjectFloatHashMap<Unit> units = new TObjectFloatHashMap<Unit>();
			List<ScoredObject<Tree>> trees = parser.getBestParses();

			for (ScoredObject<Tree> stree : trees) {
				Tree tree = stree.object();
				Vector<Tree> unitNodes = new Vector<Tree>();
				tree.setSpans();
				getUnitNodes(tree, unitNodes,StateIndex.States.U.name());
				System.out.println(tree + " " + tree.score()+ " "+parser.scoreBinarizedTree(tree, 0));

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
			System.out.println("No unit");
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

	public static void main(String args[]) throws Exception {
		List<EntryWithScore<Unit>> unitsR = new HeaderParser(null).parseHeader("Median Earnings (£/year)");
		//"billions usd", new short[][]{{(short) Tags.Mult.ordinal()},{(short) Tags.SU.ordinal()}});
		//Loading g / m ( gr / ft )"); 
		// Max. 10-min. average sustained wind Km/h
		// ("fl. oz (US)", new short[][]{{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()},{(short) Tags.SU_W.ordinal()}}); // getting wrongly matched to kg/L
		if (unitsR != null) {
			for (EntryWithScore<Unit> unit : unitsR) {
				System.out.println(unit.getKey().getBaseName()+ " " +unit.getScore());
			}
		}
		/*
		NumericAnnotator[] parsers = new NumericAnnotator[]{new HeaderParser(null), new HeaderSegmenter(null)};
		String labeledDataFile="/mnt/a99/d0/WWT/workspace/WWT_GroundTruthV2/unitLabel4Headers.xml";
		Element elem = iitb.wwt.common.Params.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();

		int total = 0;
		int mistakes[] = new int[parsers.length];
		for (int r = 0; r < len; r++) {
			total++;
			Element rec = (Element) nodeList.item(r);
			String hdr = iitb.wwt.common.Params.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			HashSet<String> trueUnits = null;
			if (unitList != null && unitList.getLength()>0) {
				trueUnits = new HashSet<String>();
				for (int u = 0; u < unitList.getLength();u++) {
					trueUnits.add(unitList.item(u).getTextContent());
				}
			}
			int p = -1;
			boolean matchedA[] = new boolean[parsers.length];
			Arrays.fill(matchedA, false);

			for (NumericAnnotator parser : parsers) {
				p++;
				boolean matched=false;
				List<EntryWithScore<Unit>> extractedUnits = parser.parseHeader(hdr);
				System.out.println("Extracted from " + parser.getClass().getSimpleName() + " " + extractedUnits);
				if ((trueUnits==null || trueUnits.size()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
					matched = true;
				} else {
					if (trueUnits != null && extractedUnits != null && trueUnits.size()==extractedUnits.size()) {
						
						matched=true;
						for (EntryWithScore<Unit> unitScore : extractedUnits) {
							Unit unit = unitScore.getKey();
							if (!trueUnits.contains(unit.getBaseName())) {
								matched=false;
								break;
							}
						}
					}
				}
				if (!matched) {
					mistakes[p]++;
				}
				matchedA[p] = matched;
			} 
			if (p > 0 && matchedA[0] != matchedA[1]) {
				System.out.println("Mismatched");
			}
		}
		int p = 0;
		for (NumericAnnotator parser : parsers) {
			System.out.println(parser.getClass().getSimpleName() + "  " + mistakes[p]+ " / "+total);
			p++;
		}
		*/
	}
}
