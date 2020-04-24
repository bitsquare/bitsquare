package bisq.core.network.p2p;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessStorageService;
import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;

import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.app.Version;
import bisq.common.storage.Storage;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.After;

/**
 * utility class for the {@link FileDatabaseTest} and {@link RequestDataTest}
 */
public class FileDatabaseTestUtils {
    // Test fixtures
    static final AccountAgeWitness object1 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, 1);
    static final AccountAgeWitness object2 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2}, 2);
    static final AccountAgeWitness object3 = new AccountAgeWitness(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3}, 3);


    List<File> files = new ArrayList<>();
    static final File storageDir = new File("src/test/resources");

    @After
    public void cleanup() {
        try {
            boolean done = false;
            while (!done) {
                Thread.sleep(100);
                Set<Thread> threads = Thread.getAllStackTraces().keySet();
                done = threads.stream().noneMatch(thread -> thread.getName().startsWith("Save-file-task"));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (File file : files) {
            file.delete();
        }

        try {
            File backupDir = new File(storageDir + "/backup");
            if (backupDir.exists())
                Files.walk(backupDir.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Arrays.stream(storageDir.list((dir, name) -> name.startsWith("AccountAgeWitnessStore"))).forEach(s -> {
            new File(storageDir + File.separator + s).delete();
        });
    }

    public File createFile(boolean isResourceFile, String name) {
        File tmp;
        if (isResourceFile)
            tmp = new File(ClassLoader.getSystemClassLoader().getResource("").getFile() + File.separator + name);
        else
            tmp = new File(storageDir + File.separator + name);

        files.add(tmp);
        return tmp;
    }

    protected AppendOnlyDataStoreService loadDatabase() {
        Storage<AccountAgeWitnessStore> storage = new Storage<>(storageDir, new CorePersistenceProtoResolver(null, null, null, null), null);
        AccountAgeWitnessStorageService storageService = new AccountAgeWitnessStorageService(storageDir, storage);
        final AppendOnlyDataStoreService protectedDataStoreService = new AppendOnlyDataStoreService();
        protectedDataStoreService.addService(storageService);
        protectedDataStoreService.readFromResources("_TEST");
        return protectedDataStoreService;
    }

    protected void createDatabase(File target,
                                  AccountAgeWitness... objects) throws IOException {

        if (null == objects) {
            return;
        }

        String filename = "";
        filename += Arrays.asList(objects).contains(object1) ? "o1" : "";
        filename += Arrays.asList(objects).contains(object2) ? "o2" : "";
        filename += Arrays.asList(objects).contains(object3) ? "o3" : "";

        File source = new File(storageDir + File.separator + filename);

        if (target.exists())
            target.delete();

        Files.copy(source.toPath(), target.toPath());
    }

    /**
     * note that this function assumes a Bisq version format of x.y.z. It will not work with formats other than that eg. x.yy.z
     * @param offset
     * @return relative version string to the Version.VERSION constant
     */
    public String getVersion(int offset) {
        return new StringBuilder().append(Integer.valueOf(Version.VERSION.replace(".", "")) + offset).insert(2, ".").insert(1, ".").toString();
    }
}