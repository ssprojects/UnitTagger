package parser;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import catalog.Co_occurrenceStatistics;
import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.QuantityReader;
import catalog.Unit;
import catalog.UnitMultPair;
import parser.CFGParser4Header;
import parser.coOccurMethods.ConceptClassifier;
import parser.coOccurMethods.ConceptTypeScores;
import parser.coOccurMethods.ConceptTypeScores.ConceptClassifierTypes;
import edu.stanford.nlp.util.IntPair;

import gnu.trove.map.hash.TObjectIntHashMap;

import iitb.shared.ArrayAsList;
import iitb.shared.EntryWithScore;
import iitb.shared.Timer;
import iitb.shared.SignatureSetIndex.DocResult;

public class SimpleParser implements HeaderUnitParser, ConceptTypeScores, Serializable {
	private static final float Unit1Score = 0.5f;
	private static final float WordSymbolScore = 0.5f;
	public static String[] WordSymbols = {"in","sl","no","are","Ch","per","point", "at","line","league","sheet","weber","shed","last","french","a","hand","mark","number","length","time","us","standard","from","natural","mass"};
	public static float ThresholdWithConcept=0.7f;
	public static float Threshold=0.9f;
	static HashSet<String> wordSymbolsHash=new HashSet<String>(Arrays.asList(WordSymbols));
	boolean debug;
	Element options;
	public ConceptClassifierTypes conceptTypeScorer=ConceptClassifierTypes.classifier;
	ConceptTypeScores conceptClassifier;
	protected QuantityCatalog quantityDict;
	public SimpleParser(Element elem, QuantityCatalog dict) throws Exception {
	  this(elem,dict,null);
	}
	public SimpleParser(Element elem, QuantityCatalog dict, ConceptTypeScores conceptClass) throws Exception {
		this.options = QuantityCatalog.loadDefaultConfig(elem);
		if (dict==null) 
			dict = new QuantityCatalog(options);
		quantityDict = dict;
		
		
		if (options != null && options.hasAttribute("ConceptTypeScorer")) {
			conceptTypeScorer = ConceptClassifierTypes.valueOf(options.getAttribute("ConceptTypeScorer"));
		}
		if (conceptTypeScorer==ConceptClassifierTypes.classifier) {
			conceptClassifier = conceptClass==null?new ConceptClassifier(options,quantityDict,this,null):conceptClass;
		} else if (conceptTypeScorer==ConceptClassifierTypes.perfectMatch) {
			conceptClassifier = quantityDict;
		} else {
		}
		System.out.println("Using concept classifier "+conceptClassifier.getClass().getName());
	}
	
	/* (non-Javadoc)
	 * @see parser.HeaderUnitParser#parseHeader(java.lang.String)
	 */
	@Override
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr) throws IOException {
		if (isURL(hdr)) return null;
		List<String> hdrToks = quantityDict.getTokens(hdr);
		DocResult res = quantityDict.subSequenceMatch(hdrToks, 0.7f);
		TObjectIntHashMap<String> conceptsFound = new TObjectIntHashMap<String>();
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			if (quantityDict.idToUnitMap.getType(id)==quantityDict.idToUnitMap.ConceptMatch) {
				Quantity concept = quantityDict.idToUnitMap.getConcept(id);
				conceptsFound.put(concept.getConcept(),h);
			}
		}
		/* now check what units have matched and with what score.  A multiplier unit is marked separately.
		 * a unit is said to have matched if 
		 *  its match score > threshold1 && corresponding concept matched or if matchscore > threshold2.
		 */
		int bestMultMatch = -1;
		float maxMultScore = Float.NEGATIVE_INFINITY;
		int bestUnitMatch = -1;
		float maxUnitScore = Float.NEGATIVE_INFINITY;
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			byte type = quantityDict.idToUnitMap.getType(id);
			if (quantityDict.idToUnitMap.getType(id)!=quantityDict.idToUnitMap.ConceptMatch) {
				Unit unit = quantityDict.idToUnitMap.get(id);
				float score = res.hitMatch(h);
				score = score*getUnitWeight(res.hitPosition(h), res.hitLength(h),hdrToks,unit);
				//if (debug) printMatch(h, conceptsFound, res, hdr, hdrToks,score,label);
				if (Quantity.isUnitLess(unit.getParentQuantity())) {
					if (bestMultMatch < 0 || maxMultScore < score) {
						bestMultMatch = h;
						maxMultScore = score;
					}
				} else {
					if (conceptsFound.containsKey(unit.getParentQuantity().getConcept())) {
						score += res.hitMatch(conceptsFound.get(unit.getParentQuantity().getConcept()))/2;
					}
					if (bestUnitMatch < 0 || maxUnitScore < score || (Math.abs(maxUnitScore-score)<Float.MIN_VALUE && res.hitLength(h)>res.hitLength(bestUnitMatch))) {
						bestUnitMatch = h;
						maxUnitScore = score;
					}							
				}
			}
		}
		if (maxUnitScore > Threshold) {
			//printMatch(bestUnitMatch,conceptsFound,res,hdr,hdrToks,maxUnitScore,label);
			List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
			Unit unit =  quantityDict.idToUnitMap.get(res.hitDocId(bestUnitMatch));
			if (maxMultScore > Threshold) {
				Unit multunit =  quantityDict.idToUnitMap.get(res.hitDocId(bestMultMatch));
				units.add(new EntryWithScore<Unit>(new UnitMultPair(unit, multunit),1));
			} else {
				units.add(new EntryWithScore<Unit>(unit,1));
			}
			return units;
		} else if (maxUnitScore > Threshold) {
			Unit multunit =  quantityDict.idToUnitMap.get(res.hitDocId(bestMultMatch));
			List<EntryWithScore<Unit>> units = new Vector<EntryWithScore<Unit>>();
			units.add(new EntryWithScore<Unit>(multunit,1));
			return units;
		}
		return null;
	}
	protected static boolean isURL(String hdr) {
		int cntSlash = 0;
		for (int p = hdr.indexOf('/'); p >=0; cntSlash++, p = hdr.indexOf('/', p+1));
		return (cntSlash >= 4);
	}

	public static float getUnitWeight(int start, int len, List<String> hdrToks, Unit unit) {
		float wt = 1;
		if (len==1 && hdrToks.get(start).length()==1 && Character.isLetter(hdrToks.get(start).charAt(0)))
			wt *= Unit1Score;
		if (len==1 && wordSymbolsHash.contains(hdrToks.get(start)))
			wt *= WordSymbolScore;
		return wt;
	}
	private void printMatch(int bestUnitMatch,
			TObjectIntHashMap<String> conceptsFound, DocResult res, String hdr, List<String> hdrToks, float score, String label) {
		Unit bestUnit = quantityDict.idToUnitMap.get(res.hitDocId(bestUnitMatch));
		//if (!bestUnit.getBaseName().equalsIgnoreCase("year")) {
		String conceptStr = "";
		if (conceptsFound.containsKey(bestUnit.getParentQuantity().getConcept())) {
			conceptStr += " Concept= "+res.toString(conceptsFound.get(bestUnit.getParentQuantity().getConcept()), hdrToks);
		}
		System.out.println(label + " : " + hdr + " ---> "+bestUnit.getBaseName()+ " maxScore "+ score + " " + res.toString(bestUnitMatch,hdrToks)+conceptStr);
		//}
		
	}
	public static void main(String args[]) throws Exception {
	}

	@Override
	public List<? extends EntryWithScore<Unit>> parseHeaderProbabilistic(String hdr,
			List<String> explanation, int debugLvl, int k, ParseState hdrMatches[]) throws IOException {
		if (explanation != null) explanation.clear();
		return parseHeader(hdr);
	}
	public List<? extends EntryWithScore<Unit> > parseHeaderExplain(String hdr, List<String> explanation, int debugLvl, ParseState hdrMatches[]) throws IOException {
		return parseHeaderProbabilistic(hdr, explanation, debugLvl, 1, hdrMatches);
	}

	@Override
	public List<? extends EntryWithScore<Unit>> parseCell(String unitStr,
			int k, ParseState context) throws IOException {
		return null;
	}

	@Override
	public List<EntryWithScore<Quantity>> getConceptScores(String hdr)
			throws Exception {
		return (conceptClassifier != null?conceptClassifier.getConceptScores(hdr):null);
	}
}

