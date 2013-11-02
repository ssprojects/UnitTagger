package eval;

import iitb.shared.EntryWithScore;

import java.util.List;

import catalog.Unit;
import catalog.UnitPair;
import catalog.UnitPair.OpType;

public class Utils {
	public static int unitsMatchedIndex(String trueUnits,
			List<? extends EntryWithScore<Unit>> extractedUnits) {
		boolean matched = false;
		if ((trueUnits==null || trueUnits.length()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
			return 0;
		} else {
			if (trueUnits != null && extractedUnits != null && trueUnits.length() > 0 && extractedUnits.size()>0) {
				matched=true;
				for (int p = extractedUnits.size()-1; p >= 0; p--) {
					EntryWithScore<Unit> unitScore = extractedUnits.get(p);
					Unit unit = unitScore.getKey();
					if (trueUnits.equals(unit.getBaseName().toLowerCase()) || trueUnits.equals(unit.getName())) {
						return p+1;
					}
				}
			}
		}
		return -1;
	}
	public static boolean unitContained(String trueUnits,
			List<? extends EntryWithScore<Unit>> extractedUnits) {
		boolean matched = false;
		if ((trueUnits==null || trueUnits.length()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
			return true;
		} else {
			if (trueUnits != null && extractedUnits != null && trueUnits.length() > 0 && extractedUnits.size()>0) {
				matched=true;
				for (int p = extractedUnits.size()-1; p >= 0; p--) {
					EntryWithScore<Unit> unitScore = extractedUnits.get(p);
					Unit unit = unitScore.getKey();
					if (trueUnits.equals(unit.getBaseName().toLowerCase()) || trueUnits.equals(unit.getName())) {
						return true;
					}
					if (unit instanceof UnitPair) {
						UnitPair unitPair = (UnitPair)unit;
						if (unitPair.getOpType() == OpType.Alt) {
							for (int part = 0; part < 2; part++) {
								if (unitPair.getUnit(part).getBaseName().equals(trueUnits)) 
									return true;
							}
						}
					}
				}
			}
		}
		return false;
	}
}
