package catalog;

import iitb.shared.EntryWithScore;

import java.util.List;

public interface WordFrequency {
	boolean getRelativeFrequency(String str, List<EntryWithScore<String[]> > matches);
}
