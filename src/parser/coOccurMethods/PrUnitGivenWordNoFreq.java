package parser.coOccurMethods;

import catalog.Co_occurrenceStatistics;

public class PrUnitGivenWordNoFreq extends PrUnitGivenWord {

	public PrUnitGivenWordNoFreq(Co_occurrenceStatistics coOcurStats) {
		super(coOcurStats);
	}

	@Override
	public boolean adjustFrequency() {
		return false;
	}

	@Override
	public float freqAdjustedScore(float freq, float f) {
		return f;
	}

}
