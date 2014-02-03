package catalog;

import edu.stanford.nlp.io.EncodingPrintWriter.out;
import edu.stanford.nlp.util.Pair;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import gnu.trove.TObjectLongHashMap;
import iitb.shared.EntryWithScore;
import iitb.shared.StringIntPair;
import iitb.shared.XMLConfigs;
import iitb.shared.SignatureSetIndex.DocResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import parser.ParseState;
import parser.RuleBasedParser;
import parser.UnitSpan;
import parser.coOccurMethods.ConceptTypeScores;

public class Co_occurrenceStatistics {
	public static final String CoOccurFilePath = "configs/cooccurrence.txt";
	public QuantityCatalog quantityDict;
	//	List<String> units = new Vector<String>();
	//TIntArrayList freqs = new TIntArrayList();
	//	TObjectLongHashMap<String> word2UnitsHashMap = new TObjectLongHashMap<String>();
	private TObjectIntHashMap<Pair<String,String>> counts = new TObjectIntHashMap<Pair<String,String>>();
	int numHdrs = 0;
	RuleBasedParser parser;
	Vector<String> explanation = new Vector<String>();
	TObjectIntHashMap<String> wordFreqs = new TObjectIntHashMap<String>();
	TObjectIntHashMap<String> unitsFreqs = new TObjectIntHashMap<String>();
	private ConceptTypeScores conceptClassifier;
	public Co_occurrenceStatistics(QuantityCatalog dict) throws IOException, ParserConfigurationException, SAXException {
		this(dict,null);
		parser = new RuleBasedParser(null, dict);
	}
	public Co_occurrenceStatistics(Element elem, QuantityCatalog dict) throws IOException, ParserConfigurationException, SAXException {
		this(dict,extractLoadFile(elem));
	}
	private static String extractLoadFile(Element elem) {
		if (elem != null) {
			Element coOccurElem = XMLConfigs.getElement(elem, "co-occur-stats");
			if (coOccurElem!=null && coOccurElem.hasAttribute("path")) {
				return coOccurElem.getAttribute("path");
			}
		}
		return CoOccurFilePath;
	}
	public Co_occurrenceStatistics(QuantityCatalog dict, String loadFile) throws IOException, ParserConfigurationException, SAXException {
		quantityDict = dict;
		if (loadFile != null) {
			load(new BufferedReader(new FileReader(new File(loadFile))));
		}
	}
	public void addHeaderSimpleMatch(String hdr) {
		numHdrs++;
		List<String> tokens = quantityDict.getTokens(hdr);
		DocResult res = quantityDict.subSequenceMatch(tokens, (float) 0.99,true);
		if (res==null || res.numHits()==0) return;
		int bestH = -1;
		for (int h = res.numHits()-1; h >= 0; h--) {
			int id = res.hitDocId(h);
			byte type = quantityDict.idToUnitMap.getType(id);
			if (quantityDict.idToUnitMap.getType(id) == quantityDict.idToUnitMap.NameMatch) {
				float score = res.hitMatch(h);
				if (score < 0.99) continue;
				if (bestH >= 0) return;
				bestH = h;
			}
		}
		if (bestH < 0) return;
		int start = res.hitPosition(bestH);
		int end = res.hitEndPosition(bestH);
		for (int t = 0; t < tokens.size(); t++) {
			if (t >= start && t <= end) continue;
			if (WordnetFrequency.stopWordsHash.contains(tokens.get(t))) continue;
			if (tokens.get(t).length()>1 && Character.isLetter(tokens.get(t).charAt(0))) {
				counts.adjustOrPutValue(new Pair(tokens.get(t), quantityDict.idToUnitMap.get(res.hitDocId(bestH)).getBaseName()), 1, 1);
			}
		}
	}
	// use rule-based parser to find high precision unit matches.
	public List<? extends EntryWithScore<Unit>>  addHeader(String hdr, Vector<String> explanation) throws IOException {
		numHdrs++;
		ParseState[] hdrMatches = new ParseState[1];
		List<? extends EntryWithScore<Unit>> units = parser.parseHeaderExplain(hdr, explanation, 0, hdrMatches);
		List<String> tokens = hdrMatches[0].tokens;
		if (tokens == null) return null;
		for (String tok : tokens) {
			wordFreqs.adjustOrPutValue(tok, 1, 1);
		}
		if (units == null || units.size()==0 || explanation.size()!=1)
			return null;
		UnitSpan unitSpan = (UnitSpan) units.get(0);
		addHeader(tokens, unitSpan);
		return units;
	}
	public void  addHeader(List<String> tokens, UnitSpan unitSpan) throws IOException {
		int start = unitSpan.start();
		int end = unitSpan.end();
		Unit unit = unitSpan.getKey();
		unitsFreqs.adjustOrPutValue(unit.getBaseName(), 1, 1);
		for (int t = 0; t < tokens.size(); t++) {
			if (t >= start && t <= end) continue;
			if (WordnetFrequency.stopWordsHash.contains(tokens.get(t))) continue;
			//if (!checkTokensCorrectness(tokens.get(t))) return false;
			if (tokens.get(t).length()>1 && Character.isLetter(tokens.get(t).charAt(0))) {
				counts.adjustOrPutValue(new Pair(tokens.get(t), unit.getBaseName()), 1, 1);
				counts.adjustOrPutValue(new Pair(tokens.get(t), unit.getParentQuantity().getConcept()), 1, 1);
			}
		}
	}

	// use rule-based parser to find high precision unit matches.
	public List<? extends EntryWithScore<Unit>>  addHeaderPMI(String hdr, Vector<String> explanation) throws IOException {
		numHdrs++;
		List<String> tokens = quantityDict.getTokens(hdr);
		if (tokens == null) return null;
		int matchLen = 0;  Unit unit = null;
		int start = -1;
		int end = -1;
		for (int i = 0; i < tokens.size(); i++) {
			String sub="";
			for (int j = i; j >= 0; j--) {
				sub = tokens.get(j) + (i > j?" "+sub:"");
				Unit tunit = quantityDict.getUnitFromBaseName(sub, true);
				if (tunit != null && matchLen < (i-j+1)) {
					unit = tunit;
					matchLen = i-j+1;
					start = j;
					end = i;
				}
			}
		}
		for (String tok : tokens) {
			wordFreqs.adjustOrPutValue(tok, 1, 1);
		}
		if (unit == null)
			return null;
		unitsFreqs.adjustOrPutValue(unit.getBaseName(), 1, 1);
		for (int t = 0; t < tokens.size(); t++) {
			if (t >= start && t <= end) continue;
			if (WordnetFrequency.stopWordsHash.contains(tokens.get(t))) continue;
			//if (!checkTokensCorrectness(tokens.get(t))) return false;
			if (tokens.get(t).length()>1 && Character.isLetter(tokens.get(t).charAt(0))) {
				counts.adjustOrPutValue(new Pair(tokens.get(t), unit.getBaseName()), 1, 1);
			}
		}
		return null;
	}

	private boolean checkTokensCorrectness(String string) {
		for (int c = string.length()-2; c > 1; c--) {
			if (Character.isDigit(string.charAt(c))) return true;
			String str1 = string.substring(0, c);
			String str2 = string.substring(c);
			if (wordFreqs.contains(str1) && wordFreqs.contains(str2)) {
				System.out.println("Concatenated word "+string);
				return false;
			}
		}
		return true;
	}

	public void save(PrintStream outFile) {
		outFile.println("# statistics over "+numHdrs+ " headers "+ counts.size() + " keys ");
		Pair keys[] = counts.keys(new Pair[]{});
		Arrays.sort(keys);
		String prevString = "";
		int total = 0;
		//	BitSet found = new BitSet();
		for(Pair<String,String> key : keys) {
			if (!key.first().equals(prevString)) {
				if (total>0) {
					outFile.print(":="+total);
				}
				prevString = key.first();
				total=0;
				outFile.println();
				outFile.print(prevString);
			}
			total += counts.get(key);
			//	found.set(key.getvalue());
			String unitName = key.second();
			outFile.print(":"+unitName+ "|"+counts.get(key));
		}
		outFile.println("\n");
		/*		for (int b = found.nextSetBit(0); b >= 0; b = found.nextSetBit(b+1)) {
			System.out.println(b + " " + quantityDict.idToUnitMap.get(b).getBaseName());
		}
		 */	
		for (TObjectIntIterator<String> iter = wordFreqs.iterator(); iter.hasNext();) {
			iter.advance();
			outFile.println(iter.key()+"|"+iter.value());
		}
		for (TObjectIntIterator<String> iter = unitsFreqs.iterator(); iter.hasNext();) {
			iter.advance();
			outFile.println(":"+iter.key()+"|"+iter.value());
		}
		outFile.close();
	}
	public void loadXML(BufferedReader statFile) throws IOException, ParserConfigurationException, SAXException {
		Element elem = XMLConfigs.load(statFile);
		NodeList wordList = elem.getElementsByTagName("w");
		int len = wordList.getLength();
		for (int w = 0; w < len; w++) {

		}
	}
	public void load(BufferedReader statFile) throws IOException, ParserConfigurationException, SAXException {
		String line=null;
		for (int l = 0; (line=statFile.readLine()) != null; l++) {
			//if (l == 0) {
			if (line.startsWith("# statistics over ")) {
				String toks[] = line.split(" ");
				numHdrs += Integer.parseInt(toks[3]);

				if (numHdrs <= 0) {
					throw new IOException("First line should contain number of headers as the four word");
				}
				continue;
			}
			if (line.startsWith("#") || line.length()==0) continue;
			String tokens[]=line.split(":");
			if (tokens.length==1) {
				// only a word with its frequency..no unit.
				tokens = tokens[0].split("\\|");
				int freq = Integer.parseInt(tokens[1]);
				wordFreqs.adjustOrPutValue(tokens[0], freq, freq);
				continue;
			} else if (tokens[0].length()==0) {
				// only a units frequency...
				int t = 1;
				String e[] = tokens[t].split("\\|");
				String unitName = e[0];
				int cnt = Integer.parseInt(e[1]);
				unitsFreqs.adjustOrPutValue(unitName,cnt,cnt);
				continue;
			}
			//int pos = units.size();
			//word2UnitsHashMap.put(tokens[0],pos);
			int total = 0;
			//TObjectIntHashMap<String> conceptCount = new TObjectIntHashMap<String>();
			for (int t = 1; t < tokens.length;t++) {
				if (tokens[t].startsWith("=")) {
					//int cnt = Integer.parseInt(tokens[t].substring(1));
					//counts.add(new Pair(tokens[0],"="),cnt,cnt);
					continue;
				}
				String e[] = tokens[t].split("\\|");
				if (e.length!=2) {
					throw new IOException("Wrong format at line "+(l+1));
				}
				String unitName = e[0];
				int cnt = Integer.parseInt(e[1]);
				counts.adjustOrPutValue(new Pair(tokens[0],unitName), cnt,cnt);
				//units.add(unitName);
				//freqs.add(cnt);
				total += cnt;
				if (quantityDict != null) {
					Unit unit = quantityDict.getUnitFromBaseName(unitName);
					if (unit!=null) {
						counts.adjustOrPutValue(new Pair(tokens[0], unit.getParentQuantity().getConcept()), cnt, cnt);
					}
				}
			}
			/*
			for (TObjectIntIterator<String> iter = conceptCount.iterator(); iter.hasNext(); ) {
				iter.advance();
				units.add(iter.key());
				freqs.add(iter.value());
			}
			units.add("="); freqs.add(total);
			 */
		}
	}
	/*	public int getOccurrenceFrequency(String word, String unitName, String conceptName, int total[]) {
		if (total != null) {total[0] = 0;total[1]=0;}
		if (!word2UnitsHashMap.contains(word)) {
			return 0;
		}
		int freq = 0;
		long posLen = word2UnitsHashMap.get(word);
		int p = getPos(posLen);
		for (; p < units.size() && !units.get(p).equals("="); p++) {
			if (units.get(p).equals(unitName)) {
				freq = freqs.get(p);
			}
			if (conceptName != null && total != null && units.get(p).equals(conceptName)) {
				total[1] = freqs.get(p);
			}
		}
		if (total!=null) {
			assert(units.get(p).equals("="));
			total[0] = freqs.get(p);
		}
		return freq;
	}
	 */
	Pair<String,String> tmpPair = new Pair("","");
	public int getOccurrenceFrequency(String word, String unitName, String conceptName, int total[]) {
		if (total != null) {total[0] = 0;total[1]=0;}
		int freq = counts.get(formTmpPair(word,unitName));
		if (conceptName != null && total != null && counts.containsKey(formTmpPair(word,conceptName))) {
			total[1] = counts.get(tmpPair);
		}
		if (total!=null) {
			total[0] = wordFreqs.get(word);
		}
		if (total != null && total.length > 2) {
			total[2] = unitsFreqs.get(unitName);
		}
		return freq;
	}
	private Pair<String, String> formTmpPair(String word, String unitName) {
		tmpPair.first = word; tmpPair.second = unitName;
		return tmpPair;
	}
	private int getPos(long posLen) {
		return (int) posLen;
	}

	public boolean tokenPresent(String word) {
		return wordFreqs.contains(word);
	}
	public float unitFrequency(String unitName) {
		return unitsFreqs.get(unitName);
	}
	public float tokenFrequency(String tok) {
		return wordFreqs.get(tok);
	}
	public int numDocs() {
		return numHdrs;
	}
	public float[] getConceptFrequencies(String word) {
		List<Quantity> concepts = quantityDict.getQuantities();
		float freqs[]=new float[concepts.size()];
		for (int i = 0; i < freqs.length; i++) {
			freqs[i] = counts.get(formTmpPair(word, concepts.get(i).getConcept()));
		}
		return freqs;
	}
	public static void main(String args[]) throws Exception {
		int total[] = new int[3]; int totalOld[] = new int[3];
		Co_occurrenceStatistics stats[] = new Co_occurrenceStatistics[1];
		stats[0] = new Co_occurrenceStatistics(new QuantityCatalog((Element)null),"/mnt/a99/d0/sunita/workspace/QuantityTagger/configs/cooccurrence.txt");
		//stats[1] = new Co_occurrenceStatistics(stats[0].quantityDict,"/mnt/a99/d0/sunita/workspace/QuantityTagger/configs/cooccurrencePMI.txt");
		String tests[][] = {{"amount", "metre", "Length"},
				{"weight","kilogram","Mass"}
		,{"duration","second","Time"}
		,{"area","kilometre","Length"}
		,{"area","square kilometre","Area"}
		};
		for (String[] test : tests) {
			System.out.println(Arrays.toString(test));
			for (Co_occurrenceStatistics stat : stats) {
				System.out.println(stat.getOccurrenceFrequency(test[0],test[1],test[2], total));
				System.out.println(Arrays.toString(total));
				System.out.println(stat.numHdrs);
			}
		}
		String conceptTests[] = {"distance", "from", "sun", "net", "worth", "year", "of", "first", "flight"};
		//stats[0].addHeaderPMI("speed in kilometre per second", null);
		List<Quantity> concepts = stats[0].quantityDict.getQuantities();
		for (String hdr : conceptTests) {
			float freq[] = stats[0].getConceptFrequencies(hdr); 
			System.out.print(hdr);
			for (int i = 0; i < freq.length; i++) {
				if (freq[i] > 0) System.out.print(" "+concepts.get(i).getConcept()+ " "+freq[i]);
			}
			System.out.println();
		}
	}
	
}
