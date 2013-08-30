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

import de.uka.ipd.idaho.onn.stringPool.StringPoolClient;

/**
 * BinoBank specific client object, adding detail search for taxonomic names.
 * 
 * @author sautter
 */
public interface BinoBankClient extends StringPoolClient {
	
	/**
	 * Search for taxonomic names, using both full text and detail predicates.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param user the name of the user to contribute or last update the
	 *            names
	 * @param higher taxonomic epithet above family level to search for
	 * @param family taxonomic epithet between family (inclusive) and genus (exclusive)
	 * @param genus taxonomic epithet between genus (inclusive) and species (exclusive)
	 * @param species taxonomic epithet at species level or below
	 * @param authority authority of taxonomic names (author and year)
	 * @param rank rank of taxonomic names to find
	 * @param limit the maximum number of names to include in the result (0 means no limit)
	 * @return an iterator over the references matching the query
	 */
	public abstract PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank, int limit);
	
	/**
	 * Search for taxonomic names, using both full text and detail predicates.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param user the name of the user to contribute or last update the
	 *            names
	 * @param higher taxonomic epithet above family level to search for
	 * @param family taxonomic epithet between family (inclusive) and genus (exclusive)
	 * @param genus taxonomic epithet between genus (inclusive) and species (exclusive)
	 * @param species taxonomic epithet at species level or below
	 * @param authority authority of taxonomic names (author and year)
	 * @param rank rank of taxonomic names to find
	 * @param concise obtain a concise result, i.e., without parses?
	 * @param limit the maximum number of names to include in the result (0 means no limit)
	 * @return an iterator over the references matching the query
	 */
	public abstract PooledStringIterator findNames(String[] textPredicates, boolean disjunctive, String user, String higher, String family, String genus, String species, String authority, String rank, boolean concise, int limit);
}