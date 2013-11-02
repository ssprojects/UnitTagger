package parser.coOccurMethods;

import java.util.Arrays;
import java.util.List;

import iitb.shared.StringMap;
import catalog.Co_occurrenceStatistics;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordFrequency;
import edu.stanford.nlp.util.Index;
import parser.CFGParser4Header.EnumIndex;
import parser.CFGParser4Header.StateIndex;
import parser.TokenScorer;
import parser.CFGParser4Header.Params.FTypes;

public class PMIScore implements Co_occurrenceScores  {
	public static final float CoccurMixWeight=0.1f;
	public static final float CoccurRatioSmooth=1f;
	public static double smoother=Math.sqrt(-Math.log(0.5)/2);
	Co_occurrenceStatistics coOcurStats;
	public PMIScore(Co_occurrenceStatistics coOcurStats) {
		this.coOcurStats = coOcurStats;
	}
	/* (non-Javadoc)
	 * @see parser.coOccurMethods.Co_occurrenceScores#getCo_occurScores(java.util.List, iitb.shared.StringMap)
	 */
	@Override
	public float[] getCo_occurScores(List<String> hdrToks, StringMap<Unit> units) {
		int total[] = new int[2];
		float totalScores[] = new float[units.size()];
		float unitFreqs[] = new float[units.size()];
		for (int id = units.size()-1; id >= 0; id--) {
			Unit unit = units.get(id);
			unitFreqs[id] =  coOcurStats.unitFrequency(unit.getBaseName());
		}
		for (int start = 0; start < hdrToks.size(); start++) {
			if (!coOcurStats.tokenPresent(hdrToks.get(start))) continue;
			float wordFreq = coOcurStats.tokenFrequency(hdrToks.get(start));
			for (int id = units.size()-1; id >= 0; id--) {
				Unit unit = units.get(id);
				int freq = coOcurStats.getOccurrenceFrequency(hdrToks.get(start), unit.getBaseName(), unit.getParentQuantity().getConcept(), total);
				totalScores[id] += getPMI(freq,wordFreq,unitFreqs[id], coOcurStats.numDocs());
			}
		}
		return totalScores;
	}
	public static float getPMI(float freq, float wordFreq, float unitFreq,
			int numDocs) {
		return (float) (freq/(wordFreq*unitFreq/numDocs + Math.max(wordFreq,unitFreq)*smoother));
	}
	@Override
	public boolean adjustFrequency() {
		return false;
	}
	@Override
	public float freqAdjustedScore(float freq, float f) {
		return f;
	}
	public static void main(String args[]) {
		System.out.println(getPMI(16, 456, 165, 10000));
	}
}
