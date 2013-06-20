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
package de.uka.ipd.idaho.binoBank;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;

import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.onn.stringPool.StringPoolServlet;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils.TaxonomicName;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.RankGroup;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * @author sautter
 *
 */
public class BinoBankServlet extends StringPoolServlet implements BinoBankClient, BinoBankConstants {
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getExternalDataName()
	 */
	protected String getExternalDataName() {
		return "TaxonomicName";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getNamespaceAttribute()
	 */
	public String getNamespaceAttribute() {
		return BBK_XML_NAMESPACE_ATTRIBUTE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringNodeType()
	 */
	public String getStringNodeType() {
		return NAME_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringParsedNodeType()
	 */
	public String getStringParsedNodeType() {
		return NAME_PARSED_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringPlainNodeType()
	 */
	public String getStringPlainNodeType() {
		return NAME_PLAIN_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringSetNodeType()
	 */
	public String getStringSetNodeType() {
		return NAME_SET_NODE_TYPE;
	}
	
	private static final String HIGHER_RANK_GROUP_COLUMN_NAME = "Higher";
	private static final String FAMILY_RANK_GROUP_COLUMN_NAME = "Family";
	private static final int HIGHER_FAMILY_COLUMN_LENGTH = 32;
	private static final String GENUS_RANK_GROUP_COLUMN_NAME = "Genus";
	private static final String SPECIES_RANK_GROUP_COLUMN_NAME = "Species";
	private static final int GENUS_SPECIES_COLUMN_LENGTH = 80;
	private static final String AUTHORITY_COLUMN_NAME = "Authority";
	private static final int AUTHORITY_COLUMN_LENGTH = 32;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#extendIndexTableDefinition(de.uka.ipd.idaho.easyIO.sql.TableDefinition)
	 */
	protected boolean extendIndexTableDefinition(TableDefinition itd) {
		itd.addColumn(HIGHER_RANK_GROUP_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, HIGHER_FAMILY_COLUMN_LENGTH);
		itd.addColumn(FAMILY_RANK_GROUP_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, HIGHER_FAMILY_COLUMN_LENGTH);
		itd.addColumn(GENUS_RANK_GROUP_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, GENUS_SPECIES_COLUMN_LENGTH);
		itd.addColumn(SPECIES_RANK_GROUP_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, GENUS_SPECIES_COLUMN_LENGTH);
		itd.addColumn(AUTHORITY_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, AUTHORITY_COLUMN_LENGTH);
		return true;
	}
	
	private TaxonomicRankSystem rankSystem;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get generic rank system (we'll be handling names from all domains)
		this.rankSystem = TaxonomicRankSystem.getRankSystem(null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#addIndexPredicates(javax.servlet.http.HttpServletRequest, java.util.Properties)
	 */
	protected void addIndexPredicates(HttpServletRequest request, Properties detailPredicates) {
		String higher = request.getParameter(HIGHER_RANK_GROUP_PARAMETER);
		if (higher != null)
			detailPredicates.setProperty(HIGHER_RANK_GROUP_COLUMN_NAME, higher);
		String family = request.getParameter(FAMILY_RANK_GROUP_PARAMETER);
		if (family != null)
			detailPredicates.setProperty(FAMILY_RANK_GROUP_COLUMN_NAME, family);
		String genus = request.getParameter(GENUS_RANK_GROUP_PARAMETER);
		if (genus != null)
			detailPredicates.setProperty(GENUS_RANK_GROUP_COLUMN_NAME, genus);
		String species = request.getParameter(SPECIES_RANK_GROUP_PARAMETER);
		if (species != null)
			detailPredicates.setProperty(SPECIES_RANK_GROUP_COLUMN_NAME, species);
		String authority = request.getParameter(AUTHORITY_PARAMETER);
		if (authority != null)
			detailPredicates.setProperty(AUTHORITY_COLUMN_NAME, authority);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#extendIndexData(de.uka.ipd.idaho.onn.stringPool.StringPoolServlet.ParsedStringIndexData, de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected void extendIndexData(ParsedStringIndexData indexData, MutableAnnotation stringParsed) {
		
		//	get index data
		NameIndexData refIndexData = this.getIndexData(stringParsed);
		
		//	add attributes
		indexData.addIndexAttribute(HIGHER_RANK_GROUP_COLUMN_NAME, refIndexData.higher.toLowerCase());
		indexData.addIndexAttribute(FAMILY_RANK_GROUP_COLUMN_NAME, refIndexData.family.toLowerCase());
		indexData.addIndexAttribute(GENUS_RANK_GROUP_COLUMN_NAME, refIndexData.genus.toLowerCase());
		indexData.addIndexAttribute(SPECIES_RANK_GROUP_COLUMN_NAME, refIndexData.species.toLowerCase());
		indexData.addIndexAttribute(AUTHORITY_COLUMN_NAME, refIndexData.authority.toLowerCase());
	}
	
	/*
<dwc:Taxon>
  <dwc:taxonID>urn:lsid:catalogueoflife.org:taxon:df0a797c-29c1-102b-9a4a-00304854f820:col20120721</dwc:taxonID>
  <dwc:parentNameUsageID>urn:lsid:catalogueoflife.org:taxon:d79c11aa-29c1-102b-9a4a-00304854f820:col20120721</dwc:parentNameUsageID>
  <dwc:scientificName>Ctenomys sociabilis</dwc:scientificName>
  <dwc:scientificNameAuthorship>Pearson and Christie, 1985</dwc:scientificNameAuthorship>
  <dwc:taxonRank>species</dwc:taxonRank>
  <dwc:nomenclaturalCode>ICZN</dwc:nomenclaturalCode>
  <dwc:higherClassification>Animalia; Chordata; Vertebrata; Mammalia; Theria; Eutheria; Rodentia; Hystricognatha; Hystricognathi; Ctenomyidae; Ctenomyini; Ctenomys</dwc:higherClassification>
  <dwc:kingdom>Animalia</dwc:kingdom>
  <dwc:phylum>Chordata</dwc:phylum>
  <dwc:class>Mammalia</dwc:class>
  <dwc:order>Rodentia</dwc:order>
  <dwc:family>Ctenomyidae</dwc:family>
  <dwc:genus>Ctenomys</dwc:genus>
  <dwc:specificEpithet>sociabilis</dwc:specificEpithet>
</dwc:Taxon>
	 */
	
	private static class NameIndexData {
		final String higher;
		final String family;
		final String genus;
		final String species;
		final String authority;
		NameIndexData(String higher, String family, String genus, String species, String authority) {
			this.higher = higher;
			this.family = family;
			this.genus = genus;
			this.species = species;
			this.authority = authority;
		}
	}
	
	private NameIndexData getIndexData(MutableAnnotation stringParsed) {
		
		//	get attributes
		TaxonomicName taxName = TaxonomicNameUtils.dwcXmlToTaxonomicName(stringParsed);
		
		//	what do we want to index?
		StringBuffer higher = new StringBuffer();
		StringBuffer family = new StringBuffer();
		StringBuffer genus = new StringBuffer();
		StringBuffer species = new StringBuffer();
		
		//	collect epithets
		RankGroup[] rankGroups = this.rankSystem.getRankGroups();
		for (int g = 0; g < rankGroups.length; g++) {
			StringBuffer target;
			if (SPECIES_ATTRIBUTE.equals(rankGroups[g].name))
				target = species;
			else if (GENUS_ATTRIBUTE.equals(rankGroups[g].name))
				target = genus;
			else if (FAMILY_ATTRIBUTE.equals(rankGroups[g].name))
				target = family;
			else target = higher;
			Rank[] ranks = rankGroups[g].getRanks();
			for (int r = 0; r < ranks.length; r++) {
				target.append('|');
				String epithet = taxName.getEpithet(ranks[r].name);
				if (epithet != null)
					target.append(epithet);
			}
		}
		
		//	get authority
		String authority = taxName.getAuthority();
		if (authority == null)
			authority = "";
		
		//	trim data
		if (higher.length() > HIGHER_FAMILY_COLUMN_LENGTH)
			higher.delete(HIGHER_FAMILY_COLUMN_LENGTH, higher.length());
		if (family.length() > HIGHER_FAMILY_COLUMN_LENGTH)
			family.delete(HIGHER_FAMILY_COLUMN_LENGTH, family.length());
		if (genus.length() > GENUS_SPECIES_COLUMN_LENGTH)
			genus.delete(GENUS_SPECIES_COLUMN_LENGTH, genus.length());
		if (species.length() > GENUS_SPECIES_COLUMN_LENGTH)
			species.delete(GENUS_SPECIES_COLUMN_LENGTH, species.length());
		if (authority.length() > AUTHORITY_COLUMN_LENGTH)
			authority = authority.substring(0, AUTHORITY_COLUMN_LENGTH);
		
		//	finally ...
		return new NameIndexData(higher.toString(), family.toString(), genus.toString(), species.toString(), authority);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#checkParsedString(java.lang.String, java.lang.String, de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected String checkParsedString(String stringId, String stringPlain, MutableAnnotation stringParsed) {
		String parseError = null;
		
		StringVector extraTokens = new StringVector();
		
		Annotation[] rankAnnots = stringParsed.getAnnotations("dwc:taxonRank");
		for (int r = 0; r < rankAnnots.length; r++)
			addTokens(extraTokens, rankAnnots[r]);
		
		for (int t = 0; (t < stringParsed.size()) && (parseError == null); t++) {
			String value = stringParsed.valueAt(t);
			if (stringPlain.indexOf(value) != -1)
				continue;
			if (extraTokens.contains(value))
				continue;
			parseError = ("Parsed string is inconsistent with plain string: '" + value + "' is not a part of '" + stringPlain + "'");
		}
		
		return parseError;
	}
	
	private static void addTokens(StringVector tokens, Annotation annot) {
		for (int t = 0; t < annot.size(); t++)
			tokens.addElement(annot.valueAt(t));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringType(de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected String getStringType(MutableAnnotation stringParsed) {
		Annotation[] rankAnnots = stringParsed.getAnnotations("dwc:taxonRank");
		return (((rankAnnots != null) && (rankAnnots.length != 0)) ? rankAnnots[0].getValue() : null);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.BinoBankClient#findNames(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank) {
		return this.findNames(textPredicates, disjunctive, user, higher, family, genus, species, authority, rank, false);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.BinoBankClient#findNames(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	public PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank, boolean concise) {
		Properties detailPredicates = new Properties();
		if (higher != null)
			detailPredicates.setProperty(HIGHER_RANK_GROUP_COLUMN_NAME, higher.toLowerCase());
		if (family != null)
			detailPredicates.setProperty(FAMILY_RANK_GROUP_COLUMN_NAME, family.toLowerCase());
		if (genus != null)
			detailPredicates.setProperty(GENUS_RANK_GROUP_COLUMN_NAME, genus.toLowerCase());
		if (species != null)
			detailPredicates.setProperty(SPECIES_RANK_GROUP_COLUMN_NAME, species.toLowerCase());
		if (authority != null)
			detailPredicates.setProperty(AUTHORITY_COLUMN_NAME, authority.toLowerCase());
		return this.findStrings(textPredicates, disjunctive, rank, user, concise, detailPredicates);
	}
	
	/**
	 * Overwrites ID generation to use UUID version 5 with 'globalnames.org' in
	 * the DNS namespace, also using an instance pool for increased performance.
	 * However, the UUIDs are converted to plain 32 character HEX strings to
	 * comply with the contract of this method.
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringId(java.lang.String)
	 */
	protected String getStringId(String string) throws IOException {
		NameBasedGenerator nbg = null;
		try {
			nbg = getUuidGenerator();
			UUID id = nbg.generate(string.getBytes("UTF-8"));
			String ids = id.toString().replaceAll("\\-", "").toUpperCase();
			return ids;
		}
		finally {
			returnUuidGenerator(nbg);
		}
	}
	
	private static UUID globalNamesInDns;
	private static LinkedList uuidGenerators = new LinkedList();
	private static NameBasedGenerator getUuidGenerator() throws IOException {
		synchronized (uuidGenerators) {
			if (uuidGenerators.size() != 0)
				return ((NameBasedGenerator) uuidGenerators.removeFirst());
			if (globalNamesInDns == null) {
				NameBasedGenerator nbg = Generators.nameBasedGenerator(NameBasedGenerator.NAMESPACE_DNS);
				globalNamesInDns = nbg.generate("globalnames.org");
			}
			return Generators.nameBasedGenerator(globalNamesInDns);
		}
	}
	private static void returnUuidGenerator(NameBasedGenerator nbg) {
		if (nbg == null)
			return;
		synchronized (uuidGenerators) {
			uuidGenerators.addLast(nbg);
		}
	}
	public static void main(String[] args) throws Exception {
		NameBasedGenerator nbg = getUuidGenerator();
		UUID id = nbg.generate("test".getBytes("UTF-8"));
//		UUID id = nbg.generate("Pardosa moesta Banks, 1892".getBytes("UTF-8"));
//		UUID id = nbg.generate("Pardosa moesta".getBytes("UTF-8"));
//		UUID id = nbg.generate("Pardosa moesta Paquin & Dupérré, 2003".getBytes("UTF-8"));
		String ids = id.toString();
		System.out.println(ids);
		System.out.println(ids.replaceAll("\\-", "").toUpperCase());
	}
}