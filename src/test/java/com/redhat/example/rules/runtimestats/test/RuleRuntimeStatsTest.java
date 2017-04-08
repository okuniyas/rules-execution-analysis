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

import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redhat.example.rules.fact.Message;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;


public class RuleRuntimeStatsTest {
	final String processName = "com.sample.bpmn.hello";
	final int numMessages = 10000;

	final String kieBaseNameProperty = "rules.runtimestats.kiebasename";
	
	String kieBaseName = "rules";
	KieBase kieBase = null;
	String statsResultJson = null;
			
	KieServices ks = KieServices.Factory.get();
	KieContainer kieContainer = ks.newKieClasspathContainer();
	KieCommands kieCommands = ks.getCommands();
	
	RuleRuntimeStatsService statsService =
			RuleRuntimeStatsService.Factory.get();
	
	ObjectMapper mapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.setDateFormat(
					SimpleDateFormat.getDateTimeInstance(
							DateFormat.LONG, DateFormat.LONG
							));
	
	@Before
	public void init() {
		String targetKieBaseName = System.getProperty(kieBaseNameProperty);
		if (targetKieBaseName != null &&
				kieContainer.getKieBaseNames().contains(targetKieBaseName)) {
			kieBaseName = targetKieBaseName;
		}
		System.out.println("workingRules is \"" + kieBaseName + "\"");
		System.out.println("(*) Can change workingRules by \"-D" + kieBaseNameProperty + "=...\"");
		kieBase = kieContainer.getKieBase(kieBaseName);
	}

	@After
	public void clear() {
		statsService.unregisterAllSessions();
		statsService.clearAllStats();
	}

	@Test
	public void test_stats_noop() {
		
		StatsType statsType = RuleRuntimeStatsService.NOOP;

		execute(true, statsType);

		// output stats result
		System.out.println(statsType + " " + kieBaseName + ":");
		System.out.println(statsResultJson);
	}

	@Test
	public void test_stats_execution_count() {

		StatsType statsType = RuleRuntimeStatsService.EXECUTION_COUNT;

		execute(true, statsType);
		
		// output stats result
		System.out.println(statsType + " " + kieBaseName + ":");
		System.out.println(statsResultJson);

		try {
			Map<String, Object> map =
					mapper.readValue(statsResultJson,
							new TypeReference<LinkedHashMap<String, Object>>() {});
			
			// verify rule execution stats
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> children = (List<Map<String,Object>>) map.get("children");
			assertThat(children.get(0).get("size"), is(numMessages/2));
			
			// verify not executed rule
			@SuppressWarnings("unchecked")
			List<String> notExecutedRules = (List<String>) map.get("notExecutedRules");
			assertThat(notExecutedRules.size(), is(1));
			assertThat(notExecutedRules.get(0), endsWith("DummyRule"));
			
			// verify ruleflow execution count
			Object executionCount = map.get("executionCount");
			assertThat(executionCount, is(1));
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}

	@Test
	public void test_stats_activation() {
		
		StatsType statsType = RuleRuntimeStatsService.ACTIVATION;

		execute(true, statsType);

		// output stats result
		System.out.println(statsType + " " + kieBaseName + ":");
		System.out.println(statsResultJson);
	}

	@Test
	public void test_stats_execution_sequence() {
		
		StatsType statsType = RuleRuntimeStatsService.EXECUTION_SEQUENCE;

		execute(true, statsType);

		// output stats result
		System.out.println(statsType + " " + kieBaseName + ":");
		System.out.println(statsResultJson);

		try {
			Map<String, Object> map =
					mapper.readValue(statsResultJson,
							new TypeReference<LinkedHashMap<String, Object>>() {});
			
			// verify rule execution stats
			Integer ruleExecutionCount = (Integer) map.get("ruleExecutionCount");
			assertThat(ruleExecutionCount, is(numMessages*3/2));
			
			// verify first three executed rules
			@SuppressWarnings("unchecked")
			List<String> ruleSequence = (List<String>) map.get("ruleSequence");
			assertThat(ruleSequence.size(), is(ruleExecutionCount));
			assertThat(ruleSequence.get(0), endsWith("Hello World"));
			assertThat(ruleSequence.get(1), endsWith("GoodBye"));
			assertThat(ruleSequence.get(2), endsWith("Make a Set"));
			
			// verify ruleflow execution count
			Object executionCount = map.get("executionCount");
			assertThat(executionCount, is(1));
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}

	private void execute(boolean stateless, StatsType statsType) {
		List<Command<?>> cmds = createCommands();
		
		if (stateless) {
			StatelessKieSession kieSession = kieBase.newStatelessKieSession();
		
			statsService.registerSession(kieSession, statsType); // set stats listener
			kieSession.execute(kieCommands.newBatchExecution(cmds));
			StringWriter writer = new StringWriter();
			statsService.writeStats(mapper, writer, kieBase, statsType); // get stats result
			statsResultJson = writer.toString();
		} else {
			KieSession kieSession = kieBase.newKieSession();
			statsService.registerSession(kieSession, statsType); // set stats listener
			kieSession.execute(kieCommands.newBatchExecution(cmds));
			statsService.unregisterSession(kieSession, statsType); // unset stats listener
			StringWriter writer = new StringWriter();
			statsService.writeStats(mapper, writer, kieBase, statsType);
			statsResultJson = writer.toString();
		}
	}
	
	private List<Command<?>> createCommands() {
		List<Message> messages = new ArrayList<Message>(numMessages);
		for (int m=0; m < numMessages; m++) {
			Message message = new Message();
			message.setMessage("Hello World " + m);
			message.setStatus(Message.HELLO);
			messages.add(message);
		}
		List<Command<?>> cmds = new ArrayList<Command<?>>();
		cmds.add(kieCommands.newInsertElements(messages));
		cmds.add(kieCommands.newStartProcess(processName));
		cmds.add(kieCommands.newFireAllRules());
		return cmds;
	}

	@Test
	public void test_stats_execution_stateful_twice() {
		
		StatsType statsType = RuleRuntimeStatsService.EXECUTION_COUNT;

		// execute 2 times
		execute(false, statsType);
		execute(false, statsType);
		
		// output stats result
		System.out.println(statsType + " " + kieBaseName + ":");
		System.out.println(statsResultJson);

		try {
			Map<String, Object> map =
					mapper.readValue(statsResultJson,
							new TypeReference<LinkedHashMap<String, Object>>() {});
			
			// verify rule execution stats
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> children = (List<Map<String,Object>>) map.get("children");
			assertThat(children.get(0).get("size"), is(numMessages));
			
			// verify not executed rule
			@SuppressWarnings("unchecked")
			List<String> notExecutedRules = (List<String>) map.get("notExecutedRules");
			assertThat(notExecutedRules.size(), is(1));
			assertThat(notExecutedRules.get(0), endsWith("DummyRule"));
			
			// verify ruleflow execution count
			Object executionCount = map.get("executionCount");
			assertThat(executionCount, is(2));
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("JSON or The runtime stats is invalid.");
		}
	}

}