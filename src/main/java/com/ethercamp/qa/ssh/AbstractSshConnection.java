package com.ethercamp.qa.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.TeeOutputStream;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Anton Nashatyrev on 23.05.2016.
 */
public abstract class AbstractSshConnection {
    public class ExecResult {
        int tailCharLimit = 100000;

        Integer exitCode;

        StringBuilder sb = new StringBuilder();
        PipedInputStream in;
        BufferedWriter bw;

        public ExecResult() {
            try {
                in = new PipedInputStream(1000000);
                PipedOutputStream out = new PipedOutputStream(in);
                bw = new BufferedWriter(new OutputStreamWriter(out));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        public String getOutputTail() {
            return sb.toString();
        }

        public boolean isComplete() {
            return exitCode != null;
        }

        public synchronized int getExitCode() throws InterruptedException {
            while (exitCode == null) {
                wait();
            }
            return exitCode;
        }

        public InputStream getOut() {
            return in;
        }

        public OutputStream getIn() {
            return os;
        }

        private synchronized void setExitCode(int exitCode) {
            this.exitCode = exitCode;
            notifyAll();
        }

        private void appendOut(String s) {
            if (sb.length() + s.length() > tailCharLimit) {
                sb.delete(0, s.length());
            }
            sb.append(s);
            try {
                bw.append(s).flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static AtomicInteger counter = new AtomicInteger();

    Session session;
//    ChannelShell channel;

    InputStream is;
    OutputStream os;
    BufferedReader br;
    BufferedWriter bw;

    public AbstractSshConnection(Session session) {
        this.session = session;
//        this.channel = channel;
    }

    public abstract void init();

    protected abstract void submitCommand(String command) throws Exception;

    protected abstract ExecResult getResult(boolean async, boolean needExitCode) throws Exception;

    ExecResult lastResult = null;
    String lastCommand;

    private void checkExecuting() {
        if (lastResult != null && !lastResult.isComplete()) {
            throw new RuntimeException("Can't execute while previous async command is still running: " + lastCommand);
        }
    }

    public void execAll(String ... commands) {
        checkExecuting();
        try {
            for (String command : commands) {
                ExecResult result = execSynchronously(command, true);
                if (result.getExitCode() != 0) {
                    throw new RuntimeException("Command '" + command + "' failed: (" + result.getExitCode() + ") " + result.getOutputTail());
                }
//                Thread.sleep(500);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ExecResult execSynchronously(String command, boolean needExitCode) throws Exception {
        checkExecuting();
        submitCommand(command);
        return getResult(false, needExitCode);
    }

    public ExecResult execAsynchronously(String command, boolean needExitCode) throws Exception {
        checkExecuting();
        submitCommand(command);
        lastCommand = command;
        return lastResult = getResult(true, needExitCode);
    }

//    public int execBackground(String command) throws Exception {
//        submitCommand(command);
//        ExecResult execResult = execSynchronously("echo $!", false);
//        if (execResult.getExitCode() != 0)
//            throw new RuntimeException("Unexpected: (" + execResult.getExitCode() + ") " + execResult.getOutputTail());
//        return Integer.parseInt(execResult.getOutputTail().trim());
//    }

    public void putFile(String remoteFile, String content) {
        try {
            submitCommand("cat > " + remoteFile);
            os.write(content.getBytes());
            os.write(0x4); //Ctrl-D
            os.flush();
            ExecResult result = getResult(false, true);
            if (result.exitCode != 0) {
                throw new RuntimeException("Putting remote file failed: " + result.getExitCode() + " (" + result.getOutputTail() + ")");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
//        SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));
//        SshConnection con = sshConnector.connect("nashat-1.cloudapp.net", "azureuser");
//
//        con.execAll(
//                "killall java; true",
//                "cd ~",
//                "rm -rf test_ws",
//                "mkdir test_ws",
//                "cd test_ws",
//                "git clone -q https://github.com/ethereum/ethereumj",
//                "cd ethereumj"
//        );
//
////        int pid = con.execBackground("./gradlew run >/dev/null 2>&1 &");
////        System.err.println("Started run: " + pid);
//
//        ExecResult fileExist = null;
//        for (int i = 0; i < 100; i++) {
//            Thread.sleep(1000);
//            fileExist = con.execSynchronously("test -e ./ethereumj-core/logs/ethereum.log", true);
//            if (fileExist.getExitCode() == 0) break;
//        }
//
//        if (fileExist.getExitCode() != 0) throw new RuntimeException("logfile wasn't created");
//
//        ExecResult result = con.execAsynchronously("tail -F ./ethereumj-core/logs/ethereum.log | grep importing", true);
//        BufferedReader br = new BufferedReader(new InputStreamReader(result.getOut()));
//
//        while(true) {
//            String s = br.readLine();
////            if (s.contains("importing")) {
//            System.err.println("###: " + s);
////            }
//        }

//        System.out.println("### Putting file...");
//        con.putFile("/root/aaa", "eee\nrrr\n");
//        System.out.println("### done 1");
//        con.execSynchronously("ls -l");
//        System.out.println("### done 2");
    }
}
