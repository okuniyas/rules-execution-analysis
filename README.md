# rules-execution-analysis
tool to compare two JBoss BRMS/Drools rules executions with different two versions of rules with same data.

## Why

With JBoss BRMS/Drools, After start modification of existing rules or tuning rules,<BR>
you would have questions such as ...


+ How much different the execution speed ?
+ Are there differences of the result facts ?
+ Are there differences of the execution count of each rules ?
+ Are there differences of the execution sequence of rules ?
+ Are there differences of the activations count created/canceled/executed in the rule engine ?


This is a tool to answer above questions.<BR>
The tool executes the two versions of the rules with the same data then generates a HTML report.<BR>
The report can be integrated into the Testing or Simulation Pipleline with the HTML report plugin of Jenkins.


### An example report

After warm up and execution of two version of rules, you will get a  HTML report like below.

![An example report](https://github.com/okuniyas/rules-execution-analysis/images/activation_report.png)

This is the part of the Activation differences. By some tuning of rules, most of the useless activations were removed. In the Activation part, you can see ...

+ How many activations created of each rules.
+ Which rule were executed prior such activation creation.
+ How many such activation executed/canceled.
+ Which rule were executed prior such activation execution/cancellation.

## How to use

```
    DefaultRuleSimulator ruleSimulator = new DefaultRuleSimulator();
    ruleSimulator.setBaseRules(kieContainer.getKieBase("rules"));
    ruleSimulator.setWorkingRules(kieContainer.getKieBase("workingRules"));
    ruleSimulator.setWarmupSeconds(30);
    ruleSimulator.setReportDir(reportBaseDir);
    ruleSimulator.executeAllStats(commandsFactory);
```

After warm up and execution of two verson of rules,
`report.html` will be generated in the `reportBaseDir` path.

### How to pass the test data

Check the [`CommandsFactory`](src/main/java/com/redhat/example/rules/runtimestats/RuleRuntimeCompareService.java#L118) interface and its test sample code in the [`RuleSimulatorTest`](src/test/java/com/redhat/example/rules/runtimestats/test/RuleSimulatorTest.java).

## License

[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html)

## Author

[okuniyas](https://github.com/okuniyas)

