package eval;

import iitb.shared.EntryWithScore;

import java.util.List;

import parser.UnitSpan;

import catalog.Unit;
import catalog.UnitPair;
import catalog.UnitPair.OpType;

public class Utils {
	public static int unitsMatchedIndex(String trueUnits,
			List<? extends EntryWithScore<Unit>> extractedUnits) {
		if ((trueUnits==null || trueUnits.length()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
			return 0;
		} else {
			if (trueUnits != null && extractedUnits != null && trueUnits.length() > 0 && extractedUnits.size()>0) {
				for (int p = extractedUnits.size()-1; p >= 0; p--) {
					EntryWithScore<Unit> unitScore = extractedUnits.get(p);
					Unit unit = unitScore.getKey();
					for (int b = 0; ; b++) {
						String baseName = unit.getBaseName(b);
						if (baseName==null) break;
						if (trueUnits.equalsIgnoreCase(baseName) || trueUnits.equalsIgnoreCase(unit.getName())) {
							return p+1;
						}
					}
				}
			}
		}
		return -1;
	}
	public static boolean unitContained(String trueUnits,
			List<? extends EntryWithScore<Unit>> extractedUnits) {
		boolean matched = false;
		int matchedIndex = unitsMatchedIndex(trueUnits, extractedUnits);
		if (matchedIndex>=1) return true;
		if (trueUnits != null && extractedUnits != null && trueUnits.length() > 0 && extractedUnits.size()>0) {
			for (int p = extractedUnits.size()-1; p >= 0; p--) {
				EntryWithScore<Unit> unitScore = extractedUnits.get(p);
				Unit unit = unitScore.getKey();
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
		return false;
	}
	public static void printExtractedUnits(List<? extends EntryWithScore<Unit>> unitsR, boolean printScore) {
		if (unitsR==null) return;
		for (EntryWithScore<Unit> unit : unitsR) {
			UnitSpan unitSpan = (UnitSpan) unit;
			System.out.println(unit.getKey().getName()+ " " +(printScore?unit.getScore():"")+ " "+unitSpan.start()+ " "+unitSpan.end());
		}
	}
}
