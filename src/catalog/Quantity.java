package catalog;

import java.util.ArrayList;


/**
 *
 * @author harsh
 * @author chander Changed package and access-specifiers.
 * @author Many changes as part of an overhaul.
 */
public class Quantity {
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
}
