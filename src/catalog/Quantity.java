package catalog;

import java.io.Serializable;
import java.util.ArrayList;


/**
 *
 * @author harsh
 * @author chander Changed package and access-specifiers.
 * @author Many changes as part of an overhaul.
 */
public class Quantity implements Serializable {
	private String concept;
	private ArrayList<String> synsets;
	private ArrayList<String> typicalUsage;
	private ArrayList<Unit> Units;
	Unit SIUnit;
	public Quantity()
	{

	}
	public Quantity(String c,ArrayList<String> s,ArrayList<String> t,ArrayList<Unit> units){
		concept=c;
		synsets=s;
		typicalUsage=t;
		Units=units;
	}
	public Quantity(String c,ArrayList<String> s,ArrayList<String> t,ArrayList<Unit> units, Unit SIUnit){
      concept=c;
      synsets=s;
      typicalUsage=t;
      Units=units;
      this.SIUnit = SIUnit;
  }

	public String getConcept(){
		return concept;
	}
	public ArrayList<String> getSynSets(){
		return synsets;
	}
	public ArrayList<String> getTypicalUsage(){
		return typicalUsage;
	}
	public ArrayList<Unit> getUnits(){
		return Units;
	}
	public void setConcept(String c){
		concept=c;
	}
	public void setSynSets(ArrayList<String> s){
		synsets=s;
	}
	public void setTypicalUsage(ArrayList<String> t){
		typicalUsage=t;
	}
	public void setUnits(ArrayList<Unit> units){
		Units=units;
	}
	public String toString() {
		return concept;
	}
	public Unit getCanonicalUnit() {return SIUnit;}
	public static boolean isUnitLess(Quantity quant) {
		return quant == null || quant.getConcept().equalsIgnoreCase("Multiples");
	}
	public static boolean sameConcept(Quantity ths, Quantity q) {
		if (ths == q || (isUnitLess(q) && isUnitLess(ths))) return true;
		if (ths == null || q == null || !ths.getConcept().equalsIgnoreCase(q.getConcept())) {
			return false;
		}
		return true;
	}
	public boolean sameConcept(Quantity q) {
		return sameConcept(this,q);
	}
	public boolean isUnitLess() {
		return isUnitLess(this);
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((concept == null) ? 0 : concept.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return sameConcept(this, (Quantity) obj);
	}
}
