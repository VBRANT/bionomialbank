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
package de.uka.ipd.idaho.binoBank.apps.editor;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.binoBank.BinoBankClient;
import de.uka.ipd.idaho.binoBank.apps.BinoBankAppServlet;
import de.uka.ipd.idaho.easyIO.web.FormDataReceiver;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.stringUtils.StringUtils;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Name string editing facility for BinoBank.
 * 
 * @author sautter
 */
public class BinoBankEditorServlet extends BinoBankAppServlet {
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get ID
		final String nameId = request.getParameter(STRING_ID_ATTRIBUTE);
		if (nameId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid name ID");
			return;
		}
		
		//	get result URL
		final String resultUrlPrefix = request.getParameter("resultUrlPrefix");
		if (resultUrlPrefix == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid result URL");
			return;
		}
		
		//	get user name
		final String user = request.getParameter(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user name");
			return;
		}
		
		//	resolve ID
		final PooledString name = this.getBinoBankClient().getString(nameId);
		if (name == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid name ID: " + nameId));
			return;
		}
		
		//	send edit form
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		HtmlPageBuilder pageBuilder = new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeParsedName();
				else super.include(type, tag);
			}
			private void includeParsedName() throws IOException {
				this.writeLine("<form id=\"nameEditorForm\" method=\"POST\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\" accept-charset=\"utf8\" encrypt=\"application/x-www-form-urlencoded; charset=utf8\">");
				this.writeLine("<table class=\"editTable\">");
				
				this.writeLine("<input type=\"hidden\" name=\"sourceNameId\" value=\"" + nameId + "\" />");
				this.writeLine("<input type=\"hidden\" name=\"resultUrlPrefix\" value=\"" + resultUrlPrefix + "\" />");
				this.writeLine("<input type=\"hidden\" name=\"" + USER_PARAMETER + "\" value=\"" + user + "\" />");
				
				this.writeLine("<tr class=\"editTableRow\">");
				this.writeLine("<td class=\"editTableCell\">");
				this.write("<p class=\"nameString\">" + xmlGrammar.escape(name.getStringPlain()) + "</p>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("<tr class=\"editTableRow\">");
				this.writeLine("<td class=\"editTableCell\">");
				this.writeLine("<textarea name=\"nameString\" id=\"nameEditorField\">");
				this.writeLine(name.getStringPlain());
				this.writeLine("</textarea>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<input type=\"button\" id=\"edit\" class=\"nameFormatLink\" onclick=\"document.getElementById('nameEditorForm').submit();\" value=\"Confirm Edit\" />");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
				this.writeLine("</form>");
			}
			
			protected String getPageTitle(String title) {
				return "Edit Taxonomic Name";
			}
		};
		this.sendPopupHtmlPage(pageBuilder);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	use form data receiver to take control of character encoding
		FormDataReceiver data = FormDataReceiver.receive(request, Integer.MAX_VALUE, null, -1, new HashSet(1));
		
		//	get source name ID
		String sourceNameId = data.getFieldValue("sourceNameId");
		if (sourceNameId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid source name ID");
			return;
		}
		
		//	get result URL
		String resultUrlPrefix = data.getFieldValue("resultUrlPrefix");
		if (resultUrlPrefix == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid result URL");
			return;
		}
		
		//	get user name
		String user = data.getFieldValue(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user name");
			return;
		}
		
		//	get edited taxonomic name
		String nameString = new String(data.getFieldByteValue("nameString"), ENCODING);
		if (nameString == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid name string");
			return;
		}
		
		//	connect to BinoBank
		BinoBankClient bbk = this.getBinoBankClient();
		
		//	resolve ID
		PooledString sourceName = bbk.getString(sourceNameId);
		if (sourceName == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid source name ID: " + sourceNameId));
			return;
		}
		
		//	verify edited name against source name
		String srNoSpace = sourceName.getStringPlain().replaceAll("\\s+", "");
		String erNoSpace = nameString.replaceAll("\\s+", "");
		int avgLength = ((srNoSpace.length() + erNoSpace.length()) / 2);
		int estEditDist = StringUtils.estimateLevenshteinDistance(srNoSpace, erNoSpace);
		if ((estEditDist * 5) > avgLength) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited name have to be at least 80% the same");
			return;
		}
		int editDist = StringUtils.getLevenshteinDistance(srNoSpace, erNoSpace, ((avgLength + 4) / 5));
		if ((editDist * 5) > avgLength) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited name have to be at least 80% the same");
			return;
		}
		
		//	store edited name
		PooledString name = bbk.updateString(nameString, user);
		nameString = name.getStringPlain();
		
		//	add parse to edited name if source name has one
		if ((sourceName.getStringParsed() != null) && (name.getParseChecksum() == null)) try {
			String sourceNameParsedString = sourceName.getStringParsed();
			MutableAnnotation sourceNameParsed = SgmlDocumentReader.readDocument(new StringReader(sourceNameParsedString));
			MutableAnnotation sourceNameString = Gamta.newDocument(Gamta.newTokenSequence(sourceName.getStringPlain(), sourceNameParsed.getTokenizer()));
			if (annotateDetails(sourceNameParsed, sourceNameString)) {
				
				//	char-wise Levenshtein transform source into edited name
				int[] editSequence = StringUtils.getLevenshteinEditSequence(sourceName.getStringPlain(), nameString);
				int sourceNameOffset = 0;
				int nameOffset = 0;
				for (int e = 0; e < editSequence.length; e++) {
					if (editSequence[e] == StringUtils.LEVENSHTEIN_KEEP) {
						sourceNameOffset++;
						nameOffset++;
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_INSERT) {
						sourceNameString.insertChar(nameString.charAt(nameOffset), sourceNameOffset);
						sourceNameOffset++;
						nameOffset++;
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_DELETE) {
						sourceNameString.removeChar(sourceNameOffset);
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_REPLACE) {
						sourceNameString.setChar(nameString.charAt(nameOffset), sourceNameOffset);
						sourceNameOffset++;
						nameOffset++;
					}
				}
				
				//	generate DwC from transformation result ==> parse for edited name
				String nameParsed = TaxonomicNameUtils.toSimpleDwcXml(TaxonomicNameUtils.dwcXmlToTaxonomicName(sourceNameString));
				bbk.updateString(nameString, nameParsed, user);
			}
		}
		catch (Exception e) {
			System.out.println("Error transforming parsed taxonomic name: " + e.getMessage());
			e.printStackTrace(System.out);
		}
		
		//	update canonical string ID to edited name for all names having source name in that spot
		bbk.setCanonicalStringId(sourceNameId, name.id, user);
		PooledStringIterator psit = bbk.getLinkedStrings(sourceNameId);
		while (psit.hasNextString())
			bbk.setCanonicalStringId(psit.getNextString().id, name.id, user);
		
		//	flag source name as deleted
		bbk.setDeleted(sourceNameId, true, user);
		
		//	shows new name in sub window
		response.sendRedirect(resultUrlPrefix + name.id);
	}
	
	private static boolean annotateDetails(MutableAnnotation sourceNameParsed, MutableAnnotation sourceNameString) {
		
		//	get details
		Annotation[] details = sourceNameParsed.getAnnotations();
		boolean gotAllDetails = true;
		
		//	annotate all occurences of all details
		StringVector detailList = new StringVector(true);
		for (int d = 0; d < details.length; d++) {
			if (details[d].size() == sourceNameParsed.size())
				continue;
			boolean gotDetail = false;
			detailList.addElement(details[d].getValue());
			Annotation[] detailOccurrences = Gamta.extractAllContained(sourceNameString, detailList, true);
			for (int o = 0; o < detailOccurrences.length; o++) {
				boolean overlap = false;
				for (int c = detailOccurrences[o].getStartIndex(); c < detailOccurrences[o].getEndIndex(); c++)
					if (sourceNameString.tokenAt(c).hasAttribute("d")) {
						overlap = true;
						break;
					}
				if (overlap)
					continue;
				sourceNameParsed.addAnnotation(details[d].getType(), detailOccurrences[o].getStartIndex(), detailOccurrences[o].size());
				for (int c = detailOccurrences[o].getStartIndex(); c < detailOccurrences[o].getEndIndex(); c++)
					sourceNameString.tokenAt(c).setAttribute("d", "d");
				gotDetail = true;
				break;
			}
			if (!gotDetail) {
				gotAllDetails = false;
				break;
			}
		}
		
		//	clean up
		for (int t = 0; t < sourceNameString.size(); t++)
			sourceNameString.tokenAt(t).removeAttribute("d");
		
		//	did we assign everything? (this should work in the very most cases)
		return gotAllDetails;
		
		//	TODO use greedy overlay technique to deal with details that have equal values, etc
		//		 - use same technique as for generating structures
		//		 - select first to cover all
	}
}