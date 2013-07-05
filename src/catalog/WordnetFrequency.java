package catalog;

import iitb.shared.EntryWithScore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import edu.smu.tspell.wordnet.NounSynset;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;

public class WordnetFrequency implements WordFrequency {
	public static String quantityTypeString = "how much there is of something that you can quantify";
	public static String quantitySearchString = "quantity";
	NounSynset quantSyn;
	WordNetDatabase database;
	float stopWordFreq = 0.9f;
	// p -- percent as against poise and poncelet.
	static String stopWords[] = new String[]{"in","are","at","a","from"};
	static HashSet<String> stopWordsHash=new HashSet<String>(Arrays.asList(stopWords));
	public WordnetFrequency() {
		System.setProperty("wordnet.database.dir", "/mnt/b100/d0/library/public_html/wordnet/WordNet-2.1/dict");
		database = WordNetDatabase.getFileInstance();
		Synset syns[] = database.getSynsets(quantitySearchString, SynsetType.NOUN);
		quantSyn = (NounSynset) syns[0];
	}
	public boolean isUnit(NounSynset nsyn) {
		for  (int path = 0; nsyn != null; path++) {
			NounSynset hypos[] = nsyn.getHypernyms();
			nsyn = null;
			for (int h = 0; h < hypos.length; h++) {
				nsyn = hypos[0];
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
						for(int j=0;j<wordForms.length;j++){
							System.out.println(wordForms[j]+" [" + synsets[i].getDefinition() + "] " + synsets[i].getTagCount(wordForms[j]));
						}
						if (synsets[i] instanceof NounSynset) {
							NounSynset nsyn = (NounSynset) synsets[i];
							
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
	 */
	public static void main(String[] args) {
		//args = HeaderSegmenter.WordSymbols;
		args = new String[]{"$"};
		new WordnetFrequency().test(args);
		// dec should not be a unit, but it is.
		// at should have a low relative freq for unit.
	}

	@Override
	public void getRelativeFrequency(String wordForm, List<EntryWithScore<String[]>> matches) {
		Synset[] synsets = database.getSynsets(wordForm, SynsetType.NOUN);
		//  Display the word forms and definitions for synsets retrieved
		matches.clear();
		if (synsets.length > 0)
		{
			int total = 0;
			for (int i = 0; i < synsets.length; i++)
			{
				String[] wordForms= synsets[i].getWordForms();
				int cnt = 1;
				for(int j=0;j<wordForms.length;j++){
					cnt += synsets[i].getTagCount(wordForms[j]);
				}
				if (isUnit((NounSynset) synsets[i])) {
					matches.add(new EntryWithScore<String[]>(wordForms, cnt));
				}
				if (isUnit((NounSynset) synsets[i]) != isUnitDefn(synsets[i])) {
					System.out.println(wordForm + " " + synsets[i]);
					System.out.println();
				}
				total += cnt;
				
			}
			if (stopWordsHash.contains(wordForm)) {
				total /= (1-stopWordFreq);
			}
			for (int i = 0; i < matches.size(); i++) {
				matches.get(i).setScore(matches.get(i).getScore()/total);
			}
		}
	}
	private boolean isUnitDefn(Synset synset) {
		return (synset.getDefinition().contains("unit") || (synset.getDefinition().contains("period of time")));
	}
}
