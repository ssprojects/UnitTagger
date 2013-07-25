package parser;

import gnu.trove.TIntArrayList;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import iitb.shared.EntryWithScore;
import iitb.shared.SignatureSetIndex.DocResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.print.attribute.standard.PDLOverrideSupported;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordnetFrequency;

public class RuleBasedParser extends SimpleParser {
	static final float ThresholdTight = 0.9f;
	static final float UnitFrequencyThreshold = 0.75f;
	static final char[] SingleLetterUnits = {'d', 'r'}; // WARNING: keep this sorted at all times.
	WordnetFrequency wordFreq;
	public class State {
		String hdr;
		DocResult dictMatch;
		TIntArrayList brackets;
		List<String> tokens;
		TObjectIntHashMap<String> conceptsFound;
		int singleUnitMatch = -2;
		Vector<EntryWithScore<String[]> > freqVector;
		float freq=Float.NEGATIVE_INFINITY;
		TIntArrayList altUnitMatches;
		public State(String hdr) {
			this.hdr= hdr;
		}
		public void setTokens() {
			if (tokens==null) {
				brackets = new TIntArrayList();
				tokens = QuantityCatalog.getTokens(hdr,brackets);
			}
		}
		public void setDictMatch() {
			if (dictMatch==null) {
				setTokens();
				dictMatch = quantityDict.subSequenceMatch(tokens, 0.7f,true);
			}
		}
		public void setConceptsFound() {
			conceptsFound = new TObjectIntHashMap<String>();
			for (int h = dictMatch.numHits()-1; h >= 0; h--) {
				int id = dictMatch.hitDocId(h);
				if (quantityDict.idToUnitMap.getType(id)==quantityDict.idToUnitMap.ConceptMatch) {
					Quantity concept = quantityDict.idToUnitMap.getConcept(id);
					conceptsFound.put(concept.getConcept(),h);
				}
			}
		}
		public int setSingleSpanMatchesUnit() {
			if (singleUnitMatch != -2) return singleUnitMatch;
			DocResult res = dictMatch;
			int matchId = -1;
			Unit singleUnit = null;
			float maxScore = Float.NEGATIVE_INFINITY;
			for (int h = res.numHits()-1; h >= 0; h--) {
				int id = res.hitDocId(h);
				byte type = quantityDict.idToUnitMap.getType(id);
				if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
					Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
					float score = res.hitMatch(h);
					if (score < ThresholdTight) continue;
					if (maxScore < score) {
						maxScore = score;
						matchId = h;
						singleUnit = unit;
					} else if (maxScore < score + Double.MIN_VALUE) {
					}
				}
			}
			if (matchId >= 0) {
				altUnitMatches = new TIntArrayList();
				// now make sure that for no other span we have a unit that is different.
				for (int h = res.numHits()-1; h >= 0; h--) {
					int id = res.hitDocId(h);
					byte type = quantityDict.idToUnitMap.getType(id);
					if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
						float score = res.hitMatch(h);
						if (res.hitPosition(h)==res.hitPosition(matchId) && res.hitLength(h)==res.hitLength(matchId)) {
							 if (score > maxScore-Double.MIN_VALUE && h != matchId) {
								altUnitMatches.add(h); 
							 }
							continue;
						}
						Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
						if (unit != singleUnit) {
							matchId=-1;
							break;
						}
					}
				}
			}
			singleUnitMatch = matchId;
			return matchId;
		}
		public float setWordFrequency() {
			if (!Float.isInfinite(freq)) return freq;
			freqVector = new Vector<EntryWithScore<String[]>>();
			Unit unit = quantityDict.idToUnitMap.get(dictMatch.hitDocId(singleUnitMatch));
			freq = 0.01f;
			float relativeFreqInCatalog = 0;
			int startM = dictMatch.hitPosition(singleUnitMatch);
			boolean inWordNet = wordFreq.getRelativeFrequency(tokens.get(startM),freqVector);
			if (!inWordNet) {
				relativeFreqInCatalog = quantityDict.getRelativeFrequency(dictMatch.hitDocId(singleUnitMatch));
				if (relativeFreqInCatalog > Float.MIN_VALUE) {
					freq = relativeFreqInCatalog;
				}
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
			return freq;
		}
	}
	public interface Rule {
		List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules);
		String name();
		boolean terminal();
	}


	public static class IsUrl implements Rule {
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr,  State processedHdr, List<String> applicableRules) {
			if (isURL(hdr)) applicableRules.add(name());
			return null;
		}
		@Override
		public String name() {
			return this.getClass().getSimpleName();
		}
		@Override
		public boolean terminal() {
			return true;
		}
	}
	public class NoUnit extends IsUrl {
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr,  State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			if (!pHdr.tokens.contains("in") && (pHdr.brackets.size()==0 || onlyNumbersWithinBracket(pHdr)) && !pHdr.tokens.contains("/")) {
				if (pHdr.dictMatch==null || pHdr.dictMatch.numHits()==0) {
					applicableRules.add(name());
				} else {
					DocResult res = pHdr.dictMatch;
					boolean numMatch = false;
					for (int h = res.numHits()-1; h >= 0; h--) {
						int id = res.hitDocId(h);
						byte type = quantityDict.idToUnitMap.getType(id);
						if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
							float score = res.hitMatch(h);
							String matchToken = pHdr.tokens.get(res.hitPosition(h));
							if (score < ThresholdTight && (matchToken.equals("number") || matchToken.equals("#") || Character.isDigit(matchToken.charAt(0)))) continue;
							numMatch = true;
							break;
						}
					}
					if (!numMatch)
						applicableRules.add(name());
				}
			}
			return null;
		}
		private boolean onlyNumbersWithinBracket(State pHdr) {
			TIntArrayList brackets = pHdr.brackets;
			for (int pos = 0; pos < brackets.size(); pos++) {
				if (brackets.get(pos) < 0) continue; // mismatched brackets
				int start = brackets.get(pos) >> 16;
				int end = brackets.get(pos) & ((1<<16)-1);
				for (int p = start; p <= end; p++) {
					String tok = pHdr.tokens.get(p);
					if (Character.isLetter(tok.charAt(0))) return false;
				}
			}
			return true;
		}
		@Override
		public boolean terminal() {
			return true;
		}
	}

	public static class NoUnitPatterns extends IsUrl {
		public static String Patterns[][] = {{"s","#"}, {"sl", "no"}, {"w", "/", "l"}, {"u", "s"}, {"*","in","last","*"}};
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr,  State pHdr, List<String> applicableRules) {
			pHdr.setTokens();
			for (String[] pat : Patterns) {
				if (pat.length==pHdr.tokens.size()) {
					boolean match = true;
					for (int i = 0; i < pat.length; i++) {
						if (!pat[i].equals("*") && !pHdr.tokens.get(i).equals(pat[i])) {
							match = false;
							break;
						}
					}
					if (match) {
						applicableRules.add(name());
						return null;
					}
				}
			}
			return null;
		}
	}
	public class DictConceptMatch1Unit extends IsUrl {
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			pHdr.setConceptsFound();

			DocResult res = pHdr.dictMatch;
			int matchId = pHdr.setSingleSpanMatchesUnit();
			if (matchId >= 0) {
				matchId = -1;
				Unit singleUnit = null;
				float maxScore = Float.NEGATIVE_INFINITY;
				for (int h = res.numHits()-1; h >= 0; h--) {
					int id = res.hitDocId(h);
					byte type = quantityDict.idToUnitMap.getType(id);
					if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
						Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
						float score = res.hitMatch(h);
						if (score < ThresholdTight) continue;
						if (pHdr.conceptsFound.containsKey(unit.getParentQuantity().getConcept())) {
							if (matchId >= 0) {
								return null;
							}
							matchId = h;
						}
					}
				}
				if (matchId >= 0) {
					Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(matchId));
					applicableRules.add(name());
					List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
					units.add(new EntryWithScore<Unit>(unit, pHdr.dictMatch.hitMatch(matchId)));
					return units;
				}
			}
			return null;
		}
		@Override
		public boolean terminal() {
			return true;
		}
	}
	public class PercentSymbolMatch extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit();
			if (bestUnitMatch < 0 || pHdr.altUnitMatches.size()>0) return null;
			Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
			if (unit.getBaseSymbols()[0].equals("%") && hdr.indexOf('/') < 0) {
				applicableRules.add(name());
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(new EntryWithScore<Unit>(unit, pHdr.dictMatch.hitMatch(bestUnitMatch)));
				return units;
			}
			return null;
		}
	}
	public class YearUnit extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit();
			if (bestUnitMatch < 0 || pHdr.altUnitMatches.size()>0) return null;
			Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
			if (unit.getBaseName().equalsIgnoreCase("year") && !hdr.toLowerCase().contains("year-")) {
				applicableRules.add(name());
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(new EntryWithScore<Unit>(unit, pHdr.dictMatch.hitMatch(bestUnitMatch)));
				return units;
			}
			return null;
		}
	}

	public class SingleLetterNotUnit extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit();
			if (bestUnitMatch < 0) return null;
			int matchStart = pHdr.dictMatch.hitPosition(bestUnitMatch);
			char letter = pHdr.tokens.get(matchStart).charAt(0);
			if (pHdr.tokens.get(matchStart).length()==1 && Character.isLetter(letter) && Arrays.binarySearch(SingleLetterUnits, letter) >= 0) {
				TIntArrayList brackets = pHdr.brackets;
				/*for (int pos = 0; pos < brackets.size(); pos++) {
					if (brackets.get(pos) < 0) continue; // mismatched brackets
					int start = brackets.get(pos) >> 16;
					int end = brackets.get(pos) & ((1<<16)-1);
					if (start==end && pHdr.dictMatch.hitPosition(bestUnitMatch)==start) { */
				applicableRules.add(name());
				return null;
				//					}
				//			}
			}
			return null;
		}
	}

	public class SingleUnitWithinBrackets extends IsUrl {
		// unless it is m.
		public List<EntryWithScore<Unit>> apply(String hdr, State pHdr, List<String> applicableRules) {
			pHdr.setDictMatch();
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit();
			if (bestUnitMatch < 0) return null;
			if (pHdr.dictMatch.hitLength(bestUnitMatch)==1 && pHdr.tokens.get(pHdr.dictMatch.hitPosition(bestUnitMatch)).length() <= 1) return null;
			TIntArrayList brackets = pHdr.brackets;
			for (int pos = 0; pos < brackets.size(); pos++) {
				if (brackets.get(pos) < 0) continue; // mismatched brackets
				int start = brackets.get(pos) >> 16;
				int end = brackets.get(pos) & ((1<<16)-1);
				if (start==end && pHdr.dictMatch.hitPosition(bestUnitMatch)==start && pHdr.dictMatch.hitEndPosition(bestUnitMatch)==end) {
					applicableRules.add(name());
					Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
					List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
					units.add(new EntryWithScore<Unit>(unit, pHdr.dictMatch.hitMatch(bestUnitMatch)));
					return units;
				}
			}
			return null;
		}
	}

	public class OnlyFreqUnitWords extends IsUrl {
		public OnlyFreqUnitWords() {
		}
		@Override
		public List<EntryWithScore<Unit>> apply(String hdr, State pHdr,
				List<String> applicableRules) {
			pHdr.setDictMatch();
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit();
			if (bestUnitMatch < 0) return null;
			if (pHdr.dictMatch.hitPosition(bestUnitMatch)!= 0 || pHdr.dictMatch.hitEndPosition(bestUnitMatch)!=pHdr.tokens.size()-1)
				return null;
			if (pHdr.setWordFrequency() > UnitFrequencyThreshold) {
				applicableRules.add(name());
				Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(new EntryWithScore<Unit>(unit, pHdr.dictMatch.hitMatch(bestUnitMatch)));
				return units;
			}
			return null;
		}
	}
	List<Rule> rules;
	public List<EntryWithScore<Unit> > parseHeaderExplain(String hdr, List<String> applicableRules) throws IOException {
		applicableRules.clear();
		State hdrToks = new State(hdr);
		List<EntryWithScore<Unit> > unitsToRet = null;
		for (Rule rule: rules) {
			int sz = applicableRules.size();
			List<EntryWithScore<Unit>> units = rule.apply(hdr, hdrToks, applicableRules);
			if (applicableRules.size() > sz) {
				if (unitsToRet == null) 
					unitsToRet = units;
				else if (units != null)
					unitsToRet.addAll(units);
				if (rule.terminal()) break;
			}
		}
		if (applicableRules.size()==0) {
			// return all matching units from dictMatch as candidates.
			TObjectDoubleHashMap<Unit> unitScore = new TObjectDoubleHashMap<Unit>();
			DocResult res = hdrToks.dictMatch;
			float maxScore = Float.NEGATIVE_INFINITY;
			for (int h = res.numHits()-1; h >= 0; h--) {
				int id = res.hitDocId(h);
				byte type = quantityDict.idToUnitMap.getType(id);
				if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
					Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
					float score = res.hitMatch(h);
					if (!unitScore.contains(unit) || unitScore.get(unit) < score) {
						unitScore.put(unit, score);
					}
				}
			}
			Vector<EntryWithScore<Unit> > units = new Vector<EntryWithScore<Unit>>();
			for (TObjectDoubleIterator<Unit> iter = unitScore.iterator(); iter.hasNext();) {
				iter.advance();
				units.add(new EntryWithScore<Unit>(iter.key(), iter.value()));
			}
			Collections.sort(units);
			unitsToRet = units;
		}
		return unitsToRet;
	}

	public RuleBasedParser(Element elem, QuantityCatalog dict)
	throws IOException, ParserConfigurationException, SAXException {
		super(elem, dict);
		wordFreq = new WordnetFrequency();
		rules = new Vector<RuleBasedParser.Rule>();
		rules.add(new IsUrl());
		rules.add(new NoUnit());
		rules.add(new NoUnitPatterns());
		rules.add(new PercentSymbolMatch());
		rules.add(new DictConceptMatch1Unit());
		rules.add(new YearUnit());
		rules.add(new SingleLetterNotUnit());
		rules.add(new SingleUnitWithinBrackets());
		rules.add(new OnlyFreqUnitWords());
		// single words like meter, feet, points
		// text in % .. ignore match of in to inch when a unit follows.
		// truly ambiguous units (nm, km)
		// multiples and units juxtaposed million $
		// TODO: filter out easy cases using rules, and see cfg for complicated compound units etc.
	}
	public static void main(String args[])  throws Exception {
		List<String> vec = new Vector<String>();
		List<EntryWithScore<Unit>> unitsR = new RuleBasedParser(null,null).parseHeaderExplain("number(s)", vec);
		//				"
		System.out.println(unitsR);
		System.out.println(Arrays.toString(vec.toArray()));
	}
}
