package catalog;

import java.util.ArrayList;

import catalog.Unit;


/* sunita: Oct 2, 2012 */
public class UnitMultPair extends UnitPair {
	public UnitMultPair(Unit key, Unit mult) {
		super(key,mult, UnitPair.OpType.Mult,key.getParentQuantity());
	}
	@Override
	public Quantity getParentQuantity() {
		return unit1.getParentQuantity();
	}
	@Override
	public double getMultiplier() {
		return unit1.getMultiplier()*unit2.getMultiplier();
	}
	public Unit baseUnit() {
		return unit1;
	}
	public String getBaseName() {
		return unit1.getBaseName() + " [" + unit2.getBaseName() +"]";
	}
}
