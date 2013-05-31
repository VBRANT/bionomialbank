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
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;

/**
 * Taxonomic name data format for reading parsed taxonomic names from tab
 * delimited text files. Column names can be DarwinCore, Simple DarwinCore, or
 * the generic names defined in TaxonomicNameConstants.
 * 
 * @author sautter
 */
public class TabTextNameDataFormat extends NameDataFormat {
	
	/** Constructor passing 'TabTXT' to super class
	 */
	public TabTextNameDataFormat() {
		super("TabTXT");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getLabel()
	 */
	public String getLabel() {
		return "Tab Delimited Text";
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
		return "<html>This format parses atomized taxonomic names provided tab delimited text format.<br/>" +
				"The column headers can be in DarwinCore or Simple DarwinCore, supplemented DarwinCore Additional Ranks,<br/>" +
				"or using the generic attribute names defined by the constants in<br/>" +
				"<a href=\"http://code.google.com/p/idaho-extensions/source/browse/src/de/uka/ipd/idaho/plugins/taxonomicNames/TaxonomicNameConstants.java\">de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants.java</a></html>";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getFileExtensions()
	 */
	public String[] getFileExtensions() {
		String[] fes = {"txt"};
		return fes;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedTaxonomicNameIterator streamParse(Reader in) throws IOException {
		final BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		
		//	get and normalize keys
		String keyLine = br.readLine();
		while ((keyLine != null) && (keyLine.length() == 0))
			keyLine = br.readLine();
		if (keyLine == null)
			return new ParsedTaxonomicNameIterator() {
				public int estimateRemaining() {return -1;}
				public boolean hasNextName() throws IOException {return false;}
				public ParsedTaxonomicName nextName() throws IOException {return null;}
			};
		final String[] keys = keyLine.split("\\t");
		for (int k = 0; k < keys.length; k++)
			keys[k] = rankNameMappings.getProperty(keys[k], keys[k]);
		
		//	get ranks
		final Rank[] ranks = rankSystem.getRanks();
		
		//	parse data
		return new ParsedTaxonomicNameIterator() {
			private ParsedTaxonomicName next;
			public int estimateRemaining() {
				return -1;
			}
			public boolean hasNextName() throws IOException {
				while (this.next == null) {
					String dataLine = br.readLine();
					if (dataLine == null) {
						br.close();
						return false;
					}
					
					//	parse data line
					String[] dataLineParts = dataLine.split("\\t");
					Properties data = new Properties();
					for (int d = 0; (d < dataLineParts.length) && (d < keys.length); d++) {
						if (dataLineParts[d].length() != 0)
							data.setProperty(keys[d], dataLineParts[d]);
					}
					
					//	get epithets
					ParsedTaxonomicName taxName = new ParsedTaxonomicName(rankSystem);
					for (int r = 0; r < ranks.length; r++) {
						String epithet = data.getProperty(ranks[r].name);
						if (epithet != null)
							taxName.setEpithet(ranks[r].name, epithet);
					}
					
					//	check what we got
					if (taxName.getRank() != null) {
					
						//	get authority
						String authName = data.getProperty(AUTHORITY_ATTRIBUTE);
						if (authName == null)
							authName = data.getProperty(AUTHORITY_NAME_ATTRIBUTE);
						String authYear = data.getProperty(AUTHORITY_YEAR_ATTRIBUTE);
						if (authName != null) {
							taxName.setAuthorityName(authName);
							if ((authYear != null) && (authName.indexOf(authYear) == -1)) try {
								taxName.setAuthorityYear(Integer.parseInt(authYear));
							} catch (NumberFormatException nfe) {}
						}
						
						//	store what we got
						taxName.setSource(dataLine);
						this.next = taxName;
					}
				}
				return true;
			}
			public ParsedTaxonomicName nextName() throws IOException {
				ParsedTaxonomicName next = this.next;
				this.next = null;
				return next;
			}
		};
	}
}