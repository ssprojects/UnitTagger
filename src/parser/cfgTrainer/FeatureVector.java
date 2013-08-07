package parser.cfgTrainer;

import java.util.Arrays;
import java.util.Vector;

import parser.CFGParser.Params.FTypes;
import parser.TokenScorer.UnitObject;

public class FeatureVector {
	float fvals[];
	public FeatureVector(int sz) {
		fvals = new float[sz];
	}
	public void add(int index, float f) {
		fvals[index] += f;
	}
	public FeatureVector clear() {
		Arrays.fill(fvals, 0);
		return this;
	}
	public void add(FeatureVector fvals2) {
		// TODO Auto-generated method stub
		
	}
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}
	public void print(FTypes[] values) {
		for (int i = 0; i < fvals.length; i++) {
			if (fvals[i] > Float.MIN_VALUE) {
				System.out.println(values[i].name()+ " "+fvals[i]);
			}
		}
		return;
	}

}
