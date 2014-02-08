package parser.coOccurMethods;

import edu.stanford.nlp.util.Pair;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import iitb.shared.EntryWithScore;
import iitb.shared.StringMap;
import iitb.shared.XMLConfigs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import parser.CFGParser4Header;
import parser.ParseState;
import parser.RuleBasedParser;
import parser.UnitSpan;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibLINEAR;
import weka.classifiers.functions.SMO;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SparseInstance;
import weka.core.TechnicalInformationHandler;

import catalog.Co_occurrenceStatistics;
import catalog.Quantity;
import catalog.QuantityCatalog;
import catalog.Unit;
import catalog.WordnetFrequency;

public class ConceptClassifier implements ConceptTypeScores,Co_occurrenceScores {
	private static final int FreqCutOff = 10;
	public static final String ClassifierFile = "conceptClassifier";
	public static final double UndecidedScore = 0.3;
	public static final double MinScore = 0.01;
	public static String[][] ignoredConcepts = {{"Percent","%"}};
	static Hashtable<String,String[]> ignoredConceptsHash=new Hashtable<String,String[]>();

	StringMap<String> wordIdMap = new StringMap<String>();
	TIntArrayList wordFreqs = new TIntArrayList();
	//StringMap<String> classIdMap = new StringMap<String>();
	TIntArrayList featureIds = new TIntArrayList();
	ArrayList<String> instClassLabels = new ArrayList<String>();
	ArrayList<String> hdrs = null; //new ArrayList<String>();
	TIntArrayList offsets = new TIntArrayList();
	List<Quantity> concepts;
	boolean addInst = false;
	MyClassifier myclassifier = new MyClassifier();
	private RuleBasedParser parser;
	CFGParser4Header cfgparser;
	SparseInstance emptyInst;
	QuantityCatalog quantDict;
	public ConceptClassifier(QuantityCatalog quantDict) throws Exception {
		this(quantDict,true);
	}
	public ConceptClassifier(QuantityCatalog quantDict, boolean trainMode) throws Exception {
		this.quantDict = quantDict;
		this.concepts = quantDict.getQuantities();
		parser = new RuleBasedParser(null, quantDict);
		if (trainMode) cfgparser = new CFGParser4Header(null,quantDict);
		init();
	}
	public ConceptClassifier(Co_occurrenceStatistics coOccurStats) throws Exception {
		this(coOccurStats.quantityDict,null);
	}
	private void init() {
		for (int i = 0; i < ignoredConcepts.length; i++) {
			for (int j = 0; j < ignoredConcepts.length; j++)
				ignoredConceptsHash.put(ignoredConcepts[i][j],ignoredConcepts[i]);
		}
	}
	public ConceptClassifier(QuantityCatalog quantDict, String loadFile) throws Exception {
		this(quantDict,false);
		if (loadFile==null) {
			loadFile = QuantityCatalog.QuantConfigDirPath+ConceptClassifier.ClassifierFile;
		}
		myclassifier = (MyClassifier) weka.core.SerializationHelper.read(loadFile);
		myclassifier.classifier.setDoNotReplaceMissingValues(true);
		emptyInst = new SparseInstance(myclassifier.wordIdMap.size());
		for(int f = 0; f < emptyInst.numAttributes(); f++)
			emptyInst.setValue(f, 0);
		emptyInst.setDataset(emptyDataset());
	}
	public static class MySparseInstance extends SparseInstance {
		@Override
		public Object copy() {
			Instance result = new MySparseInstance(this);
			result.setDataset(dataset());
			return result;
		}
		String hdr;
		public MySparseInstance(SparseInstance emptyInst, String hdr) {
			super(emptyInst);
			this.hdr = hdr;
		}
		public MySparseInstance(MySparseInstance mySparseInstance) {
			this(mySparseInstance,mySparseInstance.hdr);
		}
	}
	public static class MyClassifier implements Serializable {
		LibLINEAR classifier;
		StringMap<String> wordIdMap = new StringMap<String>();

	}
	public void makeClassifier() throws Exception {
		myclassifier = new MyClassifier();
		for (int i = 0; i < wordIdMap.size(); i++) {
			if (featureSelected(i)) {
				myclassifier.wordIdMap.add(wordIdMap.get(i));
			}
		}
		int numFs = myclassifier.wordIdMap.size();
		Instances dataset = emptyDataset(); 

		emptyInst = new SparseInstance(numFs);
		for(int f = 0; f < numFs; f++)
			emptyInst.setValue(f, 0);
		offsets.add(featureIds.size());
		int numInsts = instClassLabels.size();
		for (int i = 0; i < numInsts; i++) {
			SparseInstance inst = new MySparseInstance(emptyInst,hdrs==null?null:hdrs.get(i));
			for (int f = offsets.get(i); f < offsets.get(i+1); f++) {
				int fid = featureIds.get(f);
				int attrId = myclassifier.wordIdMap.get(wordIdMap.get(fid));
				if (attrId >= 0) inst.setValue(attrId, 1);
			}
			if (inst.numValues() > 0) {
				inst.setDataset(dataset);
				inst.setClassValue(instClassLabels.get(i));
				dataset.add(inst);
			}
		}
		LibLINEAR classifier = new LibLINEAR();
		classifier.setDoNotReplaceMissingValues(true);
		myclassifier.classifier = classifier;
		String classifierOptions="-S:0:-P";
		((OptionHandler)classifier).setOptions(classifierOptions.split(":"));
		int numFolds = 3;
		for (int f = 0; f < numFolds; f++) {
			classifier.buildClassifier(dataset.trainCV(numFolds, f));
			System.out.println(classifier.toString());
			System.out.println(((LibLINEAR)classifier).globalInfo());
			Evaluation eval = new Evaluation(dataset);
			//eval.crossValidateModel(classifier, dataset, 3, new Random(1));
			Instances testData =  dataset.testCV(numFolds, f);
			double pred[] = eval.evaluateModel(classifier,testData);
			System.out.println(eval.toSummaryString()+" "+eval.toMatrixString());
		}
		classifier.buildClassifier(dataset);
		System.out.println(classifier.toString());
		weka.core.SerializationHelper.write(ClassifierFile, myclassifier);
		if (hdrs != null) {
			Evaluation eval = new Evaluation(dataset);
			double pred[] = eval.evaluateModel(classifier,dataset);
			System.out.println(eval.toSummaryString()+" "+eval.toMatrixString());
			for (int i = 0; i < pred.length; i++) {
				int trueClass = (int) dataset.instance(i).classValue();
				int predClass = (int) pred[i];
				if (trueClass != predClass) {
					printInstance((MySparseInstance) dataset.instance(i),trueClass,predClass);
				}
			}
		}
	}
	private Instances emptyDataset() {
		int numFs = myclassifier.wordIdMap.size();
		FastVector attributes = new FastVector(numFs+1);
		for (int i = 0; i < numFs; i++) {
			attributes.addElement(new weka.core.Attribute(myclassifier.wordIdMap.get(i)));
		}
		FastVector classNames = new FastVector();
		for (int i = 0; i < concepts.size(); i++) {
			classNames.addElement(getClassString(i));
		}
		attributes.addElement(new weka.core.Attribute("Class",classNames));
		int numInsts = instClassLabels.size();
		Instances dataset = new Instances("ConceptClassifer",attributes,numInsts);
		dataset.setClassIndex(numFs);
		return dataset;
	}
	private void printInstance(MySparseInstance instance, int trueClass, int predClass) {
		System.out.print(instance.hdr+ " tokens=");
		for (int i = 0; i < instance.numValues(); i++) {
			int attInd = instance.attributeSparse(i).index();
			if (attInd < instance.numAttributes()) System.out.print(wordIdMap.get(attInd)+ " ");
		}
		System.out.println(getClassString(trueClass)+ " "+getClassString(predClass));
	}
	private boolean featureSelected(int fid) {
		return (wordFreqs.get(fid) > FreqCutOff);
	}
	public void appendInstanceFeature(String token) {
		if (addInst) {
			int f = wordIdMap.add(token);
			featureIds.add(f);
			if (f < wordFreqs.size()) {
				wordFreqs.setQuick(f, wordFreqs.getQuick(f)+1);
			} else {
				wordFreqs.add(1);
			}
		}
	}
	public String addNewInstance(String concept, String hdr) {
		if (ignoredConceptsHash.containsKey(concept)) {
			addInst=false;
			return null;
		}
		addInst=true;
		offsets.add(featureIds.size());
		instClassLabels.add(concept);
		//classIdMap.add(concept);
		if (hdrs != null) hdrs.add(hdr);
		return concept;
	}

	// use rule-based parser to find high precision unit matches.
	public String addHeader(String hdr, List<String> tokens, UnitSpan unitSpan) throws IOException {
		int start = unitSpan.start();
		int end = unitSpan.end();
		Unit unit = unitSpan.getKey();

		String classLabel = addNewInstance(unit.getParentQuantity().getConcept(),hdr);
		boolean addedTok = false;
		if (classLabel != null) {
			for (int t = 0; t < tokens.size(); t++) {
				if (t >= start && t <= end) continue;
				if (WordnetFrequency.stopWordsHash.contains(tokens.get(t))) continue;
				//if (!checkTokensCorrectness(tokens.get(t))) return false;
				if (tokens.get(t).length()>1 && Character.isLetter(tokens.get(t).charAt(0))) {
					appendInstanceFeature(tokens.get(t));
					addedTok = true;
				}
			}
		}
		return addedTok?classLabel:null;
	}
	public String  addHeader(String hdr, Vector<String> explanation) throws IOException {
		ParseState[] hdrMatches = new ParseState[1];
		List<? extends EntryWithScore<Unit>> units = parser.parseHeaderExplain(hdr, explanation, 0, hdrMatches);
		List<String> tokens = hdrMatches[0].tokens;
		if (tokens == null) return null;
		if ((units == null || units.size()==0) && explanation.size()>0)
			return null;

		if (explanation.size() == 0) {
			units = cfgparser.parseHeader(hdr, hdrMatches[0],0,null,null,1, null);
			if (units == null || units.size()!=1) 
				return null; 
			if (units.get(0).getKey().getParentQuantity() == null || !units.get(0).getKey().getParentQuantity().getConcept().equals("Currency")) {
				return null;
			}
		}
		UnitSpan unitSpan = (UnitSpan) units.get(0);
		return addHeader(hdr,tokens, unitSpan);
		//if (unitSpan.getKey().getParentQuantity().getConcept().equalsIgnoreCase("Multiples")) {
		//	System.out.println(hdr);
		//}
	}
	/* (non-Javadoc)
	 * @see parser.coOccurMethods.ConceptTypeScores#getConceptScores(java.lang.String)
	 */
	@Override
	public List<EntryWithScore<Quantity>> getConceptScores(String hdr) throws Exception {
		return getConceptScores(hdr, null);
	}
	// concept, score map.
	public List<EntryWithScore<Quantity>> getConceptScores(String hdr, String predLabel[]) throws Exception {
		ParseState[] hdrMatches = new ParseState[1];
		Vector<String> explanation = new Vector<String>();
		List<? extends EntryWithScore<Unit>> units = parser.parseHeaderExplain(hdr, explanation, 0, hdrMatches);

		if (units!=null&&units.size()==1&&explanation.size()==1) {
			return QuantityCatalog.newList(units.get(0).getKey().getParentQuantity(),1);
		}
		/*
		for (int t = tokens.size()-1; t >= 0; t--) {
			String tok = tokens.get(t);
			if (ignoredConceptsHash.containsKey(tok)) {

			}
		}
		 */
		return getConceptScores(hdrMatches[0].tokens,predLabel);
	}
	public List<EntryWithScore<Quantity>> getConceptScores(List<String> tokens, String predLabel[]) throws Exception {
		SparseInstance inst = null;
		List<EntryWithScore<Quantity>> conceptScore = null;
		for (int t = tokens.size()-1; t >= 0; t--) {
			String tok = tokens.get(t);
			int id = myclassifier.wordIdMap.get(tok);
			if (id < 0) continue;
			if (inst == null) {
				inst = new SparseInstance(emptyInst);
				inst.setDataset(emptyInst.dataset());
			}
			inst.setValue(id, 1);
		}
		if (inst != null) {
			double dist[] = myclassifier.classifier.distributionForInstance(inst);
			if (predLabel != null) {
				int classId = (int) myclassifier.classifier.classifyInstance(inst);
				predLabel[0] = getClassString(classId);
			}
			for (int i = 0; i < dist.length; i++) {
				if (dist[i] > MinScore) {
					if (conceptScore == null) 
						conceptScore = new Vector<EntryWithScore<Quantity>>();
					conceptScore.add(new EntryWithScore<Quantity>(getClassQuant(i),dist[i]));
				}
			}
		}
		if (conceptScore != null) {
			Collections.sort(conceptScore);
			if (conceptScore.size()==0 || conceptScore.get(0).getScore() < UndecidedScore) {
				Quantity multQuant = quantDict.multipleOneUnit().getParentQuantity();
				boolean multPresent = false;
				
				for (EntryWithScore<Quantity> entry : conceptScore) {
					if (entry.getKey()==multQuant) {
						entry.setScore(Math.max(entry.getScore(), UndecidedScore));
						multPresent = true;
						break;
					}
				}
				if (!multPresent) {
					conceptScore.add(new EntryWithScore<Quantity>(multQuant, UndecidedScore));
					for (EntryWithScore<Quantity> entry : conceptScore) {
						entry.setScore(entry.getScore()*(1-UndecidedScore));
					}
				}
				Collections.sort(conceptScore);
			}
		}
		return conceptScore;
	}
	private String getClassString(int i) {
		return concepts.get(i).getConcept();
	}
	private Quantity getClassQuant(int i) {
		return concepts.get(i);
	}
	@Override
	public float[] getCo_occurScores(List<String> hdrToks, StringMap<Unit> units) {
		List<EntryWithScore<Quantity>> scores=null;
		try {
			scores = getConceptScores(hdrToks,null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (scores == null || scores.size()==0) return null;
		float totalScores[] = new float[units.size()];
		for (int i = 0; i < totalScores.length; i++) {
			Quantity q = units.get(i).getParentQuantity();
			for (int j = 0; j < scores.size(); j++) {
				if (q == scores.get(j).getKey()) {
					totalScores[i] = (float) scores.get(j).getScore();
				}
			}
		}
		return totalScores;
	}
	@Override
	public boolean adjustFrequency() {
		return true;
	}
	@Override
	public float freqAdjustedScore(float freq, float f) {
		return freq*f;
	}
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String paths[]={
				"/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/statParsed"
				, "/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/SingleUnitAfterIn"
				, "/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/DictConceptMatch1Unit"
				, "/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/SingleUnitWithinBrackets"
				, "/mnt/a99/d0/sunita/workspace.broken/WWT/expts/quant/PercentSymbolMatch"
		};
		float sampleRates[] = {1,1,1,1,0.07f};
		QuantityCatalog quantDict = new QuantityCatalog((Element)null);
	
	/*	ConceptClassifier classifier = new ConceptClassifier(quantDict);
		Co_occurrenceStatistics coOccur = new Co_occurrenceStatistics(quantDict);
		Vector<String> explanation = new Vector<String>();
		Random random = new Random(1);
		for (int i = 0; i < paths.length; i++) {
			for (int j = 0; j < 10; j++) {
				File file = new File(paths[i]+j);
				if (!file.exists()) {
					System.out.println("did not find..."+paths[i]+j);
					continue;
				}
				System.out.println("reading..."+paths[i]+j);
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine())!= null) {
					if (random.nextDouble()>sampleRates[i]) continue;
					line = line.trim();
					if (!line.startsWith("<h>")) continue;
					String hdr = line.substring(3,line.indexOf("</h>")).trim();
					classifier.addHeader(hdr, explanation);
					coOccur.addHeader(hdr, explanation);
				}
			}
		}
		classifier.makeClassifier();
	*/
		ConceptClassifier classifier = new ConceptClassifier(quantDict,QuantityCatalog.QuantConfigDirPath+ConceptClassifier.ClassifierFile); //+".withPercent"
		String conceptTests[] = {"corporate income tax rate", "area code", "forest area", "Urban Area Population", "area 1000 sq km", "area", "area sq", "area km", "CO2 emissions", "distance from sun","net worth","year of first flight","weight", "pressure", "record low", "size", "volume","bandwidth","capacity"};
		for (String hdr : conceptTests) {
			System.out.print(hdr);
			List<String> tokens = QuantityCatalog.getTokens(hdr);
			List<EntryWithScore<Quantity>> scores = classifier.getConceptScores(hdr);
			if (scores != null) {
				for (Iterator<EntryWithScore<Quantity>> iter = scores.iterator(); iter.hasNext();) {
					EntryWithScore<Quantity> entry = iter.next();
					System.out.print(" "+entry.getKey().getConcept()+ " "+entry.getScore());
				}
				System.out.println();
				/*
				for (String tok : tokens) {
					float freq[] = coOccur.getConceptFrequencies(tok); 
					System.out.print(tok);
					for (int i = 0; i < freq.length; i++) {
						if (freq[i] > 0) System.out.print(" "+quantDict.getQuantities().get(i).getConcept()+ " "+freq[i]);
					}
					System.out.println();
				}
				 */
			}
		}

	}

}
