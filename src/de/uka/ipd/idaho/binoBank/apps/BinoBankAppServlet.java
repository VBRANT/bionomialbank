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
package de.uka.ipd.idaho.binoBank.apps;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import de.uka.ipd.idaho.binoBank.BinoBankClient;
import de.uka.ipd.idaho.binoBank.BinoBankRestClient;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet;

/**
 * Utility class for servlets building on BinoBank, centrally providing
 * initialization facilities and, optionally, periodical updates.
 * 
 * @author sautter
 */
public class BinoBankAppServlet extends StringPoolAppServlet {
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		this.bbc = null;
	}
	
	/**
	 * Retrieve a client object to communicate with the backing BinoBank node.
	 * This may either be a REST client, or the BinoBank servlet itself. The
	 * latter happens if (a) that servlet is in the same web application as the
	 * one this method belongs to, and (b) has been created.
	 * @return a client object to communicate with the backing BinoBank node
	 */
	protected BinoBankClient getBinoBankClient() {
		
		//	we have the local connection, go with it
		if (this.bbc instanceof Servlet)
			return this.bbc;
		
		//	try switching to local connection if possible
		if (this.stringPoolNodeName != null) {
			Servlet s = WebAppHost.getInstance(this.getServletContext()).getServlet(this.stringPoolNodeName);
			if (s instanceof BinoBankClient) {
				System.out.println("BinoBankAppServlet: found local BinoBank node");
				this.bbc = ((BinoBankClient) s);
				return this.bbc;
			}
		}
		
		//	instantiate remote connection if not done before
		if (this.bbc == null) {
			System.out.println("BinoBankAppServlet: connecting to BinoBank node via REST");
			this.bbc = new BinoBankRestClient(this.stringPoolNodeUrl);
		}
		
		//	finally ...
		return this.bbc;
	}
	private BinoBankClient bbc = null;
}