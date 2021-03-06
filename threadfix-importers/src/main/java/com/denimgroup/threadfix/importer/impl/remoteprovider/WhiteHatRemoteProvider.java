////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2015 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.importer.impl.remoteprovider;

import com.denimgroup.threadfix.annotations.RemoteProvider;
import com.denimgroup.threadfix.data.entities.Finding;
import com.denimgroup.threadfix.data.entities.RemoteProviderApplication;
import com.denimgroup.threadfix.data.entities.Scan;
import com.denimgroup.threadfix.data.entities.ScannerType;
import com.denimgroup.threadfix.exception.RestIOException;
import com.denimgroup.threadfix.importer.impl.remoteprovider.utils.HttpResponse;
import com.denimgroup.threadfix.importer.impl.remoteprovider.utils.RemoteProviderHttpUtils;
import com.denimgroup.threadfix.importer.util.DateUtils;
import com.denimgroup.threadfix.importer.util.HandlerWithBuilder;
import com.denimgroup.threadfix.importer.util.ScanUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.denimgroup.threadfix.CollectionUtils.*;
import static com.denimgroup.threadfix.importer.impl.remoteprovider.utils.RemoteProviderHttpUtilsImpl.getImpl;
import static com.denimgroup.threadfix.util.DateUtils.getDaysBetween;

@RemoteProvider(name = "WhiteHat Sentinel")
public class WhiteHatRemoteProvider extends AbstractRemoteProvider {

	private static final String SITES_URL = "https://sentinel.whitehatsec.com/api/site/";
	private static final String VULNS_URL = "https://sentinel.whitehatsec.com/api/vuln/";
	private static final String EXTRA_PARAMS = "&display_attack_vectors=1&query_site=";
	private static final int PAGE_LIMIT = 1000;

	private String apiKey = null;
	
	private List<Calendar> scanDateList = null;
	private Map<Finding, List<DateStatus>> findingDateStatusMap = null;

    RemoteProviderHttpUtils utils = getImpl(this.getClass());

	// using these findings instead of the finding list lets us do faster contains() lookups.
	// set lookup characteristics are O(1) and list is O(n)
	Set<Finding> findings = set();

    public WhiteHatRemoteProvider() {
		super(ScannerType.SENTINEL);
	}

	@Override
	public List<Scan> getScans(RemoteProviderApplication remoteProviderApplication) {
		LOG.info("Retrieving a WhiteHat scan.");

		apiKey = remoteProviderApplication.getRemoteProviderType().getApiKey();
		
		String appName = remoteProviderApplication.getNativeName();

		// TODO take these calls out entirely and save the data in the RemoteProviderApplication object instead
		// we need to follow through with this pagination too.
		WhiteHatSitesParser parser = getParserWithAllApps();

		String siteId = parser.map.get(appName);
		if (siteId == null) {
			LOG.warn("No build ID was parsed.");
			return null; // we failed.
		} else {
			LOG.info("Retrieved build ID " + siteId + " for application " + appName);
		}
		
		String url = VULNS_URL + "?key=" + apiKey + EXTRA_PARAMS + siteId;
		
		LOG.info("Requesting site ID " + siteId);

        HttpResponse response = utils.getUrl(url);
        if (response.isValid()) {
            inputStream = response.getInputStream();
        } else {
			LOG.warn("Received a bad response from WhiteHat servers, returning null.");
			return null;
		}

		DefaultHandler scanParser =
                remoteProviderApplication.getRemoteProviderType().getMatchSourceNumbersNullSafe() ?
                        new MatchingParser() :
                        new ThreadFixStyleParser();

		List<Scan> scans = parseSAXInputWhiteHat(scanParser);
		
		if (scans == null || scans.size() == 0) {
			LOG.warn("No scan was parsed, returning null.");
			return null;
		}
		
		for (Scan resultScan : scans) {
			resultScan.setApplicationChannel(remoteProviderApplication.getApplicationChannel());
        }
		
		LOG.info("WhiteHat "+ scans.size() +" scans successfully parsed.");
		
		return filterScans(scans);
	}

	@Override
	public List<RemoteProviderApplication> fetchApplications() {
		if (remoteProviderType == null || remoteProviderType.getApiKey() == null) {
			LOG.warn("Insufficient credentials.");
			return null;
		}

		apiKey = remoteProviderType.getApiKey();

		WhiteHatSitesParser parser = getParserWithAllApps();
		if (parser == null) {
			return null;
		}

		return parser.getApplications();
	}

	private WhiteHatSitesParser getParserWithAllApps() {
		int pageOffset = 0;
		String message = "Unable to retrieve applications. WhiteHat response status: ";
		String paginationSettings = "&page:limit=" + PAGE_LIMIT + "&page:offset=" + pageOffset;

		WhiteHatSitesParser parser = new WhiteHatSitesParser();

		HttpResponse response = utils.getUrl(SITES_URL + "?key=" + apiKey + paginationSettings);

		if (response.isValid()) {
            parse(response.getInputStream(), parser);
        } else {
			LOG.error(message + response.getStatus());
			throw new RestIOException(message, response.getStatus());
        }

		int totalSitesAvailable = parser.getTotalSites();

		while (parser.getApplications().size() < totalSitesAvailable) {
            pageOffset += PAGE_LIMIT;
            paginationSettings = "&page:limit=" + PAGE_LIMIT + "&page:offset=" + pageOffset;

            response = utils.getUrl(SITES_URL + "?key=" + apiKey + paginationSettings);

            if (response.isValid()) {
                parse(response.getInputStream(), parser);
            } else {
				LOG.error(message + response.getStatus());
				throw new RestIOException(message, response.getStatus());
            }
        }
		return parser;
	}

	/**
	 * This method parses input file to list of scan
	 * @param handler
	 * @return
	 */
	private List<Scan> parseSAXInputWhiteHat(DefaultHandler handler) {
		LOG.debug("Starting WhiteHat SAX Parsing.");

		Calendar currentScanDate = null;
		
		if (inputStream == null)
			return null;
		
		List<Scan> scanList = list();
		
		ScanUtils.readSAXInput(handler, "Done Parsing.", inputStream);
		Collections.sort(scanDateList);

		for (Calendar d : scanDateList) {
			date = d;

			if (currentScanDate == null || getDaysBetween(date, currentScanDate) < 0) {
				if (findings != null && !findings.isEmpty()) {
					date = currentScanDate;
					scanList.add(makeNewScan());
					date = d;

				}
				saxFindingList = list();
			}

			currentScanDate = date;

			// switching to iterating through entries saves the cost of doing Map lookups
			for (Map.Entry<Finding, List<DateStatus>> entry : findingDateStatusMap.entrySet()) {
				Finding finding = entry.getKey();
				List<DateStatus> findingDateInfo = entry.getValue();
				Collections.sort(findingDateInfo);

				DateStatus lastDate = findingDateInfo.get(findingDateInfo.size() - 1);
				// getting milliseconds in the dates lets us skip the Calendar.clone()
				// in the Calendar compareTo() implementation
				long dateInMillis = date.getTimeInMillis(),
						lastDateMillis = lastDate.getTimeMillis(),
						firstDateMillis = findingDateInfo.get(0).getDate().getTimeInMillis();

				boolean dateBeforeFirstDate = dateInMillis < firstDateMillis;
				boolean dateAfterLastDate   = dateInMillis >= lastDateMillis;

				if (dateBeforeFirstDate) {
					continue;
				}

				if (dateAfterLastDate) {
					if (lastDate.getStatus().equals("open")) {
						if (!findings.contains(finding)) {
							findings.add(finding);
						}
					} else {
						if (findings.contains(finding)) {
							findings.remove(finding);
						}
					}
					continue;
				}

				// if we get here, the date must be in the right range. There's no need to check
				if (!findings.contains(finding)) {
					findings.add(finding);
				}
			}
		}

		scanList.add(makeNewScan());

		return scanList;
	}

	private Scan makeNewScan() {

		Scan scan = new Scan();
		scan.setImportTime(date);
		addFindingsOrCopyFindings(scan);
		scan.setApplicationChannel(applicationChannel);

		if (date != null) {
			LOG.debug("SAX Parser found the scan date: " + date.getTime().toString());
		} else {
			LOG.warn("SAX Parser did not find the date.");
		}

		if (scan.getFindings() != null && scan.getFindings().size() != 0)
			LOG.debug("SAX Parsing successfully parsed " + scan.getFindings().size() +" Findings.");
		else
			LOG.warn("SAX Parsing did not find any Findings.");
		
		return scan;
	}

	// the calendar here was for debugging, should refactor to just a map
	private void addFindingsOrCopyFindings(Scan scan) {
		scan.setFindings(listOf(Finding.class));
		for (Finding finding : findings) {
			if (finding.getScan() != null) {
				Finding copyFinding = copyNativeId(finding);
				scan.getFindings().add(copyFinding);
				copyFinding.setScan(scan);

			} else {
				finding.setScan(scan);
				scan.getFindings().add(finding);
			}
		}
	}

	// so as to save memory, we'll use a copy of the findings with just the native ID
	// this way when we channel merge maps will be created and we can save memory vs
	// creating and copying all fields in Finding
	private Finding copyNativeId(Finding finding) {
		Finding newFinding = new Finding();
		newFinding.setNativeId(finding.getNativeId());
		return newFinding;
	}

	public class WhiteHatSitesParser extends HandlerWithBuilder {
		
		public Map<String, String> map = map();
		
		private String currentId = null;
		private boolean grabLabel;

        private boolean grabTotalSites;
        private String totalSites = null;

		public List<RemoteProviderApplication> getApplications() {
			List<RemoteProviderApplication> apps = list();
			for (String label : map.keySet()) {
				RemoteProviderApplication remoteProviderApplication = new RemoteProviderApplication();
	    		remoteProviderApplication.setNativeName(label);
	    		remoteProviderApplication.setNativeId(map.get(label));
	    		remoteProviderApplication.setRemoteProviderType(remoteProviderType);
	    		apps.add(remoteProviderApplication);
			}
			return apps;
		}

        public int getTotalSites() {
            int total = 0;
            if (totalSites != null) {
                try {
                    total = Integer.valueOf(totalSites);
                } catch (NumberFormatException e) {
                    total = 0;
                }
            }
            return total;
        }

	    public void startElement(String uri, String name, String qName, Attributes atts) throws SAXException {
	    	if ("site".equals(qName)) {
	    		currentId = atts.getValue("id");
	    	} else if ("label".equals(qName)) {
	    		grabLabel = true;
	    	} else if ("total_sites".equals(qName)) {
                grabTotalSites = true;
	    	}
	    }
	    
	    public void endElement(String uri, String name, String qName) {
	    	if (grabLabel) {
	    		String text = getBuilderText();
	    		if (text != null) {
	    			map.put(text, currentId);
	    		}
	    		currentId = null;
	    		grabLabel = false;
	    	}
            if (grabTotalSites) {
                totalSites = getBuilderText();
                grabTotalSites = false;
	        }
	    }
	    
	    public void characters (char ch[], int start, int length) {
	    	if (grabLabel) {
	    		addTextToBuilder(ch, start, length);
	    	}
            if (grabTotalSites) {
	    		addTextToBuilder(ch, start, length);
	    	}
	    }
	}
	
	public class ThreadFixStyleParser extends HandlerWithBuilder {
		
		public Finding finding = new Finding();
		
		private Map<FindingKey, String> map = enumMap(FindingKey.class);
		
		private boolean creatingVuln = false;
		
		private DateStatus currentDateStatus = null;
		private DateStatus initialDateStatus = null;

        private String vulnTag = null;
        private boolean inAttackVector = false;
        private StringBuffer currentRawFinding = new StringBuffer();
		
		private void addFinding() {
			Finding finding = constructFinding(map);
			
			if (finding == null) {
				LOG.warn("Finding was null.");
			} else {
				String nativeId = hashFindingInfo(map.get(FindingKey.VULN_CODE), map.get(FindingKey.PATH), map.get(FindingKey.PARAMETER));
				finding.setNativeId(nativeId);
				finding.setDisplayId(map.get(FindingKey.NATIVE_ID));
			}
			
			if (findingDateStatusMap.containsKey(finding)){
				findingDateStatusMap.get(finding).add(currentDateStatus);
				findingDateStatusMap.get(finding).add(initialDateStatus);
			} else {
				findingDateStatusMap.put(finding, list(currentDateStatus, initialDateStatus));
			}
		}

        private String buildUrlReference(String siteId, String nativeId) {

            String urlReference = null;

            if (siteId != null && nativeId != null){
                urlReference = ScannerType.SENTINEL.getBaseUrl() +  "?site_id=" + siteId +"&vuln_id=" + nativeId;
            }

            return urlReference;
        }
		
		public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException {
	    
	    	if ("vulnerabilities".equals(qName)) {
	    		scanDateList = list();
	    		findingDateStatusMap = map();
	    	}
	    	else if ("vulnerability".equals(qName)) {
                vulnTag = makeTag(name, qName, atts) + "\n";
	    		map.clear();

                String nativeId = atts.getValue("id");
                String siteId = atts.getValue("site");

                map.put(FindingKey.NATIVE_ID, nativeId);
	    		map.put(FindingKey.VULN_CODE, atts.getValue("class"));
	    		map.put(FindingKey.SEVERITY_CODE, atts.getValue("severity"));
                map.put(FindingKey.URL_REFERENCE, buildUrlReference(siteId, nativeId));
	    	} else if ("attack_vector".equals(qName)) {
                currentRawFinding.append(makeTag(name, qName , atts));
	    		map.put(FindingKey.PATH, null);
	    		map.put(FindingKey.PARAMETER, null);

	    		creatingVuln = true;
				String tested       = atts.getValue("tested");
				String testedState  = atts.getValue("state");
				String found        = atts.getValue("found");

				Calendar testedDate = DateUtils.getCalendarFromUTCString(tested);
				Calendar foundDate  = DateUtils.getCalendarFromUTCString(found);

				currentDateStatus = getDateStatus(testedState, testedDate);
				initialDateStatus = getDateStatus("open", foundDate);

	    		if (scanDateList != null && !scanDateList.contains(testedDate)) {
					scanDateList.add(testedDate);
				}
				if (scanDateList != null && !scanDateList.contains(foundDate)) {
					scanDateList.add(foundDate);
				}
	    	}
	    	else if (creatingVuln) {
                currentRawFinding.append(makeTag(name, qName , atts));
                if (qName.equals("request")) {
		    		map.put(FindingKey.PATH, getPath(atts.getValue("url")));
		    	} else if (qName.equals("param")) {
		    		map.put(FindingKey.PARAMETER, atts.getValue("name"));
		    	}
	    	}
	    }

		private DateStatus getDateStatus(String testedState, Calendar testedDate) {
			DateStatus dateStatus = new DateStatus();
			dateStatus.setDate(testedDate);
			dateStatus.setStatus(testedState);
			return dateStatus;
		}

		@Override
	    public void endElement (String uri, String localName, String qName) throws SAXException {
            if (creatingVuln) {
                currentRawFinding.append("</").append(qName).append(">");
            }

            if (qName.equals("attack_vector")) {
                currentRawFinding.append("\n</").append("vulnerability").append(">");
                map.put(FindingKey.RAWFINDING, vulnTag + currentRawFinding.toString());
	    		addFinding();
	    		creatingVuln = false;
                currentRawFinding.setLength(0);
	    	}

            if ("vulnerability".equals(qName)) {
                vulnTag = null;
            }
	    }
	}

    private String getPath(String fullUrl) {

        String returnPath = "/";

        String urlWithHttp = fullUrl.startsWith("http") ? fullUrl : "http://" + fullUrl;
        try {
            URL url = new URL(urlWithHttp);
            if (url.getPath() != null && !url.getPath().isEmpty()) {
                returnPath = url.getPath();
            }
        } catch (MalformedURLException e) {
            LOG.warn("Tried to parse a URL out of a url in Attack Vector String but failed: " + urlWithHttp);
        }

        return returnPath;
    }

	public class MatchingParser extends HandlerWithBuilder {

		public Finding finding = new Finding();

		private Map<FindingKey, String> map = enumMap(FindingKey.class);

		private boolean creatingVuln = false;

		private DateStatus dateStatus = null;

        private String vulnTag = null;
        private StringBuffer currentRawFinding	  = new StringBuffer();

		private void addFinding() {
			Finding finding = constructFinding(map);

			if (finding == null) {
				LOG.warn("Finding was null.");
			} else {
				String nativeId = hashFindingInfo(map.get(FindingKey.VULN_CODE), map.get(FindingKey.PATH), map.get(FindingKey.PARAMETER));
				finding.setNativeId(nativeId);
				finding.setDisplayId(map.get(FindingKey.NATIVE_ID));
			}

			if (findingDateStatusMap.containsKey(finding)){
				findingDateStatusMap.get(finding).add(dateStatus);
			} else {
				findingDateStatusMap.put(finding, list(dateStatus));
			}
		}

        private String buildUrlReference(String siteId, String nativeId) {

            String urlReference = null;

            if (siteId != null && nativeId != null){
                urlReference = ScannerType.SENTINEL.getBaseUrl() +  "?site_id=" + siteId +"&vuln_id=" + nativeId;
            }

            return urlReference;
        }

		public void startElement (String uri, String name, String qName, Attributes atts) throws SAXException {

	    	if ("vulnerabilities".equals(qName)) {
	    		scanDateList = list();
	    		findingDateStatusMap = map();
	    	}
	    	else if ("vulnerability".equals(qName)) {
                vulnTag = makeTag(name, qName, atts) + "\n";
	    		map.clear();

                String nativeId = atts.getValue("id");
                String siteId = atts.getValue("site");

	    		map.put(FindingKey.NATIVE_ID, nativeId);
	    		map.put(FindingKey.VULN_CODE, atts.getValue("class"));
	    		map.put(FindingKey.SEVERITY_CODE, atts.getValue("severity"));
	    		map.put(FindingKey.URL_REFERENCE, buildUrlReference(siteId, nativeId));

                // was in the attack_vector
	    		map.put(FindingKey.PATH, getPath(atts.getValue("url")));
	    		map.put(FindingKey.PARAMETER, null);
	    		creatingVuln = true;
	    		dateStatus = new DateStatus();
				String found = atts.getValue("found");
				String tested = atts.getValue("tested");
				Calendar testedDate = getCalendarAtMidnight(found);
	    		dateStatus.setDate(testedDate);
	    		dateStatus.setStatus(atts.getValue("status"));
	    		if (scanDateList != null && !scanDateList.contains(testedDate)) {
                    scanDateList.add(testedDate);
                }
	    	}
	    	else if (creatingVuln) {
                currentRawFinding.append(makeTag(name, qName , atts));
		    	if (qName.equals("param")) {
		    		map.put(FindingKey.PARAMETER, atts.getValue("name"));
		    	}
	    	}
	    }

	    @Override
	    public void endElement (String uri, String localName, String qName) throws SAXException {
            if (creatingVuln) {
                currentRawFinding.append("</").append(qName).append(">");
            }

            if ("vulnerability".equals(qName)) {
                vulnTag = null;

                currentRawFinding.append("\n</").append("vulnerability").append(">");
                map.put(FindingKey.RAWFINDING, vulnTag + currentRawFinding.toString());
                addFinding();
                creatingVuln = false;
                currentRawFinding.setLength(0);
            }
	    }
	}

	private Calendar getCalendarAtMidnight(String found) {
		Calendar testedDate = DateUtils.getCalendarFromString("yyyy-MM-dd", found);
		if (testedDate != null) {
            testedDate.set(Calendar.HOUR_OF_DAY, 0);
            testedDate.set(Calendar.MINUTE, 0);
            testedDate.set(Calendar.SECOND, 0);
            testedDate.set(Calendar.MILLISECOND, 0);
        }
		return testedDate;
	}

	public class DateStatus implements Comparable<DateStatus> {
		
		private Calendar date;
		private String status;

		public Calendar getDate() {
			return date;
		}

		public void setDate(Calendar date) {
			this.date = date;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public long getTimeMillis() {
			return date == null ? 0 : date.getTimeInMillis();
		}

		@Override
		public int compareTo(DateStatus other) {
			return this.getDate().compareTo(other.getDate());
		}
	}

	
}
