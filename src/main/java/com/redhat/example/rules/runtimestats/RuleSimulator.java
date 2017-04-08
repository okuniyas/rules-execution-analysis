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

import org.kie.api.KieBase;
import com.redhat.example.rules.runtimestats.RuleRuntimeCompareService.CommandsFactory;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule Simulator interface
 * @author okuniyas
 */
public interface RuleSimulator {
	/**
	 * provides the base rules
	 * @return the base rules
	 */
	public KieBase getBaseRules();
	
	/**
	 * provides the working rules
	 * @return the working rules
	 */
	public KieBase getWorkingRules();

	/**
	 * provides the report folder
	 * @return path of the folder
	 */
	public String getReportDir();
	
	/**
	 * verifies the parameters
	 * @return true if parameters are valid.
	 */
	public boolean isValidParameters();
	
	/**
	 * execute by the specified rules runtime stats.
	 * @param commandsFactory
	 * @param statsType
	 * @return result stats
	 */
	public String[] execute(CommandsFactory commandsFactory, StatsType statsType);
	
	/**
	 * execute for all rules runtime stats.
	 * @param commandsFactory
	 */
	public void executeAllStats(CommandsFactory commandsFactory);

	/**
	 * aggregate already created two reports into new report
	 * @param baseReportPath report path of base stats
	 * @param useBase if true, the base stats of baseReportPath is used as new base stats.
	 * @param workingReportPath report path of working stats
	 * @param useWorking if true, the working stats of workingReportPath is used as new working stats.
	 * @param newReportPath path of new report
	 */
	public void aggregate(String baseReportPath, boolean useBase,
			String workingReportPath, boolean useWorking, String newReportPath);
}
