package eval;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import parser.FeatureBasedParser;
import parser.HeaderUnitParser;
import parser.RuleBasedParser;

public class Test {
	public static String GroundTruthFile = "/mnt/a99/d0/sunita/workspace.broken/WWT/expts/out.uniq.0.xml"; // "/mnt/a99/d0/WWT/workspace/WWT_GroundTruthV2/unitLabel4Headers.xml";
	public Test(HeaderUnitParser parsers[], String labeledDataFile, String paramsFlag) throws IOException, ParserConfigurationException, SAXException {
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();

		int total = 0;
		int totalNoUnit = 0;
		int mistakes[] = new int[parsers.length];
		int criticalMistakes[] = new int[parsers.length];
		int noUnitError[] = new int[parsers.length];
		Vector<String> applicableRules = new Vector<String>();
		int numPred[] = new int[parsers.length];
		int numMatched[] = new int[parsers.length];
		for (int r = 0; r < len; r++) {
			total++;
			Element rec = (Element) nodeList.item(r);
			String hdr = XMLConfigs.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			HashSet<String> trueUnits = null;
			String trueUnitsString = "";
			if (unitList != null && unitList.getLength()>0) {
				trueUnits = new HashSet<String>();
				for (int u = 0; u < unitList.getLength();u++) {
					trueUnits.add(unitList.item(u).getTextContent().toLowerCase());
					if (u > 0) trueUnitsString.concat("|");
					//System.out.println(unitList.item(u).getTextContent());
					trueUnitsString += unitList.item(u).getTextContent().toLowerCase();
				}
			}
			int p = -1;
			boolean matchedA[] = new boolean[parsers.length];
			Arrays.fill(matchedA, false);
			if (trueUnitsString.length()==0) totalNoUnit++;
			for (HeaderUnitParser parser : parsers) {
				p++;
				boolean matched=false;
				List<? extends EntryWithScore<Unit>> extractedUnits = parser.parseHeaderExplain(hdr, applicableRules, 0, null);
				if (applicableRules.size()>0) 
					numPred[p]++;
				else if (applicableRules.size()==0 && parser.getClass().getSimpleName().startsWith("Rule")) {
					continue;
				}
				int matchedIndex = Utils.unitsMatchedIndex(trueUnitsString, extractedUnits);
				/*
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
				 */
				matched=((matchedIndex == 0 || matchedIndex==1) && (extractedUnits == null || extractedUnits.size()<=1));
				if (!matched) {System.out.print(matched + " " + hdr + " "); Utils.printExtractedUnits(extractedUnits,false);} 
				if (!matched) {
					//"Extracted from " + parser.getClass().getSimpleName() + " " + extractedUnits);
					mistakes[p]++;
					if (applicableRules.size()==1) {
						criticalMistakes[p]++;
					}
					if (trueUnitsString.length()==0) {
						noUnitError[p]++;
					}
				} else
					numMatched[p]++;
				matchedA[p] = matched;
			} 
			if (p > 0 && matchedA[0] != matchedA[1]) {
				//System.out.println("Mismatched");
			}
		}
		int p = 0;
		for (HeaderUnitParser parser : parsers) {
			float recall = ((float)numMatched[p])/total;
			float precision =  ((float)numMatched[p])/numPred[p];
			System.out.println(parser.getClass().getSimpleName() + "\t" +  paramsFlag + "\t" +   recall +"\t" + " NoUnitError="+noUnitError[p]+"/"+totalNoUnit);
			p++;
		}
	}
	public static void main(String args[]) throws Exception {
		QuantityCatalog dict = new QuantityCatalog((Element)null);
		Element emptyElement = XMLConfigs.emptyElement();

		String coOccurMethods[]={"ConceptClassifier"};//,"PrUnitGivenWord"};//, "PrUnitGivenWordNoFreq","PMIScore","LogisticUnitGivenWords"};
		String params[]={""};//"AfterIN=0,WithinBracket=0,INLANG=0,ContextWord=0"};

		for (String coOccurMethod : coOccurMethods) {
			for (String param : params) {
				emptyElement.setAttribute("co-occur-class", coOccurMethod);
				//emptyElement.setAttribute("params", "Co_occurStats=0");
				if (param.length() > 0) emptyElement.setAttribute("params",param);
				HeaderUnitParser[] parsers = new HeaderUnitParser[]{
						//new RuleBasedParser(emptyElement, dict), 
						//new FeatureBasedParser(emptyElement, dict),
						new CFGParser4Header(emptyElement,dict)
				};
				new Test(parsers,GroundTruthFile,coOccurMethod+"_"+param);
			}
		}
		//,"/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/DictConceptMatch1Unit3");/
	}
}
