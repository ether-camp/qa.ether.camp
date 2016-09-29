package com.ethercamp.qa;

import com.ethercamp.qa.scenarios.CommonTestScenario;
import com.ethercamp.qa.scenarios.ProjectTestScenario;
import com.ethercamp.qa.ssh.SshConnector;
import com.ethercamp.qa.ssh.SshServer;

import java.io.File;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public class Test {

    public static void main2(String[] args) throws Exception {
        SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));
        SshServer server = new SshServer(sshConnector, "nashat-1.cloudapp.net", "azureuser");
        {
            int port = 30300;
            TestEnv testEnv = new TestEnv(server, "~/qa" + port)
                    .setPort(port)
                    .setDbDir("/mnt/ethereumj/qa" + port);
            CommonTestScenario scenario =
//                    new ProjectTestScenario(testEnv, "https://github.com/ether-camp/peer.ether.camp", new SampleTestListener())
//                    new ProjectTestScenario(testEnv, "https://github.com/ether-camp/ethereumj.starter", new SampleTestListener())
                new ProjectTestScenario(testEnv, "https://nashatyrev:kissme1_@github.com/etherj/vmtrace.ether.camp", new SampleTestListener()) {

                    @Override
                    protected void beforeStart() {
                        getConnection().execAll(
                                "pushd " + env.workDir,
                                "git clone https://nashatyrev:kissme1_@github.com/ether-camp/storage-dict",
                                "cd storage-dict",
                                "./gradlew clean install -x test",
                                "popd"
                        );
                    }

                    @Override
                    protected String getRunCommand(String javaArgs) {
                        return "sh ./run.sh";
                    }
                }
//                .setGradleTarget("clean bootRun")
//                .setJavaPath("/usr/lib/jvm/java-7-oracle/jre/bin/java")
                .setDeleteDB(true);

            scenario.setupImpl();
            scenario.startImpl();
        }
    }
    public static void main1(String[] args) throws Exception {
        SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));
        SshServer server = new SshServer(sshConnector, "nashat-1.cloudapp.net", "azureuser");
        {
            int port = 30300;
            TestEnv testEnv = new TestEnv(server, "~/qa" + port).setPort(port).setDbDir("/mnt/ethereumj/qa" + port);
            CommonTestScenario scenario = new CommonTestScenario(testEnv, new SampleTestListener())
                .setConfig(
                        "    peer.discovery.ip.list = [\n" +
                                "        \"52.16.188.185:30303\",\n" +
                                "        \"54.94.239.50:30303\",\n" +
                                "        \"frontier-2.ether.camp:30303\",\n" +
                                "        \"frontier-3.ether.camp:30303\",\n" +
                                "        \"frontier-4.ether.camp:30303\"\n" +
                                "    ]\n")
                .setDeleteDB(true);

            scenario.setupImpl();
            scenario.startImpl();
        }
    }

    public static void main(String[] args) throws Exception {
//        TestScenario scenario = new CommonTestScenario()
//                .setDescription("Core long run", "")
//                .setEnv(new TestEnv(Servers.servers[0], "/mnt/ethereumj/1").setPort(30301));
        TestScenario scenario = new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp")
                .setGradleTarget("bootRun")
                .setDescription("peer.ether.camp long run", "")
                .setEnv(new TestEnv(Servers.servers[0], "/mnt/ethereumj/2").setPort(30302));
        scenario.setListener(new SampleTestListener());

        System.out.println("Starting monitor");
        scenario.monitor();
        System.out.println("Monitor started");
        Thread.sleep(5000);
        System.out.println("Starting setup");
        scenario.setup();
        System.out.println("Starting ");
        scenario.start();

    }

    static class SampleTestListener extends TestListener.Adapter {
        @Override
        public void scenarioStarted(TestScenario ts) {
            System.err.println("Scenario started: " + ts);
        }

        @Override
        public void newBlockImported(TestScenario ts, int block, boolean best) {
            System.err.println("New block: " + block);
        }

        @Override
        public void scenarioFailed(TestScenario ts, Exception e) {
            System.err.println("Scenario failed.");
            e.printStackTrace();
        }
    }
}
