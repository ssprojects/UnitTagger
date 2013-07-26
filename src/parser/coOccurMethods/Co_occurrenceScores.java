package parser.coOccurMethods;

import iitb.shared.StringMap;

import java.util.List;

import catalog.Unit;

public interface Co_occurrenceScores {

	public abstract float[] getCo_occurScores(List<String> hdrToks,
			StringMap<Unit> units);

	public abstract boolean adjustFrequency();

	public abstract float freqAdjustedScore(float freq, float f);

}