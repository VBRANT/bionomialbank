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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;

/**
 * Client for the BinoBank data index.
 * 
 * @author sautter
 */
public class BinoBankDataIndexRestClient implements BinoBankDataIndexConstants {
	
	private String baseUrl;
	
	/**
	 * Constructor
	 * @param baseUrl the URL of the BinoBank data index to connect to
	 */
	public BinoBankDataIndexRestClient(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	public String[] getEpithets(String rank) throws IOException {
		return this.getEpithets(rank, false);
	}
	
	public String[] getEpithets(String rank, boolean group) throws IOException {
		URL dataUrl = new URL(this.baseUrl + "?" + MODE_PARAMETER + "=" + (group ? RANK_GROUP_MODE : RANK_MODE));
		LinkedList dataList = new LinkedList();
		BufferedReader br = new BufferedReader(new InputStreamReader(dataUrl.openStream(), ENCODING));
		String data;
		while ((data = br.readLine()) != null) {
			data = data.trim();
			if (data.length() != 0)
				dataList.addLast(data);
		}
		return ((String[]) dataList.toArray(new String[dataList.size()]));
	}
//	
//	public static void main(String[] args) throws Exception {
//		BinoBankDataIndexRestClient dirc = new BinoBankDataIndexRestClient("http://localhost:8080/gnubTest/data");
//		String[] species = dirc.getEpithets("species");
//		for (int s = 0; s < species.length; s++)
//			System.out.println(species[s]);
//	}
}