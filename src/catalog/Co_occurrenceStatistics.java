package catalog;

import edu.stanford.nlp.io.EncodingPrintWriter.out;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectLongHashMap;
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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Co_occurrenceStatistics {
	QuantityCatalog quantityDict;
	List<String> units = new Vector<String>();
	TIntArrayList freqs = new TIntArrayList();
	TObjectLongHashMap<String> word2UnitsHashMap = new TObjectLongHashMap<String>();
	TObjectIntHashMap<StringIntPair> counts = new TObjectIntHashMap<StringIntPair>();
	int numHdrs = 0;
	public Co_occurrenceStatistics(Element elem, QuantityCatalog dict, String loadFile) throws IOException, ParserConfigurationException, SAXException {
		if (dict==null) {
			String path = QuantityCatalog.QuantTaxonomyPath;
			if (elem != null && elem.hasAttribute("quantity-taxonomy")) {
				path = elem.getAttribute("quantity-taxonomy");
			}
			quantityDict = new QuantityCatalog(QuantityReader.loadQuantityTaxonomy(path));
		} else 
			quantityDict = dict;
		if (loadFile != null) {
			load(new BufferedReader(new FileReader(new File(loadFile))));
		}
	}
	public void addHeader(String hdr) {
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
				counts.adjustOrPutValue(new StringIntPair(tokens.get(t), res.hitDocId(bestH)), 1, 1);
			}
		}
	}
	public void save(PrintStream outFile) {
		outFile.println("# statistics over "+numHdrs+ " headers "+ counts.size() + " keys ");
		StringIntPair keys[] = counts.keys(new StringIntPair[]{});
		Arrays.sort(keys);
		String prevString = "";
		int total = 0;
	//	BitSet found = new BitSet();
		for(StringIntPair key : keys) {
			if (!key.getKey().equals(prevString)) {
				if (total>0) {
					outFile.print(":="+total);
				}
				prevString = key.getKey();
				total=0;
				outFile.println();
				outFile.print(prevString);
			}
			total += counts.get(key);
		//	found.set(key.getvalue());
			int b = key.getvalue();
			outFile.print(":"+quantityDict.idToUnitMap.get(b).getBaseName()+ "|"+counts.get(key));
		}
		outFile.println("\n");
/*		for (int b = found.nextSetBit(0); b >= 0; b = found.nextSetBit(b+1)) {
			System.out.println(b + " " + quantityDict.idToUnitMap.get(b).getBaseName());
		}
	*/	
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
			if (line.startsWith("#")) continue;
			String tokens[]=line.split(":");
			int pos = units.size();
			word2UnitsHashMap.put(tokens[0],pos);
			int total = 0;
			for (int t = 1; t < tokens.length;t++) {
				if (tokens[t].startsWith("=")) continue;
				String e[] = tokens[t].split("\\|");
				if (e.length!=2) {
					throw new IOException("Wrong format at line "+(l+1));
				}
				String unitName = e[0];
				int cnt = Integer.parseInt(e[1]);
				units.add(unitName);
				freqs.add(cnt);
				total += cnt;
			}
			units.add("="); freqs.add(total);
		}
	}
	public int getOccurrenceFrequency(String word, String unitName, int total[]) {
		if (total != null) total[0] = 0;
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
		}
		if (total!=null) {
			assert(units.get(p).equals("="));
			total[0] = freqs.get(p);
		}
		return freq;
	}
	private int getPos(long posLen) {
		return (int) posLen;
	}
	public static void main(String args[]) throws IOException, ParserConfigurationException, SAXException {
		int total[] = new int[1];
		Co_occurrenceStatistics stats = new Co_occurrenceStatistics(null, null, "/tmp/cooccurrence6L.txt");
		System.out.println(stats.getOccurrenceFrequency("weight", "gram", total));
	}
}
