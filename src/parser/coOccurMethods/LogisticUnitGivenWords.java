package parser.coOccurMethods;

import java.util.Arrays;
import java.util.List;

import iitb.shared.RobustMath;
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

public class LogisticUnitGivenWords implements Co_occurrenceScores  {
	public static final float CoccurMixWeight=0.1f;
	public static final float CoccurRatioSmooth=1f;
	private static final double NOA_Score = Math.log(1);
	Co_occurrenceStatistics coOcurStats;
	public LogisticUnitGivenWords(Co_occurrenceStatistics coOcurStats) {
		this.coOcurStats = coOcurStats;
	}
	/* (non-Javadoc)
	 * @see parser.coOccurMethods.Co_occurrenceScores#getCo_occurScores(java.util.List, iitb.shared.StringMap)
	 */
	@Override
	public float[] getCo_occurScores(List<String> hdrToks, StringMap<Unit> units) {
		int total[] = new int[2];
		float totalScores[] = new float[units.size()];
		float totalFreq=0;
		for (int start = 0; start < hdrToks.size(); start++) {
			if (!coOcurStats.tokenPresent(hdrToks.get(start))) continue;
			for (int id = units.size()-1; id >= 0; id--) {
				Unit unit = units.get(id);
				Unit unitPart = unit;
				int freq = coOcurStats.getOccurrenceFrequency(hdrToks.get(start), unitPart.getBaseName(), unitPart.getParentQuantity().getConcept(), total);
				float f = freq + CoccurMixWeight*total[1]+1;
				totalFreq += f;
				totalScores[id] += Math.log(f);
			}
		}
		if (totalFreq > CoccurMixWeight*hdrToks.size()) {
			double logSum = RobustMath.logSumExp(RobustMath.logSumExp(totalScores), NOA_Score);
			for (int i = 0; i < totalScores.length; i++) {
				totalScores[i] = (float) Math.exp(totalScores[i]-logSum);
			}
		} else {
			Arrays.fill(totalScores,0);
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
