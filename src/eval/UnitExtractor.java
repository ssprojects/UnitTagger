package eval;

import iitb.shared.XMLConfigs;

import org.w3c.dom.Element;

import parser.CFGParser4Text;
import catalog.QuantityCatalog;

public class UnitExtractor {
	public QuantityCatalog quantDict;
	public CFGParser4Text parser;
	
	public UnitExtractor() throws Exception {
		this.quantDict = new QuantityCatalog((Element)null);
		
		Element emptyElement = XMLConfigs.emptyElement();
		this.parser = new CFGParser4Text(emptyElement,quantDict);
	}
	
}
