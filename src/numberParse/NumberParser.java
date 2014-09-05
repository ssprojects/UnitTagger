package numberParse;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberParser {
	public static byte NonDecimalNumber = 0;
	public static byte DecimalType = 1;
	public static byte CurrencyType = 2;
	public static byte TextType = 3;
	public static byte DelimType=4;
	public static byte Merged = 5;
	Pattern ignoredTokens = Pattern.compile("~|≈");
	Pattern currency = Pattern.compile("((US)?\\p{Sc})((\\+|\\-|\\.|\\d).*)");
	//Pattern numberText=Pattern.compile("((\\+|\\-|\\.|\\d|,)+)(\\p{Alpha}+)");
	static Pattern expStart = Pattern.compile("(×|x|e|E).*");
	static Pattern numberExp=Pattern.compile("(((×|x)10)|e|E)([+-]?\\d+)");
	String retainedDelims = "(\\.\\.\\.)|-|(to)";
	Pattern retainedDelimPattern = Pattern.compile(retainedDelims);
	public static String numberUnitDelims = "(\\p{javaWhitespace}|\\(|\\)";
	Pattern delimiter = Pattern.compile(numberUnitDelims+"|" + retainedDelims + ")+");
	static int NumberExpStart = 4;
	static Locale locales[] = new Locale[]{Locale.US,Locale.GERMAN,Locale.ENGLISH,Locale.FRENCH};
	static NumberFormat nfs[];
	static Number numbers[] = new Number[locales.length];
	static void init() {
		if (nfs!=null) return;
		nfs = new NumberFormat[locales.length];
		for (int i = 0; i < nfs.length; i++) {
			nfs[i] = NumberFormat.getInstance(locales[i]);
		}
	}
	public static float toFloat(String tok) {
		init();
	/*	if (tok.indexOf('e') >= 0) {
			tok = tok.replaceAll(",", "");
			float val = Float.parseFloat(tok);
			return val;
		}
		*/
		int maxPos = 0;
		Number bestNumber = null;
		for (int l = 0; l < locales.length; l++) {
			ParsePosition pos = new ParsePosition(0);
			Number number = nfs[l].parse(tok, pos);
			if (pos.getErrorIndex() < 0 && maxPos < pos.getIndex()) {
				maxPos = Math.max(maxPos, pos.getIndex());
				bestNumber = number;
			} 
		}
		return bestNumber!=null?bestNumber.floatValue():0;
	}
	public static float[] possibleFloats(String tok, float[] singleFloat) {
		init();
		int maxPos = 0;
		int numFloats=0;
	/*	if (tok.indexOf('e') >= 0) {
			tok = tok.replaceAll(",", "");
			singleFloat[0] = Float.parseFloat(tok);
			return singleFloat;
		}
		*/
		Number bestNumber = null;
		Arrays.fill(numbers, null);
		for (int l = 0; l < locales.length; l++) {
			ParsePosition pos = new ParsePosition(0);
			Number number = nfs[l].parse(tok, pos);
			if (pos.getErrorIndex() < 0 && maxPos <= pos.getIndex()) {
				maxPos = Math.max(maxPos, pos.getIndex());
				if (!Arrays.asList(numbers).contains(number)) {
					numbers[numFloats++] = number;
					if (tok.indexOf(',') < 0) {
						break;
					}
				}
			} 
		}
		if (numFloats==1 && singleFloat != null) {
			singleFloat[0] = numbers[0].floatValue();
			return singleFloat;
		}
		if (numFloats==0)
			return null;
		float floats[] = new float[numFloats];
		for (int i = 0; i < floats.length; i++) {
			floats[i] = numbers[i].floatValue();
		}
		return floats;
	}
	public NumberParser() {
		init();
	}
	
	public static class Tokens extends Vector<String> {
		TIntArrayList startPositions = new TIntArrayList();
		TByteArrayList types = new TByteArrayList();
		public void add(String str, int startPos, byte type) {
			super.add(str);
			startPositions.add(startPos);
			types.add(type);
		}
		public byte getType(int i) {
			return types.get(i);
		}
		public void setType(int i, byte merged) {
			types.set(i,merged);
		}
		public int getStartPos(int i) {
			return startPositions.get(i);
		}
	}
	public Tokens tokenize(String str) {	
		Tokens tokens = new Tokens();
		Scanner scanner = new Scanner(str);
		scanner.useDelimiter(delimiter);
		scanner.useLocale(locales[0]);
		int prevEnd = 0;
		for (int iter = 0; scanner.hasNext(); iter++) {
			String tok = scanner.next();
			int matchPos = scanner.match().start();
			int matchEnd = scanner.match().end();
			Matcher prefMatch = ignoredTokens.matcher(tok);
			if (prefMatch.matches()) {
				tok = tok.substring(1);
				continue;
			}
			if (matchPos>0) {
				String cdelim = str.substring(prevEnd, matchPos).trim();
				if (cdelim.length() > 0 && retainedDelimPattern.matcher(cdelim).matches()) {
					tokens.add(cdelim, prevEnd, DelimType);
				}
			}
			prevEnd = matchEnd;
			Matcher match = currency.matcher(tok);
			if (match.matches()) {
				String currencyStr = match.group(1);
				tok = match.group(3);
				tokens.add(currencyStr,matchPos,CurrencyType);
				matchPos+= currencyStr.length();
			}
			int maxPos = 0;
			Number bestNumber = null;
			for (int l = 0; l < locales.length; l++) {
				ParsePosition pos = new ParsePosition(0);
				Number number = nfs[l].parse(tok, pos);
				if (pos.getErrorIndex() < 0 && maxPos < pos.getIndex()) {
					maxPos = Math.max(maxPos, pos.getIndex());
					bestNumber = number;
				} 
			}
			if (maxPos > 0) {
				tokens.add(tok.substring(0,maxPos),matchPos,(bestNumber instanceof Long)?NonDecimalNumber:DecimalType);
				matchPos += maxPos;
				tok = tok.substring(maxPos,tok.length());
			}
			if (tok.length() > 0) {
				tokens.add(tok,matchPos,TextType);
			}
		}

		for (int i = 0; i < tokens.size(); i++) {
			String tok = tokens.get(i);
			if ((tokens.getType(i) == NonDecimalNumber || tokens.getType(i) == DecimalType) && tok.indexOf('e') <= 0 && tok.indexOf('E') <= 0 && i < tokens.size()-1&&tokens.getType(i+1)==TextType) {
				int newPos = parseDecimalExpression(tokens, i+1);
				for (i++; i < newPos; i++) {
					tokens.setType(i, Merged);
				}
				i--;
			}
		}
		for (int i = 0; i < tokens.size(); i++) {
			System.out.println(tokens.get(i)+ " "+tokens.getType(i)+ " "+tokens.getStartPos(i));
		}
		System.out.println();
		return tokens;
	}
	static int endPt[]={0,0};
	public static String parseDecimalExpression(String doubleStr) {
		return parseDecimalExpression(doubleStr, endPt);
	}
	// parse strings of the form  "~ 1.729 994 044×103"
	public static String parseDecimalExpression(String doubleStr, int startEndPt[]) {
		String vstr = "";
		startEndPt[0] = startEndPt[1] = 0;
		// look for the first character that can be part of a digit.
		for (int c = 0; c < doubleStr.length(); c++) {
			char ch = doubleStr.charAt(c);
			if (Character.isDigit(ch) || ch=='.' || ch=='-' || ch=='+') {
				vstr = doubleStr.substring(c);
				startEndPt[0] = startEndPt[1] = c;
				break;
			}
		}
		if (vstr=="") return "";
		doubleStr = vstr;
		// look for the first character that may not be part of the mantissa.
		String remStr = "";
		if (doubleStr.length() > 0) {
			int relEnd = doubleStr.length();
			// remove the space until the first non-digit is seen.
			for (int c = 1; c < doubleStr.length(); c++) {
				char ch = doubleStr.charAt(c);
				if (!(Character.isDigit(ch) || ch==',' || ch=='.' || ch=='-' || ch=='+' || ch==' ' || ch=='e' || ch=='E' )) {
					remStr  = doubleStr.substring(c);
					relEnd = c;
					doubleStr = doubleStr.substring(0,c);
					break;
				}
			}
	
	
			String parts[] = doubleStr.split(" ");
			doubleStr = "";
			for (int i = 0; i < parts.length; i++) {
				doubleStr += parts[i];
			}
			remStr = remStr.trim();
	
			if (remStr.startsWith("±")) {
				for (int c = 1; c < remStr.length(); c++) {
					char ch = remStr.charAt(c);
					if (!(Character.isDigit(ch)  || ch==',' || ch=='.' || ch==' ' )) {
						remStr  = remStr.substring(c);
						relEnd += c;
						break;
					}
				}
			}
			if (remStr.startsWith("×10") || remStr.startsWith("× 10")  || remStr.startsWith("x 10")) {
				int expStart = remStr.indexOf("10") + 2;
				int expEnd = expStart+1;
				for (; expEnd < remStr.length() && Character.isDigit(remStr.charAt(expEnd));expEnd++) {
				}
				if (expEnd-1 < remStr.length() && Character.isDigit(remStr.charAt(expEnd-1))) {
					doubleStr += "E"+remStr.substring(expStart,expEnd).trim().replace('−', '-');
					relEnd += expEnd;
				}
			}
			startEndPt[1] += relEnd;
		}
		if (doubleStr.endsWith(".") || doubleStr.endsWith(",")) {
			doubleStr = doubleStr.substring(0,doubleStr.length()-1);
		}
		return doubleStr;
	}
	public static int parseDecimalExpression(List<String> toks, int pos) {
		int startPos = pos-1;
		String remStr = toks.get(pos);
		if (remStr.startsWith("±")) {
			if (remStr.length()==1) {
				if (pos == toks.size()-1) {
					return pos;
				}
				pos++;
				remStr = remStr + toks.get(pos);
			}
			int digitEnd = 1;
			for (int c = 1; c < remStr.length(); c++) {
				char ch = remStr.charAt(c);
				if (Character.isDigit(ch) || ch=='.' || ch==',') {
					digitEnd++;
				} else {
					break;
				}
			}
			if (digitEnd < remStr.length()) {
				remStr = remStr.substring(digitEnd);
			} else {
				remStr="";
				if (pos == toks.size()-1) {
					return pos;
				}
				pos++;
				remStr = toks.get(pos);
			}
		}
		if (expStart.matcher(remStr).matches()) {
			for (;pos<toks.size();pos++) {
				Matcher match  = numberExp.matcher(remStr);
				if (match.matches()) {
					String exp  = match.group(NumberExpStart);
					toks.set(startPos, toks.get(startPos)+"e"+exp.replace('−', '-'));
					return pos+1;
				} else {
					if (pos < toks.size()-1)
						remStr += toks.get(pos+1);
				}
			}
		}
		return startPos+1;
	}
	/*
	 * Assumes that the str starts with a number and extracts the possible
	 * interpretations of that number based on different locales.  Eg 
	 * 
	 */
	public static float[] toFloats(String str, float singleFloat[], int startEndPt[]) {
		float vals[] = numberInWords(str,singleFloat,startEndPt);
		return vals==null?possibleFloats(parseDecimalExpression(str, startEndPt), singleFloat):vals;
	}
	public static String numbersInWords[] = {"zero", "one", "two","three","four","five","six","seven","eight","nine"};
	private static float[] numberInWords(String str, float[] singleFloat,
			int[] startEndPt) {
		str = str.trim().toLowerCase();
		for (int i = 0; i < numbersInWords.length;i++) {
			if (str.startsWith(numbersInWords[i])) {
				if (singleFloat==null)
					singleFloat = new float[1];
				singleFloat[0] = i;
				if (startEndPt != null) {
					startEndPt[0] = 0;
					startEndPt[1] = numbersInWords[i].length();
				}
				return singleFloat;
			}
		}
		return null;
	}
	public static float[] toFloats(String str) {
		return toFloats(str, null, endPt);
	//	return possibleFloats(parseDecimalExpression(str), null);
	}
	public static void main(String args[]) {
		String strs[][] = new String[][]{{"$861 603 113", "861603113"},
				{"1 in CO2 emissions", "1"}, {"9.8 billion tons", "9.8"}, {"8%", "8"}, {"1971-2004", "1971"}, {"34bn pounds","34"}};
		for (String[] strF : strs) {
			float ar[] = NumberParser.toFloats(strF[0], null, endPt);
			System.out.println(strF[0] + " " + endPt[0]);
			if ((strF.length==1 && (ar!= null || ar.length != 0)) || ar == null) {
				error(strF, ar);
				continue;
			}
			if (ar.length != strF.length -1 ) {
				error(strF, ar);
				continue;
			}
			for(int i = 0; i < strF.length-1; i++) {
				float f = Float.parseFloat(strF[i+1]);
				if (Math.abs(f-ar[i]) > 1e-6) {
					error(strF, ar);
				}
			}
		}
	}
	private static void error(String[] strF, float[] ar) {
		System.out.printf("Error "+Arrays.toString(strF)+ " "+Arrays.toString(ar));
	}
}
