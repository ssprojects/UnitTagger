package parser.cfgTrainer;

import java.util.Arrays;
import java.util.Vector;

import parser.CFGParser.Params.FTypes;

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
		for (int i = 0; i < fvals.length && i < fvals2.size(); i++) {
			fvals[i] += fvals2.get(i);
		}
	}
	public int size() {
		return fvals.length;
	}
	public void print(FTypes[] values) {
		for (int i = 0; i < fvals.length; i++) {
			if (fvals[i] > Float.MIN_VALUE) {
				System.out.println(values[i].name()+ " "+fvals[i]);
			}
		}
		return;
	}
	public float get(int i) {
		return fvals[i];
	}

}
