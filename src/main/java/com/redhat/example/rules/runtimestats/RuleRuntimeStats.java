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

package com.redhat.example.rules.runtimestats;

import java.io.Writer;

import org.kie.api.event.KieRuntimeEventManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * RuleRuntimeStats interface
 * 
 * @author okuniyas
 *
 */
public interface RuleRuntimeStats {
	/**
	 * Get the stats type
	 * @return
	 */
	public StatsType getStatsType();
	/**
	 * Set the counter listener to the rule session
	 * @param session
	 */
	void registerSession(KieRuntimeEventManager session);

	/**
	 * Reset the counter listener to the rule session
	 * @param session
	 */
	void unregisterSession(KieRuntimeEventManager session);

	/**
	 * Reset the counter listener to the all rule sessions
	 * @param session
	 */
	void unregisterAllSessions();

	/**
	 * clear the stats
	 */
	public void clearStats();
	
	/**
	 * write stats in JSON format by Jackson
	 * @param mapper
	 * @param writer
	 */
	public void writeStats(ObjectMapper mapper, Writer writer);
	
}
