package parser.cfgTrainer;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import parser.CFGParser4Header;
import parser.HeaderUnitParser;
import parser.RuleBasedParser;
import parser.UnitObject;
import catalog.QuantityCatalog;
import catalog.Unit;
import eval.Test;

public class Trainer {
	public Trainer(String labeledDataFile) throws Exception {
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();
		int total = 0;
		Vector<String> applicableRules = new Vector<String>();
		QuantityCatalog quantDict = new QuantityCatalog((Element)null);
		CFGParser4Header parser = new CFGParser4Header(null, quantDict);
		RuleBasedParser ruleParser = new RuleBasedParser(null,quantDict);
		int k = 1;
		Vector<TrainingInstance> trainSet = new Vector<TrainingInstance>();
		for (int r = 0; r < len; r++) {
			total++;
			Element rec = (Element) nodeList.item(r);
			String hdr = XMLConfigs.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			String trueUnits = "";
			if (r==0) {
				//System.out.println();
			}
			if (unitList != null && unitList.getLength()>0) {
				for (int u = 0; u < unitList.getLength();u++) {
					if (u > 0) trueUnits.concat("|");
					//System.out.println(unitList.item(u).getTextContent());
					trueUnits += unitList.item(u).getTextContent().toLowerCase();
				}
			}
			List<EntryWithScore<Unit>> extractedUnits = ruleParser.parseHeaderExplain(hdr, applicableRules);
			if (applicableRules.size()==1) {
				if (unitsMatchedIndex(trueUnits,extractedUnits)>=0) continue;
				throw new Exception("Mistake in rule-based extractor for "+hdr+trueUnits.toString()+ " "+extractedUnits.toString());
			} else {
				Vector<UnitObject> featureList = new Vector();
				extractedUnits = parser.getTopKUnits(hdr, k, featureList,1 );
				trainSet.add(new TrainingInstance(featureList, unitsMatchedIndex(trueUnits, featureList)));
			}
		}
		// now train the parameters using the training set.
	}
	private int unitsMatchedIndex(String trueUnits,
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
	public static void main(String args[]) throws Exception {
		new Trainer(Test.GroundTruthFile);
	}
}
