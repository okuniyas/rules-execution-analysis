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

import java.io.Writer;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.drools.core.impl.KnowledgeBaseImpl;
import org.kie.api.KieBase;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.runtime.KieSession;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.example.rules.runtimestats.RuleRuntimeStats;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * No operation rule runtime stats.
 * 
 * @author okuniyas
 *
 */
@JsonPropertyOrder({"name", "kieBaseId", "lastReset", "elapsedMilliseconds"})
public class RuleNoOpStats
implements RuleRuntimeStats
{
	protected KieBase kieBase = null;

	private AtomicReference<Date> lastReset = new AtomicReference<Date>(new Date());

	// prohibit to create instance without KieBase
	@SuppressWarnings("unused")
	private RuleNoOpStats() {
	}
	
    public RuleNoOpStats(KieBase kieBase) {
    	this.kieBase = kieBase;
	}

	public Date getLastReset() {
    	return lastReset.get();
    }
    
    public long getElapsedMilliseconds() {
    	return (new Date().getTime() - lastReset.get().getTime());
    }
    
	public void clearStats() {
		lastReset = new AtomicReference<Date>(new Date());
	}
	
	public String getKieBaseId() {
		if (kieBase == null) return null;
		return ((KnowledgeBaseImpl)kieBase).getId();
	}

	@Override
	@JsonIgnore
	public StatsType getStatsType() {
		return StatsType.NOOP;
	}

	public String getName() {
		return getStatsType().toString();
	}

	@Override
	public void writeStats(ObjectMapper mapper, Writer writer) {
		try {
			mapper.writeValue(writer, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void registerSession(KieRuntimeEventManager session) {
	}

	@Override
	public void unregisterSession(KieRuntimeEventManager session) {
	}
	
	@Override
	public void unregisterAllSessions() {
		for (KieSession kieSession : kieBase.getKieSessions()) {
			unregisterSession(kieSession);
		}
	}
}
