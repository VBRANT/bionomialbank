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

import java.io.IOException;
import java.io.Reader;

import de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.stringUtils.StringVector;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringRelation;
import de.uka.ipd.idaho.stringUtils.csvHandler.StringTupel;

/**
 * Taxonomic name data format for reading parsed taxonomic names from CSV
 * formatted tables. Column names can be DarwinCore, Simple DarwinCore, or the
 * generic names defined in TaxonomicNameConstants. Delimiters can be commas or
 * semicolons (used by Microsoft Excel).
 * 
 * @author sautter
 */
public class CsvNameDataFormat extends NameDataFormat {
	
	/** Constructor passing 'CSV' to super class
	 */
	public CsvNameDataFormat() {
		super("CSV");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getLabel()
	 */
	public String getLabel() {
		return "CSV Table";
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
		return "<html>This format parses atomized taxonomic names provided in CSV format.<br/>" +
				"The column headers can be in DarwinCore or Simple DarwinCore, supplemented DarwinCore Additional Ranks,<br/>" +
				"or using the generic attribute names defined by the constants in<br/>" +
				"<a href=\"http://code.google.com/p/idaho-extensions/source/browse/src/de/uka/ipd/idaho/plugins/taxonomicNames/TaxonomicNameConstants.java\">de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants.java</a></html>";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#getFileExtensions()
	 */
	public String[] getFileExtensions() {
		String[] fes = {"csv"};
		return fes;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedTaxonomicNameIterator streamParse(Reader in) throws IOException {
		
		//	read data
		final StringRelation data = StringRelation.readCsvData(in, StringRelation.GUESS_SEPARATOR, '"', true, null);
		
		//	get ranks
		final Rank[] ranks = rankSystem.getRanks();
		
		//	wrap data in iterator
		return new ParsedTaxonomicNameIterator() {
			private int index = 0;
			private ParsedTaxonomicName next;
			public int estimateRemaining() {
				return (data.size() - this.index);
			}
			public boolean hasNextName() throws IOException {
				while (this.next == null) {
					if (this.index >= data.size())
						return false;
					StringTupel st = data.get(this.index++);
					StringVector keys = st.getKeys();
					
					//	normalize keys
					for (int k = 0; k < keys.size(); k++) {
						String key = keys.get(k);
						String nKey = rankNameMappings.getProperty(key, key);
						if (!key.equals(nKey))
							st.renameKey(key, nKey);
					}
					
					//	get epithets
					ParsedTaxonomicName taxName = new ParsedTaxonomicName(rankSystem);
					for (int r = 0; r < ranks.length; r++) {
						String epithet = st.getValue(ranks[r].name);
						if (epithet != null)
							taxName.setEpithet(ranks[r].name, epithet);
					}
					
					//	check what we got
					if (taxName.getRank() != null) {
					
						//	get authority
						String authName = st.getValue(AUTHORITY_ATTRIBUTE);
						if (authName == null)
							authName = st.getValue(AUTHORITY_NAME_ATTRIBUTE);
						String authYear = st.getValue(AUTHORITY_YEAR_ATTRIBUTE);
						if (authName != null) {
							taxName.setAuthorityName(authName);
							if ((authYear != null) && (authName.indexOf(authYear) == -1)) try {
								taxName.setAuthorityYear(Integer.parseInt(authYear));
							} catch (NumberFormatException nfe) {}
						}
						
						//	store what we got
						taxName.setSource(st.toCsvString(',', '"', keys));
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