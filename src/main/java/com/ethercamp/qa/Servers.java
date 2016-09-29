package com.ethercamp.qa;

import com.ethercamp.qa.ssh.SshConnector;
import com.ethercamp.qa.ssh.SshServer;

import java.io.File;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class Servers {
    static SshConnector sshConnector = new SshConnector(new File("D:\\cygwin\\home\\Admin\\.ssh\\id_dsa"));

    public static SshServer[] servers = new SshServer[] {
            new SshServer("nashat-1.cloudapp.net", "azureuser"),
            new SshServer("nashat-2.cloudapp.net", "azureuser"),
            new SshServer("nashat-3.cloudapp.net", "azureuser"),
            new SshServer("nashat-4.cloudapp.net", "azureuser")
    };

    static {
        for (SshServer server : servers) {
            server.setConnector(sshConnector);
        }
    }
}
