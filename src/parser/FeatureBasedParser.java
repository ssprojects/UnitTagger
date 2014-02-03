package parser;

import iitb.shared.BoundedPriorityQueue;
import iitb.shared.EntryWithScore;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import parser.CFGParser4Header.Params.FTypes;

import catalog.QuantityCatalog;
import catalog.Unit;
import edu.stanford.nlp.ling.HasWord;

public class FeatureBasedParser extends CFGParser4Header {

	public FeatureBasedParser(Element elem, QuantityCatalog dict)
			throws Exception {
		super(elem, dict);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, ParseState hdrMatches, int debugLvl, short[][] forcedTags, UnitSpan forcedUnit, int k, Vector<UnitFeatures> featureList) {			
		hdrMatches = getTokensWithSpan(hdr,null,hdrMatches);
		if (hdrMatches.tokens.size()==0) return null;
		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrMatches,null,debugLvl,featureList != null,null, null);
		
		
		/*
		 * pick the single unit over all spans with the highest score...
		 */
		BoundedPriorityQueue<UnitFeatures> boundedQ = new BoundedPriorityQueue<UnitFeatures>(k);
		List<String> hdrToks = hdrMatches.tokens;
		for (int start = 0; start < hdrToks.size(); start++) {
			int addAfterIn = -1;
			if (start > 0 && hdrToks.get(start-1).equals("in")) {
				addAfterIn=FTypes.AfterIN.ordinal();
			}
			for (int end = start; end < hdrToks.size(); end++) {
				for (int state = 0; state < 2; state++) {
					if (tokenScorer.sortedUnits[start][end][state] != null && tokenScorer.sortedUnits[start][end][state].size()>0) {
						Vector<UnitFeatures> unitVec = tokenScorer.sortedUnits[start][end][state];
						for (UnitFeatures unitF : unitVec) {
							unitF.setScore(unitF.getScore()+(addAfterIn>=0?params.weights[addAfterIn]:0)+tokenScorer.getLexScore(start, end, tokenScorer.getUnitState(unitF.getKey())));
						}
						int sz = (k > 1)?unitVec.size():1;
						for (int u = 0; u < sz; u++) {
							if (unitVec.get(u).getScore() > 0) 
								boundedQ.add(unitVec.get(u));
						}
					}
				}
			}
		}
		Vector<UnitFeatures> list = new Vector<UnitFeatures>();
		list.addAll(boundedQ);
		if (featureList != null) {
			for (UnitFeatures f : list) {
				int start = f.start();
				int end = f.end();
				UnitFeatures fvec = new UnitFeatures(f,getParamsArray().length);
				featureList.add(fvec);
				tokenScorer.addLexFeatures(fvec.fvals, start, end, tokenScorer.getUnitState(f.getKey()));
				if (start > 0 && hdrToks.get(start-1).equals("in")) {
					fvec.fvals.add(FTypes.AfterIN.ordinal(),1);
				}
			}
		}
		return list;
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
		return parseHeader(hdr, hdrMatches[0], debugLvl, null, null, k, null);
	}
}
