package test;

import iitb.shared.EntryWithScore;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import parser.coOccurMethods.ConceptClassifier;
import parser.coOccurMethods.ConceptTypeScores;
import catalog.Quantity;
import catalog.QuantityCatalog;

public class QueryLogAnalysis {
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		QuantityCatalog quantDict = new QuantityCatalog((Element)null); //((FacetedNumericValuesQuery)qp.getAnswerExtractor()).getQuantityDict(); //
		ConceptTypeScores conceptClassifier = quantDict; //new ConceptClassifier(quantDict, QuantityCatalog.QuantConfigDirPath+ConceptClassifier.ClassifierFile);
		String prefix = "/mnt/a1/sdb1/sunita/AOL-user-ct-collection/user-ct-test-collection-";
		for (int i = 1; i <= 10; i++) {
			String fname = prefix + (i < 10?"0":"") + i + ".txt.gz";
			GZIPInputStream gz = new GZIPInputStream(new FileInputStream(fname));
			BufferedReader br = new BufferedReader(new InputStreamReader(gz));
			String line;
			String prevFld="Query";
			int cnt = 0, numberCnt = 0;
			while ((line=br.readLine())!= null) {
				String flds[] = line.split("\t");
				if (flds[1].equals(prevFld)) continue;
				prevFld=flds[1];
				cnt++;
				List<EntryWithScore<Quantity>> scores = conceptClassifier.getConceptScores(flds[1]);
				if (scores == null || scores.size()==0 || scores.get(0).getScore() < 0.9) continue;
				if (prevFld.contains("code") || prevFld.contains("county")) continue;
				numberCnt++;
				System.out.println(numberCnt + "/" + cnt + " "+prevFld + " TYPE="+scores.get(0).getKey().getConcept()+ " "+scores.get(0).getScore());
			}
		}
	}

}
