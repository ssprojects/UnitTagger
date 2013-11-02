package catalog;

import iitb.shared.EntryWithScore;

import java.util.Hashtable;
import java.util.List;

public class WordFrequencyImpl implements WordFrequency {
	Hashtable<String, List<EntryWithScore<String> > > dict;
	public WordFrequencyImpl() {
		dict = new Hashtable<String, List<EntryWithScore<String>>>();
	}
	@Override
	public boolean getRelativeFrequency(String str,
			List<EntryWithScore<String[]>> matches) {
				return false;

	}

}
