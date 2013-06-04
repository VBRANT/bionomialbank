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
package de.uka.ipd.idaho.binoBank.apps.parser;

import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.binoBank.apps.BinoBankAppServlet;
import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.util.AbstractAnalyzer;
import de.uka.ipd.idaho.gamta.util.Analyzer;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.gamta.util.constants.LiteratureConstants;
import de.uka.ipd.idaho.gamta.util.feedback.FeedbackPanel;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler.AsynchronousRequest;
import de.uka.ipd.idaho.gamta.util.feedback.panels.AnnotationEditorFeedbackPanel;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder.HtmlPageBuilderHost;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameConstants;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicNameUtils.TaxonomicName;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem;
import de.uka.ipd.idaho.plugins.taxonomicNames.TaxonomicRankSystem.Rank;
import de.uka.ipd.idaho.stringUtils.StringIndex;

/**
 * @author sautter
 */
public class BinoBankParserServlet extends BinoBankAppServlet implements TaxonomicNameConstants {
	
	private AsynchronousRequestHandler parseHandler;
	
	private Analyzer nameParser;
	
	private Properties nameParseParams = new Properties();
	
	private TaxonomicRankSystem rankSystem;
	private Rank[] ranks;
	private HashMap ranksByName = new HashMap();
	private String[][] rankAbbreviations;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webServices.WebServiceFrontendServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get generic rank system (we'll be handling names from all domains)
		this.rankSystem = TaxonomicRankSystem.getRankSystem(null);
		
		//	get ranks, index them, and store abbreviations
		this.ranks = this.rankSystem.getRanks();
		this.rankAbbreviations = new String[this.ranks.length][];
		for (int r = 0; r < this.ranks.length; r++) {
			this.ranksByName.put(this.ranks[r].name, this.ranks[r]);
			this.rankAbbreviations[r] = this.ranks[r].getAbbreviations();
			for (int a = 0; a < this.rankAbbreviations[r].length; a++) {
				if (this.rankAbbreviations[r][a].endsWith("."))
					this.rankAbbreviations[r][a] = this.rankAbbreviations[r][a].substring(0, (this.rankAbbreviations[r][a].length() - 1));
				this.rankAbbreviations[r][a] = this.rankAbbreviations[r][a].toLowerCase();
			}
		}
		
		//	create parse handler
		this.parseHandler = new AsynchronousParsingHandler();
		try {
			this.parseHandler.setFeedbackTimeout(Integer.parseInt(this.getSetting("feedbackTimeout", ("" + this.parseHandler.getFeedbackTimeout()))));
		} catch (RuntimeException re) {}
		
		//	create name parsing analyzer
		AnalyzerDataProvider nameParseAdp = new AnalyzerDataProviderFileBased(this.dataFolder);
		this.nameParser = new TaxonomicNameParser();
		this.nameParser.setDataProvider(nameParseAdp);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check for status request, etc.
		if (this.parseHandler.handleRequest(request, response))
			return;
		
		//	check for invokation
		String user = request.getParameter(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Reference ID, User Name, or Result URL Missing");
			return;
		}
		String apId = this.parseHandler.createRequest(request, user);
		if (apId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Reference ID, User Name, or Result URL Missing");
			return;
		}
		
		//	send status page
		this.parseHandler.sendStatusDisplayFrame(request, apId, response);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	feedback submission
		if (this.parseHandler.handleRequest(request, response))
			return;
		
		//	other post request, send error
		response.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST Not Supported");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		super.exit();
		this.parseHandler.shutdown();
	}
	
	private class AsynchronousParsingHandler extends AsynchronousRequestHandler {
		AsynchronousParsingHandler() {
			super(false);
		}
		public AsynchronousRequest buildAsynchronousRequest(HttpServletRequest request) throws IOException {
			
			//	get invokation parameters
			String nameId = request.getParameter(STRING_ID_ATTRIBUTE);
			String user = request.getParameter(USER_PARAMETER);
			String resultUrl = request.getParameter("resultUrl");
			if ((nameId == null) || (user == null) || (resultUrl == null))
				return null;
			
			//	create parse request
			return new AsynchronousParser(user, nameId, resultUrl);
		}
		protected HtmlPageBuilderHost getPageBuilderHost() {
			return BinoBankParserServlet.this;
		}
		protected void sendHtmlPage(HtmlPageBuilder hpb) throws IOException {
			BinoBankParserServlet.this.sendHtmlPage(hpb);
		}
		protected void sendPopupHtmlPage(HtmlPageBuilder hpb) throws IOException {
			BinoBankParserServlet.this.sendPopupHtmlPage(hpb);
		}
		protected void sendStatusDisplayIFramePage(HtmlPageBuilder hpb) throws IOException {
			BinoBankParserServlet.this.sendHtmlPage("processing.html", hpb);
		}
		protected void sendFeedbackFormPage(HtmlPageBuilder hpb) throws IOException {
			BinoBankParserServlet.this.sendHtmlPage("feedback.html", hpb);
		}
		protected boolean retainAsynchronousRequest(AsynchronousRequest ar, int finishedArCount) {
			/* client not yet notified that parsing is complete, we have to hold
			 * on to this one, unless last status update was more than 5 minutes
			 * ago, which indicates the client side is likely dead */
			if (!ar.isFinishedStatusSent())
				return (System.currentTimeMillis() < (ar.getLastAccessTime() + (1000 * 60 * 5)));
			/* once the client has been notified that parsing is finished, we
			 * don't need this one any longer */
			return false;
		}
	}
	
	private class AsynchronousParser extends AsynchronousRequest {
		String userName;
		String nameId;
		String resultUrl;
		PooledString name;
		AsynchronousParser(String userName, String nameId, String resultUrl) {
			super("Parse taxonomic name " + nameId);
			this.userName = userName;
			this.nameId = nameId;
			this.resultUrl = resultUrl;
		}
		protected void init() throws Exception {
			
			//	update status
			this.setStatus("Loading taxonomic name ...");
			
			//	load name from BinoBank
			this.name = getBinoBankClient().getString(this.nameId);
			if (this.name == null)
				throw new IOException("Invalid Name ID " + this.nameId);
			
			//	update status
			this.setStatus("Taxonomic name loaded.");
			this.setPercentFinished(5);
		}
		protected void process() throws Exception {
			
			//	wrap name string
			MutableAnnotation taxNameDoc = Gamta.newDocument(Gamta.newTokenSequence(this.name.getStringPlain(), Gamta.INNER_PUNCTUATION_TOKENIZER));
			taxNameDoc.setAttribute(LiteratureConstants.DOCUMENT_ID_ATTRIBUTE, this.nameId);
			taxNameDoc.addAnnotation(MutableAnnotation.PARAGRAPH_TYPE, 0, taxNameDoc.size());
			MutableAnnotation taxNameAnnot = taxNameDoc.addAnnotation(TAXONOMIC_NAME_ANNOTATION_TYPE, 0, taxNameDoc.size());
			Properties storedDetails = new Properties();
			
			//	get parsed name
			MutableAnnotation parsedNameDoc = null;
			if (this.name.getStringParsed() != null)	try {
//				System.out.println("GOT PARSED REF: " + this.name.getStringParsed());
				parsedNameDoc = Gamta.newDocument(Gamta.newTokenSequence("", taxNameDoc.getTokenizer()));
				SgmlDocumentReader.readDocument(new StringReader(this.name.getStringParsed()), parsedNameDoc);
			} catch (IOException ioe) { /* this is never gonna happen with a StringReader, but Java don't know */ }
			
			//	annotate details from parsed version
			if (parsedNameDoc != null) {
				CountingTokenSequence[] ctss = extractDetails(parsedNameDoc, storedDetails);
				
				//	annotate results
				for (int a = 0; a < ctss.length; a++) {
					ctss[a].reset();
					annotate(taxNameAnnot, ctss[a]);
					if (ctss[a].remaining() == 0)
						ctss[a] = null;
				}
				
				//	annotate remainders of partial matches
				for (int a = 0; a < ctss.length; a++) {
					if (ctss[a] == null)
						continue;
					System.out.println("UNMATCHED " + ctss[a].type + " '" + ctss[a].toString() + "'");
				}
				
				//	remove consumed token marker
				for (int t = 0; t < taxNameAnnot.size(); t++)
					taxNameAnnot.tokenAt(t).removeAttribute("C");
			}
			
			//	perform primitive parse using suffixes and abbreviations from rank system if no parse given
			else  {
				
				//	rank epithets occur in decending order
				int firstPossibleRankIndex = 0;
				
				//	authority starts where last epithet ends
				int authorityStartIndex = 0;
				
				//	try to find epithets first, using labels and suffixes
				for (int t = 0; t < taxNameAnnot.size(); t++) {
					String token = taxNameAnnot.valueAt(t).toLowerCase();
					
					//	possible punctiation of hybrid or higher classification in parenthesis
					if ("x".equals(token) || "(".equals(token)) {
						firstPossibleRankIndex = 0;
						continue;
					}
					
					//	try and match ranks
					Rank matchedRank = null;
					int matchedRankIndex = 0;
					int matchedSuffixLength = 0;
					for (int r = firstPossibleRankIndex; r < ranks.length; r++) {
						
						//	try suffixes first
						if (token.endsWith(ranks[r].getSuffix().toLowerCase()) && (matchedSuffixLength < ranks[r].getSuffix().length())) {
							matchedRank = ranks[r];
							matchedRankIndex = r;
							matchedSuffixLength = ranks[r].getSuffix().length();
							continue;
						}
						
						//	match found, we're done
						if (matchedRank != null)
							break;
						
						//	no place 
						if ((t < 1) || (".".equals(taxNameAnnot.valueAt(t-1)) && (t < 2)))
							continue;
						String abbreviation = (".".equals(taxNameAnnot.valueAt(t-1)) ? taxNameAnnot.valueAt(t-2) : taxNameAnnot.valueAt(t-1)).toLowerCase();
						for (int a = 0; a < rankAbbreviations[r].length; a++) {
							if (abbreviation.equals(rankAbbreviations[r][a])) {
								matchedRank = ranks[r];
								matchedRankIndex = r;
								break;
							}
						}
						if (matchedRank != null)
							break;
					}
					
					//	annotate any rank we matched
					if (matchedRank != null) {
						taxNameAnnot.addAnnotation(matchedRank.name, t, 1);
						authorityStartIndex = (t+1);
						firstPossibleRankIndex = (matchedRankIndex + 1);
					}
				}
				
				//	try to find authority if some epithet found
				if (authorityStartIndex != 0) {
					int authorityNameEndIndex = taxNameAnnot.size();
					
					//	try to find year from end
					for (int t = taxNameAnnot.size()-1; t >= authorityStartIndex; t--)
						if (taxNameAnnot.valueAt(t).matches("[12][0-9]{3}")) {
							taxNameAnnot.addAnnotation(AUTHORITY_YEAR_ATTRIBUTE, t, 1);
							authorityNameEndIndex = t;
						}
					
					//	annotate name
					if (authorityStartIndex < authorityNameEndIndex)
						taxNameAnnot.addAnnotation(AUTHORITY_NAME_ATTRIBUTE, authorityStartIndex, (authorityNameEndIndex - authorityStartIndex));
				}
			}
			
			//	update status
			this.setStatus("Getting user input ...");
			this.setPercentFinished(50);
			
			//	get feedback
			nameParser.process(taxNameAnnot, nameParseParams);
			
			//	update status
			this.setPercentFinished(90);
			this.setStatus("Parsing finished.");
			
			//	verify order of ranks
			Annotation[] epithets = taxNameAnnot.getAnnotations();
			int lastRankSignificance = -1;
			boolean gotAuthority = true;
			for (int e = 0; e < epithets.length; e++) {
				Rank rank = ((Rank) ranksByName.get(epithets[e].getType()));
				
				//	whole taxonomic name, or authority
				if (rank == null) {
					if (AUTHORITY_NAME_ATTRIBUTE.equals(epithets[e].getType()) || AUTHORITY_YEAR_ATTRIBUTE.equals(epithets[e].getType()))
						gotAuthority = true;
					continue;
				}
				
				//	epithet after authority
				if (gotAuthority) {
					
					//	can happen in a hybrid name in botanical literature ...
					if ((epithets[e].getStartIndex() > 0) && "x".equalsIgnoreCase(taxNameAnnot.valueAt(epithets[e].getStartIndex() - 1)))
						gotAuthority = false;
					
					//	... but not otherwise
					throw new RuntimeException("Invalid Parse: '" + epithets[e].getType() + "' epithet after authority");
				}
				
				//	current epithet more significant than last one, we're OK
				if (lastRankSignificance < rank.getRelativeSignificance()) {
					lastRankSignificance = rank.getRelativeSignificance();
					continue;
				}
				
				//	two epithets with the same rank
				if (lastRankSignificance == rank.getRelativeSignificance()) {
					
					//	can happen in a hybrid name in botanical literature ...
					if ((epithets[e].getStartIndex() > 0) && "x".equalsIgnoreCase(taxNameAnnot.valueAt(epithets[e].getStartIndex() - 1)))
						continue;
					
					//	... but not otherwise
					throw new RuntimeException("Invalid Parse: duplicate rank '" + epithets[e].getType() + "'");
				}
				
				//	high rank after low rank
				if (lastRankSignificance > rank.getRelativeSignificance()) {
					
					//	can happen in a hybrid name consisting of two binomials in botanical literature ...
					if ((epithets[e].getStartIndex() > 0) && "x".equalsIgnoreCase(taxNameAnnot.valueAt(epithets[e].getStartIndex() - 1))) {
						lastRankSignificance = rank.getRelativeSignificance();
						continue;
					}
					
					//	... as well as for standalone genera followed by higher classification in parentheses ...
					if ((epithets[e].getStartIndex() > 0) && "(".equalsIgnoreCase(taxNameAnnot.valueAt(epithets[e].getStartIndex() - 1))) {
						lastRankSignificance = rank.getRelativeSignificance();
						continue;
					}
					
					//	... but not otherwise
					throw new RuntimeException("Invalid Parse: invalid rank sequence - '" + epithets[e].getType() + "' after '" + epithets[e-1].getType() + "'");
				}
			}
			
			//	generate DwC
			TaxonomicName taxName = TaxonomicNameUtils.genericXmlToTaxonomicName(taxNameAnnot, rankSystem);
			String nameParsed = taxName.toDwcXml();
			if (nameParsed == null)
				throw new RuntimeException("Incomplete Parse");
			
			//	store reference back to BinoBank
			getBinoBankClient().updateString(this.name.getStringPlain(), nameParsed, this.userName);
			
			//	update status
			this.setPercentFinished(100);
			this.setStatus("Parsed reference stored.");
		}
		public boolean doImmediateResultForward() {
			return true;
		}
		public String getResultLink(HttpServletRequest request) {
			return this.resultUrl;
		}
		public String getResultLinkLabel() {
			return "Return to Parsed Taxonomic Name";
		}
		public String getFinishedStatusLabel() {
			return "Parsed finished, returning to taxonomic name";
		}
		public String getRunningStatusLabel() {
			return "The taxonomic name is being parsed, please wait";
		}
	}
	
	private static class CountingTokenSequence {
		String type;
		private String plain;
		private StringIndex counts = new StringIndex(true);
		private StringIndex rCounts = new StringIndex(true);
		private ArrayList tokens = new ArrayList();
		private LinkedList rTokens = new LinkedList();
		public CountingTokenSequence(String type, TokenSequence tokens) {
			this.type = type;
			this.plain = TokenSequenceUtils.concatTokens(tokens, true, true);
			for (int t = 0; t < tokens.size(); t++) {
				String token = tokens.valueAt(t);
//				if (!Gamta.isPunctuation(token)) {
					this.counts.add(token);
					this.tokens.add(token);
//				}
			}
		}
		public String toString() {
			return this.plain;
		}
//		public boolean contains(String token) {
//			return (this.rCounts.getCount(token) < this.counts.getCount(token));
//		}
		public boolean remove(String token) {
			if (this.rCounts.getCount(token) < this.counts.getCount(token)) {
				this.rCounts.add(token);
				this.rTokens.addLast(token);
				return true;
			}
			else return false;
		}
//		public int matched() {
//			return this.rCounts.size();
//		}
		public int remaining() {
			return (this.counts.size() - this.rCounts.size());
		}
		public String next() {
			return ((this.rTokens.size() < this.tokens.size()) ? ((String) this.tokens.get(this.rTokens.size())) : null);
		}
		public void reset() {
			this.rCounts.clear();
			this.rTokens.clear();
		}
	}
	
	private CountingTokenSequence[] extractDetails(MutableAnnotation nameDoc, Properties storedDetails) {
		TaxonomicName taxName = TaxonomicNameUtils.dwcXmlToTaxonomicName(nameDoc, this.rankSystem);
		ArrayList ctss = new ArrayList();
		for (int r = 0; r < this.ranks.length; r++) {
			String epithet = taxName.getEpithet(this.ranks[r].name);
			if (epithet != null)
				ctss.add(new CountingTokenSequence(this.ranks[r].name, Gamta.newTokenSequence(epithet, nameDoc.getTokenizer())));
		}
		String authorityName = taxName.getAuthorityName();
		if (authorityName != null)
			ctss.add(new CountingTokenSequence(AUTHORITY_NAME_ATTRIBUTE, Gamta.newTokenSequence(authorityName, nameDoc.getTokenizer())));
		int authorityYear = taxName.getAuthorityYear();
		if (authorityYear > 0)
			ctss.add(new CountingTokenSequence(AUTHORITY_YEAR_ATTRIBUTE, Gamta.newTokenSequence(("" + authorityYear), nameDoc.getTokenizer())));
		return ((CountingTokenSequence[]) ctss.toArray(new CountingTokenSequence[ctss.size()]));
	}
	
	private void annotate(MutableAnnotation taxName, CountingTokenSequence cts) {
		System.out.println("Matching " + cts.type + " '" + cts.toString() + "'");
		
		//	try full sequential match
		for (int t = 0; t < taxName.size(); t++) {
			if (taxName.tokenAt(t).hasAttribute("C"))
				continue;
			String token = taxName.valueAt(t);
			if (!token.equals(cts.next()))
				continue;
			
			//	got anchor, attempt match
			cts.remove(token);
			System.out.println(" - found sequence anchor '" + token + "', " + cts.remaining() + " tokens remaining");
			
			//	found end of one-sized sequence, match successful
			if (cts.remaining() == 0) {
				Annotation a = taxName.addAnnotation(cts.type, t, 1);
				a.firstToken().setAttribute("C", "C");
				System.out.println("   ==> single-token match: " + a.toXML());
				return;
			}
			
			//	continue matching
			for (int l = (t+1); l < taxName.size(); l++) {
				token = taxName.valueAt(l);
				
				//	next token continues match
				if (token.equals(cts.next())) {
					cts.remove(token);
					System.out.println("   - found continuation '" + token + "', " + cts.remaining() + " tokens remaining");
					
					//	found end of sequence, match successful
					if (cts.remaining() == 0) {
						Annotation a = taxName.addAnnotation(cts.type, t, (l-t+1));
						for (int c = 0; c < a.size(); c++)
							a.tokenAt(c).setAttribute("C", "C");
						System.out.println("   ==> sequence match: " + a.toXML());
						return;
					}
				}
				
				//	next token is punctuation, ignore it
				else if (Gamta.isPunctuation(token)) {
					System.out.println("   - ignoring punctuation '" + token + "'");
					continue;
				}
				
				//	next token does not match, reset matcher and start over
				else {
					System.out.println("   ==> cannot continue with '" + token + "'");
					cts.reset();
					break;
				}
			}
		}
	}
	
	private class TaxonomicNameParser extends AbstractAnalyzer {
		private LinkedHashMap typeHighlightColors = new LinkedHashMap();
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.AbstractAnalyzer#setDataProvider(de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider)
		 */
		public void setDataProvider(AnalyzerDataProvider dataProvider) {
			super.setDataProvider(dataProvider);
			
			//	load highlight colors for individual ranks
			try {
				Settings highlightColors = Settings.loadSettings(this.dataProvider.getInputStream("typeHighlightColors.cnfg"));
				String rgbAn = highlightColors.getSetting(AUTHORITY_NAME_ATTRIBUTE);
				this.typeHighlightColors.put(AUTHORITY_NAME_ATTRIBUTE, ((rgbAn == null) ? Color.getHSBColor(((float) Math.random()), 0.5f, 1.0f) : FeedbackPanel.getColor(rgbAn)));
				String rgbAy = highlightColors.getSetting(AUTHORITY_YEAR_ATTRIBUTE);
				this.typeHighlightColors.put(AUTHORITY_YEAR_ATTRIBUTE, ((rgbAy == null) ? Color.getHSBColor(((float) Math.random()), 0.5f, 1.0f) : FeedbackPanel.getColor(rgbAy)));
				for (int r = 0; r < ranks.length; r++) {
					String rgb = highlightColors.getSetting(ranks[r].name);
					this.typeHighlightColors.put(ranks[r].name, ((rgb == null) ? Color.getHSBColor(((float) Math.random()), 0.5f, 1.0f) : FeedbackPanel.getColor(rgb)));
				}
			} catch (IOException ioe) {}
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.gamta.util.Analyzer#process(de.uka.ipd.idaho.gamta.MutableAnnotation, java.util.Properties)
		 */
		public void process(MutableAnnotation data, Properties parameters) {
			
			//	get taxonomic name
			MutableAnnotation[] taxNames = data.getMutableAnnotations(TAXONOMIC_NAME_ANNOTATION_TYPE);
			if (taxNames.length != 1)
				return;
			
			//	create feedback panel
			AnnotationEditorFeedbackPanel aefp = new AnnotationEditorFeedbackPanel("Parse Taxonomic Name String");
			aefp.setLabel("<HTML>Please check if the epithets and authority of this taxonomic name are selected corretly." +
							"<BR>Mark only the last authority, i.e., the one after the most significant epithet." +
							"<BR>For names of hybrids, there can be an authority for both parts, but need not." +
							"<BR>Please mark the whole authority except for the year as '" + AUTHORITY_NAME_ATTRIBUTE + "' and the year as '" + AUTHORITY_YEAR_ATTRIBUTE + "' (if given)." +
							"<BR>To change a marking, select a piece of the name string and right-click.</HTML>");
			for (Iterator hit = this.typeHighlightColors.keySet().iterator(); hit.hasNext();) {
				String type = ((String) hit.next());
				aefp.addDetailType(type, ((Color) this.typeHighlightColors.get(type)));
			}
			aefp.addAnnotation(taxNames[0]);
			
			//	get feedback
			String f = aefp.getFeedback();
			
			//	write feedback through
			if ("OK".equals(f))
				aefp.writeChanges();
		}
	}
//	
//	just a little utility method for generating the default colors for the ranks
//	public static void main(String[] args) throws Exception {
//		TaxonomicRankSystem rankSystem = TaxonomicRankSystem.getRankSystem(null);
//		
//		//	index ranks
//		Rank[] ranks = rankSystem.getRanks();
//		for (int r = 0; r < ranks.length; r++) {
//			double h = (((double) r) / ranks.length);
//			Color rc = Color.getHSBColor(((float) h), 0.5f, 1.0f);
//			System.out.println(ranks[r].name + " = \"" + FeedbackPanel.getRGB(rc) + "\";");
//		}
//	}
}