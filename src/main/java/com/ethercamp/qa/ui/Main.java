package com.ethercamp.qa.ui;

import com.ethercamp.qa.*;
import com.ethercamp.qa.scenarios.CommonTestScenario;
import com.ethercamp.qa.scenarios.PeerEtherCampScenario;
import com.ethercamp.qa.scenarios.ProjectTestScenario;
import com.ethercamp.qa.ssh.SshConnector;
import com.ethercamp.qa.ssh.SshServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class Main extends JPanel {

    static SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));


    SshServer nashat1 = new SshServer(sshConnector, "13.93.89.39", "ubuntu");

    List<TestScenario> scenarios = createScenarios();
    Monitor monitor = new Monitor() {
        {
            new Thread(this::autoUpdate).start();
        }

        private void autoUpdate() {
            while(true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                updated();
            }
        }

        @Override
        protected void updated() {
            table.update(new ArrayList<>(this.states.entrySet()));
        }
    };

    private static String ago(long time) {
        if (time == 0) return "--";
        long t = (System.currentTimeMillis() - time) / 1000;
        if (t < 1) return "< 1s";
        if (t < 60) return t + " sec";
        t = t / 60;
        if (t < 60) return t + " min";
        t = t / 60;
        if (t < 24) return t + " hr";
        return (t / 24) + " day";
    }

    static final NumberFormat F1 = new DecimalFormat("#0.0", new DecimalFormatSymbols(Locale.US));
    static final NumberFormat F2 = new DecimalFormat("#0.000", new DecimalFormatSymbols(Locale.US));

    JMyTable<Map.Entry<TestScenario, ScenarioState>> table = new JMyTable<Map.Entry<TestScenario, ScenarioState>>()
            .newColumn("Server", e -> e.getKey().getEnv().getServer().host)
            .newColumn("UserDir", e -> e.getKey().getEnv().getWorkDir())
            .newColumn("Descr", e -> e.getKey().getShortDescr())
            .newColumn("Block", e -> "" + e.getValue().getLastBlock())
                .withBold(e -> true)
            .newColumn("Last block", e -> "" + ago(e.getValue().getLastBlockTime()))
                .withColor(e -> {
                    String s = ago(e.getValue().getLastBlockTime());
                    if (s.endsWith("< 1s")) return Color.BLUE;
                    if (s.endsWith("sec")) return Color.GREEN.darker().darker();
                    if (s.endsWith("min")) return Color.ORANGE.darker().darker();
                    return Color.RED.darker();
                })
            .newColumn("Delay", e -> e.getValue().isShortSync() ? F2.format(e.getValue().getLastBlockDelay() / 1000d) : "")
                .withColor(e -> e.getValue().getLastBlockDelay() < 1000 ? Color.black : (e.getValue().getLastBlockDelay() < 10000 ? Color.ORANGE.darker() : Color.RED))
            .newColumn("Errors", e -> "" + e.getValue().getErrorCount())
                .withTooltip(e -> "<html>" + e.getValue().getLastErrors().stream().collect(Collectors.joining("<br>")) + "</html>")
            .newColumn("State", e -> "" + e.getValue().getState())
            .newColumn("ShortSync", e -> "" + (e.getValue().getBlockIntervals().isFilled() ? e.getValue().isShortSync() : ""))
                .withTooltip(e -> "<html>" + Arrays.stream(e.getValue().getBlockIntervals().getAllNums()).mapToObj(F1::format).collect(Collectors.joining("<br>")) + "</html>")
                .withColor(e -> e.getValue().isShortSync() ? Color.GREEN.darker().darker() : (e.getValue().wasShortSync() ? Color.RED : Color.DARK_GRAY))
            .newColumn("Time", Main::runTime)
                .withTooltip(Main::importTableHtml)
            .newColumn("Warns", e -> e.getValue().getWarnings().isEmpty() ? "" : e.getValue().getWarnings().size() + " warns")
                .withColor(e -> e.getValue().getWarnings().isEmpty() ? Color.BLACK : Color.RED)
                .withTooltip(e -> "<html>" + e.getValue().getWarnings().stream().collect(Collectors.joining("<br>")) + "</html>")
            .newColumn("Cmd", e -> {
                    String cmd = e.getValue().getLastCommand();
                    Boolean res = e.getValue().getLastResult();
                    if (cmd == null) return "";
                    return (res == null ? "Exec: " : (res ? "OK: " : "Fail: ")) + cmd ;
                })
                .withColor(e -> e.getValue().getLastResult() == null ? Color.BLUE : (e.getValue().getLastResult() ? Color.GREEN.darker().darker() : Color.RED))
            .end();

    private static DateFormat TIME_ONLY = new SimpleDateFormat("HH:mm");
    static {
        TIME_ONLY.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    private static String runTime(Map.Entry<TestScenario, ScenarioState> e) {
        List<ScenarioState.BlockInfo> blocks100K = e.getValue().getBlocks100K();
        if (blocks100K.isEmpty()) return "";
        return TIME_ONLY.format(new Date(System.currentTimeMillis() - blocks100K.get(0).timestamp));
    }
    private static String importTableHtml(Map.Entry<TestScenario, ScenarioState> e) {
        List<ScenarioState.BlockInfo> blocks100K = e.getValue().getBlocks100K();
        if (blocks100K.isEmpty()) return "";
        String ret = "<html><table>\n";
        for (int i = 1; i < blocks100K.size(); i++) {
            ScenarioState.BlockInfo bi0 = blocks100K.get(i-1);
            ScenarioState.BlockInfo bi1 = blocks100K.get(i);
            ret += "<tr><td>" + (bi1.num / 1000) + "k" + "</td>";
            ret += "<td>" + TIME_ONLY.format(new Date(bi1.timestamp - bi0.timestamp)) + "</td>";
            ret += "<td>" + TIME_ONLY.format(new Date(bi1.timestamp - blocks100K.get(0).timestamp)) + "</td>";
            ret += "</tr>\n";
        }
        return ret + "</table></html>";
    }

    void executeCmd(Map.Entry<TestScenario, ScenarioState> entry, Runnable r, String cmdName) {
        new Thread(() -> {
            entry.getValue().commandStarted(cmdName);
            try {
                r.run();
                entry.getValue().setLastResult(true);
            } catch (Throwable t) {
                entry.getValue().setLastResult(false);
                t.printStackTrace();
            }
        }).start();
    }

    Action actSetup = new AbstractAction("Setup") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (JOptionPane.showConfirmDialog(Main.this, "Are you sure want to set up?") != JOptionPane.YES_OPTION) return;
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                System.out.println("Setting up " + entry.getKey());
                executeCmd(entry, entry.getKey()::setup, (String) getValue("Name"));
            }
        }
    };

    Action actStart = new AbstractAction("Start") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                System.out.println("Starting " + entry.getKey());
                executeCmd(entry, entry.getKey()::start, (String) getValue("Name"));
            }
        }
    };

    Action actMonitor = new AbstractAction("Monitor") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                System.out.println("Monitoring " + entry.getKey());
                executeCmd(entry, entry.getKey()::monitor, (String) getValue("Name"));
            }
        }
    };

    Action actUpdate = new AbstractAction("Update") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                System.out.println("Updating " + entry.getKey());
                executeCmd(entry,
                        () -> {
//                            entry.getKey().stop();
                            ((CommonTestScenario) entry.getKey()).updateRepository();
//                            entry.getKey().start();
                        }, (String) getValue("Name"));
            }
        }
    };

    Action actStop = new AbstractAction("Stop") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                executeCmd(entry, entry.getKey()::stop, (String) getValue("Name"));
            }
        }
    };

    Action actClean = new AbstractAction("Clean") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (JOptionPane.showConfirmDialog(Main.this, "Are you sure want to clean?") != JOptionPane.YES_OPTION) return;
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                executeCmd(entry, entry.getKey()::cleanup, (String) getValue("Name"));
            }
        }
    };

    Action actResetErrors = new AbstractAction("Reset Errors") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                entry.getValue().resetErrors();
            }
        }
    };

    Action actCleanWarnings = new AbstractAction("Clean Warns") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Map.Entry<TestScenario, ScenarioState> entry : table.getSelected()) {
                entry.getValue().resetWarnings();
            }
        }
    };


    List<TestScenario> createScenarios() {
        List<TestScenario> ret = new ArrayList<>();

        String branch = "develop";

        ret.add(new CommonTestScenario()
                .setBranch(branch)
                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
                        "sync.fast.enabled = true\n")
                .setJvmArgs("-Xmx1000M")
                .setDescription("Core long fast run -Xmx1000M", "")
                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/1").setPort(30301)));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp")
//                .setGradleTarget("bootRun")
//                .setJvmArgs("-Xmx3000M")
//                .setBranch(branch)
//                .setDescription("peer.ether.camp long run", "")
//                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/2").setPort(30302)));
        ret.add(new CommonTestScenario() {
                    protected String getGradleTarget() {
                        return "run -PmainClass=org.ethereum.samples.BasicSample";
                    }
                }
                .setBranch(branch)
                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
                        "sync.fast.enabled = true\n")
                .setDescription("Core long fast BasicSample", "")
                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/3").setPort(30303)));
//        ret.add(new CommonTestScenario() {
//                    protected String getGradleTarget() {
//                        return "run -PmainClass=org.ethereum.samples.PrivateMinerSample";
//                    }
//                }
//                .setBranch(branch)
//                .setDescription("Core long run PrivateMinerSample", "")
//                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/4").setPort(30304)));
//        ret.add(new StorageDictDependent("https://nashatyrev:kissme1_@github.com/etherj/vmtrace.ether.camp")
//                .setDescription("vmtrace long run", "")
//                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/3").setPort(30303)));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/ethereumj.starter")
//                .setGradleTarget("bootRun")
//                .setBranch("master")
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("ethereumj.starter fast long run", "")
//                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/5").setPort(30305)));
//        ret.add(new StorageDictDependent("https://nashatyrev:kissme1_@github.com/etherj/state.ether.camp")
//                .setDescription("state long run", "")
//                .setEnv(new TestEnv(nashat3, "/mnt/ethereumj/2").setPort(30302)));
        ret.add(new CommonTestScenario()
                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
                        "sync.fast.enabled = false\n")
                .setBranch(branch)
                .setJvmArgs("-Xmx3000M")
                .setDescription("Core long nonfast", "")
                .setEnv(new TestEnv(nashat1, "/mnt/ethereumj/6").setPort(30306)));

//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setJvmArgs("-Xmx512M")
//                .setConfig("cache.flush.memory = 0.8\n")
//                .setDescription("Core long run with Xmx512M", "")
//                .setEnv(new TestEnv(nashat4, "/mnt/ethereumj/2").setPort(30302)));

//        ret.add(new CommonTestScenario() {
//                    protected String getGradleTarget() {
//                        return "runRopsten";
//                    }
//                }
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = false\n")
//                .setBranch(branch)
////                .setJvmArgs("-Xmx1000M")
//                .setDescription("Core long nonfast run ropsten", "")
//                .setEnv(new TestEnv(nashat4, "/mnt/ethereumj/3").setPort(30303)));
//        ret.add(new CommonTestScenario() {
//                    protected String getGradleTarget() {
//                        return "runTest";
//                    }
//                }
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("Core long fast run testnet", "")
//                .setEnv(new TestEnv(nashat4, "/mnt/ethereumj/4").setPort(30304)));
//        ret.add(new CommonTestScenario() {
//                    protected String getGradleTarget() {
//                        return "runTest";
//                    }
//                }
//                .setBranch(branch)
//                .setDescription("Core long nonfast run testnet", "")
//                .setEnv(new TestEnv(nashat5, "/mnt/ethereumj/1").setPort(30301)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = false\n")
//                .setDescription("Core long nonfast run", "")
//                .setEnv(new TestEnv(nashat5, "/mnt/ethereumj/2").setPort(30302)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n")
//                .setJvmArgs("-Xmx1G")
//                .setDescription("Core non-fast long run with Xmx1G", "")
//                .setEnv(new TestEnv(nashat5, "/mnt/ethereumj/3").setPort(30303)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("peer.privateKey = 0bdb5315963d45dfc61957a67c5ddfdde71326b0e9a768aa7546b25ee470af4b \n" +
//                        "database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n" +
//                        "peer.discovery.enabled = false \n" +
//                        "peer.active = [ \n" +
//                        "        { url = \"enode://69bfa214a36e0e4d9fe3437e2cd5450143133d46bb28cfdfe65b9215dafee080c7ac9b275638945d77806f2bfaebedd35b922b70fe1baab0e91165cfabbc5299@frontier-2.ether.camp:30303\" }, \n" +
//                        "        { url = \"enode://73bb09f38726f3ab55b5ef61f97bebfd12bdbf2be038665e22461591e99c1dcd90d4a07173e4fb32de0ea2108e87edda273fd887a1f4b0bab24a4738295e7c40@frontier-3.ether.camp:30303\" }, \n" +
//                        "        { url = \"enode://f8fb3f5c253b8685da8b887bb5de4e39bcca2a49d0bfc1a7174b5608a2ebda8b7b2759ad7621a489699bc41e89147fdde4e4e5668b95bb7d8dcd018e7019f7aa@frontier-4.ether.camp:30303\" }  \n" +
//                        "] \n")
//                .setDescription("Core long run with 3 active peers", "")
//                .setEnv(new TestEnv(nashat5, "/mnt/ethereumj/4").setPort(30304)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("peer.privateKey = 366fcfba0bad1413e9a328e5067da2302e90fcfe58b6912beacd91516ac903ca \n" +
//                        "peer.discovery.enabled = false \n" +
//                        "peer.active = [ \n" +
//                        "        { url = \"enode://69bfa214a36e0e4d9fe3437e2cd5450143133d46bb28cfdfe65b9215dafee080c7ac9b275638945d77806f2bfaebedd35b922b70fe1baab0e91165cfabbc5299@frontier-2.ether.camp:30303\" } \n" +
//                        "] \n")
//                .setDescription("Core long run with 1 active peers", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/1").setPort(30301)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("peer.privateKey = 366fcfba0bad1413e9a328e5067da2302e90fcfe58b6912beacd91516ac903ca \n" +
//                        "peer.discovery.enabled = false \n" +
//                        "sync.fast.enabled = true \n" +
//                        "peer.active = [ \n" +
//                        "        { url = \"enode://73bb09f38726f3ab55b5ef61f97bebfd12bdbf2be038665e22461591e99c1dcd90d4a07173e4fb32de0ea2108e87edda273fd887a1f4b0bab24a4738295e7c40@frontier-3.ether.camp:30303\" } \n" +
//                        "] \n")
//                .setDescription("Core fastsync long run with 1 active peers", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/2").setPort(30302)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("peer.privateKey = 366fcfba0bad1413e9a328e5067da2302e90fcfe58b6912beacd91516ac903ca \n" +
//                        "peer.discovery.enabled = false \n" +
//                        "peer.active = [ \n" +
//                        "        { url = \"enode://f8fb3f5c253b8685da8b887bb5de4e39bcca2a49d0bfc1a7174b5608a2ebda8b7b2759ad7621a489699bc41e89147fdde4e4e5668b95bb7d8dcd018e7019f7aa@frontier-4.ether.camp:30303\" } \n" +
//                        "] \n")
//                .setDescription("Core long run with 1 active peers", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/3").setPort(30303)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                            "sync.fast.enabled = false\n")
//                .setDescription("Core long nonfast run", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/4").setPort(30304)));
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                            "sync.fast.enabled = false\n")
//                .setDescription("Core long nonfast run ", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/5").setPort(30305)));
//        ret.add(new CommonTestScenario() {
//                    protected String getGradleTarget() {
//                        return "run -PmainClass=org.ethereum.samples.BasicSample";
//                    }
//                }
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n")
//                .setDescription("Core long nonfast run BasicSample", "")
//                .setEnv(new TestEnv(nashat6, "/mnt/ethereumj/6").setPort(30306)));
//
//        ret.add(new CommonTestScenario()
//                .setBranch(branch)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n")
//                .setDescription("Core long nonfast run", "")
//                .setEnv(new TestEnv(nashat_centos1, "/mnt/resource/ethereumj/1").setPort(30301)));
//
//
//        ret.add(new PeerEtherCampScenario(5, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.1")
//                        .setPort(30303)));
//        ret.add(new PeerEtherCampScenario(7, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.2")
//                        .setPort(30305)));
//        ret.add(new PeerEtherCampScenario(9, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.3")
//                        .setPort(30307)));
//        ret.add(new PeerEtherCampScenario(11, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.4")
//                        .setPort(30309)));
//        ret.add(new PeerEtherCampScenario(13, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.5")
//                        .setPort(30311)));
//        ret.add(new PeerEtherCampScenario(15, false)
//                .setConfig("database.incompatibleDatabaseBehavior = RESET\n" +
//                        "sync.fast.enabled = true\n")
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/peer.6")
//                        .setPort(30313)));



//        ret.add(new PeerEtherCampScenario(6, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.1")
//                        .setPort(30304)));
//        ret.add(new PeerEtherCampScenario(8, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.2")
//                        .setPort(30306)));
//        ret.add(new PeerEtherCampScenario(10, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.3")
//                        .setPort(30308)));
//        ret.add(new PeerEtherCampScenario(12, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.4")
//                        .setPort(30310)));
//        ret.add(new PeerEtherCampScenario(14, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.5")
//                        .setPort(30312)));
//        ret.add(new PeerEtherCampScenario(16, true)
//                .setDescription("netstat run morden", "")
//                .setEnv(new TestEnv(nashat2, "/mnt/ethereumj/morden.6")
//                        .setPort(30314)));

//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//            protected String getRunCommand(String javaArgs) {
//                return "./run.sh";
//            }
//        }
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.1")
//                        .setPort(30303)
//                        .setDbDir("/mnt/ethereumj/peer.1")));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//                    protected String getRunCommand(String javaArgs) {
//                        return "./run.sh";
//                    }
//                    public String getRepoDir() {
//                        return env.getWorkDir();
//                    }
//                }
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.2").setPort(30305)));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//                    protected String getRunCommand(String javaArgs) {
//                        return "./run.sh";
//                    }
//                    public String getRepoDir() {
//                        return env.getWorkDir();
//                    }
//                }
//                .setDescription("netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.3").setPort(30307)));
//
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//                    protected String getRunCommand(String javaArgs) {
//                        return "./run.sh";
//                    }
//                    public String getRepoDir() {
//                        return env.getWorkDir();
//                    }
//                }
//                .setDescription("morden netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.morden.1").setPort(30304)));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//                    protected String getRunCommand(String javaArgs) {
//                        return "./run.sh";
//                    }
//                    public String getRepoDir() {
//                        return env.getWorkDir();
//                    }
//                }
//                .setDescription("morden netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.morden.2").setPort(30306)));
//        ret.add(new ProjectTestScenario("https://github.com/ether-camp/peer.ether.camp") {
//                    protected String getRunCommand(String javaArgs) {
//                        return "./run.sh";
//                    }
//                    public String getRepoDir() {
//                        return env.getWorkDir();
//                    }
//                }
//                .setDescription("morden netstat run", "")
//                .setEnv(new TestEnv(nashat2, "/home/azureuser/peer.ether.camp.morden.3").setPort(30308)));


        return ret;
    }

    void startMonitoring() {
        for (TestScenario scenario : scenarios) {
            scenario.setListener(monitor);
            monitor.addNew(scenario);
//            scenario.monitor();
        }
    }

    void init() {
        layoutGui();
        startMonitoring();
    }

    void layoutGui() {
        setLayout(new BorderLayout(5, 5));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttonPane.add(new JButton(actSetup));
        buttonPane.add(new JButton(actStart));
        buttonPane.add(new JButton(actMonitor));
        buttonPane.add(new JButton(actUpdate));
        buttonPane.add(new JButton(actStop));
        buttonPane.add(new JButton(actClean));
        buttonPane.add(new JButton(actResetErrors));
        buttonPane.add(new JButton(actCleanWarnings));
        add(buttonPane, BorderLayout.SOUTH);
    }

    public static void main(String[] args) throws Exception {
        System.out.println(TIME_ONLY.format(new Date(15 * 60*1000)));
//        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Main main = new Main();
        main.init();

        JFrame jFrame = new JFrame();
        jFrame.add(main);
        jFrame.setBounds(100, 100, 1000, 600);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.setVisible(true);
    }
}
