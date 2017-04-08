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

import java.util.ArrayList;
import java.util.Iterator;

import org.kie.api.KieBase;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.StatelessKieSession;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.redhat.example.rules.runtimestats.RuleRuntimeStats;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule runtime stats of execution sequence.
 * 
 * @author okuniyas
 */
@JsonPropertyOrder({"name", "kieBaseId", "lastReset", "elapsedMilliseconds",
	"executionCount", "ruleExecutionCount", "ruleSequence" })
public class RuleExecutionSequenceStats
extends RuleNoOpStats
implements RuleRuntimeStats
{

	private final int bucketSize = 1024*8;
	private ArrayList<ArrayList<String>> executionSequence;
	private long executionCount = 0;
	private long ruleExecutionCount = 0;

	public RuleExecutionSequenceStats(KieBase kieBase) {
		 super(kieBase);
		 executionSequence = new ArrayList<ArrayList<String>>(bucketSize);
		 executionSequence.add(new ArrayList<String>(bucketSize));
	}

	@Override
	public StatsType getStatsType() {
		return StatsType.EXECUTION_SEQUENCE;
	}
	
	@Override
	public void clearStats() {
		executionCount = 0;
		ruleExecutionCount = 0;
		for (ArrayList<String> bucket : executionSequence) {
			bucket.clear();
		}
		ArrayList<String> firstBucket = executionSequence.get(0);
		executionSequence.clear();
		executionSequence.add(firstBucket);
		super.clearStats();
	}
	
    public long getExecutionCount() {
    	return executionCount;
    }

    public long getRuleExecutionCount() {
    	return ruleExecutionCount;
    }

	private void addRule(String name) {
		ruleExecutionCount++;
		ArrayList<String> lastBucket =
				executionSequence.get(executionSequence.size()-1);
		if (lastBucket.size() >= bucketSize) {
			lastBucket = new ArrayList<String>(bucketSize);
			executionSequence.add(lastBucket);
		}
		lastBucket.add(name);
	}
	
	@JsonSerialize(using=WrapItemSerializer.class)
	public Iterator<String> getRuleSequence() {
		return new Iterator<String>() {
			Iterator<ArrayList<String>> bucketIt =
					executionSequence.iterator();
			Iterator<String> it = (bucketIt.next()).iterator();
			
			@Override
			public boolean hasNext() {
				if (it.hasNext()) return true;
				if (!bucketIt.hasNext()) return false;
				it = (bucketIt.next()).iterator();
				return it.hasNext();
			}

			@Override
			public String next() {
				if (!hasNext()) return null;
				return it.next();
			}
			
		};
	}
	
	@Override
	public void registerSession(KieRuntimeEventManager session) {
		// keep only one session registered.
		unregisterAllSessions();
		clearStats();
		
		for (AgendaEventListener listener : session.getAgendaEventListeners()) {
			if (listener instanceof SubListener1) {
				return; // do nothing
			}
		}
		session.addEventListener(new SubListener1(this));
		// StatelessKieSession can not add ProcessEventListener.
		// instead of setting the listener, increment the executionCount.
		if (session instanceof StatelessKieSession) {
			executionCount++;
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

	// AgendaEventListener to get rule execution
	private static class SubListener1 extends DefaultAgendaEventListener {
		private RuleExecutionSequenceStats parent = null;
		private SubListener1(RuleExecutionSequenceStats parent) {
			this.parent = parent;
		}
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			parent.addRule(event.getMatch().getRule().getName());
		}
	}

	// ProcessEventListener to get ruleflow execution
	private static class SubListener2 extends DefaultProcessEventListener {
		private RuleExecutionSequenceStats parent = null;
		private SubListener2(RuleExecutionSequenceStats parent) {
			this.parent = parent;
		}
		@Override
		public void afterProcessStarted(ProcessStartedEvent event) {
			parent.executionCount++;
		}
	}	
}
