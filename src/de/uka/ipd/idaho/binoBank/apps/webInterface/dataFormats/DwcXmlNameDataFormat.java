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
package de.uka.ipd.idaho.binoBank.apps.webInterface.dataFormats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

import de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Taxonomic name data format for reading parsed taxonomic names from DarwinCore
 * XML files. The name of the container element wrapping individual taxonomic
 * names should be specified as the <code>taxonNameElement</code> attribute of
 * the root element if it deviates from <code>taxonomicName</code>.
 * 
 * @author sautter
 */
public class DwcXmlNameDataFormat extends NameDataFormat {
	
	/** Constructor passing 'DwcXML' to super class
	 */
	public DwcXmlNameDataFormat() {
		super("DwcXML");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getLabel()
	 */
	public String getLabel() {
		return "DarwinCore XML";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#isParseUserApproved()
	 */
	public boolean isParseUserApproved() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getInputDescription()
	 */
	public String getDescription() {
		return "<html>This format parses atomized taxonomic names provided in XML format.<br/>" +
				"The detail element names can be in DarwinCore or Simple DarwinCore, supplemented DarwinCore Additional Ranks,<br/>" +
				"or the generic names defined (even though as attributes) by the constants in<br/>" +
				"<a href=\"http://code.google.com/p/idaho-extensions/source/browse/src/de/uka/ipd/idaho/plugins/taxonomicNames/TaxonomicNameConstants.java\">de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants.java</a><br/>" +
				"The name of the container element delimiting individual taxonomic names can be specified in the <code>taxonNameElement</code><br/>" +
				"attribute of the root element; if lacking, it defaults to <code>taxonomicName</code>.<br/>" +
				"A root element for the entire upload file has to be present, but its name does not matter.</html>";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getFileExtensions()
	 */
	public String[] getFileExtensions() {
		String[] fes = {"xml"};
		return fes;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedTaxonomicNameIterator streamParse(Reader in) throws IOException {
		
		//	wrap reader
		final BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		
		//	parse in separate thread
		final ParserThread parserThread = new ParserThread(br);
		parserThread.start();
		
		//	parse data
		return new ParsedTaxonomicNameIterator() {
			private ParsedTaxonomicName next;
			public int estimateRemaining() {
				return -1;
			}
			public boolean hasNextName() throws IOException {
				if ((this.next == null) && parserThread.isAlive())
					this.next = parserThread.getTaxName();
				else if (parserThread.hasException())
					throw parserThread.getException();
				return (this.next != null);
			}
			public ParsedTaxonomicName nextName() throws IOException {
				if (parserThread.hasException())
					throw parserThread.getException();
				ParsedTaxonomicName next = this.next;
				this.next = null;
				return next;
			}
		};
	}
	private static final Grammar xml = new StandardGrammar();
	private static final Parser parser = new Parser(xml);
	
	private static class ParserThread extends Thread {
		private BufferedReader br;
		private ParsedTaxonomicName taxName;
		private IOException ioe;
		private Rank[] ranks = rankSystem.getRanks();
		ParserThread(BufferedReader br) {
			this.br = br;
		}
		public void run() {
			synchronized(this) {
				this.notify();
			}
			try {
				parser.stream(this.br, new TokenReceiver() {
					String taxNameContainer = null;
					boolean inTaxName = false;
					String taxNameElement;
					Properties taxNameElements = new Properties();
					StringVector taxNameSource = new StringVector();
					public void storeToken(String token, int treeDepth) throws IOException {
						if (xml.isTag(token)) {
							String type = xml.getType(token);
							
							//	first tag, get container element name
							if (this.taxNameContainer == null) {
								TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, xml);
								this.taxNameContainer = tnas.getAttribute("taxonNameElement", TAXONOMIC_NAME_ANNOTATION_TYPE);
								return;
							}
							
							//	start or end of taxon name
							if (this.taxNameContainer.equals(type)) {
								this.taxNameSource.addElement(token);
								if (xml.isEndTag(token)) {
									
									//	get epithets
									ParsedTaxonomicName taxName = new ParsedTaxonomicName(rankSystem);
									for (int r = 0; r < ranks.length; r++) {
										String epithet = this.taxNameElements.getProperty(ranks[r].name);
										if (epithet != null)
											taxName.setEpithet(ranks[r].name, epithet);
									}
									
									//	check what we got
									if (taxName.getRank() != null) {
									
										//	get authority
										String authName = this.taxNameElements.getProperty(AUTHORITY_ATTRIBUTE);
										if (authName == null)
											authName = this.taxNameElements.getProperty(AUTHORITY_NAME_ATTRIBUTE);
										String authYear = this.taxNameElements.getProperty(AUTHORITY_YEAR_ATTRIBUTE);
										if (authName != null) {
											taxName.setAuthorityName(authName);
											if ((authYear != null) && (authName.indexOf(authYear) == -1)) try {
												taxName.setAuthorityYear(Integer.parseInt(authYear));
											} catch (NumberFormatException nfe) {}
										}
										
										//	store what we got
										taxName.setSource(this.taxNameSource.concatStrings(""));
										setTaxName(taxName);
									}
									
									//	clean up
									this.inTaxName = false;
									this.taxNameElements.clear();
									this.taxNameSource.clear();
								}
								else this.inTaxName = true;
							}
							
							//	taxon name data element
							else if (this.inTaxName) {
								this.taxNameSource.addElement(token);
								if (xml.isEndTag(token))
									this.taxNameElement = null;
								else this.taxNameElement = rankNameMappings.getProperty(type, type);
							}
						}
						else {
							this.taxNameSource.addElement(token);
							if (this.taxNameElement != null)
								this.taxNameElements.setProperty(this.taxNameElement, token.trim());
						}
					}
					public void close() throws IOException {}
				});
			}
			catch (IOException ioe) {
				this.ioe = ioe;
			}
			finally {
				try {
					this.br.close();
				} catch (IOException ioe) {}
			}
		}
		public synchronized void start() {
			super.start();
			try {
				this.wait();
			} catch (InterruptedException ie) {}
		}
		synchronized ParsedTaxonomicName getTaxName() throws IOException {
			
			//	wake up to parse next name, and block requester in the meantime
			if (taxName == null) {
				this.notify();
				try {
					this.wait();
				} catch (InterruptedException ie) {}
			}
			
			//	throw exception if we have encountered one
			if (this.hasException())
				throw this.getException();
			
			//	return whatever we got
			return this.taxName;
		}
		synchronized void setTaxName(ParsedTaxonomicName taxName) {
			this.taxName = taxName;
			
			//	wake up requester blocked on getTaxName(), then block
			this.notify();
			try {
				this.wait();
			} catch (InterruptedException ie) {}
		}
		IOException getException() {
			return this.ioe;
		}
		boolean hasException() {
			return (this.ioe != null);
		}
	}
}