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

package com.redhat.example.rules.runtimestats.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.kie.api.definition.rule.Rule;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.StatelessKieSession;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.redhat.example.rules.runtimestats.RuleRuntimeStats;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule runtime stats of rule executions.
 * 
 * @author okuniyas
 *
 */
@JsonPropertyOrder({"name", "kieBaseId", "lastReset", "elapsedMilliseconds",
	"executionCount", "notExecutedRules", "children" })
public class RuleExecutionStats extends RuleNoOpStats
implements RuleRuntimeStats
{
	private Map<Rule, RuleExecutionCountL1> ruleCounterMap =
			new ConcurrentHashMap<Rule, RuleExecutionCountL1>();
	
	private AtomicLong executionCount = new AtomicLong();
	
	public RuleExecutionStats(KieBase kieBase) {
		super(kieBase);
	}
	
    public long getExecutionCount() {
    	return executionCount.get();
    }
    
	/**
	 * get the not executed rules
	 * @return list of rules' name
	 */
	@JsonSerialize(using=WrapItemSerializer.class)
    public Iterator<String> getNotExecutedRules() {
    	Set<String> ret = new TreeSet<String>();
    	for (KiePackage kiePackage : kieBase.getKiePackages()) {
    		for (Rule rule : kiePackage.getRules()) {
    			RuleExecutionCountL1 l1 = ruleCounterMap.get(rule);
    			if (l1 == null || l1.count.get() == 0) {
    				ret.add(getRuleName(rule));
    			}
    		}
    	}
    	return ret.iterator();
    }

	private String getRuleName(Rule rule) {
		return rule.getPackageName() + "." + rule.getName();		
	}
	
	public Collection<RuleExecutionCountL1> getChildren() {
		return ruleCounterMap.values();
	}

	@Override
	@JsonIgnore
	public StatsType getStatsType() {
		return StatsType.EXECUTION_COUNT;
	}
	
	public void clearStats() {
		executionCount.set(0);
		ruleCounterMap.clear();
		super.clearStats();
	}

	@Override
	public void registerSession(KieRuntimeEventManager session) {
		for (AgendaEventListener listener : session.getAgendaEventListeners()) {
			if (listener instanceof SubListener1) {
				return; // do nothing
			}
		}
		session.addEventListener(new SubListener1(this));
		// StatelessKieSession can not add ProcessEventListener.
		// instead of setting the listener, increment the executionCount.
		if (session instanceof StatelessKieSession) {
			executionCount.incrementAndGet();
		} else {
			session.addEventListener(new SubListener2(this));
		}
	}

	@Override
	public void unregisterSession(KieRuntimeEventManager session) {
		// no need to unregister if session is stateless as it has been disposed.
		if (session instanceof StatelessKieSession) {
			return;
		}
		for (AgendaEventListener listener : session.getAgendaEventListeners()) {
			if (listener instanceof SubListener1) {
				session.removeEventListener(listener);
			}
		}
		for (ProcessEventListener listener : session.getProcessEventListeners()) {
			if (listener instanceof SubListener2) {
				session.removeEventListener(listener);
			}
		}
	}
	
	// AgendaEventListener to count rule execution
	private static class SubListener1 extends DefaultAgendaEventListener {
		private RuleExecutionStats parent = null;
		private SubListener1(RuleExecutionStats parent) {
			this.parent = parent;
		}
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			Rule activatedRule = event.getMatch().getRule();
			RuleExecutionCountL1 ruleExecutionCountL1 = parent.ruleCounterMap.get(activatedRule);
			if (ruleExecutionCountL1 == null) {
				ruleExecutionCountL1 = new RuleExecutionCountL1();
				ruleExecutionCountL1.rule = activatedRule;
				parent.ruleCounterMap.put(activatedRule, ruleExecutionCountL1);
			}
			ruleExecutionCountL1.count.incrementAndGet();
		}
	}

	// ProcessEventListener to count ruleflow execution
	private static class SubListener2 extends DefaultProcessEventListener {
		private RuleExecutionStats parent = null;
		private SubListener2(RuleExecutionStats parent) {
			this.parent = parent;
		}
		@Override
		public void afterProcessStarted(ProcessStartedEvent event) {
			parent.executionCount.incrementAndGet();
		}
	}
	
	// entry classes
	public static class RuleExecutionCountL1 {
		@JsonIgnore
		public Rule rule;
		@JsonProperty("size")
		public AtomicLong count = new AtomicLong();
		
		public String getName() {
			return "(Exec)" + rule.getName();
		}
	}
}
