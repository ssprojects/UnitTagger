package catalog;

import java.util.ArrayList;

import org.apache.commons.lang.NotImplementedException;

import catalog.Unit;

/* sunita: Oct 2, 2012 */
public class UnitPair extends Unit {
	public static enum OpType {Ratio,Alt,Mult};
	static String OpString[] = new String[]{"/","|"," "};
	Unit unit1;
	Unit unit2;
	OpType op;
	public UnitPair(Unit unit1, Unit unit2, OpType op) {
		this.unit1 = unit1;
		this.unit2 = unit2;
		this.op = op;
	}
	@Override
	public String getName() {
		return unit1.getName() + OpString[op.ordinal()] +  unit2.getName();
	}
	@Override
	public String getSymbol() {
		return unit1.getSymbol() + OpString[op.ordinal()] + unit2.getSymbol();
	}
	@Override
	public ArrayList<String> getLemmas() {
		throw new NotImplementedException();
	}
	@Override
	public Quantity getParentQuantity() {
		return super.getParentQuantity();
	}
	@Override
	public String toString() {
		return unit1.toString()  + OpString[op.ordinal()]+unit2.toString();
	}
	public String[] getBaseNames() {
		throw new NotImplementedException();
	}
	@Override
	public String[] getBaseSymbols() {
		throw new NotImplementedException();
	}
	@Override
	public double getMultiplier() {
		return 1;
	}
	public String getBaseName() {
		return unit1.getBaseName() + OpString[op.ordinal()]+ unit2.getBaseName();
	}
}
