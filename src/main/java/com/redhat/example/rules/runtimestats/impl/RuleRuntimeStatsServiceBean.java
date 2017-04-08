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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.core.impl.KnowledgeBaseImpl;
import org.kie.api.KieBase;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.example.rules.runtimestats.RuleRuntimeStats;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService;

/**
 * Controller Service Bean of RuleRuntimeStats
 * 
 * @author okuniyas
 */
public class RuleRuntimeStatsServiceBean
implements RuleRuntimeStatsService
{
	private Map<String, Map<StatsType, RuleRuntimeStats>> kieBaseToStatsMap =
			new ConcurrentHashMap<String, Map<StatsType, RuleRuntimeStats>>();
	
	@Override
	public void registerSession(KieRuntimeEventManager session, StatsType statsType) {
		if (session == null) return;
		if (statsType == null) return;
		KieBase kieBase = getKieBase(session);
		String kieBaseID = getKieBaseID(kieBase);
		if (kieBaseID == null) return;
		Map<StatsType, RuleRuntimeStats> statsMap = kieBaseToStatsMap.get(kieBaseID);
		if (statsMap == null) {
			statsMap = new ConcurrentHashMap<StatsType, RuleRuntimeStats>();
			kieBaseToStatsMap.put(kieBaseID, statsMap);
		}
		RuleRuntimeStats stats = statsMap.get(statsType);
		if (stats == null) {
			if (statsType == EXECUTION_COUNT) {
				stats = new RuleExecutionStats(kieBase);
			} else if (statsType == ACTIVATION) {
				stats = new RuleActivationStats(kieBase);
			} else if (statsType == EXECUTION_SEQUENCE) {
				stats = new RuleExecutionSequenceStats(kieBase);
			} else if (statsType == NOOP) {
				stats = new RuleNoOpStats(kieBase);
			}
			statsMap.put(statsType, stats);
		}
		stats.registerSession(session);
	}
	
	@Override
	public void unregisterSession(KieRuntimeEventManager session, StatsType statsType) {
		if (session == null) return;
		if (statsType == null) return;
		KieBase kieBase = getKieBase(session);
		String kieBaseID = getKieBaseID(kieBase);
		if (kieBaseID == null) return;
		Map<StatsType, RuleRuntimeStats> statsMap = kieBaseToStatsMap.get(kieBaseID);
		if (statsMap == null) return;
		RuleRuntimeStats stats = statsMap.get(statsType);
		if (stats != null)
			stats.unregisterSession(session);
	}
	
	@Override
	public void writeStats(ObjectMapper mapper, Writer writer, KieBase kieBase, StatsType statsType) {
		String kieBaseID = getKieBaseID(kieBase);
		if (kieBaseID == null) return;
		Map<StatsType, RuleRuntimeStats> statsMap = kieBaseToStatsMap.get(kieBaseID);
		if (statsMap == null) return;
		RuleRuntimeStats stats = statsMap.get(statsType);
		if (stats == null) return;
		stats.writeStats(mapper, writer);
	}

	@Override
	public void clearStats(KieBase kieBase, StatsType statsType) {
		String kieBaseID = getKieBaseID(kieBase);
		if (kieBaseID == null) return;
		if (statsType == null) return;
		Map<StatsType, RuleRuntimeStats> statsMap = kieBaseToStatsMap.get(kieBaseID);
		if (statsMap == null) return;
		RuleRuntimeStats stats = statsMap.get(statsType);
		if (stats == null) return;
		stats.clearStats();
	}
	
	@Override
	public void clearAllStats() {
		for (Map<StatsType, RuleRuntimeStats> statsMap : kieBaseToStatsMap.values()) {
			for (RuleRuntimeStats stats : statsMap.values()) {
				stats.clearStats();
			}
		}
	}

	@Override
	public void clearAllStats(StatsType statsType) {
		for (Map<StatsType, RuleRuntimeStats> statsMap : kieBaseToStatsMap.values()) {
			RuleRuntimeStats stats = statsMap.get(statsType);
			if (stats != null) {
				stats.clearStats();
			}
		}
	}

	@Override
	public void clearAllStats(KieBase kieBase) {
		String kieBaseID = getKieBaseID(kieBase);
		if (kieBaseID == null) return;
		Map<StatsType, RuleRuntimeStats> statsMap = kieBaseToStatsMap.get(kieBaseID);
		if (statsMap == null) return;
		for (RuleRuntimeStats stats : statsMap.values()) {
			stats.clearStats();
		}
	}

	@Override
	public void unregisterAllSessions(StatsType statsType) {
		if (statsType == null) return;
		for (Map<StatsType, RuleRuntimeStats> statsMap : kieBaseToStatsMap.values()) {
			RuleRuntimeStats stats = statsMap.get(statsType);
			if (stats != null) {
				stats.unregisterAllSessions();
			}
		}
	}

	@Override
	public void unregisterAllSessions() {
		for (Map<StatsType, RuleRuntimeStats> statsMap : kieBaseToStatsMap.values()) {
			for (RuleRuntimeStats stats : statsMap.values()) {
				stats.unregisterAllSessions();
			}
		}
	}

	private KieBase getKieBase(KieRuntimeEventManager runtime) {
		KieBase kieBase = null;
		if (runtime instanceof KieSession) {
			kieBase = ((KieSession)runtime).getKieBase();
		} else if (runtime instanceof StatelessKieSession) {
			kieBase = ((StatelessKieSession)runtime).getKieBase();
		}
		return kieBase;
	}
	
	private String getKieBaseID(KieBase kieBase) {
		if (kieBase == null) return null;
		return ((KnowledgeBaseImpl)kieBase).getId();
	}
}
