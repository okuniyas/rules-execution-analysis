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

import java.util.Iterator;
import java.util.List;

import org.kie.api.KieBase;
import org.kie.api.command.Command;

import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule Runtime Compare Service<BR>
 * with two rules (two KieBases) and one data set.
 * 
 * @author okuniyas
 */
public interface RuleRuntimeCompareService {
	public static final String SAME_HEADER = "= ";
	public static final String DIFF_HEADER = "! ";
	public static final String SAME_ARRAY = SAME_HEADER + "[...]";
	public static final String SAME_MAP = SAME_HEADER + "{...}";
	
	/**
	 * <p>
	 * Take RuleRuntimeStats of kieBase1 and kieBase2 and returns below information.
	 * </p>
	 * 
	 * <pre>
	 * [0] Rule runtime stats of kieBase1
	 * [1] Rule runtime stats of kieBase2
	 * [2] Comparison of [0] and [1]
	 * [3] Result facts inserted for kieBase1
	 * [4] Result facts inserted for kieBase2
	 * [5] Comparison of [3] and [4]
	 * </pre>
	 * 
	 * @param kieBase1 first KieBase
	 * @param kieBase2 second KieBase
	 * @param commandsFactory factory of commands
	 * @param statsType type of the rule runtime stats. {@link RuleRuntimeStatsService.StatsType}
	 * @return runtime stats information, comparison of stats and comparison of facts in JSON format.
	 */
	public String[] compareExecution(KieBase kieBase1, KieBase kieBase2, CommandsFactory commandsFactory, StatsType statsType);

	/**
	 * similar to compareExecution() but this method is for warming up only, no return value.
	 * @param baseRules
	 * @param workingRules
	 * @param commandsFactory
	 * @param statsType
	 * @param endWarmupTime
	 */
	public void compareExecutionForWarmup(KieBase baseRules, KieBase workingRules, CommandsFactory commandsFactory,
			StatsType statsType, long endWarmupTime);
	
	/**
	 * change the maximum fact list size<BR>
	 * the default is 1000 as it takes too long time to get differences of big number of facts.
	 * @param max
	 */
	public void setMaximumFactListSize(int max);

	/**
	 * Generates comparison string from two rule runtime stats
	 * @param stats1
	 * @param stats2
	 * @return comparison string from two rule runtime stats
	 */
	public String compareStats(String stats1, String stats2);

	/**
	 * Generates comparison string from two lists of facts
	 * @param factsStr1
	 * @param factsStr2
	 * @return comparison string from two lists of facts
	 */
	public String compareFacts(String factsStr1, String factsStr2);
	
	/**
     * A Factory for this RuleRuntimeCompareService
     */
    public static class Factory {
        private static RuleRuntimeCompareService INSTANCE;

        static {
            try {
            	INSTANCE = ( RuleRuntimeCompareService )
            			Class.forName( "com.redhat.example.rules.runtimestats.impl.RuleRuntimeCompareServiceBean" ).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to instance RuleRuntimeCompareService", e);
            }
        }

        /**
         * Returns a reference to the RuleRuntimeStatsService singleton
         */
        public static RuleRuntimeCompareService get() {
            return INSTANCE;
        }
    }
    
    public interface CommandsFactory {
    	/**
    	 * provides the first static commands<BR>
    	 * this commands are executed in every executions.
    	 * @return
    	 */
    	public List<Command<?>> getStaticFirstCommands();

    	/**
    	 * provides dynamic body commands iterator<BR>
    	 * @return the iterator of commands which used in each execution.
    	 */
    	public Iterator<List<Command<?>>> getBodyCommandsIterator();

    	/**
    	 * provides the last static commands<BR>
    	 * this commands are executed in every executions.
    	 * @return
    	 */
    	public List<Command<?>> getStaticLastCommands();
    }
}
