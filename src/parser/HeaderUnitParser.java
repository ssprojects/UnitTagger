package parser;

import iitb.shared.EntryWithScore;

import java.io.IOException;
import java.util.List;


import catalog.Unit;

public interface HeaderUnitParser {
	public List<EntryWithScore<Unit>> parseHeader(String hdr) throws IOException;
	public List<EntryWithScore<Unit>> parseHeaderExplain(String hdr, List<String> explanation, int debugLvl, ParseState hdrMatches[]) throws IOException;
	// get Top-K matches and make sure that the scores are probabilities.
	public List<EntryWithScore<Unit>> parseHeaderProbabilistic(String hdr, List<String> explanation, int debugLvl, int k, ParseState hdrMatches[]) throws IOException;
}