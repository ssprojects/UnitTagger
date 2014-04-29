package parser.coOccurMethods;

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

public class PrUnitGivenWord implements Co_occurrenceScores  {
	public static final float CoccurMixWeight=0.1f;
	public static final float CoccurRatioSmooth=1f;
	Co_occurrenceStatistics coOcurStats;
	public PrUnitGivenWord(Co_occurrenceStatistics coOcurStats) {
		this.coOcurStats = coOcurStats;
	}
	/* (non-Javadoc)
	 * @see parser.coOccurMethods.Co_occurrenceScores#getCo_occurScores(java.util.List, iitb.shared.StringMap)
	 */
	@Override
	public float[] getCo_occurScores(List<String> hdrToks, StringMap<Unit> units) {
		int total[] = new int[2];
			float totalScores[] = new float[units.size()];
			for (int start = 0; start < hdrToks.size(); start++) {
				if (!coOcurStats.tokenPresent(hdrToks.get(start))) continue;
				float freqs[] = new float[units.size()];
				float totalFreq = CoccurRatioSmooth;
				float maxFreq = Float.NEGATIVE_INFINITY;
				float minFreq = Float.POSITIVE_INFINITY;
				for (int id = units.size()-1; id >= 0; id--) {
					Unit unit = units.get(id);
					float f = Float.POSITIVE_INFINITY;
					// 1 Nov 2013: disabling this part of the code since now we are also collecting statistics for compound units.
					//for (int p =  0; unit.getBaseNamePart(p) != null; p++) {
						//Unit unitPart = unit.getBaseNamePart(p);
						Unit unitPart = unit;
						int freq = coOcurStats.getOccurrenceFrequency(hdrToks.get(start), unitPart.getBaseName(), unitPart.getParentQuantity().getConcept(), total);
						float ff = freq + CoccurMixWeight*total[1];
						f = Math.min(f, ff);
					//}
					freqs[id] = f;
					totalFreq += f;
					maxFreq = Math.max(maxFreq, f);
					minFreq = Math.min(minFreq, f);
				}
				if (maxFreq-minFreq > Float.MIN_VALUE) {
					for (int id = 0; id < freqs.length; id++) {
						float f = freqs[id];	
						totalScores[id] += f/totalFreq;
					}
				}
			}
			
		return totalScores;
	}
	
	@Override
	public boolean adjustFrequency() {
		return true;
	}
	@Override
	public float freqAdjustedScore(float freq, float f) {
		return f*freq;
	}
}
