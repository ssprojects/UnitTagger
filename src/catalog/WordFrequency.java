package catalog;

import iitb.shared.EntryWithScore;

import java.util.List;

public interface WordFrequency {
	void getRelativeFrequency(String str, List<EntryWithScore<String[]> > matches);
}
