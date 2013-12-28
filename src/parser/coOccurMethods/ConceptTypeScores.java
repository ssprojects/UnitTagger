package parser.coOccurMethods;

import iitb.shared.EntryWithScore;

import java.util.List;

public interface ConceptTypeScores {

	public List<EntryWithScore<String>> getConceptScores(String hdr)
			throws Exception;

}