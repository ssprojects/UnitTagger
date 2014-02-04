package test;

import iitb.shared.XMLConfigs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import test.UniqueRecords.Record;

public class UniqueRecords {
	public static class Record {
		public Record(Element elem, int cnt, String unit) {
			super();
			this.elem = elem;
			this.cnt = cnt;
			this.unit = unit;
		}
		Element elem;
		int cnt;
		String unit;
	}
	public static Hashtable<String, Record> getUnitLabels(Hashtable<String, Record>[] hdr2Units, String labeledDataFile, boolean checkCon) throws Exception {
		Element elem = XMLConfigs.load(new FileReader(labeledDataFile));
		NodeList nodeList = elem.getElementsByTagName("r");
		Hashtable<String, Record> hdr2Unit = new Hashtable<String, Record>();
		int len = nodeList.getLength();
		for (int r = 0; r < len; r++) {
			Element rec = (Element) nodeList.item(r);
			String hdr = XMLConfigs.getElement(rec, "h").getTextContent();
			NodeList unitList = rec.getElementsByTagName("u");
			HashSet<String> trueUnits = null;
			String trueUnitsString = "";
			if (unitList != null && unitList.getLength()>0) {
				trueUnits = new HashSet<String>();
				for (int u = 0; u < unitList.getLength();u++) {
					trueUnits.add(unitList.item(u).getTextContent().toLowerCase());
					if (u > 0) trueUnitsString.concat("|");
					//System.out.println(unitList.item(u).getTextContent());
					trueUnitsString += unitList.item(u).getTextContent().toLowerCase();
				}
			}
			Record entry = hdr2Unit.get(hdr);
			if (entry != null) {
				if (checkCon && !entry.unit.equals(trueUnitsString)) {
					System.out.println("Conflict in labeling for header "+hdr+ " in "+labeledDataFile);
				}
				entry.cnt++;
			} else {
				hdr2Unit.put(hdr,new Record(rec, 1, trueUnitsString));
			}
			for (int j = 0; j < hdr2Units.length && hdr2Units[j] != null; j++) {
				Record ent = hdr2Units[j].get(hdr);
				if (ent != null) {
					if (checkCon && !ent.unit.equals(trueUnitsString)) {
						System.out.println("Conflict in labeling for header "+hdr+ " in "+labeledDataFile + " with that in "+j);
					}
				}
			}
		}
		System.out.println("Read "+len+" records");
		return hdr2Unit;
	}
	public static void main(String args[]) throws Exception {
		String baseFile="/mnt/a99/d0/sunita/workspace.broken/WWT/expts/out";
		Hashtable<String, Record> hdr2Units[] = new Hashtable[3];
		for (int i = 0; i < 3; i++) {
			hdr2Units[i] = getUnitLabels(hdr2Units, baseFile+i+".html",true);
			PrintStream out = new PrintStream(new File(baseFile+".uniq."+i+".xml"));
			out.println("<root>");
			for (Entry<String, Record> anEntry : hdr2Units[i].entrySet()){
				Element rec = anEntry.getValue().elem;
				out.print("<r>");
				String url = XMLConfigs.getElement(XMLConfigs.getElement(rec, "t"), "a").getAttribute("href");
				out.print("<t><a href=\"" + url +"\">"+XMLConfigs.getElement(rec, "t").getTextContent() + "</a></t><c>"+XMLConfigs.getElement(rec, "c").getTextContent() +"</c>");
				out.println("<f>"+anEntry.getValue().cnt+"</f><br></br>");
				out.println("<h>"+XMLConfigs.getElement(rec, "h").getTextContent()+"</h><br></br>");
				out.println("<u>"+anEntry.getValue().unit+"</u><br></br>");
				out.println("</r>");
			}
			out.println("</root>");
			out.flush();
			out.close();
		}
	}
}
