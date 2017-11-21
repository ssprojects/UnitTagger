package catalog;

import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.smu.tspell.wordnet.NounSynset;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;

public class WordnetFrequency implements WordFrequency, Serializable {
	public static String WordNetDictPath="/usr/local/WordNet-3.0/dict";
	public static String quantityTypeString = "how much there is of something that you can quantify";
	public static String quantitySearchString = "quantity";
	public static String calendarMonth = "calendar month";
	transient NounSynset quantSyn, calenderMonthSyn;
	static WordNetDatabase database=null;
	float stopWordFreq = 0.9f;
	String wordnetFile=null;
	// p -- percent as against poise and poncelet.
	// s is often used as a pluralizer in headers.
	static String stopWords[] = new String[]{"in","are","at","a","from","of","to","the","for","and","all","st"
		,"with","on","total","per","no","number","amp","apos","quot","hr","s"};
	public static HashSet<String> stopWordsHash=new HashSet<String>(Arrays.asList(stopWords));
	public WordnetFrequency(Element options) throws ParserConfigurationException, SAXException, IOException {
	  options = QuantityCatalog.loadDefaultConfig(options);
		wordnetFile =  extractLoadFile(options);
		loadData();
	}
	/**
   * 
   */
  private void loadData() {
    if (database != null) {
      return;
    }
    System.setProperty("wordnet.database.dir",wordnetFile);
    database = WordNetDatabase.getFileInstance();
    Synset syns[] = database.getSynsets(quantitySearchString, SynsetType.NOUN);
    quantSyn = (NounSynset) syns[0];
    calenderMonthSyn = (NounSynset) database.getSynsets(calendarMonth, SynsetType.NOUN)[0];
  }
  private static String extractLoadFile(Element elem) {
		if (elem != null) {
			Element coOccurElem = XMLConfigs.getElement(elem, "WordNet");
			if (coOccurElem!=null && coOccurElem.hasAttribute("path")) {
				return coOccurElem.getAttribute("path");
			}
		}
		return WordNetDictPath;
	}
	public boolean isUnit(NounSynset nsyn) {
		// over-generalizes for words like last,span
		// Quantity hypernym includes way too many units.
		for  (int path = 0; nsyn != null; path++) {
				NounSynset hypos[] = nsyn.getHypernyms();
				nsyn = null;
				for (int h = 0; h < hypos.length; h++) {
					nsyn = hypos[0];
				if (hypos[h] == calenderMonthSyn) {
					// ensures that names of months are not marked as units.
					return false;
				}
				if (hypos[h]==quantSyn) {
					return true;
				}
			}
		}
		return false;
	}
	public void test(String args[]) {
		if (args.length > 0) {
			for (String wordForm : args) {
				//  Get the synsets containing the word form
				Synset[] synsets = database.getSynsets(wordForm, SynsetType.NOUN);
				//  Display the word forms and definitions for synsets retrieved
				if (synsets.length > 0)
				{
					System.out.println("Tag count for each synset:");
					for (int i = 0; i < synsets.length; i++)
					{
						System.out.println("");
						String[] wordForms= synsets[i].getWordForms();
						SynsetType type = synsets[i].getType();
						int cnt = 0;
						for(int j=0;j<wordForms.length;j++){
							System.out.println(wordForms[j]+" [" + synsets[i].getDefinition() + "] " + synsets[i].getTagCount(wordForms[j]));
							cnt += synsets[i].getTagCount(wordForms[j]);
						}
						if (synsets[i] instanceof NounSynset) {
							NounSynset nsyn = (NounSynset) synsets[i];
							System.out.println("Descendant of Quantity " + isUnit(nsyn)+ " "+isUnitDefn(nsyn) + " "+cnt);
						}
						
					}
				}
				else
				{
					System.err.println("No synsets exist that contain " +
							"the word form '" + wordForm + "'");
				}
			}
		}
		else
		{
			System.err.println("You must specify " +
			"a word form for which to retrieve synsets.");
		}
	}
	/**
	 * @param args
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		//args = HeaderSegmenter.WordSymbols;
		args = new String[]{"second"};
		WordnetFrequency wordFreq = new WordnetFrequency(null);
		List<EntryWithScore<String[]>> matches = new Vector<EntryWithScore<String[]>>();
		wordFreq.getRelativeFrequency(args[0], matches);
		System.out.println(Arrays.toString(matches.toArray()));
		wordFreq.test(args);
	}

	@Override
	public boolean getRelativeFrequency(String wordForm, List<EntryWithScore<String[]>> matches) {
	  if (database==null) {
	    loadData();
	  }
	 //System.out.println("Did database lookup for "+wordForm);
	  Synset[] synsets = database.getSynsets(wordForm, SynsetType.NOUN);
		//  Display the word forms and definitions for synsets retrieved
		matches.clear();
		int total = 0;
		for (int i = 0; i < synsets.length; i++)
		{
			String[] wordForms= synsets[i].getWordForms();
			int cnt = 1;
			for(int j=0;j<wordForms.length;j++){
				cnt += synsets[i].getTagCount(wordForms[j]);
			}
			if (isUnitDefn((NounSynset) synsets[i])) {
				matches.add(new EntryWithScore<String[]>(wordForms, cnt));
			}
			/*if (isUnit((NounSynset) synsets[i]) != isUnitDefn(synsets[i])) {
					System.out.println(wordForm + " " + synsets[i]);
					System.out.println();
				}*/
			total += cnt;

		}
		boolean foundMatch = synsets.length > 0;
		if (stopWordsHash.contains(wordForm)) {
			total /= (1-stopWordFreq);
			foundMatch=true;
		}
		for (int i = 0; i < matches.size(); i++) {
			matches.get(i).setScore(matches.get(i).getScore()/total);
		}
		//if (foundMatch && matches.size()==0) {
		//	matches.add(new EntryWithScore<String[]>(new String[]{wordForm}, 1e-6));
		//}
		
		return foundMatch;
	}
	private boolean isUnitDefn(Synset synset) {
		// was "period of time" earlier and did not capture words like week.
		boolean retVal = (synset.getDefinition().contains("unit") 
				|| (synset.getDefinition().contains("period") && synset.getDefinition().contains("time"))
				|| (synset.getDefinition().contains("second")) // synset.getDefinition().contains("period") && 14 Mar 2014: removed this and clause because millisecond is not getting marked as unit.
				|| synset.getDefinition().contains(" number ") || synset.getDefinition().contains("a proportion multiplied by 100")// 17 Jul 2013: added for words like thousand, billion
		);
		if (!retVal) {
			// adding this since terms like milligram do not have unit in their definition.
			// so, if immediate parent has unit-of.
			NounSynset hypos[] = ((NounSynset) synset).getHypernyms();
			for (int h = 0; h < hypos.length; h++) {
				NounSynset nsyn = hypos[0];
				String defn = nsyn.getDefinition();
				if (defn.contains("unit of") || defn.contains("units of")) return true;
			}
		}
		return retVal;
	}
}
