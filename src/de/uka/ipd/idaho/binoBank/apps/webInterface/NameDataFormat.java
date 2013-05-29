/* BinoBank, the distributed platform for taxonomic name strings.
 * Copyright (C) 2011-2013 ViBRANT (FP7/2007-2013, GA 261532), by D. King & G. Sautter
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package de.uka.ipd.idaho.binoBank.apps.webInterface;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils.TaxonomicName;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem;

/**
 * This class implements different data formats for the upload of taxonomic
 * names, Its main use is in the BinoBank web interface. Centralized methods for
 * creating plain string and parsed representations of uploaded taxonomic names
 * are given.
 * 
 * @author sautter
 */
public abstract class NameDataFormat implements TaxonomicNameConstants {
	
	//	TODO implement this for DwC and SimpleDwC plus DwCRanks XML
	
	//	TODO implement this for TCS XML
	
	//	TODO implement this for ABCD XML
	
	/** The taxonomic rank system to use for name handling */
	protected static final TaxonomicRankSystem rankSystem = TaxonomicRankSystem.getRankSystem(null);
	
	/** Mapping from DwC, S-DwC, and DwC-Ranks to generic rank names defined in TaxonomicNameConstants */
	protected static final Properties rankNameMappings = new Properties();
	static {
		rankNameMappings.setProperty("dwcranks:domain", DOMAIN_ATTRIBUTE);
		rankNameMappings.setProperty("domain", DOMAIN_ATTRIBUTE);
		
		rankNameMappings.setProperty("dwcranks:superkingdom", SUPERKINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("superkingdom", SUPERKINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subkingdom", SUBKINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("subkingdom", SUBKINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infrakingdom", INFRAKINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("infrakingdom", INFRAKINGDOM_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:superphylum", SUPERPHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("superphylum", SUPERPHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subphylum", SUBPHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("subphylum", SUBPHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infraphylum", INFRAPHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("infraphylum", INFRAPHYLUM_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:superclass", SUPERCLASS_ATTRIBUTE);
		rankNameMappings.setProperty("superclass", SUPERCLASS_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subclass", SUBCLASS_ATTRIBUTE);
		rankNameMappings.setProperty("subclass", SUBCLASS_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infraclass", INFRACLASS_ATTRIBUTE);
		rankNameMappings.setProperty("infraclass", INFRACLASS_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:superorder", SUPERORDER_ATTRIBUTE);
		rankNameMappings.setProperty("superorder", SUPERORDER_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:suborder", SUBORDER_ATTRIBUTE);
		rankNameMappings.setProperty("suborder", SUBORDER_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infraorder", INFRAORDER_ATTRIBUTE);
		rankNameMappings.setProperty("infraorder", INFRAORDER_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:superfamily", SUPERFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("superfamily", SUPERFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subfamily", SUBFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("subfamily", SUBFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infrafamily", INFRAFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("infrafamily", INFRAFAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:supertribe", SUPERTRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("supertribe", SUPERTRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:tribe", TRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("tribe", TRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subtribe", SUBTRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("subtribe", SUBTRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infratribe", INFRATRIBE_ATTRIBUTE);
		rankNameMappings.setProperty("infratribe", INFRATRIBE_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:subgenus", SUBGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("subgenus", SUBGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infragenus", INFRAGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("infragenus", INFRAGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:section", SECTION_ATTRIBUTE);
		rankNameMappings.setProperty("section", SECTION_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subsection", SUBSECTION_ATTRIBUTE);
		rankNameMappings.setProperty("subsection", SUBSECTION_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:series", SERIES_ATTRIBUTE);
		rankNameMappings.setProperty("series", SERIES_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subseries", SUBSERIES_ATTRIBUTE);
		rankNameMappings.setProperty("subseries", SUBSERIES_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwcranks:speciesAggregate", SPECIESAGGREGATE_ATTRIBUTE);
		rankNameMappings.setProperty("speciesAggregate", SPECIESAGGREGATE_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subspeciesEpithet", SUBSPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("subspeciesEpithet", SUBSPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:infraspeciesEpithet", INFRASPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("infraspeciesEpithet", INFRASPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:varietyEpithet", VARIETY_ATTRIBUTE);
		rankNameMappings.setProperty("varietyEpithet", VARIETY_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subvarietyEpithet", SUBVARIETY_ATTRIBUTE);
		rankNameMappings.setProperty("subvarietyEpithet", SUBVARIETY_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:formEpithet", FORM_ATTRIBUTE);
		rankNameMappings.setProperty("formEpithet", FORM_ATTRIBUTE);
		rankNameMappings.setProperty("dwcranks:subformEpithet", SUBFORM_ATTRIBUTE);
		rankNameMappings.setProperty("subformEpithet", SUBFORM_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwc:Kingdom", KINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("Kingdom", KINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Phylum", PHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("Phylum", PHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Class", CLASS_ATTRIBUTE);
		rankNameMappings.setProperty("Class", CLASS_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Order", ORDER_ATTRIBUTE);
		rankNameMappings.setProperty("Order", ORDER_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Family", FAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("Family", FAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Genus", GENUS_ATTRIBUTE);
		rankNameMappings.setProperty("Genus", GENUS_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Species", SPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("Species", SPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:Subspecies", SUBSPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("Subspecies", SUBSPECIES_ATTRIBUTE);
			
		rankNameMappings.setProperty("dwc:kingdom", KINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("kingdom", KINGDOM_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:phylum", PHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("phylum", PHYLUM_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:class", CLASS_ATTRIBUTE);
		rankNameMappings.setProperty("class", CLASS_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:order", ORDER_ATTRIBUTE);
		rankNameMappings.setProperty("order", ORDER_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:family", FAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("family", FAMILY_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:genus", GENUS_ATTRIBUTE);
		rankNameMappings.setProperty("genus", GENUS_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:subgenus", SUBGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("subgenus", SUBGENUS_ATTRIBUTE);
		rankNameMappings.setProperty("dwc:speciesEpithet", SPECIES_ATTRIBUTE);
		rankNameMappings.setProperty("speciesEpithet", SPECIES_ATTRIBUTE);
		
		rankNameMappings.setProperty("dwc:scientificNameAuthorship", AUTHORITY_ATTRIBUTE);
		rankNameMappings.setProperty("scientificNameAuthorship", AUTHORITY_ATTRIBUTE);
	}
	
	/**
	 * Load concrete implementations of this class from the JAR files a specific
	 * folder. For the same folder, the data formats are loaded only once, then
	 * they are shared between all code that invokes this method on the same
	 * folder from within the same JVM. The returned array itself is not shared,
	 * however, so invoking code can manipulate it as needed.
	 * @param folder the folder to load the data fromats from
	 * @return an array holding the data formats
	 */
	public static synchronized NameDataFormat[] getDataFormats(final File folder) {
		String cacheId = folder.getAbsolutePath();
		cacheId = cacheId.replaceAll("\\\\", "/");
		cacheId = cacheId.replaceAll("\\/\\.\\/", "/");
		cacheId = cacheId.replaceAll("\\/{2,}", "/");
		
		ArrayList dataFormats = ((ArrayList) dataFormatCache.get(cacheId));
		if (dataFormats != null)
			return ((NameDataFormat[]) dataFormats.toArray(new NameDataFormat[dataFormats.size()]));
		
		GamtaClassLoader.setThreadLocal(true);
		Object[] rawDataFormats = GamtaClassLoader.loadComponents(folder, NameDataFormat.class, new ComponentInitializer() {
			public void initialize(Object component, String componentJarName) throws Throwable {
				NameDataFormat rdf = ((NameDataFormat) component);
				File rdfData = new File(folder, (componentJarName.substring(0, (componentJarName.length() - ".jar".length())) + "Data"));
				if (!rdfData.exists())
					rdfData.mkdir();
				rdf.setDataProvider(new AnalyzerDataProviderFileBased(rdfData));
			}
		});
		
		dataFormats = new ArrayList();
		for (int d = 0; d < rawDataFormats.length; d++) {
			if (rawDataFormats[d] instanceof NameDataFormat)
				dataFormats.add((NameDataFormat) rawDataFormats[d]);
		}
		dataFormatCache.put(cacheId, dataFormats);
		
		return ((NameDataFormat[]) dataFormats.toArray(new NameDataFormat[dataFormats.size()]));
	}
	private static HashMap dataFormatCache = new HashMap();
	
	
	/**
	 * A taxonomic name parsed from an external representation, e.g. DwC.
	 * This class adds support for (a) keeping the source text the name
	 * was parsed from and (b) for error messages, e.g. regarding
	 * missing elements.
	 * 
	 * @author sautter
	 */
	public static class ParsedTaxonomicName extends TaxonomicName {
		private String error;
		private String source;
		private String plainString;
		
		/**
		 * Constructor
		 */
		public ParsedTaxonomicName() {}
		
		/**
		 * Constructor
		 * @param code the nomenclatorial code or biological domain the name
		 *            belongs to
		 */
		public ParsedTaxonomicName(String codeOrDomain) {
			super(codeOrDomain);
		}
		
		/** Constructor
		 * @param rankSystem the rank system representing the nomenclatorial
		 *            code the name belongs to
		 */
		public ParsedTaxonomicName(TaxonomicRankSystem rankSystem) {
			super(rankSystem);
		}
		
		/**
		 * Add the original input data to the taxonomic name. This facilitates,
		 * for instance, prompting users for corrections on erroneous or
		 * incomplete records.
		 * @param source the source the reference was parsed from
		 */
		public void setSource(String source) {
			this.source = source;
		}
		
		/**
		 * Retrieve the original source data of the taxonomic name. This method
		 * only returns a non-null value after the setSource() method has been
		 * invoked.
		 * @return the original source data of the reference
		 */
		public String getSource() {
			return this.source;
		}
		
		/**
		 * Add a plain string representation to the taxonomic name.
		 * @param plainString the plain string representation
		 */
		public void setPlainString(String plainString) {
			this.plainString = plainString;
		}
		
		/**
		 * Retrieve the plain string representation of the taxonomic name. If
		 * the plain string representation has not been set via
		 * setPlainString(), this method resorts to toString(true) to generate
		 * the plain string representation.
		 * @return the plain string representation
		 */
		public String getPlainString() {
			return ((this.plainString == null) ? this.toString(true) : this.plainString);
		}
		
		/**
		 * Add an error message and the original input data to the taxonomic
		 * name. This facilitates, for instance, prompting users for corrections
		 * of erroneous or incomplete records. If this method is used, the
		 * attributes are cleared.
		 * @param error the error message informing the user of what is wrong
		 */
		public void setError(String error) {
			this.error = error;
			this.setAuthority(null, -1);
		}
		
		/**
		 * Check if this taxonomic name has an error message attached to it.
		 * This method only returns true value after the setError() method has
		 * been invoked.
		 * @return true if there is an error message, false otherwise
		 */
		public boolean hasError() {
			return (this.error != null);
		}
		
		/**
		 * Retrieve the error message indicating the problem in this taxonomic
		 * name. This method only returns a non-null value after the setError()
		 * method has been invoked.
		 * @return the error message
		 */
		public String getError() {
			return this.error;
		}
	}
	
	/**
	 * Iterator over taxonomic names extracted from a source during parsing.
	 * 
	 * @author sautter
	 */
	public static interface ParsedTaxonomicNameIterator {
		
		/**
		 * Retrieve an estimate of the number of taxonomic names yet to come.
		 * This method is to at least vaguely gauge progress with data formats
		 * that read the entire input data before returning any reference data
		 * sets. If an estimate is not available, e.g. in data formats that
		 * really stream their input, this method should return -1.
		 * @return an estimate of the number of taxonomic names yet to come.
		 */
		public abstract int estimateRemaining();
		
		/**
		 * @return the next taxonomic name
		 */
		public abstract ParsedTaxonomicName nextName() throws IOException;
		
		/**
		 * @return true if there are more taxonomic names to retrieve, false
		 *         otherwise
		 */
		public abstract boolean hasNextName() throws IOException;
	}
	
	/** The name of the data format */
	public final String name;
	
	/**
	 * The data provider to load data from.
	 */
	protected AnalyzerDataProvider dataProvider;
	
	/**
	 * Constructor
	 * @param name the data format name
	 */
	protected NameDataFormat(String name) {
		this.name = name;
	}
	
	/**
	 * Make the taxonomic name data format know where its data is stored. This
	 * method should only be called by code instantiating taxonomic name data
	 * formats.
	 * @param adp the data provider
	 */
	public void setDataProvider(AnalyzerDataProvider adp) {
		this.dataProvider = adp;
		this.init();
	}
	
	/**
	 * Initialize the data format. This method is invoked immediately after the
	 * data provider is set. That means sub classes can assume the data provider
	 * not to be null. This convenience implementation does nothing, sub classes
	 * are welcome to overwrite it as needed.
	 */
	protected void init() {}
	
	/**
	 * Shut down the data format. This method should be invoked by client code
	 * immediately before discarding the data format to facilitate
	 * implementation specific cleanup. Invoking this method might render
	 * individual data formats useless, depending on the actual implementation.
	 * Thus client code should not use a data format after invoking this method.
	 * This convenience implementation does nothing, sub classes are welcome to
	 * overwrite it as needed.
	 */
	public void exit() {}
	
	/**
	 * Retrieve a label for the data format, e.g. for use in drop-down selector
	 * fields. The returned string should not contain any line breaks or HTML
	 * formatting.
	 * @return a label for the data format
	 */
	public abstract String getLabel();
	
	/**
	 * Retrieve a brief description of the data format, e.g. for explanation in
	 * a user interface. The returned string should not contain any line breaks,
	 * but may include HTML formatting. If HTML formatting is used, the returned
	 * string hast to start with '&lt;html&gt;' to indicate so. This default
	 * implementation simply returns the label provided by the getLabel()
	 * method. Sub classes are recommended to overwrite this method to provide a
	 * more comprehensive description.
	 * @return a description of the data format
	 */
	public String getDescription() {
		return this.getLabel();
	}
	
	/**
	 * Parse taxonomic names from the character stream provided by an input
	 * reader.
	 * @param in the reader to read from
	 * @return an array of taxonomic name objects
	 * @throws IOException
	 */
	public ParsedTaxonomicName[] parse(Reader in) throws IOException {
		ArrayList refs = new ArrayList();
		for (ParsedTaxonomicNameIterator rit = this.streamParse(in); rit.hasNextName();)
			refs.add(rit.nextName());
		return ((ParsedTaxonomicName[]) refs.toArray(new ParsedTaxonomicName[refs.size()]));
	}
	
	/**
	 * Indicate whether or not parsed versions of taxonomic names provided by
	 * the data format are approved by a user.
	 * @return true is parsed taxonomic names are approved by a user
	 */
	public abstract boolean isParseUserApproved();
	
	/**
	 * Retrieve an array holding the file extensions identifying files the data
	 * format can decode, e.g. for use in a file filter. If this method returns
	 * null, all file types are permitted. This default implementation does
	 * return null, sub classes are welcome to overwrite it as needed. In fact,
	 * overwriting this method is encouraged, so to prevent uploading files that
	 * cannot be decoded, which vainly causes network traffic.
	 * @return an array of file extensions the data format can parse
	 */
	public String[] getFileExtensions() {
		return null;
		//	would love to use MIME types, but the ACCEPT attribute of HTML file inputs does not work in many browsers
	}
	
	/**
	 * Parse taxonomic names one by one from the character stream provided by
	 * an input reader.
	 * @param in the reader to read from
	 * @return an iterator over taxonomic name objects
	 * @throws IOException
	 */
	public abstract ParsedTaxonomicNameIterator streamParse(Reader in) throws IOException;	
	
	/**
	 * Parse one or more taxonomic names from the character stream provided by
	 * an input reader.
	 * @param in the reader to read from
	 * @return an array of upload string objects holding the pairs of plain and
	 *         parsed versions of taxonomic names
	 * @throws IOException
	 */
	public UploadString[] parseNames(Reader in) throws IOException {
		ArrayList uRefs = new ArrayList();
		for (UploadStringIterator usit = this.streamParseNames(in); usit.hasNextUploadString();)
			uRefs.add(usit.nextUploadString());
		return ((UploadString[]) uRefs.toArray(new UploadString[uRefs.size()]));
	}
	
	/**
	 * Parse taxonomic names one by one from the character stream provided by
	 * an input reader.
	 * @param in the reader to read from
	 * @return an iterator over upload string objects holding the pairs of plain
	 *         and parsed versions of taxonomic names
	 * @throws IOException
	 */
	public UploadStringIterator streamParseNames(Reader in) throws IOException {
		final ParsedTaxonomicNameIterator refs = this.streamParse(in);
		return new UploadStringIterator() {
			int usTotal = 0;
			int usValid = 0;
			UploadString next = null;
			ArrayList errors = new ArrayList(4);
			public int getValidCount() {
				return this.usValid;
			}
			public int getErrorCount() {
				return this.errors.size();
			}
			public UploadStringError[] getErrors() {
				return ((UploadStringError[]) this.errors.toArray(new UploadStringError[this.errors.size()]));
			}
			public int getTotalCount() {
				return this.usTotal;
			}
			public int estimateRemaining() {
				return refs.estimateRemaining();
			}
			public UploadString nextUploadString() throws IOException {
				UploadString us = this.next;
				this.next = null;
				return us;
			}
			public boolean hasNextUploadString() throws IOException {
				if (this.next != null)
					return true;
				
				if (!refs.hasNextName())
					return false;
				
				//	get next data set
				ParsedTaxonomicName taxName = refs.nextName();
				this.usTotal++;
				
				//	check error
				if (taxName.hasError()) {
					this.errors.add(new UploadStringError(taxName.getError(), taxName.getSource()));
					return this.hasNextUploadString();
				}
				
				//	produce and store plain and parsed representations
				String taxNameString = taxName.getPlainString();
				
				//	could not generate string, proceed
				if (taxNameString == null)
					this.errors.add(new UploadStringError("Could not generate taxonomic name string", taxName.getSource()));
				else {
					taxNameString = escaper.unescape(taxNameString.trim());
					if (taxNameString.startsWith("ERROR:")) {
						taxName.setError(taxNameString.substring("ERROR:".length()));
						this.errors.add(new UploadStringError(taxName.getError(), taxName.getSource()));
					}
					else {
						this.usValid++;
						this.next = new UploadString(taxNameString, TaxonomicNameUtils.toDwcXml(taxName));
					}
				}
				
				//	recurse
				return this.hasNextUploadString();
			}
		};
	}
	
	/**
	 * Iterator over upload strings returned by the parseNames() method, keeping
	 * track of both good and erroneous strings.
	 * 
	 * @author sautter
	 */
	public static interface UploadStringIterator {
		
		/**
		 * @return the number of valid upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getValidCount();
		
		/**
		 * @return the number of erroneous upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getErrorCount();
		
		/**
		 * @return the erroneous upload strings
		 */
		public abstract UploadStringError[] getErrors();
		
		/**
		 * @return the total number of upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getTotalCount();
		
		/**
		 * Retrieve an estimate of the number of upload strings yet to come.
		 * This method is to at least vaguely gauge progress with data formats
		 * that read the entire input data before returning any taxonomic names.
		 * If an estimate is not available, e.g. in data formats that really
		 * stream their input, this method should return -1.
		 * @return an estimate of the number of upload strings yet to come.
		 */
		public abstract int estimateRemaining();
		
		/**
		 * @return the next upload string
		 */
		public abstract UploadString nextUploadString() throws IOException;
		
		/**
		 * @return true if there are more upload strings to retrieve, false otherwise
		 */
		public abstract boolean hasNextUploadString() throws IOException;
	}
	
	/**
	 * Container for erroneous input data and associated error message.
	 * 
	 * @author sautter
	 */
	public static class UploadStringError {
		
		/** the error message */
		public final String error;
		
		/** the erroneous data */
		public final String source;
		
		public UploadStringError(String error, String source) {
			this.error = error;
			this.source = source;
		}
	}
	
	private static final Grammar escaper = new StandardGrammar();
}