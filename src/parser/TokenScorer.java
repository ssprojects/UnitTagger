package parser;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.parser.lexparser.UnknownWordModel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import gnu.trove.TIntArrayList;
import iitb.shared.EntryWithScore;
import iitb.shared.SignatureSetIndex.DocResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.NotImplementedException;

import parser.CFGParser.EnumIndex;
import parser.CFGParser.Params;
import parser.CFGParser.StateIndex;
import parser.CFGParser.TagArrayIterator;
import parser.CFGParser.TaggedToken;
import parser.CFGParser.Token;
import parser.CFGParser.WordIndex;
import parser.CFGParser.EnumIndex.Tags;
import parser.CFGParser.Params.FTypes;
import parser.CFGParser.StateIndex.States;
import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordFrequency;
import conditionalCFG.ConditionalLexicon;

public class TokenScorer implements ConditionalLexicon {
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
			if (start==end && start==1) {
				System.out.println();
			}
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
					boolean found = wordFreq.getRelativeFrequency(hdrToks.get(startM),freqVector);
					if (!found) {
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
					if (!found && relativeFreqInCatalog < Float.MIN_VALUE) {
						System.out.println("Found no match in wordnet for "+hdrToks.get(startM) + " "+unit.getBaseName());
					} else {
						score += (1-freq)*params.weights[FTypes.INLANG.ordinal()];
					}
					if (SimpleParser.wordSymbolsHash.contains(hdrToks.get(startM))) {
						if (freq > 0.5f) {
							System.out.println("Frequency not well calibrated?");
						}
					}
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
			float score = 0;
			// penalize unknown words in intervening units
			//
			int unitStart = split;
			if (!stateIndex.isUnit(rule.rightChild)) {
				// right child is Op_U, so ignore the op
				unitStart = split+1;
			}
			// Penalize multiplier unit-pair from appearing as a list of units.
			if (someUnitInside(start,split, multUnitState) || someUnitInside(unitStart,end, multUnitState)) {
				return Float.NEGATIVE_INFINITY;
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
				score += fractionUnk*params.weights[FTypes.PercentUnkInUnit.ordinal()];
			
			
			
			return score;
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