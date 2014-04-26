package parser.cfgTrainer;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import parser.CFGParser4Header;
import parser.FeatureBasedParser;
import parser.RuleBasedParser;
import parser.UnitFeatures;
import parser.UnitSpan;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import catalog.QuantityCatalog;
import catalog.Unit;
import eval.Test;

public class Trainer4FeatureBased {
	Vector<TrainingInstance> trainSet;
	CFGParser4Header parser;
	public static int k = 3;
	Properties options = new Properties();
	Classifier classifier;
	String classifierOptions="";//"-M";
	public Trainer4FeatureBased(String labeledDataFile) throws Exception {
		super();
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		int len = nodeList.getLength();
		int total = 0;
		Vector<String> applicableRules = new Vector<String>();
		QuantityCatalog quantDict = new QuantityCatalog((Element)null);
		FeatureBasedParser parser = new FeatureBasedParser(null, quantDict);
		RuleBasedParser ruleParser = new RuleBasedParser(null,quantDict);
		trainSet = new Vector<TrainingInstance>();
		int numFs = parser.getParamsArray().length;
		FastVector attributes = new FastVector(numFs+1);
		for (int i = 0; i < numFs; i++) {
			attributes.addElement(new weka.core.Attribute(parser.featureName(i)));
		}
		FastVector classNames = new FastVector(2);
		classNames.addElement("C0"); classNames.addElement("C1");
		attributes.addElement(new weka.core.Attribute("Class",classNames));
		Instances dataset = new Instances("FeatureBasedParser",attributes,1000);
		dataset.setClassIndex(attributes.size()-1);
		//		System.out.println(attributes);
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
				if (Trainer.unitsMatchedIndex(trueUnits,extractedUnits)>=0) continue;
				System.out.println("Mistake in rule-based extractor for "+hdr+trueUnits+ " "+extractedUnits);
			} else {
				Vector<UnitFeatures> featureList = new Vector();//
				extractedUnits = parser.parseHeader(hdr, null,1,null,null, 5, featureList);
				int index = Trainer.unitsMatchedIndex(trueUnits, featureList);
				if (featureList.size() > 0 && (trueUnits.length()==0 || index > 0)) {
					UnitFeatures trueFeatures = (index >0)?featureList.get(index-1):null;
					System.out.println(hdr);
					for (int i = 0; i < featureList.size(); i++) {
						if (i==index-1) continue;
						FeatureVector predFeatures = featureList.get(i).getFeatureVector();
						/*
						 * create training instance with pairs of positive and negatives and ask for their score > 0.
						 */

						if (trueFeatures != null) System.out.println(trueFeatures.getKey().getBaseName() + " "+trueFeatures.getFeatureVector());
						System.out.println(featureList.get(i).getKey().getBaseName() + " "+predFeatures);

						Instance instance = null;//new Instance(attributes.size());
						Instance instanceNeg = null;//new Instance(attributes.size());
						for (int f = 0; f < attributes.size()-1; f++) {
							float val = (trueFeatures == null?0:trueFeatures.getFeatureVector().get(f)) - predFeatures.get(f);
							instance.setValue(f,val);
							instanceNeg.setValue(f, -val);
						}
						instance.setDataset(dataset);
						instanceNeg.setDataset(dataset);
						instance.setClassValue("C1");
						instanceNeg.setClassValue("C0");
						dataset.add(instanceNeg);
						dataset.add(instance);
					}
				}
			}
		}
		System.out.println(dataset);
		weka.classifiers.functions.Logistic classifier = new weka.classifiers.functions.Logistic();
		//weka.classifiers.functions.SMO classifier = new weka.classifiers.functions.SMO();
		try {
			((OptionHandler)classifier).setOptions(classifierOptions.split(":"));
			int numFolds = 3;
			for (int f = 0; f < numFolds; f++) {
				classifier.buildClassifier(dataset.trainCV(numFolds, f));
				System.out.println(classifier.toString());
				Evaluation eval = new Evaluation(dataset);
				//eval.crossValidateModel(classifier, dataset, 3, new Random(1));
				eval.evaluateModel(classifier, dataset.testCV(numFolds, f));
				System.out.println(eval.toSummaryString()+" "+eval.toMatrixString());
			}
			classifier.buildClassifier(dataset);
			System.out.println(classifier.toString());
			double [][] coeff = classifier.coefficients();
			for (int i = 1; i < coeff.length; i++) {
				System.out.print((i > 1?",":"") + "{\""+dataset.attribute(i-1).name() + "\",\""+(-coeff[i][0])+ "\"}");
			}
			System.out.println("}");
			//test(nonDups,0,data);"C0
			//test(dups,1,data);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void main(String args[]) throws Exception {
		new Trainer4FeatureBased(Test.GroundTruthFile);
	}
}
