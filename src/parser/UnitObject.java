package parser;

import iitb.shared.EntryWithScore;
import parser.CFGParser4Header.Params.FTypes;
import parser.cfgTrainer.FeatureVector;
import catalog.Unit;

public class UnitObject extends EntryWithScore<Unit> {
	FeatureVector fvals;
	public UnitObject(Unit key, double score) {
		super(key, score);
	}
	public UnitObject(Unit unit, double d,UnitObject unit1, UnitObject unit2) {
		this(unit,d);
		if (unit1 != null && unit1.fvals != null) {
			if (fvals==null) setFvals();
			fvals.add(unit1.fvals);
		}
		if (unit2 != null && unit2.fvals != null) {
			if (fvals==null) setFvals();
			fvals.add(unit2.fvals);
		}
	}
	private void setFvals() {
		fvals = new FeatureVector(FTypes.WithinBracket.ordinal()); // this is the largest set of unit-specific features.
	}
	public void addFeature(FTypes ftype, float fwt) {
		if (fvals==null) setFvals();
		fvals.add(ftype.ordinal(), fwt);
	}
	
	public void addFeatures(FeatureVector treeFeatureVector) {
		if (fvals == null || treeFeatureVector.size()>fvals.size()) {
			setFvals(treeFeatureVector.size());
		}
		fvals.add(treeFeatureVector);
	}
	private void setFvals(int size) {
		FeatureVector oldFVals = fvals;
		fvals = new FeatureVector(size);
		if (oldFVals != null) fvals.add(oldFVals);
	}
	public void checkCorrectness(float wts[]) {
		float fw=0;
		for (int i = 0; i < fvals.size(); i++) {
			fw += wts[i]*fvals.get(i);
		}
		assert(Math.abs(fw-getScore()) < 1e-6);
	}
}