package parser.coOccurMethods;

import iitb.shared.EntryWithScore;


import java.util.List;

import catalog.Quantity;

public interface ConceptTypeScores {
	public enum ConceptClassifierTypes {perfectMatch, cooccur, classifier};
	public List<EntryWithScore<Quantity>> getConceptScores(String hdr)
			throws Exception;

}