package parser;

import iitb.shared.BoundedPriorityQueue;
import iitb.shared.EntryWithScore;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import catalog.QuantityCatalog;
import catalog.Unit;
import edu.stanford.nlp.ling.HasWord;

public class FeatureBasedParser extends CFGParser4Header {

	public FeatureBasedParser(Element elem, QuantityCatalog dict)
			throws IOException, ParserConfigurationException, SAXException {
		super(elem, dict);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

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
		
		if (debugLvl > 0) System.out.println(hdr);
		hdrMatches[0] = getTokensWithSpan(hdr,null,hdrMatches[0]);
		if (hdrMatches[0].tokens.size()==0) return null;
		List<? extends HasWord> sentence = tokenScorer.cacheScores(hdrMatches[0],null,debugLvl,true,null);
		/*
		 * pick the single unit over all spans with the highest score...
		 */
		BoundedPriorityQueue<UnitFeatures> boundedQ = new BoundedPriorityQueue<UnitFeatures>(k);
		List<String> hdrToks = hdrMatches[0].tokens;
		for (int start = 0; start < hdrToks.size(); start++) {
			for (int end = start; end < hdrToks.size(); end++) {
				for (int state = 0; state < 2; state++) {
					if (tokenScorer.sortedUnits[start][end][state] != null && tokenScorer.sortedUnits[start][end][state].size()>0) {
						Vector<UnitFeatures> unitVec = tokenScorer.sortedUnits[start][end][state];
						int sz = (k > 1)?unitVec.size():1;
						for (int u = 0; u < sz; u++) {
							boundedQ.add(unitVec.get(u));
						}
					}
				}
			}
		}
		Vector<UnitFeatures> list = new Vector<UnitFeatures>();
		list.addAll(boundedQ);
		return list;
	}
}
