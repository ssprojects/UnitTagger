package parser;

import iitb.shared.EntryWithScore;
import parser.CFGParser4Header.Params.FTypes;
import parser.cfgTrainer.FeatureVector;
import catalog.Unit;

public class UnitFeatures extends EntryWithScore<Unit> {
	int span=0;
	FeatureVector fvals;
	public UnitFeatures(Unit key, double score, int start, int end) {
		super(key, score);
		setStartEnd(start, end);
	}
	public UnitFeatures(Unit unit, double d,UnitFeatures unit1, UnitFeatures unit2, int start, int end) {
		this(unit,d,start,end);
		if (unit1 != null && unit1.fvals != null) {
			if (fvals==null) setFvals();
			fvals.add(unit1.fvals);
		}
		if (unit2 != null && unit2.fvals != null) {
			if (fvals==null) setFvals();
			fvals.add(unit2.fvals);
		}
	}
	public UnitFeatures(UnitFeatures f, int length) {
		this(f.getKey(),f.getScore(),f.start(),f.end());
		fvals = new FeatureVector(length);
		fvals.add(f.fvals);
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
	public void checkCorrectness(double[] weights) {
		float fw=0;
		for (int i = 0; i < fvals.size(); i++) {
			fw += weights[i]*fvals.get(i);
		}
		assert(Math.abs(fw-getScore()) < 1e-6);
	}
	public int start() {
		return (span  >> 16);
	}
	public int end(){
		return span & ((1<<16)-1);
	}
	public void setStartEnd(int start, int end) {
		span = (start << 16) + end;
	}
	// 27 Oct 2013: do not change this because the hash in CFGParser4Headers depends on equality only based on units.
/*	@Override
	public int hashCode() {
		return getKey().hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnitFeatures other = (UnitFeatures) obj;
		if (!getKey().equals(other.getKey()))
			return false;
		return true;
	}
	*/
	
	public int numFeatures() {
		return fvals.size();
	}
	public FeatureVector getFeatureVector() {
		return fvals;
	}
	public String toString(){
		return super.toString()+"["+start()+","+end()+"]";
	}
	                         
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + span;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnitFeatures other = (UnitFeatures) obj;
		if (span != other.span)
			return false;
		return true;
	}
}