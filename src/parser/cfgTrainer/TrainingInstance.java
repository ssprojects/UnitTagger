package parser.cfgTrainer;

import iitb.shared.EntryWithScore;

import java.util.BitSet;
import java.util.List;

import parser.UnitFeatures;

import catalog.Unit;

public class TrainingInstance {
	String hdr;
	String trueUnits;
	public TrainingInstance(List<UnitFeatures> extractedUnits, int unitsMatchedIndex) {
	}

	public TrainingInstance(String hdr, String trueUnits) {
		this.hdr = hdr;
		this.trueUnits=trueUnits;
	}

}
