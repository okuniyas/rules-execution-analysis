/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.example.rules.runtimestats.test;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.KieContainer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redhat.example.rules.fact.Message;
import com.redhat.example.rules.runtimestats.DefaultRuleSimulator;
import com.redhat.example.rules.runtimestats.RuleRuntimeCompareService;
import com.redhat.example.rules.runtimestats.RuleRuntimeCompareService.CommandsFactory;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;


public class RuleSimulatorTest {
	final String processName = "com.sample.bpmn.hello";

	final String kieBaseNameProperty = "rules.runtimestats.kiebasename";
	
	String baseRules = "rules";
	String workingRules = "rules";
	String getKieBaseComparisonName() {
		return baseRules + "-VS-" + workingRules;
	}
			
	KieServices ks = KieServices.Factory.get();
	KieContainer kieContainer = ks.newKieClasspathContainer();
	KieCommands kieCommands = ks.getCommands();
	
	DefaultRuleSimulator ruleSimulator = new DefaultRuleSimulator();
	
	CommandsFactory commandsFactory = null;
	
	ObjectMapper mapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.setDateFormat(
					SimpleDateFormat.getDateTimeInstance(
							DateFormat.LONG, DateFormat.LONG
							));
	
	@Before
	public void init() {
		ruleSimulator.setBaseRules(kieContainer.getKieBase("rules"));

		String targetKieBaseName = System.getProperty(kieBaseNameProperty);
		if (targetKieBaseName != null &&
				kieContainer.getKieBaseNames().contains(targetKieBaseName)) {
			workingRules = targetKieBaseName;
		}
		System.out.println("workingRules is \"" + workingRules + "\"");
		System.out.println("(*) Can change workingRules by \"-D" + kieBaseNameProperty + "=...\"");
		ruleSimulator.setWorkingRules(kieContainer.getKieBase(workingRules));
		prepareCommands();
		ruleSimulator.setWarmupSeconds(30);
	}

	private void prepareCommands() {
		commandsFactory = new CommandsFactory() {

			@Override
			public List<Command<?>> getStaticFirstCommands() {
				return new ArrayList<Command<?>>();
			}

			@Override
			public Iterator<List<Command<?>>> getBodyCommandsIterator() {
				Iterator<List<Command<?>>> ite = new Iterator<List<Command<?>>>() {
					int exec_count = 0;
					int exec_max = 200;

					@Override
					public boolean hasNext() {
						return exec_count < exec_max;
					}

					@Override
					public List<Command<?>> next() {
						if (! hasNext()) {
							return null;
						}
						exec_count++;
						List<Message> messages = new ArrayList<Message>(exec_count);
						for (int m=0; m < exec_count; m++) {
							Message message = new Message();
							message.setMessage("Hello World " + m);
							message.setStatus(Message.HELLO);
							messages.add(message);
						}
						List<Command<?>> commands = new ArrayList<Command<?>>();
						commands.add(kieCommands.newInsertElements(messages));
						return commands;
					}
					
				};
				return ite;
			}

			@Override
			public List<Command<?>> getStaticLastCommands() {
				List<Command<?>> commands = new ArrayList<Command<?>>();
				commands.add(kieCommands.newStartProcess(processName));
				commands.add(kieCommands.newFireAllRules());
				return commands;
			}
			
		};
	}
	
	@Test
	public void test_comparison_all() {
		String reportBaseDir = "target/report/" + getKieBaseComparisonName();
		ruleSimulator.setReportDir(reportBaseDir);
		ruleSimulator.executeAllStats(commandsFactory);
		// check all files are generated.
		for (StatsType statsType : StatsType.values()) {
			int file_index = 0;
			for (String filename : DefaultRuleSimulator.FILE_NAMES) {
				String reportPath;
				if (file_index < 3) { // stats
					reportPath = reportBaseDir + "/" +
							statsType.toString().toLowerCase() + "_" +
							filename + "." + DefaultRuleSimulator.EXTENSION;
				} else { // facts
					reportPath = reportBaseDir + "/" +
							filename + "." + DefaultRuleSimulator.EXTENSION;
				}
				File file = new File(reportPath);
				assertThat("File(" + file.getPath() + ") does not exist.", file.isFile(), is(true));
				file_index++;
			}
		}
	}
	
	@Test
	public void test_comparison_noop() {
		StatsType statsType = RuleRuntimeStatsService.NOOP;
		ruleSimulator.setReportDir("target/report/" + getKieBaseComparisonName() +
				"/" + statsType.toString().toLowerCase());
		String[] stats = ruleSimulator.execute(commandsFactory, statsType);
		try {
			Map<String, Object> map = mapper.readValue(stats[2],
					new TypeReference<LinkedHashMap<String, Object>>() {});
			assertThat((String)map.get("name"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}
	
	@Test
	public void test_comparison_execution() {
		StatsType statsType = RuleRuntimeStatsService.EXECUTION_COUNT;
		ruleSimulator.setReportDir("target/report/" + getKieBaseComparisonName() +
				"/" + statsType.toString().toLowerCase());
		String[] stats = ruleSimulator.execute(commandsFactory, statsType);
		try {
			Map<String, Object> map = mapper.readValue(stats[2],
					new TypeReference<LinkedHashMap<String, Object>>() {});
			assertThat((String)map.get("name"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat((String)map.get("executionCount"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat(map.get("notExecutedRules"), is(RuleRuntimeCompareService.SAME_ARRAY));
			assertThat(map.get("children"), is(RuleRuntimeCompareService.SAME_ARRAY));
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}

	@Test
	public void test_comparison_activation() {
		StatsType statsType = RuleRuntimeStatsService.ACTIVATION;
		ruleSimulator.setReportDir("target/report/" + getKieBaseComparisonName() +
				"/" + statsType.toString().toLowerCase());
		String[] stats = ruleSimulator.execute(commandsFactory, statsType);
		try {
			Map<String, Object> map = mapper.readValue(stats[2],
					new TypeReference<LinkedHashMap<String, Object>>() {});
			assertThat((String)map.get("name"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat((String)map.get("executionCount"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat(map.get("notExecutedRules"), is(RuleRuntimeCompareService.SAME_ARRAY));
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}
	
	@Test
	public void test_comparison_execution_sequence() {
		StatsType statsType = RuleRuntimeStatsService.EXECUTION_SEQUENCE;
		ruleSimulator.setReportDir("target/report/" + getKieBaseComparisonName() +
				"/" + statsType.toString().toLowerCase());
		String[] stats = ruleSimulator.execute(commandsFactory, statsType);
		try {
			Map<String, Object> map = mapper.readValue(stats[2],
					new TypeReference<LinkedHashMap<String, Object>>() {});
			assertThat((String)map.get("name"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat((String)map.get("executionCount"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat((String)map.get("ruleExecutionCount"), is(startsWith(RuleRuntimeCompareService.SAME_HEADER)));
			assertThat(map.get("ruleSequence"), is(RuleRuntimeCompareService.SAME_ARRAY));
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}
	
	@Test
	public void test_aggregation() {
		// 1
		String reportBaseDir1 = "target/report/" + getKieBaseComparisonName() + "_1";
		ruleSimulator.setReportDir(reportBaseDir1);
		ruleSimulator.executeAllStats(commandsFactory);
		// 2
		String reportBaseDir2 = "target/report/" + getKieBaseComparisonName() + "_2";
		ruleSimulator.setReportDir(reportBaseDir2);
		ruleSimulator.executeAllStats(commandsFactory);
		// aggregate
		String reportBaseDir = "target/report/" + getKieBaseComparisonName();
		ruleSimulator.aggregate(reportBaseDir1, true, reportBaseDir2, true, reportBaseDir);
		// check all files are generated.
		for (StatsType statsType : StatsType.values()) {
			int file_index = 0;
			for (String filename : DefaultRuleSimulator.FILE_NAMES) {
				String reportPath;
				if (file_index < 3) { // stats
					reportPath = reportBaseDir + "/" +
							statsType.toString().toLowerCase() + "_" +
							filename + "." + DefaultRuleSimulator.EXTENSION;
				} else { // facts
					reportPath = reportBaseDir + "/" +
							filename + "." + DefaultRuleSimulator.EXTENSION;
				}
				File file = new File(reportPath);
				assertThat("File(" + file.getPath() + ") does not exist.", file.isFile(), is(true));
				file_index++;
			}
		}

	}

}