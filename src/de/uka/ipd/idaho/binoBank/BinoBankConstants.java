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

import de.uka.ipd.idaho.onn.stringPool.StringPoolConstants;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants;

/**
 * @author sautter
 *
 */
public interface BinoBankConstants extends StringPoolConstants, TaxonomicNameConstants {
	
	public static final String BBK_XML_NAMESPACE = null;//"http://idaho.ipd.uka.de/sp/schema";
	public static final String BBK_XML_NAMESPACE_ATTRIBUTE = ((BBK_XML_NAMESPACE == null) ? "" : (" xmlns:bbk=\"" + BBK_XML_NAMESPACE + "\""));
	public static final String BBK_XML_NAMESPACE_PREFIX = ((BBK_XML_NAMESPACE == null) ? "" : "bbk:");
	
	public static final String NAME_SET_NODE_TYPE = (BBK_XML_NAMESPACE_PREFIX + "nameSet");
	public static final String NAME_NODE_TYPE = (BBK_XML_NAMESPACE_PREFIX + "name");
	public static final String NAME_PLAIN_NODE_TYPE = (BBK_XML_NAMESPACE_PREFIX + "nameString");
	public static final String NAME_PARSED_NODE_TYPE = (BBK_XML_NAMESPACE_PREFIX + "nameParsed");
	
	public static final String HIGHER_RANK_GROUP_PARAMETER = "higher";
	public static final String FAMILY_RANK_GROUP_PARAMETER = "family";
	public static final String GENUS_RANK_GROUP_PARAMETER = "genus";
	public static final String SPECIES_RANK_GROUP_PARAMETER = "species";
	public static final String AUTHORITY_PARAMETER = "authority";
	public static final String RANK_PARAMETER = RANK_ATTRIBUTE;
	
	public static final String DARWIN_CORE_FORMAT = "DwC";
	public static final String SIMPLE_DARWIN_CORE_FORMAT = "SimpleDwC";
}