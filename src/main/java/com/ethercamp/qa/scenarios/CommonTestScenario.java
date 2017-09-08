package com.ethercamp.qa.scenarios;

import com.ethercamp.qa.ScenarioState;
import com.ethercamp.qa.ssh.SshConnection;
import com.ethercamp.qa.TestEnv;
import com.ethercamp.qa.TestListener;
import com.ethercamp.qa.TestScenario;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Anton Nashatyrev on 18.05.2016.
 */
public class CommonTestScenario extends TestScenario {

    List<Integer> backgroundTasks = new ArrayList<>();
    String branch;

    String config;

    String javaPath;

    private String jvmArgs = "";

    boolean deleteDB;

    public CommonTestScenario() {
    }

    public CommonTestScenario(TestEnv env, TestListener listener) {
        super(env, listener);
    }

    public CommonTestScenario setConfig(String config) {
        this.config = config;
        return this;
    }

    public CommonTestScenario setJavaPath(String javaPath) {
        this.javaPath = javaPath;
        return this;
    }

    public CommonTestScenario setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
        return this;
    }

    public CommonTestScenario setBranch(String branch) {
        this.branch = branch;
        return this;
    }

    public CommonTestScenario setDeleteDB(boolean deleteDB) {
        this.deleteDB = deleteDB;
        return this;
    }

    @Override
    public void setupImpl() throws Exception {
        stop();
        getConnection().execAll(
//                "killall java; true",
                "rm -rf " + env.getWorkDir() + "; true",
                "if [ ! -d " + env.getWorkDir() + " ]; then mkdir -p " + env.getWorkDir() + "; fi",
                "cd " + env.getWorkDir(),
                "git clone -q " + (branch == null ? "" : "-b " + branch) + " " + getRepository() + " repo",
                "cd repo",
                "chmod +x ./gradlew"
        );

        if (config != null) {
            installConfig(config);
        }

        if (deleteDB && env.getDbDir() != null) {
            getConnection().execSynchronously("rm -rf " + env.getDbDir(), false);
        }
    }

    @Override
    public void cleanup() {
        try {
            stop();
            getConnection().execSynchronously("rm -rf " + env.getDbDir(), false);
            getConnection().execSynchronously("rm -rf " + env.getWorkDir(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startImpl() throws Exception {

        if (getConnection().execSynchronously("cd " + getRepoDir(), true).getExitCode() != 0) {
            throw new RuntimeException("Can't cd to work dir");
        };

        getConnection().execSynchronously("pwd", false);

        if (javaPath != null) {
            getConnection().execSynchronously("export JAVA_HOME=" + javaPath, false);
        }

        String javaArgs = this.jvmArgs + " ";

        if (env.getDbDir() != null) {
            javaArgs += "-Ddatabase.dir=" + env.getDbDir() + " ";
        }
        if (env.getPort() != null) {
            javaArgs += "-Dpeer.listen.port=" + env.getPort() + " ";
        }
        javaArgs = javaArgs.trim();

        beforeStart();
//        int pid = con.execBackground("./gradlew " + (javaArgs.isEmpty() ? "" : "-PjvmArgs='" + javaArgs + "' ") + "run >/dev/null 2>&1 &");
//        backgroundTasks.add(pid);
//        System.err.println("Started run: " + pid);

        SshConnection.ExecResult execResult = getConnection().execSynchronously(getRunCommand(javaArgs), true);

        if (!waitForFile(getLogFile(), 60)) throw new RuntimeException("The log file didn't appear: " + getLogFile());

        listener.scenarioStarted(this);
    }

    @Override
    public void stop() {
        try {
            String javaPid = getJavaPid();
            if (!javaPid.isEmpty()) {
                getConnection().execSynchronously("kill " + javaPid, false);
                boolean killed = false;
                for (int i = 0; i < 20; i++) {
                    if (getJavaPid().isEmpty()) {
                        killed = true;
                        break;
                    }
                    Thread.sleep(1000);
                }

                if (!killed) {
                    getConnection().execSynchronously("kill -9 " + getJavaPid(), false);
                }
            }

            listener.scenarioStopped(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getJavaPid() throws Exception {
        if (!isFileExist(getConnection(), getLogFile())) return "";
        SshConnection.ExecResult result = getConnection().execSynchronously("lsof " + getLogFile() + " | grep java | awk '{print $2}'", false);
        return result.getOutputTail().trim();
    }

    protected void beforeStart() {

    }

    DateFormat DF1 = new SimpleDateFormat("HH:mm:ss.SSS");
    ScenarioState.Median timeDifStat = new ScenarioState.Median(10);

    @Override
    public void monitorImpl() throws Exception {
        SshConnection con = env.getServer().createConnection();
//        con.init(System.out);
        con.init();

        waitForFile(con, getLogFile(), 100000000);

        SshConnection.ExecResult result = con.execAsynchronously("tail -F " + getLogFile() + " | grep --color=never 'importing\\| ERROR '", true);
        BufferedReader br = new BufferedReader(new InputStreamReader(result.getOut()));

        while(true) {
            String s = br.readLine();
            String time = s.substring(0, 12);

            try {
                Date logTime = DF1.parse(time);
                Date now = new Date();
                long diff = now.getTime() - logTime.getTime();
                if (!timeDifStat.isFilled()) {
                    timeDifStat.add(diff);
                } else {
                    diff -= timeDifStat.getPercentile(50);
                }
//                System.out.println("---- diff: " + diff);
            } catch (ParseException | NumberFormatException e) {
                System.err.println("Can't parse: '" + time + "'");
            }

            if (s.contains(" ERROR ")) {
                if (s.length() > 2000) s = s.substring(0, 2000);
                listener.logErrorFound(this, s);
            } else if (s.contains("importing")) {
                int idx1 = s.indexOf("block.number: ");
                if (idx1 > 0) {
                    int idx2 = s.indexOf(",", idx1);
                    int blockNum = Integer.parseInt(s.substring(idx1 + "block.number: ".length(), idx2).trim());
                    listener.newBlockImported(this, blockNum, !s.contains("NOT_BEST"));
                }
            }
        }
    }



    protected String getRunCommand(String javaArgs) {
        return "nohup ./gradlew " + (javaArgs.isEmpty() ? "" : "-PjvmArgs='" + javaArgs + "' ")
                + getGradleTarget()
                + " >out.log 2>&1 &";
    }

    public void updateRepository() {
        SshConnection.ExecResult result = getConnection().execSynchronously("cd " + getRepoDir() + " && git pull", true);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Update failed: " + result.getOutputTail());
        }
    }

    public String getRepository() {
        return "https://github.com/ethereum/ethereumj";
    }

    protected String getGradleTarget() {
        return "run";
    }

    public String getRepoDir() {
        return env.getWorkDir() + "/repo";
    }

    public String getRunningDir() {
        return getRepoDir() + "/ethereumj-core";
    }

    public String getLogFile() {
        return getRunningDir() + "/logs/ethereum.log";
    }

    public void installConfig(String config) throws Exception {
        getConnection().execSynchronously("mkdir " + getRunningDir() + "/config", false);
        getConnection().putFile(getRunningDir() + "/config/ethereumj.conf", config);
    }
    public boolean waitForFile(String file, int timeoutSec) throws Exception {
        return waitForFile(getConnection(), file, timeoutSec);
    }

    public boolean waitForFile(SshConnection conn, String file, int timeoutSec) throws Exception {
        long end = System.currentTimeMillis() + timeoutSec * 1000;
        while (System.currentTimeMillis() < end) {
            if (isFileExist(conn, file)) return true;
            Thread.sleep(1000);
        }
        return false;
    }

    public boolean isFileExist(SshConnection conn, String fileOrDir) throws Exception {
        SshConnection.ExecResult rc = conn.execSynchronously("if [ -e " + fileOrDir + " ]; then echo OK; fi", false);
        return (rc.getOutputTail().contains("OK"));
    }
}
