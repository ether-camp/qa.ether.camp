package com.ethercamp.qa.scenarios;

import com.ethercamp.qa.TestScenario;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class Scenarios {

    public static TestScenario[] scenarios = new TestScenario[] {
            new CommonTestScenario().setDescription("Core long run", ""),
            new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp")
                    .setDescription("peer.ether.camp long run", ""),
            new ProjectTestScenario("https://github.com/ether-camp/ethereumj.starter")
                    .setDescription("ethereumj.starter long run", ""),
            new StorageDictDependent("https://nashatyrev:kissme1_@github.com/etherj/vmtrace.ether.camp")
                    .setDescription("vmtrace long run", ""),
            new StorageDictDependent("https://nashatyrev:kissme1_@github.com/etherj/state.ether.camp")
                    .setDescription("state long run", "")
    };

}
