package catalog;

import gnu.trove.TByteArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TShortArrayList;
import iitb.shared.ArrayUtils;
import iitb.shared.EntryWithScore;
import iitb.shared.SignatureSetIndex.DocResult;
import iitb.shared.SignatureSetIndex.Index;
import iitb.shared.SignatureSetIndex.IndexImpl;
import iitb.shared.SignatureSetIndex.Result;
import iitb.shared.SignatureSetIndex.SignatureSetImpl;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.map.AbstractMapDecorator;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.util.Version;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import parser.UnitSpan;

/* sunita: Sep 8, 2012 */
public class QuantityCatalog implements WordFrequency {
	private static final double MinContextOverlap = 0.5;
	private static final double MinMatchThreshold = 0.5;
	/* Create an exact name match units dictionary consisting of 
		the full name, 
		all the baseNames.
	 */
	MultiValueMap nameDict = new MultiValueMap();
	Index<String> nameTokDict = new IndexImpl<String>();
	/* 
	 * unit dictionary same preprocessing as above.
	 */
	MultiValueMap symbolDict = new MultiValueMap();

	MultiValueMap lemmaDict = new MultiValueMap();

	public Index<String> tokenDict = new IndexImpl<String>();

	MultiValueMap conceptDict = new MultiValueMap();
	public static String QuantTaxonomyPath = "/mnt/a99/d0/sunita/workspace/QuantityTagger/configs/QuantityTaxonomy.xml";
	ArrayList<Quantity> taxonomy;
	public static class IdToUnitMap extends Vector<Unit> {
		public static final byte ConceptMatch = (byte)'c';
		public static final byte SymbolMatch = (byte)'s';
		public static final byte LemmaMatch = (byte)'l';
		public static final byte NameMatch = (byte)'n';
		Vector<List<String> > names = new Vector<List<String> >();
		TByteArrayList byteArry = new TByteArrayList();
		public void add(Unit u, List<String> name, byte c) {
			super.add(u);
			names.add(name);
			byteArry.add(c);
		}
		public List<String> getTokens(int pos) {
			return names.get(pos);
		}
		public byte getType(int pos) {
			return byteArry.get(pos);
		}
		public Quantity getConcept(int id) {
			return get(id).getParentQuantity();
		}
	}
	public IdToUnitMap idToUnitMap = new IdToUnitMap();
	
	SignatureSetImpl<String> signSet;
	Analyzer analyzer;
	public static String impDelims = "£$#\\/%\\(\\)\\[";
	// 17/7/2013: remove - because words like year-end and ten-year were getting marked as year.
	public static String delims =impDelims +  "!#&'\\*\\+,\\.:;\\<=\\>\\?@\\^\\_\\`\\|~ \t\\]\\{\\}";//NumberUnitParser.numberUnitDelims+"|\\p{Punct})";//)";
	public static List<String> getTokens(String name) {
		return getTokens(name,null);
	}
	public static List<String> getTokens(String name, TIntArrayList brackets) {
		return getTokens(name,brackets,null,null);
	}
		
	public static List<String> getTokens(String name, TIntArrayList brackets, String specialTokens[], TIntArrayList specialTokenPosition) {
		/*
		if (name.indexOf(' ') < 0) {
			List<String> toks = new Vector<String>();
			toks.add(name);
			return toks;
		}
		List<String> toks =  TableUtils.getTokens(name.toLowerCase().trim(), null, analyzer, false);
		
		*/
		//return Arrays.asList(name.toLowerCase().split(delims));
		Vector<String> toks = new Vector<String>();
		StringTokenizer textTok = new StringTokenizer(name, delims, true);
		while (textTok.hasMoreTokens()) {
			String tokStr=textTok.nextToken();
			int tpos;
			if (specialTokens != null && (tpos = ArrayUtils.find(specialTokens, tokStr)) >= 0) {
				if (specialTokenPosition !=null) {
					specialTokenPosition.add((tpos << 16) + toks.size());
				}
				continue;
			}
			if (delims.indexOf(tokStr)==-1 || impDelims.indexOf(tokStr)!=-1) {	 
				char ch = tokStr.charAt(0);
				if (ch=='(' || ch == ')' || ch=='[' || ch == ']') {
					if (brackets != null) {
						if (ch=='(' || ch=='[')
							brackets.add(-toks.size());
						else {
							for (int pos = brackets.size()-1; pos >= 0; pos--) {
								int val = brackets.get(pos);
								if (val < 0) {
									// found a match opening bracket..
									brackets.set(pos, ((-val) << 16) + (toks.size()-1));
									assert(brackets.get(pos) >= 0);
									break;
								}
							}
						}
					}
				} else 
					toks.add(tokStr.toLowerCase());
			}
		}
		for (int i = 0; i < toks.size(); i++) {
			if (toks.get(i).length() > 4 && toks.get(i).endsWith("s")) {
				toks.set(i, toks.get(i).substring(0,toks.get(i).length()-1));
			}
		}
		return toks;
	}
	public QuantityCatalog(Element elem) throws IOException, ParserConfigurationException, SAXException {
		this(QuantityReader.loadQuantityTaxonomy((elem != null && elem.hasAttribute("quantity-taxonomy"))?elem.getAttribute("quantity-taxonomy"):QuantTaxonomyPath));
	}
	public QuantityCatalog(ArrayList<Quantity> taxonomy) throws IOException {
		analyzer =  new StandardAnalyzer(Version.LUCENE_33);
		this.taxonomy=taxonomy;
		signSet = new SignatureSetImpl<String>(null);
		int stats[] = new int[10];
		for (Quantity q : taxonomy) {
			for (Unit u : q.getUnits()) {
				String names[] = u.getBaseNames();
				for (String name : names) {
					nameDict.put(name.toLowerCase(),u);
					List<String> toks = getTokens(name);
					if (toks.size()>stats.length-1) {
						continue;
					}
					stats[toks.size()]++;
					tokenDict.put(signSet.toSignSet(toks), idToUnitMap.size());
					idToUnitMap.add(u,toks,idToUnitMap.NameMatch);
				}
				for (String symbol  : u.getBaseSymbols()) {
					symbolDict.put(symbol.toLowerCase(), u);
					List<String> tokens = getTokens(symbol);
					tokenDict.put(signSet.toSignSet(tokens), idToUnitMap.size());
					idToUnitMap.add(u,tokens,idToUnitMap.SymbolMatch);
				}
				for (int l =  u.getLemmas().size()-1; l >= 0; l--) {
					String lemma = u.getLemmas().get(l);
					float relFreq = u.getLemmaFrequency(l);
					if (relFreq > Float.MIN_VALUE)
						lemmaDict.put(lemma, new EntryWithScore<Unit>(u, relFreq));
					else
						lemmaDict.put(lemma.toLowerCase(), u);
					if (symbolDict.containsKey(lemma.toLowerCase())) {
						//System.out.println("Bad lemma in "+u.getBaseName()+ " ... "+lemma);
						continue;
					}
					List<String> lemmaToks = u.getLemmaTokens(l);
					tokenDict.put(signSet.toSignSet(lemmaToks), idToUnitMap.size());
					idToUnitMap.add(u,lemmaToks,idToUnitMap.LemmaMatch);
				}
			}
			conceptDict.put(q.getConcept().toLowerCase().trim(), q);
			List<String> ctoks = getTokens(q.getConcept());
			tokenDict.put(signSet.toSignSet(ctoks), idToUnitMap.size());
			if (q.SIUnit==null) {
				if (q.getUnits().size()>0) idToUnitMap.add(q.getUnits().get(0),ctoks,idToUnitMap.ConceptMatch);
			} else {
				idToUnitMap.add(q.SIUnit,ctoks,idToUnitMap.ConceptMatch);
			}
		}
		addCompoundUnitParts();
		//System.out.println(Arrays.toString(stats));
	}

	private void addCompoundUnitParts() {
		for (Quantity q : taxonomy) {
			for (Unit u : q.getUnits()) {
				String baseName = u.getBaseName();
				int index = baseName.indexOf(" per ");
				if (index > 1 && index + 5 < baseName.length()-1) {
					String unit1Str = baseName.substring(0,index).trim();
					String unit2Str = baseName.substring(index+5).trim();
					
					if (unit1Str.length() > 0 && unit2Str.length()>0) {
						Unit unit1 = getUnitFromBaseName(unit1Str);
						Unit unit2 = getUnitFromBaseName(unit2Str);
						if (unit1 != null && unit2 != null) {
							u.setCompoundUnitParts(unit1,unit2);
						}
					}
				}
			}
		}
	}
	public Quantity bestConceptMatch(String str) {
		str = str.trim().toLowerCase();
		if (str.endsWith("."))
			str = str.substring(0,str.length()-1).trim();
		Collection matches = conceptDict.getCollection(str);
		if (matches != null && matches.size()>1) 
			return null;
		if (matches != null && matches.size()==1) 
			return (Quantity) matches.toArray()[0];
		Quantity bestMatch = null;
		for (Quantity quant : taxonomy) {
			if (str.endsWith(quant.getConcept().toLowerCase().trim())) {
				if (bestMatch==null)
					bestMatch = quant;
				else
					return null;
			}
		}
		return bestMatch;
	}
	public List<EntryWithScore<Unit> >getTopK(String str, String contextStr, double matchThreshold) {
		return getTopK(str, contextStr, matchThreshold, null);
	}
	/* get rid of trailing "." and "s (for long words)" during a match

	// If unit name matches name dictionary exactly, return all matches.

	// If symbol name has no ambiguity and of length at least 3 return match 1.

	// If table header words match with concept name: e.g. header has "weight" and unit is "lb" return match 1.

	 */
	public List<EntryWithScore<Unit> >getTopK(String str, String contextStr, double matchThreshold, String newContext[]) {
		str = str.trim().toLowerCase();
		if (str.startsWith("$") && str.length() > 1 && str.charAt(1) != ' ') {
			str = "$" + " "+str.substring(1);
		}
		if (str.endsWith("$") && str.length() > 1 && str.charAt(str.length()-2) != ' ') {
			str = str.substring(0,str.length()-1) + " "+"$";
		}
		if (str.endsWith("."))
			str = str.substring(0,str.length()-1).trim();
		if (str.endsWith("approx"))
			str = str.substring(0,str.length()-6).trim();
		List<EntryWithScore<Unit> > entries=null;
		Collection matches = nameDict.getCollection(str);
		if (matches != null) {
			entries = new Vector<EntryWithScore<Unit>>();
			for (Iterator<Unit> iter = matches.iterator(); iter.hasNext();) {
				Unit u = iter.next();
				entries.add(new EntryWithScore<Unit>(u, 1));
			}
		} else {
			matches = symbolDict.getCollection(str);
			if (matches != null) {
				entries = new Vector<EntryWithScore<Unit>>();
				if (matches.size() == 1 && str.length()>1) {
					for (Iterator<Unit> iter = matches.iterator(); iter.hasNext();) {
						Unit u = iter.next();
						entries.add(new EntryWithScore<Unit>(u, 1));
					}
				} else {
					for (Iterator<Unit> iter = matches.iterator(); iter.hasNext();) {
						Unit u = iter.next();
						if (similarity(u.getParentQuantity().getConcept(),contextStr) > MinContextOverlap)
							entries.add(new EntryWithScore<Unit>(u, 1));
					}
				}
			} else {
				matches = lemmaDict.getCollection(str);
				if (matches != null) {
					entries = new Vector<EntryWithScore<Unit>>();
					for (Iterator<Unit> iter = matches.iterator(); iter.hasNext();) {
						Object u = iter.next();
						float freq = 0;
						if (u instanceof EntryWithScore) {
							EntryWithScore<Unit> unitFreq = (EntryWithScore<Unit>) u;
							freq = (float) unitFreq.getScore();
							entries.add(new EntryWithScore<Unit>(unitFreq.getKey(), (freq > Float.MIN_VALUE?freq:0.5)));
						} else {
							entries.add(new EntryWithScore<Unit>((Unit)u, 0.5));
						}
					}
				}
			}
		} 
		if (entries == null && str.endsWith("s") && str.length() > 4) {
			return getTopK(str.substring(0,str.length()-1), contextStr, matchThreshold);
		}
		int part1Index = -1;
		if (entries==null && (part1Index = str.indexOf(' ')) > 0) {
			String candMult = str.substring(0,part1Index).trim();
			String candUnitName = str.substring(part1Index+1);
			List<EntryWithScore<Unit>> multUnits = (candUnitName.length() > 0?getTopK(candMult, null, matchThreshold):null);
			if (!(multUnits==null || multUnits.size()==0 || candUnitName.length() == 0)) { 
				Unit multiplier = null;
				for (EntryWithScore<Unit> multUnit : multUnits) {
					if (Quantity.isUnitLess(multUnit.getKey().getParentQuantity())) {
						multiplier=multUnit.getKey();
						break;
					}
				}
				if (multiplier==null) {
					/*
					 * perhaps the unit and multiplier are interchanged.
					 */
					List<EntryWithScore<Unit>> multUnits2 = (candUnitName.length() > 0?getTopK(candUnitName, null, matchThreshold):null);
					if (!(multUnits2==null || multUnits2.size()==0 || candUnitName.length() == 0)) { 
						for (EntryWithScore<Unit> multUnit : multUnits2) {
							if (Quantity.isUnitLess(multUnit.getKey().getParentQuantity())) {
								multiplier=multUnit.getKey();
								candUnitName=candMult; // interchanged...
								break;
							}
						}
					}
				}
				if (multiplier!=null)  {
					List<EntryWithScore<Unit>> baseUnits = getTopK(candUnitName, null, matchThreshold);
					if (!(baseUnits==null || baseUnits.size()==0)) { 
						for (EntryWithScore<Unit> baseUnit : baseUnits) {
							baseUnit.setKey(new UnitMultPair(baseUnit.getKey(),multiplier));
						}
						entries = baseUnits;
					}
				}
			}
		}
		if (part1Index > 0 && entries==null) {
			try {
				signSet.toSignSet(getTokens(str, null, analyzer, false));
			} catch (IOException e) {
				e.printStackTrace();
			}
			int numTokensToMatch = (int) (signSet.size()*matchThreshold);
			if (numTokensToMatch > 0) {
				Result result = tokenDict.findMatches(signSet, numTokensToMatch);
				if (!(result == null || result.numHits()==0)) {
					entries = new Vector<EntryWithScore<Unit>>();
					for (int i = 0; i < result.numHits(); i++) {
						if (i==0 || result.hit(i) != result.hit(i-1))
							entries.add(new EntryWithScore<Unit>(idToUnitMap.get(result.hit(i)), matchThreshold));
					}
					return entries;
				}
			}
		}
		return entries;
	}
	public static List<String> getTokens(String s, String field, Analyzer analyzer, boolean prefixField) throws IOException {
		List<String> result = (List<String>) new ArrayList<String>();
        if(s == null) { s = ""; }
        TokenStream stream = analyzer.tokenStream(field, new StringReader(s));
        /*Token token = new Token();
        while((token = stream.next(token))!=null) {
        	String t = new String(token.termBuffer(), 0, token.termLength());
            result.add(!prefixField ? t : field + ":" + t);
        }
        */
        while(stream.incrementToken()) {
        	String t = ((TermAttribute) stream.getAttribute(org.apache.lucene.analysis.tokenattributes.TermAttribute.class)).term();
            result.add(!prefixField ? t : field + ":" + t);
        }
        stream.close();
        return result;
    }

	//TODO:
	private int similarity(String concept, String str) {
		return 1;
	}
	public DocResult subSequenceMatch(List<String> tokens, float threshold) {
		return tokenDict.findSubsequenceMatchesCosine(signSet.toSignSet(tokens), threshold);
	}
	public DocResult subSequenceMatch(List<String> tokens, float threshold, boolean maximal) {
		return tokenDict.findSubsequenceMatchesCosine(signSet.toSignSet(tokens), threshold, maximal);
	}
	public DocResult subSequenceMatch(List<String> tokens, float thresholds[]) {
		float minThreshold = ArrayUtils.min(thresholds);
		return tokenDict.findSubsequenceMatchesCosine(signSet.toSignSet(tokens), minThreshold);
	}
	static class NewUnit extends Unit {
		public NewUnit(String Name,String Sym)
		{
			super(Name,Sym,"",null);
		}
	}
	public Unit newUnit(List<String> subList) {
		String name = "";
		for (String subString : subList) {
			name += subString;
		}
		return new NewUnit(name,name);
	}
	public Unit newUnit(String name) {
		return new NewUnit(name,name);
	}
	public Unit newUnit(Unit unit1, Unit unit2, UnitPair.OpType op) {
		String name = unit1.getSymbol() + op + unit2.getSymbol();
		if (symbolDict.containsKey(name)) {
			for(Object unit : symbolDict.getCollection(name))
				return (Unit) unit;
		}
		return new UnitPair(unit1, unit2, op);
	}
	public static void main(String args[]) throws Exception {
		String tests[][] = {
				{"sq.km.","Area","square kilometre"},
				{"million Km", "", "kilometre [million]"},
				{"$mil", "net worth", "united states dollar [million]"},
				{"millions of Miles", "Distance from earth in millions miles", "mile [million]"},
				{"FY 2006, USD billion", "", "united states dollar [billion]"},
				{"cm", "Diameter", "centimetre"},
				{"in", "length", "inch"},
				{"\"", "length", "inch"},
				{"Miles", "", "mile"},
				{"Miles(approx)", "", "mile"},
				{"mil $", "", "united states dollar [million]"}, 
				{"$", "net worth", "united states dollar"},
				{"ft./s", "Average Length","foot"},
				{"m", "value", "metre"},
				{"y", "decay, half-life", "year"}, // the spurious a needs to be removed.
				{"h", "decay, half-life", "hour"},
				{"km²","","square kilometre"}
		};
		QuantityCatalog matcher = new QuantityCatalog(QuantityReader.loadQuantityTaxonomy(QuantTaxonomyPath));
		String unitName = "kilometre/hour"; // "United States Dollar [billion]";
		Unit parsedUnit = matcher.getUnitFromBaseName(unitName);
		if (!parsedUnit.getBaseName().equalsIgnoreCase(unitName))
			throw new Exception("Mistake in unit parsing");
		
		
/*		DocResult res = matcher.tokenDict.findSubsequenceMatchesCosine(new SignatureSetImpl<String>(matcher.getTokens(tests[0][1])), 0.8f);
		for (int h = 0; h < res.numHits(); h++) {
			System.out.println(res.hitDocId(h) + " " + matcher.idToUnitMap.getString(res.hitDocId(h))
					+ "\n " + matcher.idToUnitMap.get(res.hitDocId(h))
					+ " "+res.hitMatch(h)+ " "+res.hitPosition(h)+ " "+res.hitEndPosition(h));
		}
		*/
		for (int i = 0; i < tests.length; i++) {
			//List<String> toks = matcher.getTokens(tests[i][0]);
			//System.out.println(tests[i][0]+"--->"+toks);
			List<EntryWithScore<Unit>> matches = matcher.getTopK(tests[i][0], tests[i][1], MinMatchThreshold);
			if (!(matches != null && matches.size()>0 && matches.get(0).getKey().getBaseName().equalsIgnoreCase(tests[i][2]))) {
				System.out.println("Mistake for "+tests[i][0]+ " predicted "+ (matches != null?matches.get(0).getKey().getBaseName():""));
			}
		}
	}
	public boolean getRelativeFrequency(String str, List<EntryWithScore<String[]>> matchesArg) {
		Collection matches = lemmaDict.getCollection(str);
		matchesArg.clear();
		if (matches != null) {
			for (Iterator<Unit> iter = matches.iterator(); iter.hasNext();) {
				Object u = iter.next();
				float freq = 0;
				if (u instanceof EntryWithScore) {
					EntryWithScore<Unit> unitFreq = (EntryWithScore<Unit>) u;
					freq = (float) unitFreq.getScore();
					matchesArg.add(new EntryWithScore<String[]>(unitFreq.getKey().getBaseNames(), (freq > Float.MIN_VALUE?freq:0.5)));
				}
			}
		}
		return matchesArg.size()>0;
	}
	public float getRelativeFrequency(Unit unit, String name) {
		float freq = 0;
		for (int l = unit.getLemmas().size()-1; l >= 0; l--) {
			String lemma = unit.getLemmas().get(l);
			if (lemma.equalsIgnoreCase(name)) {
				return unit.getLemmaFrequency(l);
			}
		}
		return freq;
	}
	public List<Unit> bestMatchUnit(String[] wordForms) {
		HashSet<Unit> visited = new HashSet<Unit>();
		Vector<Unit> bestUnits = null;
		float bestScore = Float.NEGATIVE_INFINITY;
		for (String word : wordForms) {
			Collection<Unit> units = nameDict.getCollection(word.toLowerCase());
			if (units != null) {
				for (Unit unit : units) {
					if (visited.contains(unit)) continue;
					visited.add(unit);
					float score = matchScore(unit, wordForms);
					if (score > bestScore) {
						bestScore = score;
						if (bestUnits != null) bestUnits.clear();
						else bestUnits = new Vector<Unit>();
						bestUnits.add(unit);
					} else if (bestScore - score < Float.MIN_VALUE) {
						bestUnits.add(unit);
					}
				}
			}
		}
		return bestUnits;
	}
	public float matchScore(Unit unit, String[] wordForms) {
		float score = 0;
		for (String word : wordForms) {
			boolean wordMatched = false;
			for (String baseName : unit.baseNames) {
				if (baseName.equalsIgnoreCase(word)) {
					score += 1;
					wordMatched = true;
					break;
				}
			}
			if (wordMatched) continue;
			for (String lemma : unit.getLemmas()) {
				if (lemma.equalsIgnoreCase(word)) {
					wordMatched = true;
					score += 0.7;
					break;
				}
			}
			if (wordMatched) continue;
			for (String symbol : unit.baseSyms) {
				if (symbol.equalsIgnoreCase(word)) {
					score += 0.5;
					break;
				}
			}
		}
		return score;
	}
	public float getRelativeFrequency(int id) {
		Unit u = idToUnitMap.get(id);
		List<String> tokens = idToUnitMap.getTokens(id);
		if (!u.hasFrequency()) return 0;
		for (int l = 0; l < u.getLemmas().size(); l++) {
			if (tokens.equals(u.getLemmaTokens(l))) {
				return u.getLemmaFrequency(l);
			}
		}
		return 0;
	}
	public Unit getUnitFromBaseName(String unitName) {
		return getUnitFromBaseName(unitName, false);
	}
	public Unit getUnitFromBaseName(String unitName, boolean onlyDict) {
		if (unitName == null) return null;
		Collection<Unit> units = nameDict.getCollection(unitName.toLowerCase());
		if (units != null && units.size()>0)
			return units.iterator().next();
		if (units==null && !onlyDict) {
			String parts[] =  UnitPair.extractUnitParts(unitName);
			if (parts != null) {
				Unit unit1 = null, unit2 = null;
				units = nameDict.getCollection(parts[0].toLowerCase());
				if (units != null && units.size()>0)
					unit1 = units.iterator().next();
				units = nameDict.getCollection(parts[1].toLowerCase());
				if (units != null && units.size()>0)
					unit2 = units.iterator().next();
				if (unit1 != null && unit2 != null) {
					return UnitPair.newUnitPair(unit1, unit2, UnitPair.getOpTypeFromOpStr(parts[2]));
				}
			}
		}
		return null;
	}
	public Unit isUnit(String str) {
		List<EntryWithScore<Unit>> matches = getTopK(str, "", 0.9);
		return (matches != null && matches.size()>0)?matches.get(0).getKey():null;
	}
	public float convert(float value, Unit fromUnit, Unit toUnit, boolean success[]) {
		success[0] = true;
		if (toUnit == null)
			return value;
		double convValue = value*(fromUnit==null?1:fromUnit.getMultiplier())/toUnit.getMultiplier();
		if (Double.isNaN(convValue) || Double.isInfinite(convValue)) {
			success[0] = false;
			return value;
		}
		return (float) convValue;
	}
	
}
