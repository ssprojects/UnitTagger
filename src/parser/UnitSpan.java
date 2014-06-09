package parser;

import java.io.Serializable;

import iitb.shared.EntryWithScore;
import parser.CFGParser4Header.EnumIndex.Tags;
import parser.CFGParser4Header.StateIndex.States;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.UnitPair;
import catalog.UnitPair.OpType;

public class UnitSpan extends EntryWithScore<Unit> implements Serializable {
	public static String StartXML="_UnitS_"; //"<ut>";
	public static String EndXML= "_UnitE_"; // "</ut>";
	public static String StartString=" unittokenstart ";
	public static String EndString=" unittokenend ";
	public static String SpecialTokens[] = {StartString.trim(), EndString.trim()};
	int rootState;
	int span=0;
	public UnitSpan(Unit trueUnits) {
		super(trueUnits,0);
	}
	public UnitSpan(Unit trueUnits, double score, int start, int end) {
		super(trueUnits,score);
		setSpan(start,end);
	}
	public void setSpan(int start, int end) {
		span = (start << 16) + end;
	}
	public UnitSpan(String string) {
		super(new Unit(string, string, "1", null),0);
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
		Unit unit = getKey();
		if (unit.getBaseName().equalsIgnoreCase(key.getBaseName())) return true;
		if (unit.getBaseName().contains(key.getBaseName())) return true;
		return false;
	}
	public boolean isOpType(OpType op) {
		Unit unit = getKey();
		return UnitPair.hasOpInString(unit.getBaseName(),op);
	}
	public int start() {
		return (span  >> 16);
	}
	public int end() {
		return span & ((1<<16)-1);
	}
	public boolean properSubset(UnitSpan uspan) {
		if (start() <= uspan.start() && end() >= uspan.end() 
				&& end()-start() > uspan.end()-uspan.start())
			return true;
		return false;
	}
}
