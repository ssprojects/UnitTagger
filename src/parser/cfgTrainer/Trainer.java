package parser.cfgTrainer;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import parser.CFGParser4Header;
import parser.HeaderUnitParser;
import parser.RuleBasedParser;
import parser.UnitFeatures;
import parser.UnitSpan;
import catalog.QuantityCatalog;
import catalog.Unit;
import eval.Test;


public class Trainer {
/*implements ConstraintsGenerator {
	Vector<TrainingInstance> trainSet;
	CFGParser4Header parser;
	public static int k = 3;
	Properties options = new Properties();
	public Trainer(String labeledDataFile) throws Exception {
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();
		int total = 0;
		Vector<String> applicableRules = new Vector<String>();
		QuantityCatalog quantDict = new QuantityCatalog((Element)null);
		CFGParser4Header parser = new CFGParser4Header(null, quantDict);
		RuleBasedParser ruleParser = new RuleBasedParser(null,quantDict);
		trainSet = new Vector<TrainingInstance>();
		for (int r = 0; r < len; r++) {
			total++;
			Element rec = (Element) nodeList.item(r);
			String hdr = XMLConfigs.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			String trueUnits = "";
			if (unitList != null && unitList.getLength()>0) {
				for (int u = 0; u < unitList.getLength();u++) {
					if (u > 0) trueUnits.concat("|");
					//System.out.println(unitList.item(u).getTextContent());
					trueUnits += unitList.item(u).getTextContent().toLowerCase();
				}
			}
			
			List<? extends EntryWithScore<Unit>> extractedUnits = ruleParser.parseHeaderExplain(hdr, applicableRules,0,null);
			if (applicableRules.size()==1) {
				if (unitsMatchedIndex(trueUnits,extractedUnits)>=0) continue;
				throw new Exception("Mistake in rule-based extractor for "+hdr+trueUnits.toString()+ " "+extractedUnits.toString());
			} else {
				Vector<UnitFeatures> featureList = new Vector();//
				extractedUnits = parser.parseHeader(hdr, null,1,null, new UnitSpan(trueUnits), 1, featureList);
				int index = unitsMatchedIndex(trueUnits, extractedUnits);
				if (!(index == 0 && trueUnits.length()==0 || index == 1)) {
					System.out.println("True unit not found");
				}
				trainSet.add(new TrainingInstance(hdr,trueUnits));
			}
		}
		StructTrainer structTrainer = new StructTrainer(this);
		structTrainer.train(parser.getParamsArray(), options);
	}
	public Trainer() {
		// TODO Auto-generated constructor stub
	}
	
	public static void main(String args[]) throws Exception {
		new Trainer(Test.GroundTruthFile);
	}
	@Override
	public Vector<Constraint> getViolatedConstraints(double[] lambda,int iterNum, LossScaler lossScaler) throws Exception {
		return null;
	}
	@Override
	public Vector<Constraint> getViolatedConstraints(double[] lambda, DataSequence dataSeq, int iterNum, int numRecord, LossScaler lossScaler) {
		String hdr = trainSet.get(numRecord).hdr;
		String trueUnits= trainSet.get(numRecord).trueUnits;
		Vector<UnitFeatures> featureList = new Vector();
		parser.getTopKUnits(hdr, k, featureList,1);
//		trainSet.add(new TrainingInstance(featureList, unitsMatchedIndex(trueUnits, featureList)));
		int matchIndex = unitsMatchedIndex(trueUnits, featureList);
		int returnedCons=-1;
		if (matchIndex < 0) {
			// no match.
			if (trueUnits != null && trueUnits.length() > 0) returnedCons = 0;
		} else {
			
		}
		return null;
	}
	@Override
	public double maxLoss(DataSequence dataSeq, int numRecord) {
		return 1;
	}
	@Override
	public double minNonZeroLoss(DataSequence dataSeq, int numRecord) {
		return 1;
	}
	@Override
	public double dataDiameter() {
		return 0;
	}
	@Override
	public int instanceCount() {
		return trainSet.size();
	}
	*/
	public static int unitsMatchedIndex(String trueUnits,
			List<? extends EntryWithScore<Unit>> extractedUnits) {
		if ((trueUnits==null || trueUnits.length()==0) && (extractedUnits==null || extractedUnits.size()==0)) {
			return 0;
		} else {
			if (trueUnits != null && extractedUnits != null && trueUnits.length() > 0 && extractedUnits.size()>0) {
				float maxScore = Float.NEGATIVE_INFINITY;
				int matchedId = -1;
				for (int p = extractedUnits.size()-1; p >= 0; p--) {
					EntryWithScore<Unit> unitScore = extractedUnits.get(p);
					Unit unit = unitScore.getKey();
					if (trueUnits.equals(unit.getBaseName().toLowerCase()) || trueUnits.equals(unit.getName())) {
						if (matchedId == -1 || maxScore < unitScore.getScore()) {
							matchedId = p+1;
							maxScore = (float) unitScore.getScore();
						}
					}
				}
				return matchedId;
			}
		}
		return -1;
	}
}
