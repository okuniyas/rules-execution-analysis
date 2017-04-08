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

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.drools.core.command.runtime.rule.InsertElementsCommand;
import org.drools.core.command.runtime.rule.InsertObjectCommand;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.concurrent.ExecutorProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.EmptyIterator;
import com.redhat.example.rules.runtimestats.RuleRuntimeCompareService;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Rule Runtime Compare Service Bean<BR>
 * with two rules (two KieBases) and one data set.
 * 
 * @author okuniyas
 */
public class RuleRuntimeCompareServiceBean implements RuleRuntimeCompareService {
	private static final Logger logger = LoggerFactory.getLogger(RuleRuntimeCompareServiceBean.class);
			
	private KieServices ks = KieServices.Factory.get();
	private KieCommands kcommands = ks.getCommands();
	private RuleRuntimeStatsServiceBean runtimeStatsService =
			(RuleRuntimeStatsServiceBean)RuleRuntimeStatsService.Factory.get();
	
	private static final ObjectMapper mapper =
			new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.setDateFormat(
					SimpleDateFormat.getDateTimeInstance(
							DateFormat.LONG, DateFormat.LONG
							));


	private int maximumFactListSize = 1000;
	
	// references of inserted objects
	private ArrayList<Object> facts1 = new ArrayList<Object>(maximumFactListSize);
	private ArrayList<Object> facts2 = new ArrayList<Object>(maximumFactListSize);

	@Override
	public void compareExecutionForWarmup(KieBase baseRules, KieBase workingRules, CommandsFactory commandsFactory,
			StatsType statsType, long endWarmupTime) {
		compareExecution(baseRules, workingRules, commandsFactory, statsType, endWarmupTime);
	}
	
	@Override
	public String[] compareExecution(KieBase kieBase1, KieBase kieBase2,
			CommandsFactory commandsFactory, StatsType statsType) {
		return compareExecution(kieBase1, kieBase2, commandsFactory, statsType, -1);
	}
	

	@Override
	public void setMaximumFactListSize(int max) {
		maximumFactListSize = max;
		facts1.ensureCapacity(max);
		facts2.ensureCapacity(max);
	}
	
	private String[] compareExecution(KieBase kieBase1, KieBase kieBase2, CommandsFactory commandsFactory,
			StatsType statsType, long endWarmupTime) {
		String[] ret = new String[6];

		long endWarmupTime1 = -1;
		if (endWarmupTime > 0) {
			endWarmupTime1 = System.currentTimeMillis();
			endWarmupTime1 += (endWarmupTime - endWarmupTime1)/2;
		}

		// [0] Rule runtime stats for kieBase1
		runtimeStatsService.clearStats(kieBase1, statsType);
		ret[0] = executeAll(kieBase1, commandsFactory, statsType, facts1, endWarmupTime1);
		// [1] Rule runtime stats for kieBase2
		runtimeStatsService.clearStats(kieBase2, statsType);
		ret[1] = executeAll(kieBase2, commandsFactory, statsType, facts2, endWarmupTime);
		
		// clear stats
		runtimeStatsService.unregisterAllSessions();
		runtimeStatsService.clearAllStats();
		
		if (endWarmupTime > 0) {
			return null;
		}
				
		// [2] Compare two stats
		try {
			ret[2] = compareStats(ret[0], ret[1]);
		} catch (Exception e) {
			e.printStackTrace();
			ret[2] = "{}";
		}
		
		// [3] Result facts inserted for kieBase1
		try {
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, facts1);
			ret[3] = writer.toString();
		} catch (Exception e) {
			e.printStackTrace();
			ret[3] = "{}";
		}
		
		// [4] Result facts inserted for kieBase2
		try {
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, facts2);
			ret[4] = writer.toString();
		} catch (Exception e) {
			e.printStackTrace();
			ret[4] = "{}";
		}
		
		// [5] Compare inserted facts
		try {
			ret[5] = compareFacts(facts1, facts2);
		} catch (Exception e) {
			e.printStackTrace();
			ret[5] = "{}";
		}
		
		facts1.clear();
		facts2.clear();
		
		return ret;
	}

	private String executeAll(KieBase kieBase, CommandsFactory commandsFactory,
			StatsType statsType, List<Object> facts, long endWarmupTime) {
		boolean isWarmup = endWarmupTime > 0;
		ThreadPoolExecutor ex = null;
		long i = 0;
		if (isWarmup && logger.isTraceEnabled()) {
			ex = (ThreadPoolExecutor)ExecutorProviderFactory.getExecutorProvider().getExecutor();
			i = ex.getCompletedTaskCount();
		}
		Iterator<List<Command<?>>> commandsIte =
				commandsFactory.getBodyCommandsIterator();
		while (commandsIte.hasNext()) {
			if (isWarmup && System.currentTimeMillis() > endWarmupTime) {
				break;
			}
			List<Command<?>> commands = new ArrayList<Command<?>>();
			for (Command<?> cmd : commandsFactory.getStaticFirstCommands()) {
				commands.add(cmd);
				// collect references of inserted objects
				if (!isWarmup) {
					if (cmd instanceof InsertObjectCommand) {
						if (facts.size() < maximumFactListSize) {
							facts.add(((InsertObjectCommand)cmd).getObject());
						}
					} else if (cmd instanceof InsertElementsCommand) {
						for (Object o : ((InsertElementsCommand)cmd).getObjects()) {
							if (maximumFactListSize <= facts.size()) {
								break;
							}
							facts.add(o);
						}
					}
				}
			}
			for (Command<?> cmd : commandsIte.next()) {
				commands.add(cmd);
				// collect references of inserted objects
				if (!isWarmup) {
					if (cmd instanceof InsertObjectCommand) {
						if (facts.size() < maximumFactListSize) {
							facts.add(((InsertObjectCommand)cmd).getObject());
						}
					} else if (cmd instanceof InsertElementsCommand) {
						for (Object o : ((InsertElementsCommand)cmd).getObjects()) {
							if (maximumFactListSize <= facts.size()) {
								break;
							}
							facts.add(o);
						}
					}
				}
			}
			for (Command<?> cmd : commandsFactory.getStaticLastCommands()) {
				commands.add(cmd);
				// collect references of inserted objects
				if (!isWarmup) {
					if (cmd instanceof InsertObjectCommand) {
						if (facts.size() < maximumFactListSize) {
							facts.add(((InsertObjectCommand)cmd).getObject());
						}
					} else if (cmd instanceof InsertElementsCommand) {
						for (Object o : ((InsertElementsCommand)cmd).getObjects()) {
							if (maximumFactListSize <= facts.size()) {
								break;
							}
							facts.add(o);
						}
					}
				}
			}
			StatelessKieSession kieSession = kieBase.newStatelessKieSession();
			runtimeStatsService.registerSession(kieSession, statsType);
			kieSession.execute(kcommands.newBatchExecution(commands));
			if (ex != null) {
				long c = ex.getCompletedTaskCount();
				long a = ex.getActiveCount();
				if (i < c) {
					logger.trace("Warming Up Jitting Threads: {} completed, {} active.", c, a);
					i = c;
				}
			}
		}
		if (isWarmup) {
			return null;
		}
		StringWriter writer = new StringWriter();
		runtimeStatsService.writeStats(mapper, writer, kieBase, statsType);
		return writer.toString();
	}

	@Override
	public String compareStats(String stats1, String stats2) {
		LinkedHashMap<String, Object> retMap = new LinkedHashMap<String, Object>();
		Map<String, Object> map1, map2;
		try {
			map1 = mapper.readValue(stats1,
					new TypeReference<LinkedHashMap<String, Object>>() {});
			map2 = mapper.readValue(stats2,
					new TypeReference<LinkedHashMap<String, Object>>() {});
			for (String key : map1.keySet()) {
				if (key.equals("notExecutedRules")) {
					// String list
					@SuppressWarnings("unchecked")
					Collection<Object> list1 = (Collection<Object>)(map1.get(key));
					@SuppressWarnings("unchecked")
					Collection<Object> list2 = (Collection<Object>)(map2.get(key));
					if (!list1.equals(list2)) {
						retMap.put(key, new DiffList(list1, list2));
					} else {
						retMap.put(key, SAME_ARRAY);
					}
				} else {
					retMap.put(key, compareObj(map1.get(key), map2.get(key)));
				}
			}
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, retMap);
			return writer.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "{}";
	}
	
	@Override
	public String compareFacts(String factsStr1, String factsStr2) {
		ArrayList<Object> facts1 =
				new ArrayList<Object>();
		ArrayList<Object> facts2 =
				new ArrayList<Object>();
		try {
			facts1 = mapper.readValue(factsStr1,
					new TypeReference<ArrayList<Object>>() {});
			facts2 = mapper.readValue(factsStr2,
					new TypeReference<ArrayList<Object>>() {});
			return compareFacts(facts1, facts2);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "{}";
	}
	
	private String compareFacts(ArrayList<Object> facts1, ArrayList<Object> facts2) {
		try {
			Object facts_diff = new DiffList(facts1, facts2);
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, facts_diff);
			return writer.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "{}";
	}


	private static Object compareObj(Object obj1, Object obj2) {
		if (obj1 == null || obj2 == null) {
			if (obj1 == obj2)
				return getSameObj(obj1);
			else
				return getDiffObj(obj1, obj2);
		}
		if (! obj1.equals(obj2)) {
			if (obj1 instanceof Number) {
				Number num1 = (Number)obj1;
				Number num2 = (Number)obj2;
				return getDiffNumber(num1, num2);
			} else if (obj1 instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> subMap1 = (Map<String, Object>)obj1;
				@SuppressWarnings("unchecked")
				Map<String, Object> subMap2 = (Map<String, Object>)obj2;
				return getMapDifference(subMap1, subMap2);
			} else if (obj1 instanceof Collection) {
				@SuppressWarnings("unchecked")
				Collection<Object> col1 = (Collection<Object>)obj1;
				@SuppressWarnings("unchecked")
				Collection<Object> col2 = (Collection<Object>)obj2;
				return getCollectionDifference(col1, col2);
			} else {
				return getDiffObj(obj1, obj2);
			}
		} else {
			// same
			if (obj1 instanceof Map) {
				return SAME_MAP;
			} else if (obj1 instanceof Collection) {
				return SAME_ARRAY;
			} else {
				return getSameObj(obj1);
			}
		}
	}

	private static Object getMapDifference(Map<String, Object> subMap1, Map<String, Object> subMap2) {
		LinkedHashMap<String, Object> retMap = new LinkedHashMap<String, Object>();
		for (String key : subMap1.keySet()) {
			retMap.put(key, compareObj(subMap1.get(key), subMap2.get(key)));
		}
		return retMap;
	}

	private static Object getCollectionDifference(Collection<Object> obj1, Collection<Object> obj2) {
		ArrayList<Object> ret = new ArrayList<Object>();
		Iterator<Object> ite1 = obj1 != null ? obj1.iterator() : new EmptyIterator<Object>();
		Iterator<Object> ite2 = obj2 != null ? obj2.iterator() : new EmptyIterator<Object>();
		while (ite1.hasNext() || ite2.hasNext()) {
			Object item1 = ite1.hasNext() ? ite1.next() : null;
			Object item2 = ite2.hasNext() ? ite2.next() : null;
			ret.add(compareObj(item1, item2));
		}
		return ret;
	}

	private static String getSameObj(Object obj1) {
		return SAME_HEADER + obj1;
	}

	private static String getDiffObj(Object obj1, Object obj2) {
		return DIFF_HEADER + obj1 + " -> " + obj2;
	}
	
	private static Object getDiffNumber(Number num1, Number num2) {
		if (num1 instanceof Integer || num1 instanceof Long) {
			long val1 = num1.longValue();
			long val2 = num2.longValue();
			long diff = val2 - val1;
			return String.format("%s%d -> %d (%+d, %+.1f%%)", DIFF_HEADER, val1, val2, diff, (float)(diff*100.0/val1));
		} else {
			double val1 = num1.doubleValue();
			double val2 = num2.doubleValue();
			double diff = val2 - val1;
			return String.format("%s%f -> %f (%+.1f, %+.1f%%)", DIFF_HEADER, (float)val1, (float)val2, (float)diff, (float)(diff*100/num1.doubleValue()));
		}
	}
	
	/*
	 * 
	 */
	@JsonSerialize(using=WrapItemSerializer.class)
	public static class DiffList implements Iterator<String> {
		Iterator<Object> ite1, ite2;
		
		public DiffList(Collection<Object> list1, Collection<Object> list2) {
			ite1 = list1 != null ? list1.iterator() : new EmptyIterator<Object>();
			ite2 = list2 != null ? list2.iterator() : new EmptyIterator<Object>();
		}

		@Override
		public boolean hasNext() {
			return ite1.hasNext() || ite2.hasNext();
		}

		@Override
		public String next() {
			Object item1 = ite1.hasNext() ? ite1.next() : null;
			Object item2 = ite2.hasNext() ? ite2.next() : null;
			Object diff = compareObj(item1, item2);
			return diff.toString();
		}
		
	}
}
