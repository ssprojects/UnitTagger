package parser;

import parser.CFGParser4Header.EnumIndex.Tags;
import parser.CFGParser4Header.StateIndex.States;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitPair;
import catalog.UnitPair.OpType;

public class UnitSpan {
	public static String StartXML="_UnitS_"; //"<ut>";
	public static String EndXML= "_UnitE_"; // "</ut>";
	public static String StartString=" unittokenstart ";
	public static String EndString=" unittokenend ";
	public static String SpecialTokens[] = {StartString.trim(), EndString.trim()};
	int rootState;
	int start=-1;
	int end=-1;
	Unit unit;
	public UnitSpan(Unit trueUnits) {
		this.unit = trueUnits;
	}
	public UnitSpan(String string) {
		unit = new Unit(string, string, "1", null);
	}
	public boolean allowed(Unit key) {
		/*
		if (unit.getBaseName().equalsIgnoreCase(key.getBaseName())) return true;
		if (unit instanceof UnitPair) {
			UnitPair unitPair = (UnitPair) unit;
			for (int id = 0; id < 2; id++) {
				if (unitPair.getUnit(id).getBaseName().equals(key.getBaseName())) return true;
			}
		}
		*/
		if (unit.getBaseName().equalsIgnoreCase(key.getBaseName())) return true;
		if (unit.getBaseName().contains(key.getBaseName())) return true;
		return false;
	}
	public boolean isOpType(OpType op) {
		return UnitPair.hasOpInString(unit.getBaseName(),op);
	}
	
}
