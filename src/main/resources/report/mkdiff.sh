#!/bin/sh

if [ -f result_facts_base_rules.json ]; then
    diff -u result_facts_base_rules.json result_facts_working_rules.json > result_facts.diff
fi
for stats in execution_count activation execution_sequence noop; do
    if [ -f ${stats}_rule_runtime_stats_base_rules.json ]; then
	diff -u ${stats}_rule_runtime_stats_base_rules.json ${stats}_rule_runtime_stats_working_rules.json > ${stats}_rule_runtime_stats.diff
    fi
done
