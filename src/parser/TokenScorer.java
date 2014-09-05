package parser;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.parser.lexparser.UnknownWordModel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectFloatHashMap;
import iitb.shared.EntryWithScore;
import iitb.shared.StringMap;
import iitb.shared.SignatureSetIndex.DocResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.NotImplementedException;

import parser.CFGParser4Header.EnumIndex;
import parser.CFGParser4Header.EnumIndex.Tags;
import parser.CFGParser4Header.Params;
import parser.CFGParser4Header.Params.FTypes;
import parser.CFGParser4Header.StateIndex;
import parser.CFGParser4Header.StateIndex.States;
import parser.CFGParser4Header.TagArrayIterator;
import parser.CFGParser4Header.Token;
import parser.CFGParser4Header.WordIndex;
import parser.cfgTrainer.FeatureVector;
import parser.coOccurMethods.Co_occurrenceScores;
import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitPair;
import catalog.WordFrequency;
import conditionalCFG.ConditionalLexicon;

public class TokenScorer implements ConditionalLexicon {
	private static final float NegInfty = -100;
	private static final float ScoreEps = 0.01f;
	boolean disableNewUnits = true;
	transient private DocResult dictMatches;
	transient private List<Token> sentence;
	transient protected List<String> hdrToks;
	transient private TIntArrayList brackets;
	EnumIndex tagIndex;
	Index<String> wordIndex;
	StateIndex stateIndex;
	QuantityCatalog matcher;
	protected int altUnitCounts = 5;
	int numStatesWithFeatures=2;
	int lexScore = numStatesWithFeatures;
	int multUnitState=0;
	int unitState=1;
	Params params;
	private float scores[][][];
	//	protected Unit bestUnit[][][];
	int lastMatch[] = new int[1];
	int numMatch[] = new int[1];
	public UnitFeatures newUnit(Unit newUnit, int start, int end) {
		UnitFeatures unitObj = new UnitFeatures(newUnit, 0,start,end);
		unitObj.setScore(defaultFeatureScore(start, end, trainMode?unitObj:null));
		return unitObj;
	}
	private float matchFeatureValue(int len) {
		// 8 feb 2014 do not make this less than 3 because common units like km/s will get uncessarily penalized.
		// 11 apr, increased to 4 to allow units like pound per square inch
		if (len<=4) return len-1;
		return (float) Math.pow(10, len-1);
	}
	private float defaultFeatureScore(int start, int end, UnitFeatures unitObj) {
		if (unitObj != null) {
			unitObj.addFeature(FTypes.UnitBias,1);
			unitObj.addFeature(Params.FTypes.MatchLength, matchFeatureValue(end-start+1));
		}
		if (disableNewUnits)
			return -Params.LargeWeight;
		return (float) (params.weights[FTypes.UnitBias.ordinal()] 
		                               + params.weights[Params.FTypes.MatchLength.ordinal()]*(matchFeatureValue(end-start+1)))
		                               ;
	}
	Vector<UnitFeatures> sortedUnits[][][];
	short forcedTags[][]=null;
	WordFrequency wordFreq;
	Vector<EntryWithScore<String[]> > freqVector = new Vector<EntryWithScore<String[]>>();
	Co_occurrenceScores coOcurStats;
	HashSet<String> unitWords = new HashSet<String>();
	int debugLvl;
	private boolean trainMode;
	ParseState context;
	List<String> contextTokens;
	UnitSpan forcedUnit;
	String hdr;
	boolean useExtendedGrammar=false;
	public TokenScorer(StateIndex stateIndex, EnumIndex tagIndex, QuantityCatalog matcher, 
			Index<String> wordIndex, WordFrequency wordFreq, Co_occurrenceScores coOcurStats,Params params) {
		this.tagIndex = tagIndex;
		this.wordIndex = wordIndex;
		this.matcher = matcher;
		this.stateIndex = stateIndex;
		this.params = params;
		forcedTags = null;
		this.wordFreq = wordFreq;
		this.coOcurStats = coOcurStats;
	}
	public List<? extends HasWord> cacheScores(ParseState hdrState, short[][] forcedTags, int debugLvl, boolean trainMode, UnitSpan forcedUnit, ParseState context) {
		this.hdr = hdrState.hdr;
		this.hdrToks = hdrState.tokens;
		this.dictMatches=hdrState.setDictMatch(matcher);
		this.brackets = hdrState.brackets;
		this.forcedTags=forcedTags;
		this.forcedUnit = forcedUnit;
		this.context = context;
		hdrState.setConceptsFound(matcher);
		if (forcedUnit != null) {
			initForcedUnitState(forcedUnit);
		}
		if (context != null) {
			 context.setTokens();
			 contextTokens = context.tokens;
		}
		return fillScoreArrays(debugLvl,trainMode);
	}
	public List<? extends HasWord> cacheScores(String hdr, List<String> hdrToks, TIntArrayList brackets, short[][] forcedTags, int debugLvl, boolean trainMode, UnitSpan forcedUnit) {
		this.hdrToks = hdrToks;
		DocResult res = matcher.subSequenceMatch(hdrToks, 0.8f);
		this.hdr = hdr;
		this.dictMatches = res;
		this.brackets = brackets;
		this.forcedTags=forcedTags;
		this.forcedUnit = forcedUnit;
		if (forcedUnit != null) {
			initForcedUnitState(forcedUnit);
		}
		return fillScoreArrays(debugLvl,trainMode);
	}
	private void initForcedUnitState(UnitSpan forcedUnit) {
		if (forcedUnit.isOpType(UnitPair.OpType.Ratio)) {
			forcedUnit.rootState=stateIndex.indexOf(States.CU2); // or States.CU2_Q;
		} else if (forcedUnit.isOpType(UnitPair.OpType.Alt)) {
			forcedUnit.rootState = stateIndex.indexOf(States.UL);
		} else if (forcedUnit.isOpType(UnitPair.OpType.Mult)) {
			forcedUnit.rootState = stateIndex.indexOf(States.U);
		} else {
			forcedUnit.rootState=stateIndex.indexOf(States.SU.name());
			//forcedUnit.rootState=Tags.SU.ordinal();
		}
	}
	int getUnitState(Unit unit) {
		int state = unitState;
		if (Quantity.isUnitLess(unit.getParentQuantity())) {
			state = multUnitState;
		}
		return state;
	}
	public List<? extends HasWord> fillScoreArrays(int debugLvl, boolean trainMode) {
		DocResult res = dictMatches;
		this.trainMode=trainMode;
		this.debugLvl=debugLvl;
		if (scores == null || scores.length < hdrToks.size()) {
			scores = new float[hdrToks.size()][hdrToks.size()][numStatesWithFeatures*2]; 
			//bestUnit = new Unit[hdrToks.size()][hdrToks.size()][numStatesWithFeatures*altUnitCounts];
			sortedUnits = new Vector[hdrToks.size()][hdrToks.size()][2];
		} 
		float unitBias = (float) params.weights[FTypes.UnitBias.ordinal()];

		for (int i = 0; i < hdrToks.size(); i++) {
			for (int j = i; j < hdrToks.size(); j++) {
				Arrays.fill(scores[i][j],0);
				// need the negative length bias here to ensure that new units are short as much as possible. 
				//  eg. density people per sq km should not include word density in unit.
				scores[i][j][unitState] = scores[i][j][multUnitState] = (float) (unitBias - matchFeatureValue(j-i+1)*params.weights[FTypes.MatchLength.ordinal()]);
				//Arrays.fill(bestUnit[i][j],null);
				for (int s = 0; s < sortedUnits[i][j].length; s++) if (sortedUnits[i][j][s] != null) sortedUnits[i][j][s].clear();
			}

			//Arrays.fill(scores[i][i], unitBias);
		}

		// collect unit matches for a position, so as to collectively find their frequencies in wordnet.
		TObjectFloatHashMap<Unit> unitFreqs[] = new TObjectFloatHashMap[hdrToks.size()];
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			if (res.hitLength(h) > 1) continue;
			float score = res.hitMatch(h);
			if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.SymbolMatch && score + params.weights[FTypes.SymbolDictMatchThreshold.ordinal()] < 0)
				continue;
			if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.LemmaMatch && score + params.weights[FTypes.LemmaDictMatchThreshold.ordinal()] < 0)
				continue;
			if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.ConceptMatch) continue;
			Unit unit = matcher.idToUnitMap.get(id);
			int p = res.hitPosition(h);
			if (unitFreqs[p]==null)
				unitFreqs[p] = new TObjectFloatHashMap<Unit>();
			unitFreqs[p].put(unit, -res.hitDocId(h)-1);
		}
		for (int i = 0; i < unitFreqs.length; i++) {
			if (unitFreqs[i] != null) {
				getFrequencies(unitFreqs[i], hdrToks.get(i));
			}
		}
		int maxMatchLen = 0;
		BitSet quantPositions = new BitSet(hdrToks.size());
		int targetQuantPos=-1;
		for (int i = 0; i < hdrToks.size(); i++) {
			String w = hdrToks.get(i);
			if (w.equals(CFGParser4Text.QuantityToken)) {
				targetQuantPos=i;
			} else if (isdigit(w)) {
				quantPositions.set(i);
			}
		}
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			float score = res.hitMatch(h);
			if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.SymbolMatch && score + params.weights[FTypes.SymbolDictMatchThreshold.ordinal()] < 0)
				continue;
			if (matcher.idToUnitMap.getType(id) == matcher.idToUnitMap.LemmaMatch && score + params.weights[FTypes.LemmaDictMatchThreshold.ordinal()] < 0)
				continue;
			int start = res.hitPosition(h);
			int end = res.hitEndPosition(h);
			if (targetQuantPos >= 0 && closerToAnotherNumber(start,end,targetQuantPos,quantPositions)) {
				continue;
			}

			Unit unit = matcher.idToUnitMap.get(id);
			int state = getUnitState(unit);
			
			UnitFeatures unitObject  = new UnitFeatures(unit, 0,start,end);
			if (matcher.idToUnitMap.getType(id) != matcher.idToUnitMap.ConceptMatch) {
				maxMatchLen = Math.max(maxMatchLen, res.hitLength(h));
				List<String> unitToks = matcher.idToUnitMap.getTokens(id);
				int startM = getMaximalTokens(res.hitMatch(h), unitToks,hdrToks,start,end,lastMatch,numMatch);
				int endM=lastMatch[0];
				//Quantity concept = matcher.idToUnitMap.getConcept(id);
				// 27/2/2014: increasing dictionary match based on the number of actual tokens that matched --- otherwise travels km/s is getting tagged as km.
				score = (float) (score*params.weights[FTypes.DictMatchWeight.ordinal()]*numMatch[0]);
				registerFeatureInfo(start, end, state, FTypes.DictMatchWeight, res.hitMatch(h)*numMatch[0], unit.getName(), trainMode?unitObject:null);
				score += unitBias;
				registerFeatureInfo(start, end, state, FTypes.UnitBias, 1, unit.getName(), trainMode?unitObject:null);
				score += matchFeatureValue(res.hitLength(h))*params.weights[FTypes.MatchLength.ordinal()];
				registerFeatureInfo(start, end, state, FTypes.MatchLength, matchFeatureValue(res.hitLength(h)), unit.getName(), trainMode?unitObject:null);
				
				if (startM==endM && hdrToks.get(startM).length()==1 && Character.isLetterOrDigit(hdrToks.get(startM).charAt(0))) {
					score += params.weights[FTypes.SINGLELetter.ordinal()];
					registerFeatureInfo(start, end, state, FTypes.SINGLELetter, 1, unit.getName(), trainMode?unitObject:null);
				}

				//if (startM==endM && HeaderSegmenter.wordSymbolsHash.contains(hdrToks.get(startM)))
				//	score += params.weights[FTypes.INLANG.ordinal()];

				if (startM==endM) {
					float freq = unitFreqs[startM].get(unit); //getFrequency(unit, hdrToks.get(startM), id);
					score += (1-freq)*params.weights[FTypes.INLANG.ordinal()];
					registerFeatureInfo(start, end, state, FTypes.INLANG, (1-freq), unit.getName(), trainMode?unitObject:null);
				}
				float bestScore = 0;
				DocResult contextDictMatch = (context==null || context.dictMatch==null?res:context.dictMatch);
				for (int hp = contextDictMatch.numHits()-1; hp >= 0; hp--) {
					int idp = contextDictMatch.hitDocId(hp);
					if (matcher.idToUnitMap.getType(idp) != matcher.idToUnitMap.ConceptMatch) continue;
					if (context == null) if (contextDictMatch.hitEndPosition(hp) >= start || start - contextDictMatch.hitEndPosition(hp) > params.contextDiffThreshold) continue;
					if (matcher.idToUnitMap.get(idp).getParentQuantity()==unit.getParentQuantity()) {
						bestScore = Math.max(bestScore,contextDictMatch.hitMatch(hp));
					}
				}
				if (bestScore > 0) {
					score += params.weights[FTypes.ContextWord.ordinal()]*bestScore;
					registerFeatureInfo(start, end, state, FTypes.ContextWord, bestScore, unit.getName(), trainMode?unitObject:null);
				}
				if (sortedUnits[start][end][state]==null) {
					sortedUnits[start][end][state] = new Vector<UnitFeatures>();
				}
				if (unitObject != null) unitObject.setScore(score);
				sortedUnits[start][end][state].add(unitObject);
			}
		}

		for (int start = 0; start < sortedUnits.length; start++) {
			for (int end = 0; end < sortedUnits[start].length; end++) {
				for (int state = 0; state  < sortedUnits[start][end].length; state++) {
					if (sortedUnits[start][end][state]==null) continue;
					for (EntryWithScore<Unit> unitObject : sortedUnits[start][end][state]) {
						float score = (float) unitObject.getScore();
						Unit unit = unitObject.getKey();
						if (scores[start][end][state] < score-Float.MIN_VALUE) {
							scores[start][end][state] = score;
							/*bestUnit[start][end][state] = unit;
							for (int a = 1; a < altUnitCounts; a++) {
								bestUnit[start][end][a*2+state]=null;
							}
							 */
						} /*else if (Math.abs(scores[start][end][state]-score) < Float.MIN_VALUE) {
							for (int a = 0; a < altUnitCounts; a++) {
								if (bestUnit[start][end][a*2+state]==null || bestUnit[start][end][a*2+state]==unit) {
									bestUnit[start][end][a*2+state] = unit;
									break;
								}
							}
						}*/
					}
				}
			}
		}
		// add features corresponding to subsumed matches...
		for (int len = 1; len < maxMatchLen; len++) {
			for (int start = 0; start < scores.length-len+1; start++) {
				int end = start+len-1;
				int state = -1;
				if (getUnit(start,end,unitState) != null)
					state = unitState;
				else if (getUnit(start,end,multUnitState) != null)
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
							Vector<UnitFeatures> unitVec = sortedUnits[start][end][state];
							if (unitVec != null) {
								for (UnitFeatures unitObj : unitVec) {
									unitObj.setScore(unitObj.getScore()+params.weights[FTypes.Subsumed.ordinal()]);
								}
							}
							registerFeatureInfo(start,end,state,FTypes.Subsumed,1,null, trainMode?sortedUnits[start][end][state]:null);
							subsumed = true;
							break;
						}
					}
				}
			}
		}

		// if there are alternative units for a span, check if corpus-level stats can help differentiate 
		//
		if (coOcurStats!=null) {
			StringMap<Unit> units = new StringMap<Unit>();
			for (int start = 0; start < hdrToks.size(); start++) {
				for (int end = start; end < hdrToks.size(); end++) {
					for (int state = 0; state < 2; state++) {
						if (sortedUnits[start][end][state] == null) continue;
						for (int a = 0; a < sortedUnits[start][end][state].size(); a++) {
							units.add(sortedUnits[start][end][state].get(a).getKey());
						}
					}
				}
			}
			float totalScores[] = coOcurStats.getCo_occurScores((context==null?hdrToks:contextTokens), units);
			if (totalScores!=null) {
			// the total contribution from co-occurrence statistics is now available.
			// now add the winning units in each slots.
			for (int start = 0; start < hdrToks.size(); start++) {
				for (int end = start; end < hdrToks.size(); end++) {
					for (int state = 0; state < 2; state++) {
						if (sortedUnits[start][end][state]==null) continue;
						/*
						float maxFreq = Float.NEGATIVE_INFINITY;
						float adjustedScores[] = new float[sortedUnits[start][end][state].size()];
						for (int a = 0; a < adjustedScores.length; a++) {
							Unit unit = sortedUnits[start][end][state].get(a).getKey();
							int id = units.get(unit);
							float freq = (start==end && coOcurStats.adjustFrequency())?getFrequency(unit, hdrToks.get(start),-1):1;
							adjustedScores[a] = coOcurStats.freqAdjustedScore(freq,totalScores[id]);
							maxFreq = Math.max(maxFreq, adjustedScores[a]);
						}
						Unit unitsT[] = new Unit[altUnitCounts];
						for (int a = 0, uCtr=0; a < altUnitCounts && bestUnit[start][end][a*2+state] != null; a++) {
							float f = adjustedScores[a];
							if (f < maxFreq*0.9) {
								bestUnit[start][end][a*2+state] = null;
							} else {
								unitsT[uCtr++] = bestUnit[start][end][a*2+state];
							}
						}
						if (unitsT[0] != null) {
							scores[start][end][state] += maxFreq*params.weights[FTypes.Co_occurStats.ordinal()];
							registerFeatureInfo(start,end,state,FTypes.Co_occurStats,maxFreq,unitsT[0].getName(), null);
						}
						for (int a = 0; a < unitsT.length && unitsT[a]!=null;a++) {
							bestUnit[start][end][a*2+state] = unitsT[a];
						}
						if (sortedUnits!=null && sortedUnits[start][end][state] != null) {
						 */
						for (int a = sortedUnits[start][end][state].size()-1; a >= 0; a--) {
							UnitFeatures unit  = sortedUnits[start][end][state].get(a);
							int id = units.get(unit.getKey());
							if (id < 0) continue;
							float freq = (start==end && coOcurStats.adjustFrequency())?unitFreqs[start].get(unit.getKey()):1;   //getFrequency(unit.getKey(), hdrToks.get(start),-1):1;
							float cooccurScore = coOcurStats.freqAdjustedScore(freq,totalScores[id]);
							unit.setScore(unit.getScore()+cooccurScore*params.weights[FTypes.Co_occurStats.ordinal()]);
							registerFeatureInfo(start,end,state,FTypes.Co_occurStats,cooccurScore,unit.getKey().getName(), unit);
						}
					}
				}
			}
			}
		}

		/*
				int numM = 0;
				for(int p = 0; p < bestUnit[start][end].length&& p < 4; p++) {
					if (bestUnit[start][end][p] != null) numM++;
				}
				if (numM < 2) continue;
				float freqs[] = new float[bestUnit[start][end].length];
				float totalFreq = 0;
				float maxFreq = Float.NEGATIVE_INFINITY;
				float minFreq = Float.POSITIVE_INFINITY;
				for (int state = 0; state < 2; state++) {
					for (int a = 0; a < altUnitCounts && bestUnit[start][end][a*2+state] != null; a++) {
						int freq = coOcurStats.getOccurrenceFrequency(hdrToks.get(start), bestUnit[start][end][a*2+state].getBaseName(), bestUnit[start][end][a*2+state].getParentQuantity().getConcept(), total);
						float f = freq + CoccurMixWeight*total[1];
						freqs[a*2+state] = f;
						totalFreq += f;
						maxFreq = Math.max(maxFreq, f);
						minFreq = Math.min(minFreq, f);
					}
				}
				if (maxFreq-minFreq > Float.MIN_VALUE) {
					for (int state = 0; state < 2; state++) {
						Unit units[] = new Unit[altUnitCounts];
						for (int a = 0, uCtr=0; a < altUnitCounts && bestUnit[start][end][a*2+state] != null; a++) {
							float f = freqs[a*2+state];
							if (f < maxFreq*0.9) {
								bestUnit[start][end][a*2+state] = null;
							} else {
								units[uCtr++] = bestUnit[start][end][a*2+state];
							}
						}
						if (units[0] != null) scores[start][end][state] += maxFreq/totalFreq*params.weights[FTypes.Co_occurStats.ordinal()];
						for (int a = 0; a < units.length && units[a]!=null;a++) {
							bestUnit[start][end][a*2+state] = units[a];
						}
					}
				}
		 */

		sentence = new Vector<Token>();
		for (int i = 0; i < hdrToks.size(); i++) {
			String w = hdrToks.get(i);
			if (w.equalsIgnoreCase("in")) {
				sentence.add(new Token(EnumIndex.Tags.IN,w));
			} else if (w.equalsIgnoreCase("per")){
				sentence.add(new Token(EnumIndex.Tags.PER, w));
			} else if (w.equals("/")){
				sentence.add(new Token(EnumIndex.Tags.Op, w));
			} else if (w.equalsIgnoreCase("of")) {
				sentence.add(new Token(EnumIndex.Tags.OF, w));
			} else if (w.equals(CFGParser4Text.QuantityToken)) {
				sentence.add(new Token(EnumIndex.Tags.Q,w));
			} else if (getUnit(i,i,multUnitState) != null) {
				sentence.add(new Token(EnumIndex.Tags.Mult, w));
			} else if (quantPositions.get(i)) {
				sentence.add(new Token(EnumIndex.Tags.Number, w));
			} else {
				sentence.add(new Token(EnumIndex.Tags.W, w));
			}
		}
		sentence.add(tagIndex.boundaryToken);
		for (int pos = 0; pos < brackets.size(); pos++) {
			if (brackets.get(pos) < 0) continue; // mismatched brackets
			int start = brackets.get(pos) >> 16;
			int end = brackets.get(pos) & ((1<<16)-1);
			float bscore = (float) params.weights[FTypes.WithinBracket.ordinal()];
			if (start==end  && hdrToks.get(start).length()==1)  {
				// do not give bracket advantage to (s) and [number]
				char ch = hdrToks.get(start).charAt(0);
				if (ch == 's'|| Character.isDigit(ch))
					continue;
			}
			for (int state = 0; state <=1; state++) {
				if (someUnitInside(start,end,multUnitState) || someUnitInside(start,end,unitState)) {
					if (end > start && (sentence.get(start).tag==Tags.IN || sentence.get(start).tag==Tags.Q)) {
						// capture patterns like 10 meter (33 feet)
						scores[start+1][end][lexScore+state] += bscore;
						registerFeatureInfo(start+1,end,state,FTypes.WithinBracket,1,null, (UnitFeatures)null);
					} else {
						// 13/09/2013: Added the else because otherwise the within-bracket score was 
						// getting added twice when IN is wrongly mapped to unit "Inch". 
						scores[start][end][lexScore+state] += bscore;
						registerFeatureInfo(start,end,state,FTypes.WithinBracket,1,null, (UnitFeatures)null);
					}
				}
			}
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
		for (int start = 0; start < hdrToks.size(); start++) {
			for (int end = start; end < hdrToks.size(); end++) {
				for (int state = 0; state < 2; state++) {
					if (sortedUnits[start][end][state] != null && sortedUnits[start][end][state].size()>0) {
						Vector<UnitFeatures> unitVec = sortedUnits[start][end][state];
						if (forcedUnit != null && start >= forcedUnit.start() && end <= forcedUnit.end()) {
							for(int i = unitVec.size()-1; i >= 0; i--) {
								if (!forcedUnit.allowed(unitVec.get(i).getKey())) {
									unitVec.get(i).setScore(Double.NEGATIVE_INFINITY);
								}
							}
						}
						Collections.sort(sortedUnits[start][end][state]);
						for(int i = unitVec.size()-1; i >= 0; i--) {
							if (Double.isInfinite(unitVec.get(i).getScore())) {
								unitVec.remove(i);
							}
						}
					}
				}
			}
		}
		return sentence;
	}

	private boolean closerToAnotherNumber(int start, int end, int targetQuantPos, BitSet quantPositions) {
		int targetDist = Math.min(Math.abs(targetQuantPos-start), Math.abs(targetQuantPos-end));
		for (int i = start-1; i >= Math.max(0, start-targetDist-1); i--) {
			if (quantPositions.get(i))
				return true;
		}
		for (int i = end+1; i < Math.min(end+targetDist-1, quantPositions.length()); i++) {
			if (quantPositions.get(i))
				return true;
		}
		return false;
	}
	private boolean isdigit(String w) {
		boolean hasDigit=false;
		for (int i = 0; i < w.length(); i++) {
			if (Character.isDigit(w.charAt(i))) {
				hasDigit = true;
				continue;
			}
			if ((w.charAt(i)!='.' && w.charAt(i) != ','))return false;
		}
		return hasDigit;
	}
	public void getFrequencies(TObjectFloatHashMap<Unit> unitFreqs, String unitMention) {
		float freq = 0.01f;
		boolean found = true;
		// 19 Jul: first searching in catalog because words like h for hour have no match in wordnet.
		// get relative frequencies 
		for (TObjectFloatIterator<Unit> iter = unitFreqs.iterator(); iter.hasNext(); ) {
			iter.advance();
			Unit unit = iter.key();
			int id = (int) -iter.value() - 1;
			float relativeFreqInCatalog = 0;
			if (id == -1) 
				relativeFreqInCatalog = matcher.getRelativeFrequency(unit, unitMention);
			else
				relativeFreqInCatalog = matcher.getRelativeFrequency(id);
			if (relativeFreqInCatalog > Float.MIN_VALUE) {
				freq = relativeFreqInCatalog;
				iter.setValue(freq);
			} else {
				found = false;
			}
		}
		// for unit without frequency info, in catalog search wordnet.
		// any wordnet synset found has to be a noun, has to be of type unit, and is matched to the best of the give units 
		// provided at least one word in the wordform matches the unit's base name. 
		boolean someCatalogEntryFound=false;
		if (!found) {
			// 10 Nov 2013: shifted someCatalogEntryFound settting to here because otherwise for words like British which have no unit match, we wrong frequency.
			someCatalogEntryFound = wordFreq.getRelativeFrequency(unitMention,freqVector);
			for (EntryWithScore<String[]> entry : freqVector) {
				String[] wordForms = entry.getKey();
				List<Unit> bestUnits = matcher.bestMatchUnit(wordForms);
				if (bestUnits != null) {
					for (Unit bestUnit : bestUnits) {
						if (unitFreqs.contains(bestUnit)) {
							//someCatalogEntryFound=true;
							float oldFreq = unitFreqs.get(bestUnit);
							if (oldFreq > 0) continue;
							unitFreqs.put(bestUnit, (float) entry.getScore());
						}
					}
				}
			}
		}
		// set frequency of units not found in either wordnet or catalog to zero.
		for (TObjectFloatIterator<Unit> iter = unitFreqs.iterator(); iter.hasNext(); ) {
			iter.advance();
			Unit unit = iter.key();
			float oldFreq = iter.value();
			if (oldFreq < 0) iter.setValue(someCatalogEntryFound || unitMention.length()==1?0.01f:1);
		}
		/*
		for (TObjectFloatIterator<Unit> iter = unitFreqs.iterator(); iter.hasNext(); ) {
			iter.advance();
			Unit unit = iter.key();
			float oldFreq = iter.value();
			System.out.println(unit.getBaseName() + " "+oldFreq);
		}
		 */
		return;
	}
	private float getFrequencyold(Unit unit, String unitMention, int id) {
		float freq = 0.01f;
		boolean found = false;
		// 19 Jul: first searching in catalog because words like h for hour have no match in wordnet.
		//
		// get relative frequencies 
		float relativeFreqInCatalog = 0;
		if (id == -1) 
			relativeFreqInCatalog = matcher.getRelativeFrequency(unit, unitMention);
		else
			relativeFreqInCatalog = matcher.getRelativeFrequency(id);
		if (relativeFreqInCatalog > Float.MIN_VALUE) {
			freq = relativeFreqInCatalog;
			found=true;
		}
		if (!found) {
			found = wordFreq.getRelativeFrequency(unitMention,freqVector);
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
						//System.out.println("Violated uniqueness assumption of base unit");
					}
					freq += (float) entry.getScore();
				}
			}
		}
		if (!found) {
			freq = 1;
			//System.out.println("Found no match in wordnet for "+hdrToks.get(startM) + " "+unit.getBaseName());
		} else {

		}
		if (SimpleParser.wordSymbolsHash.contains(unitMention)) {
			if (freq > 0.5f) {
				System.out.println("Frequency not well calibrated?");
			}
		}
		return freq;
	}
	private void registerFeatureInfo(int start, int end, int unitState2,
			FTypes fname, float bscore, String unitName, UnitFeatures unitObject) {
		if (debugLvl > 0) System.out.println("Feature "+fname.name()+ " ("+start+" "+end+") "+unitState2+ " "+(bscore*params.weights[fname.ordinal()])+" "+unitName);
		if (unitObject!=null) unitObject.addFeature(fname, bscore);
	}
	private void registerFeatureInfo(int start, int end, int unitState2,
			FTypes fname, int bscore, Object unitName,
			Vector<UnitFeatures> unitObjectVec) {
		if (debugLvl > 0) System.out.println("Feature "+fname.name()+ " ("+start+" "+end+") "+unitState2+ " "+(bscore*params.weights[fname.ordinal()])+" "+unitName);
		if (unitObjectVec!=null) {
			for (UnitFeatures unitObject : unitObjectVec) {
				unitObject.addFeature(fname, bscore);
			}
		}
	}
	private boolean someUnitInside(int start, int end, int state) {
		for (int s = start; s <= end; s++) {
			for (int e = start; e <= end; e++) {
				if (getUnit(s,e,state) != null) return true;
			}
		}
		return false;
	}
	private int getMaximalTokens(float score, List<String> unitToks,
			List<String> hdrToks2, int start, int end, int[] lastMatch2, int[] numMatches) {
		lastMatch2[0]=end;
		//if (score > 1 - Float.MIN_VALUE)
		//	return start;
		for (; start <= end; start++) {
			if (unitToks.contains(hdrToks2.get(start)))
				break;
		}
		for (;start <= end; end--) {
			if (unitToks.contains(hdrToks2.get(end))) {
				break;
			}
			lastMatch2[0]=end-1;
		}
		if (numMatches != null) {
			numMatches[0] = 0;
			for (int i = start; i <= lastMatch2[0]; i++) {
				if (unitToks.contains(hdrToks2.get(i)))
					numMatches[0]++;
			}
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
		throw new NotImplementedException("");
	}

	@Override
	public void initializeTraining(double numTrees) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(Collection<Tree> trees) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(Collection<Tree> trees, double weight) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(Tree tree, double weight) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(List<TaggedWord> sentence, double weight) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void train(TaggedWord tw, int loc, double weight) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void trainUnannotated(List<TaggedWord> sentence, double weight) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void finishTraining() {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
		return score(null,iTW,loc,word,featureSpec);
	}
	public float score(FeatureVector fvec, IntTaggedWord iTW, int loc, String word, String featureSpec) {
		if (forcedTags != null && forcedTags.length > loc && forcedTags[loc][0] != iTW.tag) {
			System.out.println("Disallowed tags tried");
		}
		if (forcedUnit != null && forcedUnit.start()==loc && forcedUnit.end()==loc && (forcedUnit.rootState != iTW.tag)) {
			return Float.NEGATIVE_INFINITY;
		}
		float score = scoreSpan(fvec,loc,loc,tagIndex.isMultUnit(iTW.tag())?multUnitState:(tagIndex.isSimpleUnit(iTW.tag())?unitState:-1));
		//System.out.println(iTW.toString(wordIndex, tagIndex) + " "+ loc + " " + word + "  " + score);
		return score;
	}

	@Override
	public float score(BinaryRule rule, int start, int end, int split) {
		return score(rule,start,end,split,null);
	}
	public float score(BinaryRule rule, int start, int end, int split, FeatureVector fvec) {
		if (rule==null) return Float.NEGATIVE_INFINITY;
		if (start+1==end || start == end) {assert(false); return 0;} // since score already accounted for at the word-level
		end = end-1;
		if (forcedUnit != null) {
			if ((forcedUnit.start()==start && forcedUnit.end()==end && forcedUnit.rootState != rule.parent)
					||  ((forcedUnit.start() > end || forcedUnit.end() < start)) && stateIndex.hasUnit(rule.parent)
					|| (forcedUnit.start() > start  && stateIndex.hasUnit(rule.parent)) // || forcedUnit.end < end-1
			) {
				return Float.NEGATIVE_INFINITY;
			}
		}
		if (stateIndex.isState(States._WU, rule.parent)) {
			if (!useExtendedGrammar) return Float.NEGATIVE_INFINITY;
			if (sentence.get(start).tag==Tags.Number) 
				return Float.NEGATIVE_INFINITY;
			return 0;
		}
		if (stateIndex.isState(States.PureWs_Q, rule.parent)) {
			if (!useExtendedGrammar) return Float.NEGATIVE_INFINITY;
			if (end-start > 5) 
				return Float.NEGATIVE_INFINITY;
			for (int p = start; p < end; p++) {
				if (sentence.get(start).tag==Tags.Number) 
					return Float.NEGATIVE_INFINITY;
			}
			if (fvec!=null) {fvec.add(FTypes.MatchLength.ordinal(),10*(end-start));}
			return (float) ((end-start)*10*params.weights[FTypes.MatchLength.ordinal()]);
		}
		
		if (stateIndex.isCU2(rule.parent)) {
			// ensure that the two children are from different parts of the unit taxonomy.
			if (start > split-1 || split+1 > end) 
				return NegInfty;
			if (stateIndex.isState(States.CU2, rule.parent) && noMatchingUnit(sortedUnits[start][split-1][unitState],sortedUnits[split+1][end][unitState],false)) {
				return NegInfty;
			}
			/*Unit bestUnitL = getUnit(start,split-1,unitState); // OP symbol at split
			if (split+1 > end) 
				return NegInfty;
			Unit bestUnitR = getUnit(split+1,end,unitState);
			if (bestUnitL != null && bestUnitR != null) {
				if (bestUnitL.getParentQuantity() == bestUnitR.getParentQuantity()) 
					return NegInfty;
			}
			*/
			// 16/7/2013: added so that units like MJ/l have a slight bias to be compound units instead of alternatives.
			if (fvec!=null) {fvec.add(FTypes.CU2Bias.ordinal(),1f); addLexFeatures(fvec,start,end,unitState);}
			return (float) (params.weights[FTypes.CU2Bias.ordinal()] + getLexScore(start,end, unitState));
			//return 0
		} else if (stateIndex.isIN_U(rule.parent)) {
			float bscore = (float) params.weights[FTypes.AfterIN.ordinal()];
			// 16/7/2013: added the second condition because IN_MULT is not allowed to stand on its own.
			//if (bestUnit[split][end][unitState] != null || bestUnit[split][end][multUnitState] != null) {
			// 13/9/2013: even the above did not work because for compound units e.g. IN billion US$ the bestUnit field is empty.
			//            so, changed to the one below.
			if (someUnitInside(split,end,unitState) || someUnitInside(split,end,multUnitState)) {
				if (fvec!=null) fvec.add(FTypes.AfterIN.ordinal(), 1);
				return bscore;
			}

		}else if (stateIndex.isState(StateIndex.States.IN_Mult, rule.parent)) {
			float bscore = (float) params.weights[FTypes.AfterIN.ordinal()];
			if (sortedUnits[split][end][multUnitState] != null && sortedUnits[split][end][multUnitState].size()>0) {
				if (fvec!=null) fvec.add(FTypes.AfterIN.ordinal(), 1);
				return bscore;
			}
		} else if (stateIndex.isState(StateIndex.States.U, rule.parent)) {
			float multBias = 0;
			// adding this to prevent million metre being marked as a list of two units.
			if (stateIndex.isMultUnit(rule.leftChild) || stateIndex.isMultUnit(rule.rightChild)) {
				if (fvec!=null) fvec.add(FTypes.MultBias.ordinal(), 1);
				multBias = (float) params.weights[FTypes.MultBias.ordinal()];
			}
			if (fvec!=null) addLexFeatures(fvec, start, end, unitState);
			return multBias + getLexScore(start, end, unitState);
		}  else if (stateIndex.isState(StateIndex.States.Op_U, rule.parent)) {
			float perMultBias = 0;
			// adding this to prevent million metre being marked as a list of two units.
			if (stateIndex.isMultUnit(rule.rightChild)) {
				if (fvec!=null) fvec.add(FTypes.PerMult.ordinal(), 1);
				perMultBias = (float) params.weights[FTypes.PerMult.ordinal()];
			}
			if (fvec!=null) addLexFeatures(fvec, start, end, unitState);
			return perMultBias + getLexScore(start, end, unitState);
		}else if (stateIndex.isState(States.UL, rule.parent)) {
			float score = 0;
			// penalize unknown words in intervening units
			//
			int unitStart = split;
			if (!stateIndex.isUnit(rule.rightChild)) {
				// right child is Op_U, so ignore the op
				unitStart = split+1;
			} else {
				// Penalize multiplier unit-pair from appearing as a list of units.
				// when there is no / separating the two.

				// Jul 25, disabled again because even if ambigous words like "m" are marked as meter instead of million this problem remains.
				//	if (someUnitInside(start,split-1, multUnitState) || someUnitInside(unitStart,end, multUnitState)) {
				//		return NegInfty;
				//	}
				// at least make sure that for single word units the candidate units are of the same type.
				if (end-unitStart == 0 && split-1-start == 0 && noMatchingUnit(sortedUnits[start][split-1][unitState],sortedUnits[unitStart][end][unitState],true)) {
					return NegInfty;
				}
			}
			float totalFractionUnk=0;
			for (int child = 0; child <= 1; child++) {
				int numUnkToks = 0;
				int numW = 0;
				for (int p = child==0?start:unitStart; p <= (child==0?unitStart-1:end); p++) {
					if (sentence.get(p).tag==Tags.W) {
						numW++;
						if (((WordIndex) wordIndex).isUnknown(sentence.get(p).wrd))
							numUnkToks++;
					}
				}
				float fractionUnk  = ((float)numUnkToks)/Math.max(numW, 1);
				totalFractionUnk += fractionUnk;
				if (fractionUnk > params.weights[FTypes.PercenUnkInUnitThreshold.ordinal()]) {
					score += fractionUnk*params.weights[FTypes.PercentUnkInUnit.ordinal()];
					if (fvec!=null) fvec.add(FTypes.PercentUnkInUnit.ordinal(), fractionUnk);
				}
			}
			if (start==end-1 && !hasDelim(start,end) && totalFractionUnk <= params.weights[FTypes.PercenUnkInUnitThreshold.ordinal()]) {
				score += params.weights[FTypes.UL_Cont.ordinal()];
				if (fvec!=null) fvec.add(FTypes.UL_Cont.ordinal(), 1);
			}
			if (fvec!=null) addLexFeatures(fvec, start, end, unitState);
			return score+getLexScore(start, end, unitState);
		} else if (stateIndex.isState(States.SU,rule.parent) && (stateIndex.isState(States.SU_MW, rule.leftChild))) {
			// 15 Jul 2013: should reward dictionary match only when a true compound unit otherwise things like "sq m" take double reward by
			// outputing "sq (new unit) million"

			return scoreSpan(fvec,start,end,stateToScorePosition(rule.parent));
		}
		return 0;
	}

	private boolean noMatchingUnit(Vector<UnitFeatures> vector, Vector<UnitFeatures> vector2, boolean noMatch) {
		if ((vector == null || vector.size()==0) ||  (vector2==null || vector2.size()==0)) {
			return true;
		}
		for (UnitFeatures unit1 : vector) {
			for (UnitFeatures unit2 : vector2) {
				if (noMatch == unit1.getKey().getParentQuantity().sameConcept(unit2.getKey().getParentQuantity()))
					return false;
			}
		}
		return true;
	}
	private boolean hasDelim(int start, int end) {
		return !(hdr.toLowerCase().contains(hdrToks.get(start)+ " "+hdrToks.get(end)));
	}
	private Unit getUnit(int start, int split, int unitState) {
		return sortedUnits[start][split][unitState]!=null&&sortedUnits[start][split][unitState].size()>0?sortedUnits[start][split][unitState].get(0).getKey():null;
	}
	void addLexFeatures(FeatureVector fvec, int start, int end,	int unitState) {
		if (fvec != null && scores[start][end][lexScore+unitState] > 0) {
			fvec.add(Params.FTypes.WithinBracket.ordinal(), 1);
		}
	}
	float getLexScore(int start, int end, int unitState) {
		return scores[start][end][lexScore+unitState];
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
	private float scoreSpan(FeatureVector fvec, int start, int end, int state) {
		if (state >=0) {
			if (fvec!=null) {
				addLexFeatures(fvec, start, end, state);
			}
			//return scores[start][end][state]+getLexScore(start, end, state);
			int index = 0;
			Vector<UnitFeatures> unitVec = sortedUnits[start][end][state];

			return ((unitVec != null && unitVec.size()>0 && index >= 0)?(float)unitVec.get(index).getScore():defaultFeatureScore(start,end,null)) +getLexScore(start, end, state);
		}
		return 0;
	}

	@Override
	public void writeData(Writer w) throws IOException {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public void readData(BufferedReader in) throws IOException {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}

	@Override
	public UnknownWordModel getUnknownWordModel() {
		// TODO Auto-generated method stub
		//return null;
		throw new NotImplementedException("");
	}

	@Override
	public void setUnknownWordModel(UnknownWordModel uwm) {
		// TODO Auto-generated method stub
		throw new NotImplementedException("");
	}
	public double dictionaryMatch(int source, int target) {
		// TODO Auto-generated method stub
		return 0;
	}
	public static void main(String args[]) {

	}
	public void incrementTreesRead(double weight) {
		throw new UnsupportedOperationException();
	}

}