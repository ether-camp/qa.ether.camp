package com.ethercamp.qa.ssh;

import com.jcraft.jsch.*;

import java.io.File;

/**
 * Created by Anton Nashatyrev on 17.05.2016.
 */
public class SshConnector {
    File identityFile;
    JSch jsch;

    public SshConnector(File identityFile) {
        this.identityFile = identityFile;
        jsch = new JSch();
        try {
            jsch.addIdentity(identityFile.getAbsolutePath());
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    public SshConnection connect(String host, String user) {
        return connect(host, 22, user);
    }

    public SshConnection connect(String host, int port, String user) {
        return connect(host, port, user, null);
    }

    public SshConnection connect(String host, int port, String user, String pass) {
        try {
            Session session = jsch.getSession(user, host, port);

            UserInfo ui=new MyUserInfo(pass);
            session.setUserInfo(ui);

            session.connect();
            Channel channel = session.openChannel("shell");
//            ((ChannelShell)channel).setPty(false);
            SshConnection ret = new SshConnection(session, (ChannelShell)channel);
//            ret.init();

            return ret;
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    public static class MyUserInfo implements UserInfo, UIKeyboardInteractive {
        String pass;

        public MyUserInfo(String pass) {
            this.pass = pass;
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
            return new String[0];
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return pass;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {

        }
    }
}
