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
import java.net.URLEncoder;

import de.uka.ipd.idaho.onn.stringPool.StringPoolRestClient;

/**
 * BinoBank specific REST client, adding detail search for taxonomic names.
 * 
 * @author sautter
 */
public class BinoBankRestClient extends StringPoolRestClient implements BinoBankConstants, BinoBankClient {
	
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
	
	/**
	 * Constructor
	 * @param baseUrl the URL of the BinoBank node to connect to
	 */
	public BinoBankRestClient(String baseUrl) {
		super(baseUrl);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.BinoBankClient#findNames(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int)
	 */
	public PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank, int limit) {
		return this.findNames(textPredicates, disjunctive, user, higher, family, genus, species, authority, rank, false, limit);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.BinoBankClient#findNames(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean, int)
	 */
	public PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank, boolean concise, int limit) {
		try {
			StringBuffer detailPredicates = new StringBuffer();
			if (higher != null)
				detailPredicates.append("&" + HIGHER_RANK_GROUP_PARAMETER + "=" + URLEncoder.encode(higher, ENCODING));
			if (family != null)
				detailPredicates.append("&" + FAMILY_RANK_GROUP_PARAMETER + "=" + URLEncoder.encode(family, ENCODING));
			if (genus != null)
				detailPredicates.append("&" + GENUS_RANK_GROUP_PARAMETER + "=" + URLEncoder.encode(genus, ENCODING));
			if (species != null)
				detailPredicates.append("&" + SPECIES_RANK_GROUP_PARAMETER + "=" + URLEncoder.encode(species, ENCODING));
			if (authority != null)
				detailPredicates.append("&" + AUTHORITY_PARAMETER + "=" + URLEncoder.encode(authority, ENCODING));
			return this.findStrings(textPredicates, disjunctive, rank, user, concise, limit, false, detailPredicates.toString());
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
}