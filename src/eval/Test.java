package eval;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
import parser.CFGParser4Text;
import parser.FeatureBasedParser;
import parser.HeaderUnitParser;
import parser.ParseState;
import parser.RuleBasedParser;

public class Test {
//	public static String GroundTruthFile = "/mnt/a99/d0/WWT/workspace/WWT_GroundTruthV2/unitLabel4Text.xml"; //"/mnt/a99/d0/sunita/workspace.broken/WWT/expts/out.uniq.0.xml"; //
	
	public static String GroundTruthFile = "data/unitLabel4Text.xml";
	 //Added some code to write to output file.
    public static String ResultsFile = "/mnt/a99/d0/ashishm/dataset/version3/results2_context.txt";
    File resultFile;
    FileWriter fw;
    BufferedWriter bw;

	
	public Test(HeaderUnitParser parsers[], String labeledDataFile, String paramsFlag, QuantityCatalog dict) throws Exception {
		
		resultFile = new File(ResultsFile);
        fw = new FileWriter(resultFile.getAbsoluteFile());
        bw = new BufferedWriter(fw);


        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        bw.write("Result of evaluation of file /mnt/a99/d0/ashishm/output.xml \n");
        bw.write("Date: "+dateFormat.format(cal.getTime())+"\n");
        bw.write("Evaluator: Ashish Mittal\n\n\n\n");

		
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
			String contextStr = XMLConfigs.getElement(rec, "ht").getTextContent();
			ParseState context[] = new ParseState[1];
			context[0] = new ParseState(contextStr);

			bw.write("Example: "+(r+1)+": "+hdr);

			NodeList unitList = rec.getElementsByTagName("u");
			HashSet<String> trueUnits = null;
			String trueUnitsString = "";
			String trueUnit = "";
			String taggedUnit = "";
			if (unitList != null && unitList.getLength()>0) {
				trueUnits = new HashSet<String>();
				for (int u = 0; u < unitList.getLength();u++) {
					trueUnit = unitList.item(u).getTextContent();
					taggedUnit = unitList.item(u).getTextContent();
					String parts[] = trueUnit.split("\\|");
					for(int p=0;p<parts.length;p++)
					{
						if(p>0)
							trueUnitsString += "|";
						if (parts[p].length() > 0) {
							List<EntryWithScore<Unit>> retVal = dict.getTopK(parts[p], "", QuantityCatalog.MinMatchThreshold);
							if (retVal == null || retVal.size()<1) {
								//throw new Exception("Could not find the correct match in the catalog for "+trueUnit);
							} else {
								trueUnit = retVal.get(0).getKey().getBaseName();
								if(trueUnit.startsWith("per"))
									trueUnitsString +=" "; //account for an extra space given to per units in the beginning
								trueUnitsString += trueUnit;
							}
						}
					}	
					trueUnits.add(trueUnit.toLowerCase());
					if (u > 0) trueUnitsString.concat("|");
					//System.out.println(unitList.item(u).getTextContent());
					//trueUnitsString += trueUnit;
				}
			}
			int p = -1;
			boolean matchedA[] = new boolean[parsers.length];
			Arrays.fill(matchedA, false);
			if (trueUnitsString.length()==0) totalNoUnit++;
			for (HeaderUnitParser parser : parsers) {
				p++;
				boolean matched=false;
				List<? extends EntryWithScore<Unit>> extractedUnits = parser.parseHeaderExplain(hdr, applicableRules, 0, context);
				if (applicableRules.size()>0) 
					numPred[p]++;
				else if (applicableRules.size()==0 && parser.getClass().getSimpleName().startsWith("Rule")) {
					continue;
				}
				int matchedIndex;
				
				matchedIndex = Utils.unitsMatchedIndex(trueUnitsString, extractedUnits);
				if(trueUnitsString.equals("") || matchedIndex < 0){
					matchedIndex = Utils.unitsMatchedIndex(taggedUnit, extractedUnits);
				}
				
				bw.write("Annotated unit : "+trueUnitsString+"\n");
				bw.write("Tagged unit    : "+taggedUnit+"\n");
                bw.write("Extracted unit : "+extractedUnits+"\n");

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
					bw.write("Not Matched.\n");
				} else{
					numMatched[p]++;
					bw.write("Matched. \n");
				}
				matchedA[p] = matched;
			} 
			if (p > 0 && matchedA[0] != matchedA[1]) {
				//System.out.println("Mismatched");
			}
			
            bw.write("\n\n");
		}
		int p = 0;
		for (HeaderUnitParser parser : parsers) {
			float recall = ((float)numMatched[p])/total;
			float precision =  ((float)numMatched[p])/numPred[p];
			System.out.println(parser.getClass().getSimpleName() + "\t" +  paramsFlag + "\t" +   recall +"\t" + " NoUnitError="+noUnitError[p]+"/"+totalNoUnit);
			p++;
		}
		bw.close();
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
						new CFGParser4Text(emptyElement,dict)
				};
				new Test(parsers,GroundTruthFile,coOccurMethod+"_"+param, dict);
			}
		}
		//,"/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/DictConceptMatch1Unit3");/
	}
}
	