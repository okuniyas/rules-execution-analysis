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
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.spi.Activation;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.runtime.StatelessKieSession;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.redhat.example.rules.runtimestats.RuleRuntimeStats;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule runtime stats of activations by below levels (L1 to L4).
 * 
 * (L1) activatedRule : count of activations created
 *  -> (L2) activatedByRule : count of activations created by which rule
 *   -> (L3-1) canceled : count of activations canceled
 *    -> (L4-1) canceledByRule : count of activations canceled by which rule
 *   -> (L3-2) executed : count of activations executed
 *    -> (L4-2) executedAfterRule : count of activations executed after which rule
 * 
 * @author okuniyas
 *
 */
@JsonPropertyOrder({"name", "kieBaseId", "lastReset", "elapsedMilliseconds",
	"executionCount", "notExecutedRules", "children" })
public class RuleActivationStats extends RuleNoOpStats
implements RuleRuntimeStats
{
	private Map<Long, Rule> activationToOriginRuleMap =
			new ConcurrentHashMap<Long, Rule>();
	
	private Map<Rule, RuleActivationCountL1> activatedRuleMap =
			new ConcurrentHashMap<Rule, RuleActivationCountL1>();
	
	private AtomicLong executionCount = new AtomicLong();

	public RuleActivationStats(KieBase kieBase) {
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
    		ruleLevel: for (Rule rule : kiePackage.getRules()) {
    			RuleActivationCountL1 l1 = activatedRuleMap.get(rule);
    			if (l1 == null || l1.count.get() == 0) {
    				// no activations == not executed
    				ret.add(getRuleName(rule));
    			} else {
    				// there are activations
    				for (RuleActivationCountL2 l2
    						: l1.activatedByRuleMap.values()) {
    					if (l2.executedAfterRuleMap != null &&
    							l2.executedAfterRuleMap.size() > 0) {
    						// activation executed
    						continue ruleLevel;
    					}
    				}
    				// all activations cancelled == not executed
    				ret.add(getRuleName(rule));
    			}
    		}
    	}
    	return ret.iterator();
    }

	private String getRuleName(Rule rule) {
		return rule.getPackageName() + "." + rule.getName();		
	}
	
	public Collection<RuleActivationCountL1> getChildren() {
		return activatedRuleMap.values();
	}

	@Override
	@JsonIgnore
	public StatsType getStatsType() {
		return StatsType.ACTIVATION;
	}
	
	public void clearStats() {
		executionCount.set(0);
		activatedRuleMap.clear();
		activationToOriginRuleMap.clear();
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
		private RuleActivationStats parent = null;
		private Rule previousExecutedRule = new RuleImpl("root");
		
		private SubListener1(RuleActivationStats parent) {
			this.parent = parent;
		}

		@Override
		public void matchCreated(MatchCreatedEvent event) {
			Rule activatedRule = event.getMatch().getRule();
			RuleActivationCountL1 ruleActivationCountL1 =
					parent.activatedRuleMap.get(activatedRule);
			if (ruleActivationCountL1 == null) {
				ruleActivationCountL1 = new RuleActivationCountL1();
				ruleActivationCountL1.rule = activatedRule;
				parent.activatedRuleMap.put(activatedRule, ruleActivationCountL1);
			}
			ruleActivationCountL1.count.incrementAndGet();
			RuleActivationCountL2 ruleActivationCountL2 =
					ruleActivationCountL1.activatedByRuleMap.get(previousExecutedRule);
			if (ruleActivationCountL2 == null) {
				ruleActivationCountL2 = new RuleActivationCountL2();
				ruleActivationCountL2.rule = previousExecutedRule;
				ruleActivationCountL1.activatedByRuleMap.put(previousExecutedRule, ruleActivationCountL2);
			}
			ruleActivationCountL2.count.incrementAndGet();
			// record activation -> originRule for cancel or execution
			parent.activationToOriginRuleMap.put(((Activation<?>)event.getMatch()).getActivationNumber(), previousExecutedRule);
		}
		
		@Override
		public void matchCancelled(MatchCancelledEvent event) {
			Rule rule = event.getMatch().getRule();
			Rule originRule = parent.activationToOriginRuleMap.get(((Activation<?>)event.getMatch()).getActivationNumber());
			RuleActivationCountL2 ruleActivationCountL2 = parent.activatedRuleMap.get(rule).activatedByRuleMap.get(originRule);
			RuleActivationCountL4 ruleActivationCountL4 = ruleActivationCountL2.canceledByRuleMap.get(previousExecutedRule);
			if (ruleActivationCountL4 == null) {
				ruleActivationCountL4 = new RuleActivationCountL4();
				ruleActivationCountL4.rule = previousExecutedRule;
				ruleActivationCountL2.canceledByRuleMap.put(previousExecutedRule, ruleActivationCountL4);
			}
			ruleActivationCountL4.count.incrementAndGet();
			parent.activationToOriginRuleMap.remove(((Activation<?>)event.getMatch()).getActivationNumber());
		}
		
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			Rule rule = event.getMatch().getRule();
			Rule originRule = parent.activationToOriginRuleMap.get(((Activation<?>)event.getMatch()).getActivationNumber());
			RuleActivationCountL2 ruleActivationCountL2 = parent.activatedRuleMap.get(rule).activatedByRuleMap.get(originRule);
			RuleActivationCountL4 ruleActivationCountL4 = ruleActivationCountL2.executedAfterRuleMap.get(previousExecutedRule);
			if (ruleActivationCountL4 == null) {
				ruleActivationCountL4 = new RuleActivationCountL4();
				ruleActivationCountL4.rule = previousExecutedRule;
				ruleActivationCountL2.executedAfterRuleMap.put(previousExecutedRule, ruleActivationCountL4);
			}
			ruleActivationCountL4.count.incrementAndGet();
			parent.activationToOriginRuleMap.remove(((Activation<?>)event.getMatch()).getActivationNumber());
			// record rule as the last executed rule.
			previousExecutedRule = rule;
		}
	}

	// ProcessEventListener to count ruleflow execution
	private static class SubListener2 extends DefaultProcessEventListener {
		private RuleActivationStats parent = null;
		
		private SubListener2(RuleActivationStats parent) {
			this.parent = parent;
		}
		
		@Override
		public void afterProcessStarted(ProcessStartedEvent event) {
			parent.executionCount.incrementAndGet();
		}
	}
	
	// entry classes
	public static class RuleActivationCountL1 {
		@JsonIgnore
		public Rule rule;
		@JsonProperty("size")
		public AtomicLong count = new AtomicLong();
		
		@JsonIgnore
		public Map<Rule, RuleActivationCountL2> activatedByRuleMap =
				new ConcurrentHashMap<Rule, RuleActivationCountL2>();

		public String getName() {
			return "(Act)" + rule.getName();
		}
		public Collection<RuleActivationCountL2> getChildren() {
			return activatedByRuleMap.values();
		}
	}

	public static class RuleActivationCountL2 {
		@JsonIgnore
		public Rule rule;
		@JsonProperty("size")
		public AtomicLong count = new AtomicLong();
		@JsonIgnore
		public Map<Rule, RuleActivationCountL4> canceledByRuleMap =
				new ConcurrentHashMap<Rule, RuleActivationCountL4>();
		@JsonIgnore
		public Map<Rule, RuleActivationCountL4> executedAfterRuleMap =
				new ConcurrentHashMap<Rule, RuleActivationCountL4>();
		
		public String getName() {
			return "(ActBy)" + rule.getName();
		}
		public Collection<RuleActivationCountL3> getChildren() {
			LinkedList<RuleActivationCountL3> children = new LinkedList<RuleActivationCountL3>();
			RuleActivationCountL3 item = new RuleActivationCountL3();
			item.name = "Executed";
			item.children = executedAfterRuleMap.values();
			children.add(item);
			item = new RuleActivationCountL3();
			item.name = "Canceled";
			item.children = canceledByRuleMap.values();
			children.add(item);
			return children;
		}
	}

	public static class RuleActivationCountL3 {
		public String name;
		public Collection<RuleActivationCountL4> children;
	}
	
	public static class RuleActivationCountL4 {
		@JsonIgnore
		public Rule rule;
		@JsonProperty("size")
		public AtomicLong count = new AtomicLong();
		
		public String getName() {
			return "(AfterExec)" + rule.getName();
		}
	}
}
