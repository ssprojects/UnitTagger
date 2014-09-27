package catalog;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.lang3.NotImplementedException;

import catalog.Unit;
import catalog.UnitPair.OpType;

/* sunita: Oct 2, 2012 */
public class UnitPair extends Unit {
	public static enum OpType {Ratio,Alt,Mult};
	static String OpStringFull[] = new String[]{" per ","|","["};
	static String OpString[] = new String[]{"/","|","["};
	static String OpStringRegex[] = new String[]{"/","|","\\["};
	Unit unit1;
	Unit unit2;
	OpType op;
	public UnitPair(Unit unit1, Unit unit2, OpType op, Quantity quant) {
		super((quant == null && op == OpType.Alt)?unit1.getParentQuantity():quant);
		this.unit1 = unit1;
		this.unit2 = unit2;
		this.op = op;
	}
	public OpType getOpType() {return op;}
	@Override
	public String getName() {
		return getBaseName();
	}
	@Override
	public String getSymbol() {
		return unit1.getSymbol() + OpString[op.ordinal()] + unit2.getSymbol();
	}
	@Override
	public ArrayList<String> getLemmas() {
		throw new NotImplementedException("");
	}
	@Override
	public Quantity getParentQuantity() {
		return super.getParentQuantity();
	}
	@Override
	public String toString() {
		return getBaseName();//unit1.toString()  + OpString[op.ordinal()]+unit2.toString();
	}
	public String[] getBaseNames() {
		throw new NotImplementedException("");
	}
	@Override
	public String[] getBaseSymbols() {
		throw new NotImplementedException("");
	}
	@Override
	public double getMultiplier() {
		if (op == OpType.Ratio) {
			return unit1.getMultiplier()/unit2.getMultiplier();
		}
		return 1;
	}
	public String getBaseName() {
		return unit1.getBaseName() + OpStringFull[op.ordinal()]+ unit2.getBaseName();
	}
	public String getBaseName(int i) {
		if (i==0) return getBaseName();
		if (i == 1 && !OpStringFull[op.ordinal()].equals(OpString[op.ordinal()]))
			return unit1.getBaseName() + OpString[op.ordinal()]+ unit2.getBaseName();
		return null;
	}
	public Unit getUnit(int id) {
		if (id==0) return unit1;
		if (id==1) return unit2;
		return null;
	}
	public static boolean hasOpInString(String unit, OpType op2) {
		return unit.contains(OpString[op2.ordinal()]);
	}
	public static OpType getOpTypeFromOpStr(String string) {
		for (int i = 0; i < OpString.length; i++) 
			if (OpString[i].equals(string)) return OpType.values()[i];
		return null;
	}
	public static String[] extractUnitParts(String unitName) {
		for (int i = 0; i < OpString.length; i++) {
			if (hasOpInString(unitName, OpType.values()[i])) {
				String parts[] = unitName.split(OpStringRegex[i]);
				String part3[] = new String[3];
				part3[0] = parts[0].trim();
				part3[1] = parts[1].trim();
				if (part3[1].endsWith("]")) part3[1] = part3[1].substring(0, part3[1].length()-1);
				part3[2] = OpString[i];
				return part3;
			}
		}
		return null;
	}
	public Unit firstAlternative() {
		if (op.equals(OpType.Alt))
			return unit1;
		return this;
	}
	public static Unit newUnitPair(Unit unit12, Unit unit22, OpType opTypeFromOpStr, MultiValueMap conceptDict, Unit canonicalUnit) {
		switch (opTypeFromOpStr) {
		case Mult:
			return new UnitMultPair(unit12, unit22);
		}
		String conceptName = unit12.getParentQuantity()+OpStringFull[opTypeFromOpStr.ordinal()]+unit22.getParentQuantity();
		Quantity quant = null;
		Collection<Quantity> units = conceptDict.getCollection(conceptName.toLowerCase().trim());
		if (units != null && units.size()>0) {
			quant = units.iterator().next();
		} else {
			quant = new Quantity(conceptName, null, null, null,canonicalUnit);
		}
		return new UnitPair(unit12,unit22,opTypeFromOpStr,quant);
	}
}
