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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;
import net.tanesha.recaptcha.ReCaptchaImpl;
import net.tanesha.recaptcha.ReCaptchaResponse;
import de.uka.ipd.idaho.binoBank.BinoBankClient;
import de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat.UploadStringError;
import de.uka.ipd.idaho.binoBank.apps.webInterface.NameDataFormat.UploadStringIterator;
import de.uka.ipd.idaho.easyIO.web.FormDataReceiver;
import de.uka.ipd.idaho.easyIO.web.FormDataReceiver.FieldValueInputStream;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler.AsynchronousRequest;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder.HtmlPageBuilderHost;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString;

/**
 * @author sautter
 */
public class BinoBankUploadServlet extends BinoBankWiServlet {
	private static final int uploadMaxLength = (4 * 1024 * 1024); // 4MB for starters
	
	private String reCaptchaPublicKey;
	private String reCaptchaPrivateKey;
	private boolean useReCaptcha = false;
	
	private File uploadCacheFolder = null;
	
	private AsynchronousRequestHandler uploadHandler;
	
	private String putAccessKey = "";
	
	private TreeMap dataFormats = new TreeMap();
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	load data formats
		File dfFolder = new File(this.webInfFolder, "nameDataFormats");
		if (dfFolder.exists()) {
			NameDataFormat[] dataFormats = NameDataFormat.getDataFormats(dfFolder);
			for (int f = 0; f < dataFormats.length; f++)
				this.dataFormats.put(dataFormats[f].name, dataFormats[f]);
		}
		
		//	prepare for upload caching
		this.uploadCacheFolder = new File(this.dataFolder, "cache");
		this.uploadCacheFolder.mkdirs();
		
		//	create upload handler
		this.uploadHandler = new AsynchronousUploadHandler();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.binoBank.apps.BinoBankAppServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		
		//	load reCAPTCHA keys
		this.reCaptchaPublicKey = this.getSetting("reCaptchaPublicKey", this.reCaptchaPublicKey);
		this.reCaptchaPrivateKey = this.getSetting("reCaptchaPrivateKey", this.reCaptchaPrivateKey);
		this.useReCaptcha = ((this.reCaptchaPublicKey != null) && (this.reCaptchaPrivateKey != null));
		
		//	read access key for PUT uploads
		this.putAccessKey = this.getSetting("putAccessKey", this.putAccessKey);
	}
	
	/**
	 * @return the names of plug-in data formats
	 */
	protected String[] getDataFormatNames() {
		ArrayList idfNames = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			NameDataFormat rdf = getDataFormat(dfName);
			if (rdf != null)
				idfNames.add(dfName);
		}
		return ((String[]) idfNames.toArray(new String[idfNames.size()]));
	}
	
	/**
	 * @return the plug-in data formats
	 */
	protected NameDataFormat[] getDataFormats() {
		ArrayList idfs = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			NameDataFormat rdf = getDataFormat(dfName);
			if (rdf != null)
				idfs.add(rdf);
		}
		return ((NameDataFormat[]) idfs.toArray(new NameDataFormat[idfs.size()]));
	}
	
	/**
	 * @param name the data format name
	 * @return the data format with the argument name
	 */
	protected NameDataFormat getDataFormat(String name) {
		return ((name == null) ? null : ((NameDataFormat) this.dataFormats.get(name)));
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check for upload related request
		if (this.uploadHandler.handleRequest(request, response))
			return;
		
		//	send upload form
		HtmlPageBuilder pageBuilder = this.getUploadPageBuilder(request, null, null, response);
		this.sendHtmlPage(pageBuilder);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check for upload related request (some data format might be getting feedback ...)
		if (this.uploadHandler.handleRequest(request, response))
			return;
		
		//	prepare receiving upload
		String message = null;
		Properties reCapchaErrorInputTransfer = null;
		
		//	parse request
		try {
			HashSet fileFieldSet = new HashSet(2);
			fileFieldSet.add("nameFile");
			FormDataReceiver data = FormDataReceiver.receive(request, uploadMaxLength, this.uploadCacheFolder, 1024, fileFieldSet);
			
			//	check ReCAPTCHA
			if ((message == null) && this.useReCaptcha) {
				String remoteAddr = request.getRemoteAddr();
				ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
				reCaptcha.setPrivateKey(this.reCaptchaPrivateKey);
				
				String challenge = data.getFieldValue("recaptcha_challenge_field");
				String uresponse = data.getFieldValue("recaptcha_response_field");
				ReCaptchaResponse reCaptchaResponse = reCaptcha.checkAnswer(remoteAddr, challenge, uresponse);
				
				if (!reCaptchaResponse.isValid()) {
					message = "BinoBank could not process your upload because your CAPTCHA response is not valid.";
					reCapchaErrorInputTransfer = new Properties() {
						public synchronized Object setProperty(String key, String value) {
							return ((value == null) ? null : super.setProperty(key, value));
						}
					};
					reCapchaErrorInputTransfer.setProperty("dataFormat", data.getFieldValue("dataFormat"));
					FieldValueInputStream refStringIn = data.getFieldByteStream("nameStrings");
					String encoding = refStringIn.getEncoding();
					if (encoding == null)
						encoding = "ISO-8859-1";
					System.out.println("IMPORT DATA ENCODING IS " + encoding);
					StringWriter refStringWriter = new StringWriter();
					BufferedReader refStringReader = new BufferedReader(new InputStreamReader(refStringIn));
					char[] refStringBuffer = new char[1024];
					int r;
					while ((r = refStringReader.read(refStringBuffer, 0, refStringBuffer.length)) != -1)
						refStringWriter.write(refStringBuffer, 0, r);
					refStringWriter.flush();
					reCapchaErrorInputTransfer.setProperty("nameStrings", refStringWriter.toString());
					reCapchaErrorInputTransfer.setProperty("nameFile", data.getSourceFileName("nameFile"));
					reCapchaErrorInputTransfer.setProperty(USER_PARAMETER, data.getFieldValue(USER_PARAMETER));
				}
			}
			
			//	import data
			if (message == null) {
				String dataFormatName = data.getFieldValue("dataFormat");
				String userName = data.getFieldValue(USER_PARAMETER);
				FieldValueInputStream nameDataStream = data.getFieldByteStream("nameFile");
				if ((nameDataStream == null) || (nameDataStream.fieldLength == 0))
					nameDataStream = data.getFieldByteStream("nameStrings");
				NameDataFormat dataFormat = this.getDataFormat(dataFormatName);
				if ((nameDataStream != null) && (dataFormat != null) && (userName != null)) {
					AsynchronousUpload au = new AsynchronousUpload(data.id, nameDataStream, dataFormat, data, userName);
					this.uploadHandler.enqueueRequest(au, userName);
					message = ("Thank you for your contribution to BinoBank. Your taxonomic names are being imported, you can monitor the import above.");
					HtmlPageBuilder pageBuilder = this.getUploadStatusPageBuilder(request, au.id, message, response);
					this.sendHtmlPage(pageBuilder);
					return;
				}
				else {
					if (nameDataStream == null)
						message = ("Please select a file to upload or enter taxonomic names in the text area.");
					else if (dataFormat == null)
						message = ("The data format " + dataFormatName + " does not exist or cannot handle uploads.");
				}
			}
			
			//	clean up (if we get here, the import could not be started)
			data.dispose();
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			message = "BinoBank encountered an error while processing your upload: " + ioe.getMessage();
		}
		
		//	send upload form
		HtmlPageBuilder pageBuilder = this.getUploadPageBuilder(request, message, reCapchaErrorInputTransfer, response);
		this.sendHtmlPage(pageBuilder);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get basic parameters
		String dataFormatName = request.getHeader("Data-Format");
		NameDataFormat dataFormat = this.getDataFormat(dataFormatName);
		String userName = request.getHeader("User-Name");
		if ((dataFormat == null) || (userName == null)) {
			StringBuffer error = new StringBuffer();
			if (dataFormat == null)
				error.append("Invalid data format '" + dataFormatName + "'. ");
			if (userName == null)
				error.append("User name missing.");
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, error.toString().trim());
			return;
		}
		String accessKey = request.getHeader("Access-Key");
		if ((this.putAccessKey != null) && (this.putAccessKey.length() != 0) && !this.putAccessKey.equals(accessKey)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API access key.");
			return;
		}
		
		//	set up parsing
		String dataEncoding = request.getCharacterEncoding();
		if (dataEncoding == null)
			dataEncoding = "ISO-8859-1";
		UploadStringIterator usit = dataFormat.streamParseNames(new InputStreamReader(request.getInputStream(), dataEncoding));
		int total = 0;
		int created = 0;
		int updated = 0;
		
		//	parse and store data
		while (usit.hasNextUploadString()) {
			
			//	get next data element
			UploadString us = usit.nextUploadString();
			
			//	retrieve BinoBank client on the fly to use local bridge if possible
			BinoBankClient bbc = this.getBinoBankClient();
			
			//	parsed version user approved, store plain string and parsed version rigth away
			if (dataFormat.isParseUserApproved()) {
				PooledString ps = bbc.updateString(us, userName);
				total++;
				if (ps.wasCreated())
					created++;
				else if (ps.wasUpdated())
					updated++;
			}
			
			//	parsed version not user approved, store plain string first and then update with auto-generated parsed version
			else {
				PooledString ps = bbc.updateString(us.stringPlain, userName);
				total++;
				if (ps.wasCreated())
					created++;
				if ((ps.getParseChecksum() == null) && (us.stringParsed != null)) {
					bbc.updateString(us.stringPlain, us.stringParsed, "AutomatedParser");
					if (!ps.wasCreated())
						updated++;
				}
			}
			
			//	wait a little bit
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {}
		}
		
		//	indicate success
		response.setStatus(HttpServletResponse.SC_OK);
		
		//	get erroneous parts
		UploadStringError[] errors = usit.getErrors();
		
		//	configure response
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/plain");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		
		//	write statistics, one per line
		bw.write("RECEIVED: " + total);
		bw.newLine();
		bw.write("ERRORS: " + errors.length);
		bw.newLine();
		bw.write("CREATED: " + created);
		bw.newLine();
		bw.write("UPDATED: " + updated);
		bw.newLine();
		
		//	send erroneous parts (if any)
		for (int e = 0; e < errors.length; e++) {
			bw.newLine();
			bw.newLine();
			bw.newLine();
			bw.write(errors[e].error);
			bw.newLine();
			bw.newLine();
			bw.write(errors[e].source);
		}
		
		//	send response
		bw.flush();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		super.exit();
		
		//	shut down upload handler
		this.uploadHandler.shutdown();
		
		//	shut down data formats
		NameDataFormat[] dataFormats = ((NameDataFormat[]) this.dataFormats.values().toArray(new NameDataFormat[this.dataFormats.size()]));
		for (int f = 0; f < dataFormats.length; f++)
			dataFormats[f].exit();
		this.dataFormats.clear();
	}
	
	private HtmlPageBuilder getUploadPageBuilder(HttpServletRequest request, final String message, final Properties reCapchaErrorInputTransfer, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		final NameDataFormat[] dataFormats = this.getDataFormats();
		final String dataFormatName = ((reCapchaErrorInputTransfer == null) ? ((dataFormats.length == 0) ? "" : dataFormats[0].name) : reCapchaErrorInputTransfer.getProperty("dataFormat", ((dataFormats.length == 0) ? "" : dataFormats[0].name)));
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeForm".equals(type))
					this.includeUploadForm();
				else if ("includeUploadFormatSelector".equals(type))
					this.includeUploadFormatSelector();
				else if ("includeUploadFormatLabels".equals(type))
					this.includeUploadFormatLabels();
				else if ("includeUploadFormatScript".equals(type) || "includeUploadFormScript".equals(type))
					this.includeUploadFormScript();
				else if ("includeReCAPTCHA".equals(type))
					this.includeReCAPTCHA();
				else if ("includeResult".equals(type)) {
					if (message != null)
						this.includeUploadStatistics();
				}
				else super.include(type, tag);
			}
			
			public void storeToken(String token, int treeDepth) throws IOException {
				if ((reCapchaErrorInputTransfer == null) || (message == null)) {
					super.storeToken(token, treeDepth);
					return;
				}
				if (!html.isTag(token) || html.isEndTag(token)) {
					super.storeToken(token, treeDepth);
					return;
				}
				String type = html.getType(token);
				if ("input".equalsIgnoreCase(type) && ((token.indexOf("value=") == -1) || (token.indexOf("value=\"\"") != -1))) {
					TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
					String name = tnas.getAttribute("name");
					if (name != null) {
						String value = reCapchaErrorInputTransfer.getProperty(name);
						if (value != null) {
							if (token.indexOf("value=\"\"") != -1)
								token = (token.substring(0, token.indexOf("value=\"\"")) + token.substring(token.indexOf("value=\"\"") + "value=\"\"".length()));
							while (token.endsWith("/") || token.endsWith(">"))
								token = token.substring(0, (token.length() - 1));
							token = (token + " value=\"" + value + "\" />");
						}
					}
				}
				super.storeToken(token, treeDepth);
				if ("textarea".equalsIgnoreCase(type)) {
					TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
					String name = tnas.getAttribute("name");
					if (name != null) {
						String value = reCapchaErrorInputTransfer.getProperty(name);
						if (value != null)
							super.storeToken(value);
					}
				}
			}
			
			private void includeUploadForm() throws IOException {
				this.writeLine("<form id=\"uploadForm\" method=\"POST\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\"" + ((dataFormats.length == 0) ? "" : (" onsubmit=\"return prepareUpload();\" enctype=\"multipart/form-data\"")) + ">");
				this.includeFile("uploadFields.html");
				this.writeLine("</form>");
			}
			private void includeReCAPTCHA() throws IOException {
				if (useReCaptcha) {
					ReCaptcha reCaptcha = ReCaptchaFactory.newReCaptcha(reCaptchaPublicKey, reCaptchaPrivateKey, true);
					this.write(reCaptcha.createRecaptchaHtml(null, null));
				}
				else this.write("<!-- ReCAPTCHA not in use -->");
			}
			private void includeUploadFormatSelector() throws IOException {
				if (dataFormats.length == 0)
					this.writeLine("<b>Plain Text</b>");
				else {
					this.writeLine("<select id=\"dataFormatSelector\" class=\"uploadField\" name=\"dataFormat\" onchange=\"dataFormatChanged();\">");
					for (int f = 0; f < dataFormats.length; f++)
						this.writeLine("<option value=\"" + dataFormats[f].name + "\"" + (dataFormats[f].name.equals(dataFormatName) ? " selected" : "") + ">" + dataFormats[f].getLabel() + "</option>");
					this.writeLine("</select>");
				}
			}
			private void includeUploadFormatLabels() throws IOException {
				if (dataFormats.length == 0)
					this.writeLine("<span id=\"labelPLAIN\">Enter bibliographic reference strings <b>(one line each!)</b> in the text area below to upload them to BinoBank.</span>");
				else for (int f = 0; f < dataFormats.length; f++) {
					String description = dataFormats[f].getDescription();
					if (description == null)
						this.writeLine("<span id=\"label" + dataFormats[f].name + "\"" + (dataFormats[f].name.equals(dataFormatName) ? "" : " style=\"display: none;\"") + ">Enter parsed bibliographic reference strings in <b>" + dataFormats[f].getLabel() + "</b> format in the text area below to upload them to <b>BinoBank</b>.</span>");
					else this.writeLine("<span id=\"label" + dataFormats[f].name + "\"" + (dataFormats[f].name.equals(dataFormatName) ? "" : " style=\"display: none;\"") + ">" + description + "</span>");
				}
			}
			protected String[] getOnloadCalls() {
				String[] olcs = {"setUserName();"};
				return olcs;
			}
			private void includeUploadFormScript() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				this.writeLine("function setUserName() {");
				this.writeLine("  var unf = document.getElementById('userNameField');");
				this.writeLine("  if (unf == null)");
				this.writeLine("    return;");
				this.writeLine("  if ((unf.value != null) && (unf.value.length != 0))");
				this.writeLine("    return;");
				this.writeLine("  var user = document.cookie;");
				this.writeLine("  if (user == null)");
				this.writeLine("    return;");
				this.writeLine("  if (user.indexOf('" + USER_PARAMETER + "=') == -1)");
				this.writeLine("    return;");
				this.writeLine("  user = user.substring(user.indexOf('" + USER_PARAMETER + "=') + '" + USER_PARAMETER + "='.length);");
				this.writeLine("  if (user.indexOf(';') != -1)");
				this.writeLine("    user = user.substring(0, user.indexOf(';'));");
				this.writeLine("  unf.value = unescape(user);");
				this.writeLine("}");
				this.writeLine("function prepareUpload() {");
				this.writeLine("  var unf = document.getElementById('userNameField');");
				this.writeLine("  if (unf != null) {");
				this.writeLine("    var user = unf.value;");
				this.writeLine("    if (user.length == 0) {");
				this.writeLine("      user = window.prompt('Please enter a user name so BinoBank can credit your contribution', '');");
				this.writeLine("      if ((user == null) || (user.length == 0))");
				this.writeLine("        return false;");
				this.writeLine("      unf.value = user;");
				this.writeLine("    }");
				this.writeLine("    document.cookie = ('" + USER_PARAMETER + "=' + escape(user) + ';domain=' + escape(window.location.hostname) + ';expires=' + new Date(" + (System.currentTimeMillis() + (1000L * 60L * 60L * 24L * 365L * 5L)) + ").toGMTString());");
				this.writeLine("  }");
				this.writeLine("  return " + ((dataFormats.length == 0) ? "true" : "checkFileType()") + ";");
				this.writeLine("}");
				if (dataFormats.length != 0) {
					this.writeLine("var dataFormats = new Array(" + (dataFormats.length) + ");");
					for (int f = 0; f < dataFormats.length; f++)
						this.writeLine("dataFormats[" + f + "] = '" + dataFormats[f].name + "';");
					this.writeLine("function dataFormatChanged() {");
					this.writeLine("  var dfs = document.getElementById('dataFormatSelector');");
					this.writeLine("  if (dfs == null)");
					this.writeLine("    return;");
					this.writeLine("  var df = dfs.value;");
					this.writeLine("  if (df == null)");
					this.writeLine("    return;");
					this.writeLine("  for (var f = 0; f < dataFormats.length; f++) {");
					this.writeLine("    var dfl = document.getElementById('label' + dataFormats[f]);");
					this.writeLine("    if (dfl != null)");
					this.writeLine("      dfl.style.display = ((df == dataFormats[f]) ? '' : 'none');");
					this.writeLine("  }");
					this.writeLine("}");
					this.writeLine("var acceptFileExtensions = new Array(" + (dataFormats.length + 1) + ");");
					for (int f = 0; f < dataFormats.length; f++) {
						this.write("acceptFileExtensions[" + f + "] = '");
						String[] ifes = dataFormats[f].getFileExtensions();
						if (ifes != null)
							for (int e = 0; e < ifes.length; e++) {
								if (e != 0)
									this.write(",");
								this.write(ifes[e]);
							}
						this.writeLine("';");
					}
					this.writeLine("function checkFileType() {");
					this.writeLine("  var uf = document.getElementById('uploadForm');");
					this.writeLine("  if (uf == null)");
					this.writeLine("    return true;");
					this.writeLine("  var uff = uf.elements['refFile'];");
					this.writeLine("  if (uff == null)");
					this.writeLine("    return true;");
					this.writeLine("  var ufn = uff.value;");
					this.writeLine("  if ((ufn == null) || (ufn.length == 0) || (ufn.indexOf('.') == -1))");
					this.writeLine("    return true;");
					this.writeLine("  var ufe = ufn.substring(ufn.lastIndexOf('.')+1).toLowerCase();");
					this.writeLine("  var dfs = document.getElementById('dataFormatSelector');");
					this.writeLine("  if (dfs == null)");
					this.writeLine("    return true;");
					this.writeLine("  var df = dfs.value;");
					this.writeLine("  if (df == null)");
					this.writeLine("    return true;");
					this.writeLine("  var fes = null;");
					this.writeLine("  for (var f = 0; f < dataFormats.length; f++) {");
					this.writeLine("    if (df == dataFormats[f])");
					this.writeLine("      fes = acceptFileExtensions[f].toLowerCase();");
					this.writeLine("  }");
					this.writeLine("  if ((fes == null) || (fes.length == 0))");
					this.writeLine("    return true;");
					this.writeLine("  if (fes.indexOf(ufe) != -1)");
					this.writeLine("    return true;");
					this.writeLine("  alert('The selected file does not fit the selected data format.');");
					this.writeLine("  return false;");
					this.writeLine("}");
				}
				this.writeLine("</script>");
			}
			private void includeUploadStatistics() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.write("<span class=\"resultString\">" + html.escape(message) + "</span>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
			}
			protected String getPageTitle(String title) {
				return "BinoBank Upload";
			}
		};
	}
	
	private class AsynchronousUploadHandler extends AsynchronousRequestHandler {
		AsynchronousUploadHandler() {
			super(false);
		}
		public AsynchronousRequest buildAsynchronousRequest(HttpServletRequest request) throws IOException {
			return null; // we're creating our request ourselves
		}
		protected HtmlPageBuilderHost getPageBuilderHost() {
			return BinoBankUploadServlet.this;
		}
		protected void sendHtmlPage(HtmlPageBuilder hpb) throws IOException {
			BinoBankUploadServlet.this.sendHtmlPage(hpb);
		}
		protected void sendPopupHtmlPage(HtmlPageBuilder hpb) throws IOException {
			BinoBankUploadServlet.this.sendPopupHtmlPage(hpb);
		}
		protected boolean retainAsynchronousRequest(AsynchronousRequest ar, int finishedArCount) {
			/* client not yet notified that upload is complete, we have to hold
			 * on to this one, unless last status update was more than 5 minutes
			 * ago, which indicates the client side is likely dead */
			if (!ar.isFinishedStatusSent())
				return (System.currentTimeMillis() < (ar.getLastAccessTime() + (1000 * 60 * 5)));
			//	no errors to report, or error log retrieved, we're done with this one
			if (!ar.hasError() || ar.isErrorLogSent())
				return false;
			//	retain error log for at least 15 minutes
			if (System.currentTimeMillis() < (ar.getFinishTime() + (1000 * 60 * 15)))
				return true;
			//	retain at most 32 uploads beyond the 15 minute limit
			return (finishedArCount <= 32);
		}
	}
	
	private class AsynchronousUpload extends AsynchronousRequest {
		private FieldValueInputStream nameDataStream;
		private NameDataFormat dataFormat;
		private FormDataReceiver nameData;
		UploadStringIterator usit;
		String userName;
		int created = 0;
		int updated = 0;
		int total = 0;
		UploadStringError[] errors = null;
		AsynchronousUpload(String name, FieldValueInputStream nameDataStream, NameDataFormat dataFormat, FormDataReceiver data, String userName) {
			super(name);
			this.nameDataStream = nameDataStream;
			this.dataFormat = dataFormat;
			this.nameData = data;
			this.userName = userName;
		}
		protected void init() throws Exception {
			
			//	update status
			this.setStatus("Setting up data import.");
			
			//	create iterator
			String encoding = this.nameDataStream.getEncoding();
			if (encoding == null)
				encoding = "ISO-8859-1";
			System.out.println("IMPORT DATA ENCODING IS " + encoding);
			this.usit = this.dataFormat.streamParseNames(new InputStreamReader(new FilterInputStream(this.nameDataStream) {
				public int read() throws IOException {
					int r = super.read();
					if ((r >= 32) || (r == 10) || (r == 13) || (r < 0))
						return r;
					else return 32;
				}
				public int read(byte[] b) throws IOException {
					return this.read(b, 0, b.length);
				}
				public int read(byte[] b, int off, int len) throws IOException {
					int r = super.read(b, off, len);
					for (int i = off; i < (off+r); i++) {
						if ((b[i] < 32) && (b[i] != 10) && (b[i] != 13) && (b[i] >= 0))
							b[i] = ((byte) 32);
					}
					return r;
				}
			}, encoding));
		}
		protected void process() throws Exception {
			
			//	update status
			this.setStatus("Data import running, so far" +
					" " + this.usit.getTotalCount() + " taxonomic names extracted" +
					", " + this.usit.getValidCount() + " valid ones" +
					", " + ((this.usit.getErrorCount() == 0) ? "no errors" : (this.usit.getErrorCount() + " errors")) +
					", " + this.total + " imported (" + this.created + " new / " + this.updated + " updated)" +
					".");
			
			//	parse and store data
			while (this.usit.hasNextUploadString()) {
				
				//	check for interruption
				if (this.isTerminated()) return;
				
				//	get next data element
				UploadString us = this.usit.nextUploadString();
				
				//	retrieve BinoBank client on the fly to use local bridge if possible
				BinoBankClient bbc = getBinoBankClient();
				
				//	parsed version user approved, store plain string and parsed version rigth away
				if (this.dataFormat.isParseUserApproved()) {
					PooledString ps = bbc.updateString(us, this.userName);
					this.total++;
					if (ps.wasCreated())
						this.created++;
					else if (ps.wasUpdated())
						this.updated++;
				}
				
				//	parsed version not user approved, store plain string first and then update with auto-generated parsed version
				else {
					PooledString ps = bbc.updateString(us.stringPlain, this.userName);
					this.total++;
					if (ps.wasCreated())
						this.created++;
					if ((ps.getParseChecksum() == null) && (us.stringParsed != null)) {
						bbc.updateString(us.stringPlain, us.stringParsed, "AutomatedParser");
						if (!ps.wasCreated())
							this.updated++;
					}
				}
				
				//	update progress percentage
				if (this.nameDataStream.getBytesRead() == this.nameDataStream.fieldLength) {
					int erc = (this.usit.getTotalCount() + this.usit.estimateRemaining());
					if (erc < 1)
						erc = 1;
					this.setPercentFinished((this.usit.getTotalCount() * 100) / erc);
				}
				else this.setPercentFinished((this.nameDataStream.getBytesRead() * 100) / this.nameDataStream.fieldLength);
				
				//	update status
				this.setStatus("Data import running, so far" +
						" " + this.usit.getTotalCount() + " taxonomic names extracted" +
						", " + this.usit.getValidCount() + " valid ones" +
						", " + ((this.usit.getErrorCount() == 0) ? "no errors" : (this.usit.getErrorCount() + " errors")) +
						", " + this.total + " imported (" + this.created + " new / " + this.updated + " updated)" +
						".");
				
				//	wait a little bit
				try {
					Thread.sleep(333);
				} catch (InterruptedException ie) {}
			}
			
			//	store erroneous parts for user retrieval
			UploadStringError[] errors = this.usit.getErrors();
			if (errors.length != 0)
				this.errors = errors;
			
			//	update status
			this.setPercentFinished(100);
			this.setStatus("Data import finished," +
					" " + this.usit.getTotalCount() + " taxonomic names extracted" +
					", " + this.usit.getValidCount() + " valid ones" +
					", " + ((this.usit.getErrorCount() == 0) ? "no errors" : (this.usit.getErrorCount() + " errors")) +
					", " + this.total + " imported (" + this.created + " new / " + this.updated + " updated)" +
					".");
			
			//	close data stream
			this.nameDataStream.close();
		}
		protected void cleanup() throws Exception {
			this.nameData.dispose();
		}
		public String getResultLink(HttpServletRequest request) {
			return (request.getContextPath() + request.getServletPath());
		}
		public String getResultLinkLabel() {
			return "Return to Upload Form";
		}
		public String getCancelLinkLabel() {
			return "Cancel Upload";
		}
		public String getCancellationConfirmDialogText() {
			return "Do you really want to cancel your reference upload?";
		}
		public String getCancelledLink(HttpServletRequest request) {
			return this.getResultLink(request);
		}
		public String getCancelledLinkLabel() {
			return this.getResultLinkLabel();
		}
		public String getRunningStatusLabel() {
			return "Processing your reference data, please wait";
		}
		public String getCancelledStatusLabel() {
			return "Reference data upload cancelled";
		}
		public String getFinishedStatusLabel() {
			return "Reference data upload finished";
		}
		public boolean hasError() {
			return (super.hasError() || (this.errors != null));
		}
		public String getErrorMessage() {
			String em = super.getErrorMessage();
			if (em != null)
				return em;
			return ((this.errors == null) ? null : ("Found " + this.errors.length + " erroneous references."));
		}
		public boolean sendErrorReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
			if (super.sendErrorReport(request, response))
				return true;
			if (this.errors == null)
				return false;
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			for (int e = 0; e < this.errors.length; e++) {
				bw.write(this.errors[e].error);
				bw.newLine();
				bw.newLine();
				bw.write(this.errors[e].source);
				bw.newLine();
				bw.newLine();
				bw.newLine();
			}
			bw.flush();
			return true;
		}
	}
	
	private HtmlPageBuilder getUploadStatusPageBuilder(HttpServletRequest request, final String uploadId, final String message, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeForm".equals(type))
					this.includeUploadForm();
				else if ("includeResult".equals(type)) {
					if (message != null)
						this.includeUploadStatistics();
				}
				else super.include(type, tag);
			}
			private void includeUploadForm() throws IOException {
				this.writeLine("<table class=\"uploadTable\">");
				this.writeLine("<tr class=\"uploadTableBody\">");
				this.writeLine("<td class=\"uploadTableCell\">");
				uploadHandler.writeStatusDisplay(this.asWriter(), request, uploadId);
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
			}
			private void includeUploadStatistics() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.write("<span class=\"resultString\">" + html.escape(message) + "</span>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
			}
			protected String getPageTitle(String title) {
				return "BinoBank Upload Processing ...";
			}
		};
	}
}