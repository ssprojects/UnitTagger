package parser;

import iitb.shared.EntryWithScore;

import java.io.IOException;
import java.util.List;

import catalog.Unit;

public interface HeaderUnitParser {
	public List<EntryWithScore<Unit>> parseHeader(String hdr) throws IOException;
	public List<EntryWithScore<Unit>> parseHeaderExplain(String hdr, List<String> explanation) throws IOException;
}