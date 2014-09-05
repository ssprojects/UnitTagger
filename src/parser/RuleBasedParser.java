package parser;

import gnu.trove.list.array.TIntArrayList;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;
import iitb.shared.SignatureSetIndex.DocResult;

import java.io.IOException;
import java.io.Serializable;
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

import parser.coOccurMethods.ConceptTypeScores;

import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordFrequency;
import catalog.WordFrequencyImpl;
import catalog.WordnetFrequency;

public class RuleBasedParser extends SimpleParser {
	static final float UnitFrequencyThreshold = 0.75f;
	static final char[] SingleLetterUnits = {'d', 'r'}; // WARNING: keep this sorted at all times.
	WordFrequency wordFreq;
	public interface Rule extends Serializable {
		List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules);
		String name();
		boolean terminal();
	}


	public static class IsUrl implements Rule {
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr,  ParseState processedHdr, List<String> applicableRules) {
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
		List<EntryWithScore<Unit>> apply(String hdr,  ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			if (!pHdr.tokens.contains("in") && (pHdr.brackets.size()==0 || onlyNumbersWithinBracket(pHdr)) && !pHdr.tokens.contains("/")) {
				if (pHdr.dictMatch==null || pHdr.dictMatch.numHits()==0) {
					applicableRules.add(name());
				} else {
					if (pHdr.setRealMatches(quantityDict) == 0)
						applicableRules.add(name());
				}
			}
			return null;
		}
		private boolean onlyNumbersWithinBracket(ParseState pHdr) {
			TIntArrayList brackets = pHdr.brackets;
			for (int pos = 0; pos < brackets.size(); pos++) {
				if (brackets.get(pos) < 0) continue; // mismatched brackets
				int start = brackets.get(pos) >> 16;
				int end = brackets.get(pos) & ((1<<16)-1);
				for (int p = start; p <= end && p < pHdr.tokens.size(); p++) {
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
		List<EntryWithScore<Unit>> apply(String hdr,  ParseState pHdr, List<String> applicableRules) {
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
	EntryWithScore<Unit> newUnit(Unit unit, ParseState pHdr,
			int bestUnitMatch) {
		return new UnitSpan(unit, pHdr.dictMatch.hitMatch(bestUnitMatch), pHdr.dictMatch.hitPosition(bestUnitMatch), pHdr.dictMatch.hitEndPosition(bestUnitMatch));
	}
	public class DictConceptMatch1Unit extends IsUrl {
		@Override
		public
		List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			pHdr.setConceptsFound(quantityDict);

			DocResult res = pHdr.dictMatch;
			int matchId = pHdr.setSingleSpanMatchesUnit(quantityDict);
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
						if (score < ParseState.ThresholdTight) continue;
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
					units.add(newUnit(unit, pHdr, matchId));
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
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit(quantityDict);
			if (bestUnitMatch < 0 || pHdr.altUnitMatches.size()>0) return null;
			Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
			if (unit.getBaseSymbols()[0].equals("%") && hdr.indexOf('/') < 0) {
				applicableRules.add(name());
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(newUnit(unit, pHdr, bestUnitMatch));
				return units;
			}
			return null;
		}
	}
	public class NumberCount extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			hdr = hdr.toLowerCase();
			if (hdr.toLowerCase().endsWith("tax rate")) {
				applicableRules.add(name());
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(new UnitSpan(quantityDict.getUnitFromBaseName("percent"),1,-1,-1));
				return units;
			}
			if (!hdr.contains("number") && !hdr.contains("count") && !hdr.contains("code")) return null;
			pHdr.setDictMatch(quantityDict);
			if (pHdr.setRealMatches(quantityDict) > 0) return null;
			applicableRules.add(name());
			List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
			units.add(new UnitSpan(quantityDict.multipleOneUnit(),1,0,pHdr.tokens.size()));
			return units;
		}
	}
	public class YearUnit extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit(quantityDict);
			if (bestUnitMatch < 0 || pHdr.altUnitMatches.size()>0) return null;
			Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
			if (unit.getBaseName().equalsIgnoreCase("year") && !hdr.toLowerCase().contains("year-")) {
				applicableRules.add(name());
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(newUnit(unit, pHdr, bestUnitMatch));
				return units;
			}
			return null;
		}
	}

	public class SingleLetterNotUnit extends IsUrl {
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit(quantityDict);
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
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit(quantityDict);
			// added the second condition because otherwise major axis (AU) is getting wrongly labeled since AU is ambiguous.
			if (bestUnitMatch < 0 || pHdr.altUnitMatches.size()>0) return null;
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
					units.add(newUnit(unit, pHdr, bestUnitMatch));
					return units;
				}
			}
			return null;
		}
	}

	public class SingleUnitAfterIn extends IsUrl {
		// unless it is m.
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr, List<String> applicableRules) {
			for (int t = 0; t < pHdr.tokens.size(); t++) {
				if (pHdr.tokens.get(t).equals("in")) {
					pHdr.setDictMatch(quantityDict);
					int h = pHdr.singleMatchIgnoringToken(quantityDict, t);
					if (h >= 0 && pHdr.dictMatch.hitPosition(h) == t+1) {
						applicableRules.add(name());
						Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(h));
						List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
						units.add(newUnit(unit, pHdr, h));
						return units;
					}
				}
			}
			return null;
		}
	}

	public class OnlyFreqUnitWords extends IsUrl {
		public OnlyFreqUnitWords() {
		}
		@Override
		public List<EntryWithScore<Unit>> apply(String hdr, ParseState pHdr,
				List<String> applicableRules) {
			pHdr.setDictMatch(quantityDict);
			int bestUnitMatch = pHdr.setSingleSpanMatchesUnit(quantityDict);
			if (bestUnitMatch < 0) return null;
			if (pHdr.dictMatch.hitPosition(bestUnitMatch)!= 0 || pHdr.dictMatch.hitEndPosition(bestUnitMatch)!=pHdr.tokens.size()-1)
				return null;
			if (pHdr.setWordFrequency(quantityDict,wordFreq) > UnitFrequencyThreshold) {
				applicableRules.add(name());
				Unit unit = quantityDict.idToUnitMap.get(pHdr.dictMatch.hitDocId(bestUnitMatch));
				List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
				units.add(newUnit(unit, pHdr, bestUnitMatch));
				return units;
			}
			return null;
		}
	}

	List<Rule> rules;
	public List<? extends EntryWithScore<Unit>> parseHeaderProbabilistic(String hdr, List<String> applicableRules, int debugLvl, int k, ParseState hdrMatches[]) throws IOException {
		if (applicableRules != null) applicableRules.clear();
		ParseState hdrToks = new ParseState(hdr);
		if (hdrMatches != null && hdrMatches.length > 0) hdrMatches[0] = hdrToks;
		List<EntryWithScore<Unit> > unitsToRet = null;
		if (applicableRules==null) {
			applicableRules = new Vector<String>();
		}
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
		/*
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
		}*/
		return unitsToRet;
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
			int start = -1, end = -1;
			for (int i = 0; i < unitSpanPos.size(); i++) {
				if (i %2==0) {
					start = (unitSpanPos.get(i) & ((1<<16)-1));
				} else {
					end = (unitSpanPos.get(i) & ((1<<16)-1))-1;
				}
			}
			forcedUnit.setSpan(start, end);
		}
		hdrMatches = new ParseState(hdr);
		hdrMatches.tokens = hdrToks;
		hdrMatches.brackets=brackets;
		filterUselessTokens(hdrToks);
		return hdrMatches;
	}
	protected void filterUselessTokens(List<String> hdrToks) {
		;
	}
	public RuleBasedParser(Element elem, QuantityCatalog dict, ConceptTypeScores conceptClass) throws Exception {
	  super(elem,dict,conceptClass);

      if (options != null && XMLConfigs.getElementAttributeBoolean(options, "disable-wordnet", false)){ 
          wordFreq = new WordFrequencyImpl();
      } else{
          wordFreq = new WordnetFrequency(options);
          
      }
      rules = new Vector<RuleBasedParser.Rule>();
      rules.add(new IsUrl());
      rules.add(new NumberCount());
      rules.add(new NoUnit());
      rules.add(new NoUnitPatterns());
      rules.add(new PercentSymbolMatch());
      rules.add(new DictConceptMatch1Unit());
      rules.add(new YearUnit());
      rules.add(new SingleLetterNotUnit());
      rules.add(new SingleUnitWithinBrackets());
      rules.add(new OnlyFreqUnitWords());
      rules.add(new SingleUnitAfterIn());
      // single words like meter, feet, points
      // text in % .. ignore match of in to inch when a unit follows.
      // truly ambiguous units (nm, km)
      // multiples and units juxtaposed million $
      // TODO: filter out easy cases using rules, and see cfg for complicated compound units etc.
	}
	public RuleBasedParser(Element elem, QuantityCatalog dict)
	throws Exception {
		this(elem, dict,null);
	}
	public static void main(String args[])  throws Exception {
		List<String> vec = new Vector<String>();
		List<? extends EntryWithScore<Unit>> unitsR = new RuleBasedParser(null,null).parseHeaderExplain("Concorde year of crash", vec, 1, null);
		//				"
		System.out.println(unitsR);
		System.out.println(Arrays.toString(vec.toArray()));
	}
}
