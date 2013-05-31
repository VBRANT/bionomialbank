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
package de.uka.ipd.idaho.binoBank.apps.webInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import de.uka.ipd.idaho.binoBank.BinoBankClient;
import de.uka.ipd.idaho.binoBank.BinoBankConstants;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils.IsolatorWriter;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils.TaxonomicName;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Search facility for BinoBank.
 * 
 * @author sautter
 */
public class BinoBankSearchServlet extends BinoBankWiServlet implements BinoBankConstants {
	
	private static final String IS_FRAME_PAGE_PARAMETER = "isFramePage";
	
	private TreeMap formats = new TreeMap(String.CASE_INSENSITIVE_ORDER);
	
	private static final String PARSE_NAME_FORMAT = "PaRsEtHeNaMe";
	
	private static final String EDIT_NAME_FORMAT = "EdItNaMeStRiNg";
	
	private static final String MINOR_UPDATE_FORM_NAME_ID = "MiNoRuPdAtE";
	
	private static final String DELETED_PARAMETER = "deleted";
	
	private String nameParserUrl = null;
	
	private String nameEditorUrl = null;
	
	private TaxonomicRankSystem rankSystem;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	make default formats available
		this.formats.put(DARWIN_CORE_FORMAT, "");
		this.formats.put(SIMPLE_DARWIN_CORE_FORMAT, "");
		
		//	read available data formats and respective XSL transformers
		Settings formats = this.config.getSubset("format");
		String[] formatNames = formats.getKeys();
		for (int f = 0; f < formatNames.length; f++) {
			String xsltName = formats.getSetting(formatNames[f]);
			try {
				Transformer xslt = XsltUtils.getTransformer(new File(this.dataFolder, xsltName));
				this.formats.put(formatNames[f], xslt);
			} catch (IOException ioe) {}
		}
		
		//	get links to name string editor and parser
		this.nameParserUrl = this.getSetting("nameParserUrl");
		this.nameEditorUrl = this.getSetting("nameEditorUrl");
		
		//	get generic rank system (we'll be handling names from all domains)
		this.rankSystem = TaxonomicRankSystem.getRankSystem(null);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get parameters
		String id = request.getParameter(STRING_ID_ATTRIBUTE);
		String canonicalId = request.getParameter(CANONICAL_STRING_ID_ATTRIBUTE);
		String deleted = request.getParameter(DELETED_PARAMETER);
		String user = request.getParameter(USER_PARAMETER);
		if (user == null)
			user = "Anonymous";
		
		//	make name cluster representative
		if (canonicalId != null) {
			System.out.println("Making name " + id + " representative of its cluster");
			BinoBankClient bbc = this.getBinoBankClient();
			PooledString ps = bbc.getString(canonicalId);
			if (ps != null) {
				String prevCanonicalId = ps.getCanonicalStringID();
				if (!ps.id.equals(prevCanonicalId)) {
					PooledStringIterator psi = bbc.getLinkedStrings(prevCanonicalId);
					if (psi.getException() == null) {
						ArrayList refIDs = new ArrayList();
						while (psi.hasNextString())
							refIDs.add(psi.getNextString().id);
						for (Iterator idit = refIDs.iterator(); idit.hasNext();)
							bbc.setCanonicalStringId(((String) idit.next()), canonicalId, user);
					}
				}
			}
		}
		
		//	delete or undelete name
		else if ((id != null) && (deleted != null)) {
			System.out.println("Setting deletion status of name " + id + " to " + deleted);
			BinoBankClient bbc = this.getBinoBankClient();
			bbc.setDeleted(id, "true".equals(deleted), user);
		}
		
		//	send back form
		this.sendMinorUpdateForm(request, response);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	retrieve BinoBank client on the fly to use local bridge if possible
		BinoBankClient bbc = this.getBinoBankClient();
		
		// name id plus format or style ==> name in special format
		String id = request.getParameter(STRING_ID_ATTRIBUTE);
		String format = request.getParameter(FORMAT_PARAMETER);
		if ((format != null) && (format.trim().length() == 0))
			format = null;
		if (id != null) {
			if (format != null) {
				if ("true".equals(request.getParameter(IS_FRAME_PAGE_PARAMETER)))
					this.sendFormattedNameFrame(request, id, format, response);
				else this.sendFormattedName(request, id, ((format == null) ? DARWIN_CORE_FORMAT : format), response);
				return;
			}
			else if (MINOR_UPDATE_FORM_NAME_ID.equals(id)) {
				this.sendMinorUpdateForm(request, response);
				return;
			}
		}
		
		//	query parameters and perform search if given
		PooledStringIterator psi = null;
		
		//	get search parameters
		String canonicalStringId = request.getParameter(CANONICAL_STRING_ID_ATTRIBUTE);
		String query = request.getParameter(QUERY_PARAMETER);
		String rank = request.getParameter(RANK_PARAMETER);
		String user = request.getParameter(USER_PARAMETER);
		String higher = request.getParameter(HIGHER_RANK_GROUP_PARAMETER);
		String family = request.getParameter(FAMILY_RANK_GROUP_PARAMETER);
		String genus = request.getParameter(GENUS_RANK_GROUP_PARAMETER);
		String species = request.getParameter(SPECIES_RANK_GROUP_PARAMETER);
		String authority = request.getParameter(AUTHORITY_PARAMETER);
		
		//	request for specific name cluster
		if (canonicalStringId != null) {
			psi = bbc.getLinkedStrings(canonicalStringId);
			if (psi.getException() != null)
				throw psi.getException();
		}
		
		//	perform search if query given;
		else if ((query != null) || (higher != null) || (family != null) || (genus != null) || (species != null)) {
			String[] textPredicates = {query};
			psi = bbc.findNames(textPredicates, false, user, higher, family, genus, species, authority, rank, true);
			if (psi.getException() != null)
				throw psi.getException();
		}
		
		//	create page builder
		HtmlPageBuilder pageBuilder = this.getSearchPageBuilder(request, psi, canonicalStringId, query, rank, higher, family, genus, species, authority, response);
		
		//	send page
		this.sendHtmlPage(pageBuilder);
	}
	
	private void sendFormattedName(HttpServletRequest request, String id, String format, HttpServletResponse response) throws IOException {
		
		//	retrieve BinoBank client on the fly to use local bridge if possible
		BinoBankClient bbc = this.getBinoBankClient();
		
		// get parsed string
		final PooledString ps = bbc.getString(id);
		
		//	check error
		if (this.sendFormattedNameError(request, id, ps, response))
			return;
		
		//	get format transformer
		final Object xsltObj = this.formats.get(format);
		final Transformer xslt = ((xsltObj instanceof Transformer) ? ((Transformer) xsltObj) : null);
		if (xslt == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			if (DARWIN_CORE_FORMAT.equals(format)) {
				String nameParsedString = ps.getStringParsed();
				for (int c = 0; c < nameParsedString.length(); c++) {
					char ch = nameParsedString.charAt(c);
					if ((ch == '<') && (c != 0) && (nameParsedString.charAt(c - 1) == '>'))
						bw.newLine();
					bw.write(ch);
				}
			}
			else if (SIMPLE_DARWIN_CORE_FORMAT.equals(format)) {
				TaxonomicName name = TaxonomicNameUtils.dwcXmlToTaxonomicName(SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed())));
				String nameParsedString = name.toSimpleDwcXml();
				for (int c = 0; c < nameParsedString.length(); c++) {
					char ch = nameParsedString.charAt(c);
					if ((ch == '<') && (c != 0) && (nameParsedString.charAt(c - 1) == '>'))
						bw.newLine();
					bw.write(ch);
				}
			}
			else {
				bw.write("Unknown name format: " + format);
				bw.newLine();
				bw.write("Use the links below to get to the valid formats.");
			}
			bw.flush();
			return;
		}
		
		//	prepare parsed name for XSLT
		String nameParsedString = ps.getStringParsed();
		StringBuffer nameParsed = new StringBuffer("<" + STRING_NODE_TYPE + SP_XML_NAMESPACE_ATTRIBUTE + "><" + STRING_PARSED_NODE_TYPE + ">\n");
		int fc = 0;
		for (int c = fc; c < nameParsedString.length(); c++) {
			char ch = nameParsedString.charAt(c);
			if ((ch == '<') && (c != 0) && (nameParsedString.charAt(c - 1) == '>'))
				nameParsed.append('\n');
			nameParsed.append(ch);
		}
		nameParsed.append("\n</" + STRING_PARSED_NODE_TYPE + "></" + STRING_NODE_TYPE + ">");
		
		//	send formatted name
		response.setContentType("text/plain");
		response.setCharacterEncoding(ENCODING);
		final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		final CharSequenceReader csr = new CharSequenceReader(nameParsed);
		final IOException[] ioe = {null};
		Thread tt = new Thread() {
			public void run() {
				synchronized (csr) {
					csr.notify();
				}
				try {
					xslt.transform(new StreamSource(csr), new StreamResult(new IsolatorWriter(bw)));
				}
				catch (TransformerException te) {
					ioe[0] = new IOException(te.getMessage());
				}
			}
		};
		synchronized (csr) {
			tt.start();
			try {
				csr.wait();
			} catch (InterruptedException ie) {}
		}
		while (tt.isAlive()) {
			try {
				tt.join(250);
			} catch (InterruptedException ie) {}
			if (ioe[0] != null)
				throw ioe[0];
			if ((csr.lastRead + 2500) < System.currentTimeMillis())
				break;
		}
		bw.flush();
	}
	
	private void sendMinorUpdateForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head></head><body>");
		bw.newLine();
		bw.write("<form id=\"minorUpdateForm\" method=\"POST\" action=\"" + request.getContextPath() + request.getServletPath() + "\">");
		bw.write("<input type=\"hidden\" name=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\" />");
		bw.write("<input type=\"hidden\" name=\"" + STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + STRING_ID_ATTRIBUTE + "\" />");
		bw.write("<input type=\"hidden\" name=\"" + DELETED_PARAMETER + "\" value=\"\" id=\"" + DELETED_PARAMETER + "\" />");
		bw.write("<input type=\"hidden\" name=\"" + USER_PARAMETER + "\" value=\"\" id=\"" + USER_PARAMETER + "\" />");
		bw.write("</form>");
		bw.newLine();
		bw.write("</body></html>");
		bw.newLine();
		bw.flush();
	}
	
	private boolean sendFormattedNameError(HttpServletRequest request, String id, PooledString ps, HttpServletResponse response) throws IOException {
		if (ps == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Invalid name ID: " + id);
			bw.flush();
			return true;
		}
		else if (ps.getStringParsed() == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("A parsed version is not yet available for this name.");
			if (this.nameParserUrl != null) {
				bw.newLine();
				bw.write("Use the 'Parse Name' link below to create a parsed version.");
				bw.newLine();
				bw.newLine();
				bw.write("If you just parsed the name string, there might be a problem in the parse.");
				bw.newLine();
				bw.write("In that case, you can use the 'Parse Name' link below to re-open the parser.");
			}
			bw.flush();
			return true;
		}
		else return false;
	}
	
	private void sendFormattedNameFrame(HttpServletRequest request, final String id, final String format, HttpServletResponse response) throws IOException {
		
		//	retrieve BinoBank client on the fly to use local bridge if possible
		BinoBankClient bbc = this.getBinoBankClient();
		
		//	get parsed string
		final PooledString ps = bbc.getString(id);
		
		//	check null
		if (ps == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, ("Invalid name ID: " + id));
			return;
		}
		
		//	send code frame
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		HtmlPageBuilder pageBuilder = new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeParsedName();
				else super.include(type, tag);
			}
			protected String[] getOnloadCalls() {
				String[] olcs = new String[1];
				if ((nameEditorUrl != null) && EDIT_NAME_FORMAT.equals(format))
					olcs[0] = "editName();";
				else if ((nameParserUrl != null) && PARSE_NAME_FORMAT.equals(format))
					olcs[0] = "parseName();";
				else olcs[0] = "setFormat('" + ((format == null) ? DARWIN_CORE_FORMAT : format) + "');";
				return olcs;
			}
			private void includeParsedName() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.write("<p class=\"nameString\">" + xmlGrammar.escape(ps.getStringPlain()) + "</p>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<iframe src=\"about:blank\" id=\"refCodeFrame\">");
				this.writeLine("</iframe>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<span class=\"nameFormatLinkLabel\">");
				this.writeLine("Contributed by <b>" + ps.getCreateUser() + "</b> (at <b>" + ps.getCreateDomain() + "</b>)");
				this.writeLine("</span>");
				if (ps.getParseChecksum() != null) {
					this.writeLine("&nbsp;&nbsp;");
					this.writeLine("<span class=\"nameFormatLinkLabel\">");
					this.writeLine("Parsed by <b>" + ps.getUpdateUser() + "</b> (at <b>" + ps.getUpdateDomain() + "</b>)");
					this.writeLine("</span>");
				}
				this.writeLine("</td>");
				this.writeLine("</tr>");
				if (ps.getStringParsed() != null) {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\">");
					this.writeLine("<span class=\"nameFormatLinkLabel\">Other Formats:</span>");
					for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
						String format = ((String) fit.next());
						this.writeLine("<input" + 
								" class=\"nameFormatLink\"" + 
								" type=\"button\"" + 
								" value=\"" + format + "\"" + 
								" title=\"Get this name formatted as " + format + "\"" + 
								" onclick=\"return setFormat('" + format + "');\"" + 
								" />");
					}
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<span class=\"nameFormatLinkLabel\">Contribute to Bibliography:</span>");
				if (nameParserUrl != null) {
					this.writeLine("<input" + 
							" class=\"nameFormatLink\"" +
							" type=\"button\"" +
							" value=\"" + ((ps.getStringParsed() == null) ? "Parse Name" : "Refine Parsed Name") + "\"" +
							" title=\"" + ((ps.getStringParsed() == null) ? "Parse this taxonomic name so formatted versions become available" : "Refine or correct the parsed version of this taxonomic name") + "\"" +
							" onclick=\"return parseRef();\"" + 
							" />");
				}
				if (nameEditorUrl != null) {
					this.writeLine("<input" + 
							" class=\"nameFormatLink\"" +
							" type=\"button\"" +
							" value=\"Edit Name\"" +
							" title=\"" + "Correct this name string, e.g. to eliminate typos or punctuation errors" + "\"" +
							" onclick=\"return editRef();\"" + 
							" />");
				}
				this.writeLine("<input type=\"button\" id=\"delete" + ps.id + "\" class=\"nameFormatLink\"" + (ps.isDeleted() ? " style=\"display: none;\"" : "") + " onclick=\"return setDeleted('" + ps.id + "', true);\" value=\"Delete\">");
				this.writeLine("<input type=\"button\" id=\"unDelete" + ps.id + "\" class=\"nameFormatLink\"" + (ps.isDeleted() ? "" : " style=\"display: none;\"") + " onclick=\"return setDeleted('" + ps.id + "', false);\" value=\"Un-Delete\">");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
				
				this.write("<iframe id=\"minorUpdateFrame\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + MINOR_UPDATE_FORM_NAME_ID + "\">");
				this.writeLine("</iframe>");
			}
			
			protected String getPageTitle(String title) {
				if (EDIT_NAME_FORMAT.equals(format))
					return "Edit Name";
				else if (PARSE_NAME_FORMAT.equals(format))
					return "Parse Name";
				else return ("Parsed Name as " + format);
			}
			
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("function setDeleted(nameId, deleted) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  var nameIdField = minorUpdateFrame.contentWindow.document.getElementById('" + STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (nameIdField == null)");
				this.writeLine("    return false;");
				this.writeLine("  nameIdField.value = nameId;");
				this.writeLine("  var deletedField = minorUpdateFrame.contentWindow.document.getElementById('" + DELETED_PARAMETER + "');");
				this.writeLine("  if (deletedField == null)");
				this.writeLine("    return false;");
				this.writeLine("  deletedField.value = deleted;");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.document.getElementById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  document.getElementById('delete' + nameId).style.display = (deleted ? 'none' : '');");
				this.writeLine("  document.getElementById('unDelete' + nameId).style.display = (deleted ? '' : 'none');");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("var currentFormat = '" + (EDIT_NAME_FORMAT.equals(format) ? DARWIN_CORE_FORMAT : format) + "';");
				this.writeLine("function setFormat(format) {");
				this.writeLine("  document.getElementById('nameCodeFrame').src = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + id + "&" + FORMAT_PARAMETER + "=' + format);");
				this.writeLine("  document.title = ('Parsed Name as ' + format);");
				this.writeLine("  currentFormat = format;");
				this.writeLine("  currentStyle = null;");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				if ((nameParserUrl != null) || (nameEditorUrl != null)) {
					this.writeLine("var user = null;");
					this.writeLine("function getUser() {");
					this.writeLine("  if ((user == null) || (user.length == 0)) {");
					this.writeLine("    var cUser = document.cookie;");
					this.writeLine("    if ((cUser != null) && (cUser.indexOf('" + USER_PARAMETER + "=') != -1)) {");
					this.writeLine("      cUser = cUser.substring(cUser.indexOf('" + USER_PARAMETER + "=') + '" + USER_PARAMETER + "='.length);");
					this.writeLine("      if (cUser.indexOf(';') != -1)");
					this.writeLine("        cUser = cUser.substring(0, cUser.indexOf(';'));");
					this.writeLine("      user = unescape(cUser);");
					this.writeLine("    }");
					this.writeLine("    if ((user == null) || (user.length == 0)) {");
					this.writeLine("      user = window.prompt('Please enter a user name so BinoBank can credit your contribution', '');");
					this.writeLine("      if ((user == null) || (user.length == 0))");
					this.writeLine("        return false;");
					this.writeLine("      document.cookie = ('" + USER_PARAMETER + "=' + escape(user) + ';domain=' + escape(window.location.hostname) + ';expires=' + new Date(" + (System.currentTimeMillis() + (1000L * 60L * 60L * 24L * 365L * 5L)) + ").toGMTString());");
					this.writeLine("    }");
					this.writeLine("  }");
					this.writeLine("  return true;");
					this.writeLine("}");
				}
				if (nameParserUrl != null) {
					String parsedNameBaseLinkFormatted = (this.request.getContextPath() + this.request.getServletPath() + 
							"?" + STRING_ID_ATTRIBUTE + "=" + id + 
							"&" + FORMAT_PARAMETER + "="
						);
					String parserLinkFormatted = (nameParserUrl + 
							"?" + STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "" +
							"&resultUrl=" + URLEncoder.encode((parsedNameBaseLinkFormatted), ENCODING)
						);
					this.writeLine("function parseRef() {");
					this.writeLine("  if (!getUser())");
					this.writeLine("    return false;");
					this.writeLine("  document.getElementById('nameCodeFrame').src = ('" + parserLinkFormatted + "' + currentFormat + '&" + USER_PARAMETER + "=' + encodeURIComponent(user));");
					this.writeLine("  return false;");
					this.writeLine("}");
				}
				if (nameEditorUrl != null) {
					String editedNameBaseLink = (this.request.getContextPath() + this.request.getServletPath() + 
							"?" + IS_FRAME_PAGE_PARAMETER + "=true" + 
							"&" + FORMAT_PARAMETER + "=" + DARWIN_CORE_FORMAT +
							"&" + STRING_ID_ATTRIBUTE + "="
						);
					String editorLink = (nameEditorUrl + 
							"?" + STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "" +
							"&resultUrlPrefix=" + URLEncoder.encode((editedNameBaseLink), ENCODING)
						);
					this.writeLine("function editName() {");
					this.writeLine("  if (!getUser())");
					this.writeLine("    return false;");
					this.writeLine("  window.location.href = ('" + editorLink + "&" + USER_PARAMETER + "=' + encodeURIComponent(user));");
					this.writeLine("  return false;");
					this.writeLine("}");
				}
				
				this.writeLine("</script>");
			}
		};
		this.sendPopupHtmlPage(pageBuilder);
	}
	
	private static class CharSequenceReader extends Reader {
		private CharSequence chars;
		private int length;
		private int offset = 0;
		private int mark = 0;
		long lastRead = System.currentTimeMillis();
		CharSequenceReader(CharSequence chars) {
			this.chars = chars;
			this.length = this.chars.length();
		}
		private void ensureOpen() throws IOException {
			if (this.chars == null) throw new IOException("Stream closed");
		}
		public int read() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				this.lastRead = System.currentTimeMillis();
				if (this.offset >= this.length)
					return -1;
				else return this.chars.charAt(this.offset++);
			}
		}
		public int read(char cbuf[], int off, int len) throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0))
					throw new IndexOutOfBoundsException();
				else if (len == 0)
					return 0;
				else if (this.offset >= this.length)
					return -1;
				int readable = Math.min(this.length - this.offset, len);
				for (int r = 0; r < readable; r++)
					cbuf[off + r] = this.chars.charAt(this.offset++);
				this.lastRead = System.currentTimeMillis();
				return readable;
			}
		}
		public long skip(long ns) throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				if (this.offset >= this.length)
					return 0;
				long skippable = Math.min(this.length - this.offset, ns);
				skippable = Math.max(-this.offset, skippable);
				this.offset += skippable;
				this.lastRead = System.currentTimeMillis();
				return skippable;
			}
		}
		public boolean ready() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				return true;
			}
		}
		public boolean markSupported() {
			return true;
		}
		public void mark(int readAheadLimit) throws IOException {
			if (readAheadLimit < 0)
				throw new IllegalArgumentException("Read-ahead limit < 0");
			synchronized (this.lock) {
				ensureOpen();
				this.mark = this.offset;
			}
		}
		public void reset() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				this.offset = this.mark;
			}
		}
		public void close() {
			this.chars = null;
		}
	}
	
	private HtmlPageBuilder getSearchPageBuilder(HttpServletRequest request, final PooledStringIterator psi, final String canonicalStringId, final String query, final String rank, final String higher, final String family, final String genus, final String species, final String authority, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeForm".equals(type)) {
					if (canonicalStringId == null)
						this.includeSearchForm();
				}
				else if ("includeResult".equals(type)) {
					if (psi != null)
						this.includeSearchResult();
				}
				else if ("includeRankOptions".equals(type))
					this.includeRankOptions();
				else super.include(type, tag);
			}
			private boolean inSearchForm = false;
			public void storeToken(String token, int treeDepth) throws IOException {
				if (this.inSearchForm && html.isTag(token)) {
					String type = html.getType(token);
					if ("input".equals(type)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String name = tnas.getAttribute("name");
						if ((name != null) && !tnas.containsAttribute("value")) {
							String value = null;
							if (QUERY_PARAMETER.equals(name))
								value = query;
							else if (HIGHER_RANK_GROUP_PARAMETER.equals(name))
								value = higher;
							else if (FAMILY_RANK_GROUP_PARAMETER.equals(name))
								value = family;
							else if (GENUS_RANK_GROUP_PARAMETER.equals(name))
								value = genus;
							else if (SPECIES_RANK_GROUP_PARAMETER.equals(name))
								value = species;
							else if (AUTHORITY_PARAMETER.equals(name))
								value = authority;
							if (value != null)
								token = (token.substring(0, (token.length() - 1)) + " value=\"" + xmlGrammar.escape(value) + "\">");
						}
					}
					else if ((rank != null) && "option".equals(type) && !html.isEndTag(token)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String value = tnas.getAttribute("value");
						if (rank.equals(value))
							token = (token.substring(0, (token.length() - 1)) + " selected>");
					}
				}
				super.storeToken(token, treeDepth);
			}
			
			private void includeSearchForm() throws IOException {
				this.writeLine("<form" +
						" method=\"GET\"" +
						" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\"" +
						">");
				this.inSearchForm = true;
				this.includeFile("searchFields.html");
				this.inSearchForm = false;
				this.writeLine("</form>");
			}
			
			private void includeRankOptions() throws IOException {
				Rank[] ranks = rankSystem.getRanks();
				for (int r = 0; r < ranks.length; r++)
					this.storeToken(("<option value=\"" + xmlGrammar.escape(ranks[r].name) + "\">" + ranks[r].name.substring(0, 1).toUpperCase() + ranks[r].name.substring(1).toLowerCase() + "</option>"), 0);
			}
			
			private void includeSearchResult() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				StringVector deletedRefIDs = new StringVector();
				if (!psi.hasNextString()) {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\">");
					this.writeLine("Your search did not return any results, sorry.");
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				else {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\" width=\"20%\">");
					this.writeLine("<p class=\"nameString\" style=\"font-size: 60%;\">Hover&nbsp;names&nbsp;for&nbsp;further&nbsp;options</p>");
					this.writeLine("</td>");
					this.writeLine("<td class=\"resultTableCell\" style=\"text-align: right;\">");
					this.writeLine("<input type=\"button\" id=\"showDeleted\" class=\"nameFormatLink\" onclick=\"return toggleDeleted(true);\" value=\"Show names flagged as deleted\" />");
					this.writeLine("<input type=\"button\" id=\"hideDeleted\" class=\"nameFormatLink\" style=\"display: none;\" onclick=\"return toggleDeleted(false);\" value=\"Hide names flagged as deleted\" />");
					this.writeLine("</td>");
					this.writeLine("</tr>");
					
					while (psi.hasNextString()) {
						PooledString ps = psi.getNextString();
						if ((canonicalStringId == null) && !ps.id.equals(ps.getCanonicalStringID()))
							continue;
						if (ps.isDeleted())
							deletedRefIDs.addElement(ps.id);
						this.writeLine("<tr class=\"resultTableRow\" id=\"ref" + ps.id + "\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? " style=\"display: none;\"" : "") + ">");
						this.writeLine("<td class=\"resultTableCell\" colspan=\"2\">");
						this.writeLine("<p class=\"nameString" + (ps.id.equals(canonicalStringId) ? " representative" : "") + "\" onmouseover=\"showOptionsFor('" + ps.id + "')\">" + xmlGrammar.escape(ps.getStringPlain()) + "</p>");
						this.writeLine("<span class=\"nameFormatLinkLabel\">");
						this.writeLine("Contributed by <b>" + ps.getCreateUser() + "</b> (at <b>" + ps.getCreateDomain() + "</b>)");
						this.writeLine("</span>");
						if (ps.getParseChecksum() != null) {
							this.writeLine("&nbsp;&nbsp;");
							this.writeLine("<span class=\"nameFormatLinkLabel\">");
							this.writeLine("Parsed by <b>" + ps.getUpdateUser() + "</b> (at <b>" + ps.getUpdateDomain() + "</b>)");
							this.writeLine("</span>");
						}
						this.writeLine("<div id=\"optionsFor" + ps.id + "\" class=\"resultOptions\" style=\"display: none;\">");
//						String[] styles = BibRefUtils.getRefStringStyles();
						if (ps.getParseChecksum() != null) {
							this.writeLine("<span class=\"nameFormatLinkLabel\">Additional Formats &amp; Styles:</span>");
							for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
								String format = ((String) fit.next());
								String formatLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
										STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" + 
										IS_FRAME_PAGE_PARAMETER + "=true&" + 
										FORMAT_PARAMETER + "=" + URLEncoder.encode(format, ENCODING)
									);
								this.writeLine("<input" + 
										" class=\"nameFormatLink\"" + 
										" type=\"button\"" + 
										" value=\"" + format + "\"" + 
										" title=\"Get this name formatted as " + format + "\""	+ 
										" onclick=\"" +
											"window.open(" +
												"'" + formatLink + "'" +
												", " +
												"'Parsed Name'" +
												", " +
												"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
											");" +
											" return false;\"" + 
										" />");
							}
							this.writeLine("&nbsp;");
						}
						if (ps.getParseChecksum() != null)
							this.writeLine("<br>");
						this.writeLine("<span class=\"nameFormatLinkLabel\">Contribute to Bibliography:</span>");
						if (nameParserUrl != null) {
							String parserLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
									STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" +
									IS_FRAME_PAGE_PARAMETER + "=true&" + 
									FORMAT_PARAMETER + "=" + URLEncoder.encode(PARSE_NAME_FORMAT, ENCODING));
							this.writeLine("<input" +
									" class=\"nameFormatLink\"" +
									" type=\"button\"" +
									" value=\"" + ((ps.getParseChecksum() == null) ? "Parse Name" : "Refine Parsed Name") + "\"" +
									" title=\"" + ((ps.getParseChecksum() == null) ? "Parse this taxonomic name so formatted versions become available" : "Refine or correct the parsed version of this taxonomic name") + "\"" + 
									" onclick=\"" +
										"window.open(" +
											"'" + parserLink + "'" +
											", " +
											"'Parse Name'" +
											", " +
											"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
										");" +
										" return false;\"" + 
									" />");
						}
						if (nameEditorUrl != null) {
							String editorLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
									STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" +
									IS_FRAME_PAGE_PARAMETER + "=true&" + 
									FORMAT_PARAMETER + "=" + URLEncoder.encode(EDIT_NAME_FORMAT, ENCODING));
							this.writeLine("<input" +
									" class=\"nameFormatLink\"" +
									" type=\"button\"" +
									" value=\"Edit Name\"" +
									" title=\"Correct this taxonomic name string, e.g. to eliminate typos or punctuation errors\"" + 
									" onclick=\"" +
										"window.open(" +
											"'" + editorLink + "'" +
											", " +
											"'Edit Name'" +
											", " +
											"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
										");" +
										" return false;\"" + 
									" />");
						}
						
						this.writeLine("<input type=\"button\" id=\"delete" + ps.id + "\" class=\"nameFormatLink\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? " style=\"display: none;\"" : "") + " onclick=\"return setDeleted('" + ps.id + "', true);\" value=\"Delete\" />");
						this.writeLine("<input type=\"button\" id=\"unDelete" + ps.id + "\" class=\"nameFormatLink\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? "" : " style=\"display: none;\"") + " onclick=\"return setDeleted('" + ps.id + "', false);\" value=\"Un-Delete\" />");
						
						if (canonicalStringId == null)
							this.writeLine("<input type=\"button\" id=\"showVersions" + ps.id + "\" class=\"nameFormatLink\" onclick=\"showVersions('" + ps.id + "');\" value=\"Show All Versions\" />");
						else if (!ps.id.equals(canonicalStringId))
							this.writeLine("<input type=\"button\" id=\"makeRepresentative" + ps.id + "\" class=\"nameFormatLink\" onclick=\"return makeRepresentative('" + ps.id + "');\" value=\"Make Representative\" />");
						
						this.writeLine("</div>");
						this.writeLine("</td>");
						this.writeLine("</tr>");
					}
				}
				this.writeLine("</table>");
				
				this.writeLine("<script type=\"text/javascript\">");
				this.writeLine("function buildDeletedArray() {");
				this.writeLine("  deletedRefIDs = new Array(" + deletedRefIDs.size() + ");");
				for (int d = 0; d < deletedRefIDs.size(); d++)
					this.writeLine("  deletedRefIDs[" + d + "] = '" + deletedRefIDs.get(d) + "';");
				this.writeLine("}");
				this.writeLine("</script>");
				
				this.write("<iframe id=\"minorUpdateFrame\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + MINOR_UPDATE_FORM_NAME_ID + "\">");
				this.writeLine("</iframe>");
			}
			
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("var showingOptionsFor = null;");
				this.writeLine("function showOptionsFor(refId) {");
				this.writeLine("  if (showingOptionsFor != null) {");
				this.writeLine("    var showingOptions = document.getElementById('optionsFor' + showingOptionsFor);");
				this.writeLine("    if (showingOptions != null)");
				this.writeLine("      showingOptions.style.display = 'none';");
				this.writeLine("    showingOptionsFor = null;");
				this.writeLine("  }");
				this.writeLine("  if (refId != null) {");
				this.writeLine("    var toShowOptions = document.getElementById('optionsFor' + refId);");
				this.writeLine("    if (toShowOptions != null)");
				this.writeLine("      toShowOptions.style.display = '';");
				this.writeLine("    showingOptionsFor = refId;");
				this.writeLine("  }");
				this.writeLine("}");
				
				this.writeLine("var deletedRefIDs = null;");
				this.writeLine("var showingDeleted = " + ((canonicalStringId == null) ? "false" : "true") + ";");
				this.writeLine("function toggleDeleted(showDeleted) {");
				this.writeLine("  if (deletedRefIDs == null)");
				this.writeLine("    buildDeletedArray();");
				this.writeLine("  showingDeleted = showDeleted;");
				this.writeLine("  for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("    var deletedRef = document.getElementById('ref' + deletedRefIDs[d]);");
				this.writeLine("    if (deletedRef != null)");
				this.writeLine("      deletedRef.style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  }");
				this.writeLine("  document.getElementById('showDeleted').style.display = (showDeleted ? 'none' : '');");
				this.writeLine("  document.getElementById('hideDeleted').style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function setDeleted(refId, deleted) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  var refIdField = minorUpdateFrame.contentWindow.document.getElementById('" + STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (refIdField == null)");
				this.writeLine("    return false;");
				this.writeLine("  refIdField.value = refId;");
				this.writeLine("  var deletedField = minorUpdateFrame.contentWindow.document.getElementById('" + DELETED_PARAMETER + "');");
				this.writeLine("  if (deletedField == null)");
				this.writeLine("    return false;");
				this.writeLine("  deletedField.value = deleted;");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.document.getElementById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  if (deletedRefIDs == null)");
				this.writeLine("    buildDeletedArray();");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  document.getElementById('delete' + refId).style.display = (deleted ? 'none' : '');");
				this.writeLine("  document.getElementById('unDelete' + refId).style.display = (deleted ? '' : 'none');");
				this.writeLine("  if (!showingDeleted && deleted)");
				this.writeLine("    document.getElementById('ref' + refId).style.display = 'none';");
				this.writeLine("  if (deleted)");
				this.writeLine("    deletedRefIDs[deletedRefIDs.length] = refId;");
				this.writeLine("  else {");
				this.writeLine("    for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("      if (deletedRefIDs[d] == refId) {");
				this.writeLine("        deletedRefIDs[d] = '';");
				this.writeLine("        d = deletedRefIDs.length;");
				this.writeLine("      }");
				this.writeLine("    }");
				this.writeLine("  }");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function showVersions(canRefId) {");
				this.writeLine("  window.location.href = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + CANONICAL_STRING_ID_ATTRIBUTE + "=' + canRefId);");
				this.writeLine("}");
				
				this.writeLine("function makeRepresentative(canRefId) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  var canRefIdField = minorUpdateFrame.contentWindow.document.getElementById('" + CANONICAL_STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (canRefIdField == null)");
				this.writeLine("    return false;");
				this.writeLine("  canRefIdField.value = canRefId;");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.document.getElementById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  refreshVersionsId = canRefId;");
				this.writeLine("  window.setTimeout('refreshVersions()', 250);");
				this.writeLine("  return false;");
				this.writeLine("}");
				//	TODO consider using layover DIV to block page
				
				this.writeLine("var refreshVersionsId = '';");
				this.writeLine("function refreshVersions() {");
				this.writeLine("  if (refreshVersionsId == '')");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null) {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  var canRefIdField = minorUpdateFrame.contentWindow.document.getElementById('" + CANONICAL_STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (canRefIdField == null) {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  if (canRefIdField.value != '') {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  window.location.href = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + CANONICAL_STRING_ID_ATTRIBUTE + "=' + refreshVersionsId);");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("</script>");
			}
			
			protected String getPageTitle(String title) {
				return ("BinoBank Search" + ((psi == null) ? "" : " Results"));
			}
		};
	}
}
