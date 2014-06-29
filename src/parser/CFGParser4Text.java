package parser;

import iitb.shared.EntryWithScore;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import numberParse.NumberParser;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import parser.CFGParser4Header.EnumIndex.Tags;
import parser.CFGParser4Header.StateIndex;

import catalog.QuantityCatalog;
import catalog.Unit;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.trees.Tree;

public class CFGParser4Text extends CFGParser4Header {
	public CFGParser4Text(Element options) throws Exception {
		super(options);
		initParams();
		
	}
	private void initParams() {
		params.contextDiffThreshold=4; // need a larger threshold for context word match for text data.
	}
	public static String QuantityToken="qqqq";
	public static String WordToken="wwww";
	public CFGParser4Text(Element options, QuantityCatalog quantMatcher)
			throws Exception {
		super(options, quantMatcher);
		initParams();
	}
	// put the grammar before BU.
	public static String textGrammar=
		"ROOT ::- ROOT_ "+Lexicon.BOUNDARY_TAG + " 1f" + "\n" +
        "ROOT_ ::- Junk Q_U 1f" + "\n" +
        "ROOT_ ::- Junk 1f" + "\n" +
        "ROOT_ ::- Junk_QU Junk 1f" + "\n" +
        "ROOT_ ::- Q_U Junk 1f" + "\n" +
        "ROOT_ ::- Q_U 1f" + "\n" +

        "Junk_QU ::= Junk Q_U 1f"+ "\n" +
        "Junk ::= Junk W 1f"+ "\n" +
        "Junk ::= W 1f"+ "\n" +
        
        "Q_U ::= Q_Junk Q_U 1f"+ "\n"+  //for cases like "2 and 5 m","4 +- 6 m"
        "Q_U ::= Q_Junk N_U 1f"+ "\n"+
        "N_U ::= Number U 1f"+ "\n"+
        "Q_Junk ::= Q Junk 1f"+"\n"+
        
        "Q_U := Q W_Op_U 1f"+"\n"+ 
        "W_Op_U := Op_U 1f"+"\n"+  	  //for cases like 31 per thousand
        "W_Op_U := W Op_U 1f"+"\n"+   //for cases like 22123 parts per billion
        "Op_U := PER SU 1f"+"\n"+
        "Op_U := PER Mult 1f"+"\n"+
        
        "Q_U ::= Q_U Rep_QU 1f"+"\n"+  //for cases like 4 m (5 feet), between 3 m and 4 m, 
        "Rep_QU ::= W Q_U 1f"+"\n"+        
        "Rep_QU ::= Q_U 1f"+"\n"+
        
        "Q_U ::- Q U 1f" +    "\n" + //Quantity followed by a unit.
        "Q_U ::- SU_Q 1f" +    "\n" +                // a units followed by a quantity e.g. "$500"
        "Q_U ::- Q 1f" + "\n" +                    // unitless and multiplier-less quantity e.g. Population of India is 1,200,000
        "Q_U ::- SU_Q Mult 1" + "\n"+
        //Assuming Q is tag standing for a quantity, expressed either in words or numbers, which has been recognized and normalized
        "Q_U ::- BU_Q 1f" +    "\n" + // for things like $100 per litre 
        "U ::= BU 1f"+ "\n" +
        "U ::- BU Mult 1" + "\n" +
		"U ::- Mult BU 1" + "\n" +
		"U ::- Mult 1" + "\n" + 
		"SU_Q ::- SU Q 1f" +    "\n" +
		"BU_Q ::- CU2_Q 1f" +    "\n" +
		"CU2_Q ::- SU_Q PER_SU 1f\n" +
		"CU2_Q ::- SU_Q Sep_SU 1f\n" +
		
       "Q_U ::- _WU PureWs_Q 1\n"+  // these rules are for patterns of the form: co2 emissions (kt) in france was qqqq. 
    "_WU ::- W U 1\n"+              // These should be applied only when none of the previous rules manage to find a unit.
    "PureWs_Q ::- PureWs Q 1\n"+
    "PureWs ::- W 1\n"+
    "PureWs ::- PureWs W 1\n";
	
	@Override
	protected String getGrammar() {
		return textGrammar + basicUnitGrammar;
	}
	
	protected void getUnitNodes(Tree tree, Vector<Tree> unitNodes, String unitLabel) {
		if (tree.label().value().equals(unitLabel)) {
			unitNodes.add(tree);
			return;
		}
		for (Tree kid : tree.children()) {
			getUnitNodes(kid, unitNodes, unitLabel);
		}
	}
	protected void getTopUnitNodes(Tree tree, Vector<Tree> unitNodes) {
		getUnitNodes(tree,unitNodes,StateIndex.States.U.name());
		if (unitNodes.size()==0) {
			getUnitNodes(tree,unitNodes,StateIndex.States.Q_U.name());
		}
	}
	public List<? extends EntryWithScore<Unit>> getTopKUnits(String hdr, int numberStart, int numberEnd, int k, int debugLvl) {
		String hdrQQ = hdr.substring(0,numberStart).replaceAll(QuantityToken, "") 
		+ QuantityToken + hdr.substring(numberEnd+1).replaceAll(QuantityToken, ""); 
		return parseHeader(hdrQQ, null, debugLvl, k, null);
	}
	public List<? extends EntryWithScore<Unit>> getTopKUnits(String taggedHdr, String tag, int k, int debugLvl) {
		String hdrQQ = taggedHdr.substring(0,taggedHdr.indexOf("<"+tag+">")).replaceAll(QuantityToken, "") 
		+ QuantityToken + taggedHdr.substring(taggedHdr.indexOf("</"+tag+">")+tag.length()+3).replaceAll(QuantityToken, ""); 
		return parseHeader(hdrQQ, null, debugLvl, k, null);
	}
	public List<? extends EntryWithScore<Unit>> getTopKUnitsValues(String taggedHdr, int numStart, int k, int debugLvl, float[][] values) {
		int numStartEnd[] = new int[2];
		String prefix = taggedHdr.substring(numStart);
		values[0] = NumberParser.toFloats(prefix, values[0], numStartEnd);
		String hdrQQ = taggedHdr.substring(0,numStart+numStartEnd[0]).replaceAll(QuantityToken, "") 
		+ QuantityToken
		+ prefix.substring(numStartEnd[1]).replaceAll(QuantityToken, "");
		return parseHeader(hdrQQ, null, debugLvl, k, null);
	}
	public List<? extends EntryWithScore<Unit>> getTopKUnitsValues(String taggedHdr, String tag, int k, int debugLvl, float[][] values) {
		int numStart = taggedHdr.indexOf("<"+tag+">");
		int numStartEnd[] = new int[2];
		String prefix = taggedHdr.substring(numStart+tag.length()+2);
		values[0] = NumberParser.toFloats(prefix, values[0], numStartEnd);
		String hdrQQ = taggedHdr.substring(0,numStart).replaceAll(QuantityToken, "") + prefix.substring(0,numStartEnd[0])
		+ QuantityToken;
		String suffix = prefix.substring(numStartEnd[1]);
		int tagIndex = suffix.indexOf("</"+tag+">");
		hdrQQ += suffix.substring(0, tagIndex) +  " " + suffix.substring(tagIndex+tag.length()+3).replaceAll(QuantityToken, "");
		return parseHeader(hdrQQ, null, debugLvl, k, null);
	}
	public List<? extends EntryWithScore<Unit>> parseHeader(String hdr, short[][] forcedTags, int debugLvl, int k, Vector<UnitFeatures> featureList) {
		List<? extends EntryWithScore<Unit>>  units =  super.parseHeader(hdr, null, debugLvl,forcedTags, null, k, featureList);
		if (units != null && units.size() > 0) return units;
		tokenScorer.useExtendedGrammar=true;
		units = super.parseHeader(hdr, null, debugLvl,forcedTags, null, k, featureList);
		tokenScorer.useExtendedGrammar = false;
		return units;
	}
	/*
	 * (non-Javadoc)
	 * @see parser.RuleBasedParser#filterUselessTokens(java.util.List)
	 * Remove tokens far off from the numbers by casting them to a word.
	 */
	protected void filterUselessTokens(List<String> hdrToks) {
		int numberTok = -1;
		for (int i = 0; i < hdrToks.size(); i++) {
			if (hdrToks.get(i).equals(QuantityToken)) {
				numberTok = i;
				break;
			}
		}
		for(int j = Math.max(0, numberTok-params.contextDiffThreshold); j < Math.min(hdrToks.size(), numberTok+params.contextDiffThreshold); j++) {
			
		}
	}
	public static void main(String args[]) throws Exception {
		Vector<UnitFeatures> featureList = new Vector();
		Vector<String> explanation = new Vector<String>();
		
		String hdr = 
			"Of these qqqq% are endemic";
		//"The value for CO2 emissions from solid fuel consumption (kt) in France was qqqq as of 2009. ";
	//		"Land area: 10,579 sq mi (27,400 sq km); total area: 11,100 sq mi (28,748 sq km). Population (2012 est.): qqqq (growth rate: 0.28%); birth rate: 12.38/1000; infant mortality rate: 14.12/1000; life expectancy: 77.59; density per sq mi: 340. Capital";
		//new CFGParser4Text(null).getTopKUnits(hdr, 12, 15, 1, 1);
		float values[][] = new float[1][1];
		List<? extends EntryWithScore<Unit>> unitsS = new CFGParser4Text(null).getTopKUnitsValues("of these <b>1.1%</b> are endemic", "b", 1, 1,values);
		//List<? extends EntryWithScore<Unit>> unitsS = new CFGParser4Text(null).parseHeader(hdr, null, 1,2, featureList);
		
		if (unitsS != null) {
			eval.Utils.printExtractedUnits(unitsS,true);
		} else {
			System.out.println("No unit found");
		}
		System.out.println("----------");
		
	
		List<? extends EntryWithScore<Unit>> unitsR = new CFGParser4Text(null).parseHeader(hdr,
				new short[][]{{(short) Tags.W.ordinal()},{(short) Tags.W.ordinal()},{(short) Tags.Q.ordinal()},{(short) Tags.W.ordinal()},{(short) Tags.Number.ordinal()},{(short) Tags.SU_1W.ordinal()}}
				//null
				,1,1,featureList); 
	
		if (unitsR != null) {
				eval.Utils.printExtractedUnits(unitsR,true);
		} else {
			System.out.println("No unit found");
		}
		
	}
	
}
