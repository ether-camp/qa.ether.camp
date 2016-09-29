package com.ethercamp.qa.scenarios;

/**
 * Created by Anton Nashatyrev on 06.06.2016.
 */
public class PeerEtherCampScenario extends ProjectTestScenario {

    static final String appPropertiesTempl =
            //"server.port=8545\n" +
            //"shell.ssh.port=2200\n" +
            "shell.ssh.idle_timeout=0\n" +
            "shell.auth: spring\n" +
            "security.user.name: admin\n" +
            "security.user.password: admin\n";

    static final String mordenConfTempl =
            "peer.discovery = {\n" +
            "    ip.list = [\n" +
            "        \"94.242.229.4:40404\",\n" +
            "        \"94.242.229.203:30303\"\n" +
            "    ]\n" +
            "}\n" +
            "peer.networkId = 2\n" +
            "peer.p2p.eip8 = true\n" +
            "genesis = frontier-morden.json\n" +
            "blockchain.config.name = \"morden\"\n";

    String appProperties;
    String config;
    int modifier;
    boolean morden;

    public PeerEtherCampScenario(int portModifier, boolean morden) {
        super("https://github.com/ether-camp/peer.ether.camp");
        this.modifier = portModifier;
        this.morden = morden;
    }

    @Override
    public void setupImpl() throws Exception {
        config = morden ? mordenConfTempl : "";
        config += "peer.listen.port=" + getEnv().getPort() + "\n";
        config += getEnv().getDbDir() != null ? ("database.dir=" + getEnv().getDbDir() + "\n") : "";
        setConfig(config);

        super.setupImpl();

        appProperties = appPropertiesTempl +
                "server.port=" + (8540 + modifier) + "\n" +
                "shell.ssh.port=" + (2200 + modifier) + "\n";
        getConnection().putFile(getRepoDir() + "/src/main/resources/application.properties", appProperties);
    }

    protected String getRunCommand(String javaArgs) {
        return "./run.sh";
    }
}
