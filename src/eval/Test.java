package eval;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.BufferedReader;
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

import catalog.QuantityCatalog;
import catalog.Unit;

import parser.CFGParser4Header;
import parser.HeaderUnitParser;
import parser.RuleBasedParser;

public class Test {
	public static String GroundTruthFile = "/mnt/a99/d0/WWT/workspace/WWT_GroundTruthV2/unitLabel4Headers.xml";
	public Test(HeaderUnitParser parsers[], String labeledDataFile) throws IOException, ParserConfigurationException, SAXException {
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();

		int total = 0;
		int mistakes[] = new int[parsers.length];
		int criticalMistakes[] = new int[parsers.length];
		Vector<String> applicableRules = new Vector<String>();
		for (int r = 0; r < len; r++) {
			total++;
			Element rec = (Element) nodeList.item(r);
			String hdr = XMLConfigs.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			HashSet<String> trueUnits = null;
			if (unitList != null && unitList.getLength()>0) {
				trueUnits = new HashSet<String>();
				for (int u = 0; u < unitList.getLength();u++) {
					trueUnits.add(unitList.item(u).getTextContent().toLowerCase());
				}
			}
			int p = -1;
			boolean matchedA[] = new boolean[parsers.length];
			Arrays.fill(matchedA, false);

			for (HeaderUnitParser parser : parsers) {
				p++;
				boolean matched=false;
				List<EntryWithScore<Unit>> extractedUnits = parser.parseHeaderExplain(hdr, applicableRules);
				
				if ((trueUnits==null || trueUnits.size()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
					matched = true;
				} else {
					if (trueUnits != null && extractedUnits != null && trueUnits.size()==extractedUnits.size()) {
						
						matched=true;
						for (EntryWithScore<Unit> unitScore : extractedUnits) {
							Unit unit = unitScore.getKey();
							if (!trueUnits.contains(unit.getBaseName().toLowerCase()) && !trueUnits.contains(unit.getName())) {
								matched=false;
								break;
							}
						}
					}
				}
				if (!matched) {
					System.out.println(hdr); //"Extracted from " + parser.getClass().getSimpleName() + " " + extractedUnits);
					mistakes[p]++;
					if (applicableRules.size()==1) {
						criticalMistakes[p]++;
					}
				}
				matchedA[p] = matched;
			} 
			if (p > 0 && matchedA[0] != matchedA[1]) {
				System.out.println("Mismatched");
			}
		}
		int p = 0;
		for (HeaderUnitParser parser : parsers) {
			System.out.println(parser.getClass().getSimpleName() + "  " + mistakes[p]+ " / "+total);
			p++;
		}
	}
	public static void main(String args[]) throws IOException, ParserConfigurationException, SAXException {
		QuantityCatalog dict = new QuantityCatalog((Element)null);
		HeaderUnitParser[] parsers = new HeaderUnitParser[]{new CFGParser4Header(null,dict)};
		new Test(parsers,GroundTruthFile);
	}
}
