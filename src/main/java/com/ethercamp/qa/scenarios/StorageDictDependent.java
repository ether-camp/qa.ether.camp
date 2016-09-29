package com.ethercamp.qa.scenarios;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class StorageDictDependent extends ProjectTestScenario {
    public StorageDictDependent(String repository) {
        super(repository);
    }

    @Override
    public void setupImpl() throws Exception {
        super.setupImpl();

        getConnection().execAll(
                "pushd " + env.getWorkDir(),
                "rm -rf storage-dict; true",
                "git clone https://nashatyrev:kissme1_@github.com/ether-camp/storage-dict",
                "cd storage-dict",
                "./gradlew clean install -x test",
                "popd"
        );
    }

    @Override
    protected void beforeStart() {
    }

    @Override
    protected String getRunCommand(String javaArgs) {
        return "sh ./run.sh";
    }
}
