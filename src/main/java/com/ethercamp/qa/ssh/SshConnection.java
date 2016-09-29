package com.ethercamp.qa.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Anton Nashatyrev on 17.05.2016.
 */
public class SshConnection {

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

        public synchronized int getExitCode() {
            while (exitCode == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return exitCode;
        }

        public InputStream getOut() {
            return in;
        }

        public InputStream getErr() {
            return null;
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

    String customPromptText = "MyCustomPrompt";
    String customPrompt = "<<<MyCustomPrompt>>>";
    Session session;
    ChannelShell channel;

    InputStream is;
    OutputStream os;
    BufferedReader br;
    BufferedWriter bw;

    public SshConnection(Session session, ChannelShell channel) {
        this.session = session;
        this.channel = channel;
    }

    public boolean isClosed() {
        return channel.isClosed();
    }

    public void init() {
        init(new NullOutputStream());
    }

    public void init(OutputStream stdout) {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true) {
                        try {
                            int b = System.in.read();
                            os.write(b);
                            os.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }).start();
            channel.setPtySize(100000, 10000, 10000, 10000); // set terminal width large enough

            File file = new File("ssh-" + /*session.getUserName() + "-" + */session.getHost() +
                    /*"-" + session.getPort() + */"-" + counter.incrementAndGet() + ".log");

            is = new TeeInputStream(channel.getInputStream(), stdout);
            br = new BufferedReader(new InputStreamReader(is));
            os = channel.getOutputStream();
            bw = new BufferedWriter(new OutputStreamWriter(os));

            channel.connect();

            waitForInitialPrompt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void waitForInitialPrompt() throws Exception {
        br.read();
        bw.append("PS1='<<<'" + customPromptText + "'>>>\\n'\n").flush();
        while (!br.readLine().contains(customPrompt));
//        bw.append(new String(new byte[]{0x1b}) + "[7l\n").flush(); // disable line wrapping in VT100
    }

    private String removeControlSymbols(String s) {
        return s.replace(new String(new byte[] {0xd}), "");
    }

    int cnt = 0;
    private void submitCommand(String command) throws Exception{
        cnt++;
        bw.append(command).append(" # MyEOL\n").flush();
        while (!br.readLine().contains("# MyEOL")); // skipping initial command echo
    }

    private ExecResult getResult(boolean async, boolean needExitCode) throws Exception {

        final ExecResult res = new ExecResult();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                String s;
                try {
                    while (!(s = br.readLine()).contains(customPrompt)) {
                        res.appendOut(s + '\n');
                    }
                    int idx = s.indexOf(customPrompt);
                    res.appendOut(s.substring(0, idx));
                    int exitCode = 0;
                    if (needExitCode) {
                        lastResult = null;
                        ExecResult rcRes = execSynchronously("echo $?", false);
                        exitCode = Integer.parseInt(rcRes.getOutputTail().trim());
                    }
                    res.setExitCode(exitCode);
                } catch (Exception e) {
                    res.appendOut("\nException reading sdtout: " + e);
                    res.setExitCode(-777);
                    e.printStackTrace();
                }
            }
        };

        if (async) {
            new Thread(r).start();
        } else {
            r.run();
        }
        return res;
    }

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
                Thread.sleep(500);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ExecResult execSynchronously(String command, boolean needExitCode) {
        try {
            checkExecuting();
            submitCommand(command);
            return getResult(false, needExitCode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ExecResult execAsynchronously(String command, boolean needExitCode) {
        try {
            checkExecuting();
            submitCommand(command);
            lastCommand = command;
            return lastResult = getResult(true, needExitCode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            os.flush();
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
        SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));
        SshConnection con = sshConnector.connect("nashat-1.cloudapp.net", "azureuser");
        con.init();

        con.execAll(
                "killall java; true",
                "cd ~",
                "rm -rf test_ws",
                "mkdir test_ws",
                "cd test_ws",
                "git clone -q https://github.com/ethereum/ethereumj",
                "cd ethereumj"
        );

//        int pid = con.execBackground("./gradlew run >/dev/null 2>&1 &");
//        System.err.println("Started run: " + pid);

        ExecResult fileExist = null;
        for (int i = 0; i < 100; i++) {
            Thread.sleep(1000);
            fileExist = con.execSynchronously("test -e ./ethereumj-core/logs/ethereum.log", true);
            if (fileExist.getExitCode() == 0) break;
        }

        if (fileExist.getExitCode() != 0) throw new RuntimeException("logfile wasn't created");

        ExecResult result = con.execAsynchronously("tail -F ./ethereumj-core/logs/ethereum.log | grep importing", true);
        BufferedReader br = new BufferedReader(new InputStreamReader(result.getOut()));

        while(true) {
            String s = br.readLine();
//            if (s.contains("importing")) {
                System.err.println("###: " + s);
//            }
        }

//        System.out.println("### Putting file...");
//        con.putFile("/root/aaa", "eee\nrrr\n");
//        System.out.println("### done 1");
//        con.execSynchronously("ls -l");
//        System.out.println("### done 2");
    }
}
