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
package de.uka.ipd.idaho.binoBank.apps.dataIndex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.binoBank.BinoBankClient;
import de.uka.ipd.idaho.binoBank.apps.BinoBankAppServlet;
import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils.TaxonomicName;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.RankGroup;
import de.uka.ipd.idaho.stringUtils.StringIterator;

/**
 * Servlet providing data from individual fields of references in concise form.
 * 
 * @author sautter
 */
public class BinoBankDataIndexServlet extends BinoBankAppServlet implements BinoBankDataIndexConstants {
	
	private static class CountingStringSet {
		private TreeMap content = new TreeMap();
//		private int size = 0;
		
		CountingStringSet() {}
		
		StringIterator getIterator() {
			final Iterator it = this.content.keySet().iterator();
			return new StringIterator() {
				public boolean hasNext() {
					return it.hasNext();
				}
				public Object next() {
					return it.next();
				}
				public void remove() {}
				public boolean hasMoreStrings() {
					return it.hasNext();
				}
				public String nextString() {
					return ((String) it.next());
				}
			};
		}
		
		boolean add(String string) {
			Int i = ((Int) this.content.get(string));
//			this.size++;
			if (i == null) {
				this.content.put(string, new Int(1));
				return true;
			}
			else {
				i.increment();
				return false;
			}
		}
		
		boolean remove(String string) {
			Int i = ((Int) this.content.get(string));
			if (i == null)
				return false;
//			this.size--;
			if (i.intValue() > 1) {
				i.decrement();
				return false;
			}
			else {
				this.content.remove(string);
				return true;
			}
		}
		
		private class Int {
			private int value;
			Int(int val) {
				this.value = val;
			}
			int intValue() {
				return this.value;
			}
			void increment() {
				this.value ++;
			}
			void decrement() {
				this.value --;
			}
		}
	}
	
	private HashMap epithetsByRank = new HashMap();
	
	
	private static final String NAME_ID_COLUMN_NAME = "NameId";
	private static final String NAME_ID_HASH_COLUMN_NAME = "IdHash"; // int hash of the ID string, speeding up joins with index table
	
	private static final String EPITHET_TABLE_NAME = "BinoBankEpithets";
	private static final String EPITHET_COLUMN_NAME = "Epithet";
	private static final int EPITHET_COLUMN_LENGTH = 60;
	private static final String RANK_COLUMN_NAME = "Rank";
	private static final int RANK_COLUMN_LENGTH = 32;
	
	private IoProvider io;
	
	private long lastReceived = 0;
	
	private TaxonomicRankSystem rankSystem;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get generic rank system (we'll be handling names from all domains)
		this.rankSystem = TaxonomicRankSystem.getRankSystem(null);
		
		//	get timestamp of last received update
		try {
			this.lastReceived = Long.parseLong(this.getSetting("lastReceived", ("" + this.lastReceived)));
		} catch (NumberFormatException nfe) {}
		 catch (Exception e) { /* TODO remove this after test */ }
		
		// get and check database connection
		this.io = WebAppHost.getInstance(this.getServletContext()).getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("RefBankDataIndex: Cannot work without database access.");
		
		//	produce epithet table
		TableDefinition ptd = new TableDefinition(EPITHET_TABLE_NAME);
		ptd.addColumn(NAME_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		ptd.addColumn(NAME_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		ptd.addColumn(EPITHET_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, EPITHET_COLUMN_LENGTH);
		ptd.addColumn(RANK_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, RANK_COLUMN_LENGTH);
		if (!this.io.ensureTable(ptd, true))
			throw new RuntimeException("BinoBankDataIndex: Cannot work without database access.");
		
		this.io.indexColumn(EPITHET_TABLE_NAME, NAME_ID_COLUMN_NAME);
		this.io.indexColumn(EPITHET_TABLE_NAME, NAME_ID_HASH_COLUMN_NAME);
		
		//	fill in-memory data structures
		String query = null;
		SqlQueryResult sqr;
		
		//	load epithets
		try {
			query = "SELECT " + EPITHET_COLUMN_NAME + ", " + RANK_COLUMN_NAME + " FROM " + EPITHET_TABLE_NAME + ";";
			sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				CountingStringSet epithets = ((CountingStringSet) this.epithetsByRank.get(sqr.getString(1)));
				if (epithets == null) {
					epithets = new CountingStringSet();
					this.epithetsByRank.put(sqr.getString(1), epithets);
				}
				epithets.add(sqr.getString(0));
			}
		}
		catch (SQLException sqle) {
			System.out.println("BinoBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading persons.");
			System.out.println("  query was " + query);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		this.setSetting("lastReceived", ("" + this.lastReceived));
		super.exit();
		this.io.close();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#fetchUpdates()
	 */
	protected boolean fetchUpdates() {
		return true; // we do want periodical updates
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#fetchUpdates(long)
	 */
	protected void fetchUpdates(long lastLookup) throws IOException {
		
		//	retrieve BinoBank client on the fly to use local bridge if possible
		BinoBankClient bbc = this.getBinoBankClient();
		
		//	we have to catch this, as super class might start update thread before we have the client instantiated
		if (bbc == null)
			return;
		
		//	read update feed
		long timestampThreshold = Math.max((this.lastReceived - 999), 1);
		LinkedList updateIdList = new LinkedList();
		System.out.println("Fetching updates since " + timestampThreshold + " from " + this.stringPoolNodeUrl);
		PooledStringIterator pbri = bbc.getStringsUpdatedSince(timestampThreshold);
		while (pbri.hasNextString()) {
			PooledString ps = pbri.getNextString();
			if (ps.getParseChecksum() != null) {
				updateIdList.addLast(ps.id);
				this.lastReceived = Math.max(this.lastReceived, ps.getNodeUpdateTime());
			}
		}
		System.out.println("Got " + updateIdList.size() + " updates");
		
		//	get ranks
		Rank[] ranks = this.rankSystem.getRanks();
		
		//	do updates
		while (updateIdList.size() != 0) {
			String updateId = ((String) updateIdList.removeFirst());
			PooledString ps = bbc.getString(updateId);
			if (ps == null)
				continue;
			else if ("AutomatedParser".equals(ps.getUpdateUser()))
				continue;
			
			//	parse XML name
			MutableAnnotation nameParsed = SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed()));
			System.out.println("Got parsed name for ID " + updateId);
			
			//	get and store attributes
			TaxonomicName taxName = TaxonomicNameUtils.dwcXmlToTaxonomicName(nameParsed, this.rankSystem);
			this.storeEpithets(taxName, ranks, updateId);
		}
	}
	
	private void storeEpithets(TaxonomicName taxName, Rank[] ranks, String nameId) {
		HashMap toRemove = new HashMap();
		HashMap toAdd = new HashMap();
		for (int r = 0; r < ranks.length; r++) {
			String epithet = taxName.getEpithet(ranks[r].name);
			if (epithet != null)
				toAdd.put(ranks[r].name, epithet);
		}
		
		//	diff with database
		String query = null;
		try {
			query = "SELECT " + EPITHET_COLUMN_NAME + ", " + RANK_COLUMN_NAME +
					" FROM " + EPITHET_TABLE_NAME + 
					" WHERE " + NAME_ID_HASH_COLUMN_NAME + " = " + nameId.hashCode() +
						" AND " + NAME_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(nameId) + "'" +
					";";
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				String epithet = sqr.getString(0);
				String rank = sqr.getString(1);
				if (epithet.equals(toAdd.get(rank)))
					toAdd.remove(rank);
				else toRemove.put(rank, epithet);
			}
		}
		catch (SQLException sqle) {
			System.out.println("BinoBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading existing epithets for name '" + nameId + "'.");
			System.out.println("  query was " + query);
		}
		
		//	remove persons
		if (toRemove.size() != 0) {
			StringBuffer rRenkWhere = new StringBuffer();
			for (Iterator rit = toRemove.keySet().iterator(); rit.hasNext();) {
				String rRank = ((String) rit.next());
				if (rRenkWhere.length() != 0)
					rRenkWhere.append(", ");
				rRenkWhere.append("'" + EasyIO.sqlEscape(rRank) + "'");
				CountingStringSet rEpithets = ((CountingStringSet) this.epithetsByRank.get(rRank));
				if (rEpithets != null)
					rEpithets.remove((String) toRemove.get(rRank));
			}
			try {
				query = "DELETE FROM " + EPITHET_TABLE_NAME + 
						" WHERE " + NAME_ID_HASH_COLUMN_NAME + " = " + nameId.hashCode() +
							" AND " + NAME_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(nameId) + "'" +
							" AND " + RANK_COLUMN_NAME + " IN (" + rRenkWhere.toString() + ")" +
						";";
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				System.out.println("BinoBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while removing epithets for name '" + nameId + "'.");
				System.out.println("  query was " + query);
			}
		}
		
		//	add persons
		if (toAdd.size() != 0)
			for (Iterator ait = toAdd.keySet().iterator(); ait.hasNext();) {
				String aRank = ((String) ait.next());
				String aEpithet = ((String) toAdd.get(aRank));
				CountingStringSet aEpithets = ((CountingStringSet) this.epithetsByRank.get(aRank));
				if (aEpithets == null) {
					aEpithets = new CountingStringSet();
					this.epithetsByRank.put(aRank, aEpithets);
				}
				aEpithets.add(aEpithet);
				try {
					query = "INSERT INTO " + EPITHET_TABLE_NAME + " (" + 
							NAME_ID_COLUMN_NAME + ", " + NAME_ID_HASH_COLUMN_NAME + ", " + EPITHET_COLUMN_NAME + ", " + RANK_COLUMN_NAME + 
							") VALUES (" +
							"'" + EasyIO.sqlEscape(nameId) + "', " + nameId.hashCode() + ", '" + EasyIO.sqlEscape(aEpithet) + "', '" + EasyIO.sqlEscape(aRank) + "'" +
							");";
					this.io.executeUpdateQuery(query);
				}
				catch (SQLException sqle) {
					System.out.println("BinoBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while adding epithets for name '" + nameId + "'.");
					System.out.println("  query was " + query);
				}
			}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Rank rank = this.rankSystem.getRank(request.getParameter(RANK_ATTRIBUTE));
		if (rank == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		CountingStringSet[] epithets;
		if (RANK_GROUP_MODE.equals(request.getParameter(MODE_PARAMETER))) {
			ArrayList groupEpithets = new ArrayList();
			RankGroup group = rank.getRankGroup();
			Rank[] ranks = group.getRanks();
			for (int r = 0; r < ranks.length; r++) {
				CountingStringSet rankEpithets = ((CountingStringSet) this.epithetsByRank.get(ranks[r].name));
				if (rankEpithets != null)
					groupEpithets.add(rankEpithets);
			}
			epithets = ((CountingStringSet[]) groupEpithets.toArray(new CountingStringSet[groupEpithets.size()]));
		}
		else {
			CountingStringSet rankEpithets = ((CountingStringSet) this.epithetsByRank.get(rank.name));
			if (rankEpithets == null)
				epithets = new CountingStringSet[0];
			else {
				epithets = new CountingStringSet[1];
				epithets[0] = rankEpithets;
			}
		}
		
		response.setContentType("text/plain");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		for (int s = 0; s < epithets.length; s++)
			for (StringIterator eit = epithets[s].getIterator(); eit.hasMoreStrings();) {
				bw.write(eit.nextString());
				bw.newLine();
			}
		bw.flush();
	}
}