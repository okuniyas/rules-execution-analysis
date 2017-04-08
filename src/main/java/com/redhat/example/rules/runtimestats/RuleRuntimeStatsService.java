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

import org.kie.api.KieBase;
import org.kie.api.event.KieRuntimeEventManager;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controller Service of RuleRuntimeStats
 * 
 * @author okuniyas
 */
public interface RuleRuntimeStatsService {
	/**
	 * Type of stats.
	 * 
	 * <pre>
	 * EXECUTION_COUNT	: count execution times of each rules.
	 * ACTIVATION		: count activation created, executed and canceled.
	 * EXECUTION_SEQUENCE	: collect rule execution sequence for the last execution.
	 * NOOP			: duration time from the last reset to the reporting time.
	 * </pre>
	 */
	public static enum StatsType { EXECUTION_COUNT, ACTIVATION, EXECUTION_SEQUENCE, NOOP }

	public static final StatsType EXECUTION_COUNT = StatsType.EXECUTION_COUNT;
	public static final StatsType ACTIVATION = StatsType.ACTIVATION;
	public static final StatsType EXECUTION_SEQUENCE = StatsType.EXECUTION_SEQUENCE;
	public static final StatsType NOOP = StatsType.NOOP;

	/**
	 * Set the stats listener to the rule session
	 * @param session
	 * @param statsType
	 */
	void registerSession(KieRuntimeEventManager session, StatsType statsType);

	/**
	 * Reset the stats listener to the rule session
	 * @param session
	 * @param statsType
	 */
	void unregisterSession(KieRuntimeEventManager session, StatsType statsType);

	/**
	 * clear all setting of all stats types
	 */
	void unregisterAllSessions();

	/**
	 * clear all setting of the stats type
	 * @param statsType
	 */
	void unregisterAllSessions(StatsType statsType);

	/**
	 * write stats of specific stats type result about the KieBase
	 * @param writer
	 * @param kieBase
	 * @param statsType
	 */
	void writeStats(ObjectMapper mapper, Writer writer, KieBase kieBase, StatsType statsType);

	/**
	 * reset the stats about the KieBase
	 * @param kieBase
	 * @param statsType
	 */
	void clearStats(KieBase kieBase, StatsType statsType);

	/**
	 * reset the stats about all KieBase of all stats types
	 */
	void clearAllStats();

	/**
	 * reset the stats about all KieBase of specific stats type
	 * @param statsType
	 */
	void clearAllStats(StatsType statsType);

	/**
	 * reset the stats about specific KieBase of all stats type
	 * @param kieBase
	 */
	void clearAllStats(KieBase kieBase);

    /**
     * A Factory for this RuleRuntimeStatsService
     */
    public static class Factory {
        private static RuleRuntimeStatsService INSTANCE;

        static {
            try {
            	INSTANCE = ( RuleRuntimeStatsService )
            			Class.forName( "com.redhat.example.rules.runtimestats.impl.RuleRuntimeStatsServiceBean" ).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to instance RuleRuntimeStatsService", e);
            }
        }

        /**
         * Returns a reference to the RuleRuntimeStatsService singleton
         */
        public static RuleRuntimeStatsService get() {
            return INSTANCE;
        }
    }

}