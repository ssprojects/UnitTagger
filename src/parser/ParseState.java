package parser;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import iitb.shared.EntryWithScore;
import iitb.shared.SignatureSetIndex.DocResult;

import java.util.BitSet;
import java.util.List;
import java.util.Vector;

import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordFrequency;

public class ParseState {
	static final float ThresholdTight = 0.9f;
	String hdr;
	public DocResult dictMatch;
	TIntArrayList brackets;
	public List<String> tokens;
	TObjectIntHashMap<String> conceptsFound;
	int singleUnitMatch = -2;
	Vector<EntryWithScore<String[]> > freqVector;
	float freq=Float.NEGATIVE_INFINITY;
	TIntArrayList altUnitMatches;
	BitSet realMatches;
	public ParseState(String hdr) {
		this.hdr= hdr;
	}
	public String text() {return hdr;}
	public List<String> setTokens() {
		if (tokens==null) {
			brackets = new TIntArrayList();
			tokens = QuantityCatalog.getTokens(hdr,brackets);
		}
		return tokens;
	}
	public DocResult setDictMatch(QuantityCatalog quantityDict) {
		if (dictMatch==null) {
			setTokens();
			dictMatch = quantityDict.subSequenceMatch(tokens, 0.7f,true);
		}
		return dictMatch;
	}
	public int setRealMatches(QuantityCatalog quantityDict) {
		if (realMatches != null) return realMatches.cardinality();
		setDictMatch(quantityDict);
		DocResult res = dictMatch;
		realMatches = new BitSet();
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			byte type = quantityDict.idToUnitMap.getType(id);
			if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
				float score = res.hitMatch(h);
				String matchToken = tokens.get(res.hitPosition(h));
				if (score < ThresholdTight && (matchToken.equals("number") || matchToken.equals("#") || Character.isDigit(matchToken.charAt(0)))) continue;
				realMatches.set(h);
			}
		}
		return realMatches.cardinality();
	}
	public TObjectIntHashMap<String> setConceptsFound(QuantityCatalog quantityDict) {
		if (conceptsFound != null) return conceptsFound;
		if (dictMatch==null) setDictMatch(quantityDict);
		conceptsFound = new TObjectIntHashMap<String>();
		for (int h = dictMatch.numHits()-1; h >= 0; h--) {
			int id = dictMatch.hitDocId(h);
			if (quantityDict.idToUnitMap.getType(id)==quantityDict.idToUnitMap.ConceptMatch) {
				Quantity concept = quantityDict.idToUnitMap.getConcept(id);
				conceptsFound.put(concept.getConcept(),h);
			}
		}
		return conceptsFound;
	}
	public int setSingleSpanMatchesUnit(QuantityCatalog quantityDict) {
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
				} else if (maxScore < score + Float.MIN_VALUE) {
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
						 if (score > maxScore-1e-6f && h != matchId) {
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
	public int singleMatchIgnoringToken(QuantityCatalog quantityDict, int tokenId) {
		DocResult res = dictMatch;
		int matchId = -1;
		Unit singleUnit = null;
		float maxScore = Float.NEGATIVE_INFINITY;
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			byte type = quantityDict.idToUnitMap.getType(id);
			if (res.hitLength(h)==1 && res.hitPosition(h)==tokenId) continue;
			if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
				Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
				float score = res.hitMatch(h);
				if (score < ThresholdTight) continue;
				if (maxScore < score) {
					maxScore = score;
					matchId = h;
					singleUnit = unit;
				} else if (maxScore < score + Float.MIN_VALUE) {
				}
			}
		}
		if (matchId >= 0) {
			// now make sure that for no other span we have a unit that is different.
			for (int h = res.numHits()-1; h >= 0; h--) {
				if (res.hitLength(h)==1 && res.hitPosition(h)==tokenId) continue;
				if (h == matchId) continue;
				int id = res.hitDocId(h);
				byte type = quantityDict.idToUnitMap.getType(id);
				if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
					Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(h));
					if (unit.getBaseName().toLowerCase().contains(singleUnit.getBaseName().toLowerCase())) continue;
					if (unit != singleUnit) {
						matchId=-1;
						break;
					}
				}
			}
		}
		return matchId;
	}
	public float setWordFrequency(QuantityCatalog quantityDict, WordFrequency wordFreq) {
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
				freq += (float) entry.getScore();
			}
		}
		return freq;
	}
}