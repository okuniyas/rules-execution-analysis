package com.redhat.example.rules.runtimestats.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;

import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;

import com.redhat.example.rules.fact.Message;

public class RuleExecutionTest {

	@Test
	public void test() {
        KieServices ks = KieServices.Factory.get();
	    KieContainer kContainer = ks.getKieClasspathContainer();
	    KieBase kBase = kContainer.getKieBase("rules");
	    
    	StatelessKieSession kSession = kBase.newStatelessKieSession();
    	
        // go !
    	List<Message> messages = new ArrayList<Message>();
        Message message1 = new Message();
        message1.setMessage("Hello World 1");
        message1.setStatus(Message.HELLO);
        messages.add(message1);
        Message message2 = new Message();
        message2.setMessage("Hello World 2");
        message2.setStatus(Message.HELLO);
        messages.add(message2);
        
        // 
        List<Command<?>> cmds = new ArrayList<Command<?>>();
        KieCommands kcmd = ks.getCommands();
        cmds.add(kcmd.newInsertElements(messages));
        cmds.add(kcmd.newStartProcess("com.sample.bpmn.hello"));
        cmds.add(kcmd.newFireAllRules("fireCount"));
        ExecutionResults results = kSession.execute(kcmd.newBatchExecution(cmds));
        Integer fireCount = (Integer)results.getValue("fireCount");
        assertThat(fireCount, is(3));
	}

}
