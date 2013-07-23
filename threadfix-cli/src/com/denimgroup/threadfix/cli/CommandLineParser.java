////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2013 Denim Group, Ltd.
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
package com.denimgroup.threadfix.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class CommandLineParser {
	
	public static ThreadFixRestClient client = new ThreadFixRestClient();
	
	@SuppressWarnings("static-access")
	public static final Options getOptions() {
		Options options = new Options();

		Option teams = OptionBuilder.withLongOpt("teams")
				.withDescription("Fetches a list of ThreadFix teams and applications.")
				.create("t");
		options.addOption(teams);
		options.addOption(new Option("help", "Print this message" ));
		
		Option set = OptionBuilder.withArgName("property> <value")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("set")
				.withDescription("Set either the url (ThreadFix base url) or key (ThreadFix API key) properties")
				.create("s");
		options.addOption(set);
		
		Option createTeam = OptionBuilder.withArgName("name")
				.hasArg()
				.withLongOpt("create-team")
				.withDescription("Creates a ThreadFix team and returns its JSON.")
				.create("ct");
		options.addOption(createTeam);
		
		Option createApp = OptionBuilder.withArgName("teamId> <name> <url")
				.withValueSeparator(' ')
				.hasArgs(3)
				.withLongOpt("create-app")
				.withDescription("Creates a ThreadFix application and returns its JSON.")
				.create("ca");
		options.addOption(createApp);
		
		Option createWaf = OptionBuilder.withArgName("name> <wafTypeName")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("create-waf")
				.withDescription("Creates a ThreadFix WAF and returns its JSON.")
				.create("cw");
		options.addOption(createWaf);
		
		Option searchTeam = OptionBuilder.withArgName("property> <value")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("search-team")
				.withDescription("Searches for a ThreadFix team and returns its JSON.")
				.create("st");
		options.addOption(searchTeam);
		
		Option searchWaf = OptionBuilder.withArgName("property> <value")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("search-waf")
				.withDescription("Searches for a ThreadFix WAF and returns its JSON.")
				.create("sw");
		options.addOption(searchWaf);
		
		Option searchApp = OptionBuilder.withArgName("property> <value")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("search-app")
				.withDescription("Searches for a ThreadFix application and returns its JSON.")
				.create("sa");
		options.addOption(searchApp);
		
		Option upload = OptionBuilder.withArgName("appId> <file")
				.withValueSeparator(' ')
				.hasArgs(2)
				.withLongOpt("upload")
				.withDescription("Uploads a scan to the specified application.")
				.create("u");
		options.addOption(upload);
		
		Option getRules = OptionBuilder.withArgName("wafId")
				.hasArg()
				.withLongOpt("rules")
				.withDescription("Gets WAF Rules and returns its JSON.")
				.create("r");
		options.addOption(getRules);
		
		return options;
	}
	
	public static void main (String[] args) {
		
		Options options = getOptions();
		
		PosixParser parser = new PosixParser();
		try {
			CommandLine cmd = parser.parse( options, args);
			
			if (cmd.hasOption("help")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar tfcli.jar", options );
				
			} else if (cmd.hasOption("s")) {
				
				String[] setArgs = cmd.getOptionValues("s");
				if (setArgs == null || setArgs.length != 2) {
					throw new ParseException("Bad arguments for set.");
				}
				
				if ("url".equals(setArgs[0])) {
					System.out.println("Setting URL to " + setArgs[1]);
					client.setUrl(setArgs[1]);
				} else if ("key".equals(setArgs[0])) {
					System.out.println("Setting API Key to " + setArgs[1]);
					client.setKey(setArgs[1]);
				} else {
					throw new ParseException("First argument to set must be url or key");
				}
				
			} else if (cmd.hasOption("ct")) {
				String[] createArgs = cmd.getOptionValues("ct");
				if (createArgs.length != 1) {
					throw new ParseException("Wrong number of arguments.");
				}
				System.out.println("Creating a Team with the name " + createArgs[0] + ".");
				System.out.println(client.createTeam(createArgs[0]));
			
			} else if (cmd.hasOption("cw")) {
				String[] createArgs = cmd.getOptionValues("cw");
				if (createArgs.length != 2) {
					throw new ParseException("Wrong number of arguments.");
				}
				System.out.println("Creating a Waf with the name " + createArgs[0] + ".");
				System.out.println(client.createWaf(createArgs[0], createArgs[1]));
				
			} else if (cmd.hasOption("ca")) {
				String[] createArgs = cmd.getOptionValues("ca");
				if (createArgs.length != 3) {
					throw new ParseException("Wrong number of arguments.");
				}
				System.out.println("Creating an Application with the name " + createArgs[1] + ".");
				System.out.println(client.createApplication(createArgs[0], createArgs[1], createArgs[2]));

			} else if (cmd.hasOption("teams")) {
				System.out.println("Getting all teams.");
				System.out.println(client.getAllTeams());
				
			} else if (cmd.hasOption("u")) {
				String[] uploadArgs = cmd.getOptionValues("u");
				// Upload a scan
				if (uploadArgs.length != 2) {
					throw new ParseException("Wrong number of arguments.");
				}
				System.out.println("Uploading " + uploadArgs[1] + 
						" to Application " + uploadArgs[0] + ".");
				System.out.println(client.uploadScan(uploadArgs[0], uploadArgs[1]));

			} else if (cmd.hasOption("st")) {
				String[] searchArgs = cmd.getOptionValues("st");
				if (searchArgs.length != 2) {
					throw new ParseException("Wrong number of arguments.");
				}
				if ("id".equals(searchArgs[0])) {
					System.out.println("Searching for team with the id " + searchArgs[1] + ".");
					System.out.println(client.searchForTeamById(searchArgs[1]));
				} else if ("name".equals(searchArgs[0])) {
					System.out.println("Searching for team with the name " + searchArgs[1] + ".");
					System.out.println(client.searchForTeamByName(searchArgs[1]));
				} else {
					System.out.println("Unknown property argument. Try either id or name.");
					return;
				}
				
			} else if (cmd.hasOption("sw")) {
				String[] searchArgs = cmd.getOptionValues("sw");
				if (searchArgs.length != 4) {
					throw new ParseException("Wrong number of arguments.");
				}
				if ("id".equals(searchArgs[0])) {
					System.out.println("Searching for WAF with the id " + searchArgs[1] + ".");
					System.out.println(client.searchForWafById(searchArgs[1]));
				} else if ("name".equals(searchArgs[0])) {
					System.out.println("Searching for WAF with the name " + searchArgs[1] + ".");
					System.out.println(client.searchForWafByName(searchArgs[1]));
				} else {
					throw new ParseException("Unknown property argument. Try either id or name.");
				}
			
			} else if (cmd.hasOption("sa")) {
				String[] searchArgs = cmd.getOptionValues("sa");
				if (searchArgs.length != 2) {
					System.out.println("Wrong number of arguments.");
					return;
				}
				if ("id".equals(searchArgs[0])) {
					System.out.println("Searching for team with the id " + searchArgs[1] + ".");
					System.out.println(client.searchForApplicationById(searchArgs[1]));
				} else if ("name".equals(searchArgs[0])) {
					System.out.println("Searching for team with the name " + searchArgs[1] + ".");
					System.out.println(client.searchForApplicationByName(searchArgs[1]));
				} else {
					System.out.println("Unknown property argument. Try either id or name.");
					return;
				}
			
			} else if (cmd.hasOption("r")) {
				String[] ruleArgs = cmd.getOptionValues("r");
				if (ruleArgs.length != 1) {
					System.out.println("Wrong number of arguments.'");
				}
				System.out.println("Downloading rules from WAF with ID " + ruleArgs[0] + ".");
				System.out.println(client.getRules(ruleArgs[0]));
				
			} else {
				throw new ParseException("No arguments found.");
			}
		
		} catch (ParseException e) {
			if (e.getMessage() != null) {
				System.out.println(e.getMessage());
			}
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar tfcli.jar", options);
		}
	}
}
