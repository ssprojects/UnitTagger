package catalog;


import gnu.trove.list.array.TFloatArrayList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 *
 * @author harsh
 * @author chander Fixed bugs for getting the Lemma-sets and appropriate
 * unit-level attributes.
 * 
 */
public class QuantityReader {
	public static ArrayList<Quantity> loadQuantityTaxonomy(String path) throws ParserConfigurationException, IOException, SAXException {
	return loadQuantityTaxonomy(new FileInputStream(new File(path)));
	}	
	public static ArrayList<Quantity> loadQuantityTaxonomy(InputStream is) throws ParserConfigurationException, IOException, SAXException
	{
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc =  dBuilder.parse(is);
		NodeList nList = doc.getElementsByTagName("concept");

		ArrayList<Quantity> qList=new ArrayList<Quantity>();

		for (int temp = 0; temp < nList.getLength(); temp++)
		{
			Quantity quant=new Quantity();
			Node qNode=nList.item(temp);
			if(qNode.getNodeName().equals("concept"))
			{
				NamedNodeMap qattributes = qNode.getAttributes();
				quant.setConcept(qattributes.item(0).getNodeValue());
			}


			NodeList unitList=qNode.getChildNodes();
			// Synste.. Typical Usage,, Units..



			ArrayList<Unit> units=new ArrayList<Unit>();
			for(int u=0;u<unitList.getLength();u++){
				Node unit=unitList.item(u);

				if(unit.getNodeName().trim().equals("Synset")){
					NodeList SynsetsNodes=unit.getChildNodes();
					ArrayList<String> synsets=new ArrayList<String>();
					for(int s=0;s<SynsetsNodes.getLength();s++){
						Node sn=SynsetsNodes.item(s);
						if(sn.getNodeName().equals("String")){
							synsets.add(sn.getTextContent());
						}
					}
					quant.setSynSets(synsets);
				}
				if(unit.getNodeName().trim().equals("typicalUsage")){
					NodeList TypicalUsageNodes=unit.getChildNodes();
					ArrayList<String> TypicalUsage=new ArrayList<String>();
					for(int t=0;t<TypicalUsageNodes.getLength();t++){
						Node tuse=TypicalUsageNodes.item(t);
						if(tuse.getNodeName().equals("String")){
							TypicalUsage.add(tuse.getTextContent());
						}
					}

					quant.setTypicalUsage(TypicalUsage);
				}

				if(unit.getNodeName().trim().equals("unit")){
					Unit un=new Unit(quant);
					// get attributes from unit node...
					NamedNodeMap attributes = unit.getAttributes();
					String name = attributes.getNamedItem("name").getNodeValue();
					String symb = attributes.getNamedItem("symbol").getNodeValue();
					String cf   = attributes.getNamedItem("conversionFactor").getNodeValue();

					NodeList lemmasNodes=unit.getChildNodes();
					ArrayList<String> lemmaset=new ArrayList<String>();
					TFloatArrayList freqs = null;
					for(int l=0;l<lemmasNodes.getLength();l++)
					{
						Node lm=lemmasNodes.item(l);
						if(lm.getNodeName().equals("lemmaset"))
						{
							NodeList unitlemmaNodes=lm.getChildNodes();
							for(int lmn=0 ; lmn < unitlemmaNodes.getLength(); lmn++)
							{
								Node ulemmastrnode = unitlemmaNodes.item(lmn);
								if(ulemmastrnode.getNodeName().equals("String"))
									lemmaset.add(ulemmastrnode.getTextContent());
								if (ulemmastrnode.hasAttributes()) {
									NamedNodeMap attributesF = ulemmastrnode.getAttributes();
									float freq = Float.parseFloat(attributesF.getNamedItem("frequency").getNodeValue());
									if (freqs==null) {
										freqs = new TFloatArrayList();
									}
									for (int f = freqs.size(); f < lemmaset.size()-1; freqs.add(0),f++);
									freqs.add(freq);
								}
							}
						}
					}
					if (name.endsWith("(SI base unit)") || name.endsWith("(SI unit)")) {
						if (quant.SIUnit != null) throw new IOException("Two SI units for "+quant.getConcept() + " " + name + " & " + quant.SIUnit.getName());
						quant.SIUnit = un;
						name = name.substring(0, name.lastIndexOf("(SI "));
						// System.out.println("Canonical unit "+quant.getConcept() + " "+name);
					}
					un.setConversionFactor(cf);
					un.setName(name);
					un.setSymbol(symb);
					un.setLemmas(lemmaset,freqs);
					units.add(un);
				}
			}
			//   if (quant.SIUnit==null) {
			//	 throw new IOException("No SI units for "+quant.getConcept());
			//}
			quant.setUnits(units);
			qList.add(quant);
		}

		return qList;
	}

	// break typical usage on ":" and retain only unique first strings.
	// remove lemmas with more than 10 entries as they are mostly junk.
	public static void cleanUp() {

	}

	public static void main(String[] args) throws FileNotFoundException, ParserConfigurationException, IOException, SAXException
	{
		ArrayList<Quantity> catalog=QuantityReader.loadQuantityTaxonomy(QuantityCatalog.QuantTaxonomyPath);
		System.out.println(catalog.size());
	}

}
