/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.file;

import bisq.common.util.Utilities;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class JsonFileManager {
    private final static List<JsonFileManager> INSTANCES = new ArrayList<>();

    public static void shutDownAllInstances() {
        INSTANCES.forEach(JsonFileManager::shutDown);
    }


    @Nullable
    private ThreadPoolExecutor executor;
    private final File dir;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public JsonFileManager(File dir) {
        this.dir = dir;

        if (!dir.exists() && !dir.mkdir()) {
            log.warn("make dir failed");
        }

        INSTANCES.add(this);
    }

    @NotNull
    protected ThreadPoolExecutor getExecutor() {
        if (executor == null) {
            executor = Utilities.getThreadPoolExecutor("JsonFileManagerExecutor", 5, 50, 60);
        }
        return executor;
    }

    public void shutDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public void writeToDiscThreaded(String json, String fileName) {
        getExecutor().execute(() -> write(json, fileName));
    }

    public Optional<String> read(String fileName) {
        File jsonFile = new File(Paths.get(dir.getAbsolutePath(), fileName + ".json").toString());
        if (!jsonFile.exists()) {
            return Optional.empty();
        }
        StringBuilder json = new StringBuilder();
        try (Scanner scanner = new Scanner(jsonFile)) {
            while (scanner.hasNext()) {
                json.append(scanner.next());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return Optional.of(json.toString());
    }

    public void write(String json, String fileName) {
        File jsonFile = new File(Paths.get(dir.getAbsolutePath(), fileName + ".json").toString());
        File tempFile = null;
        PrintWriter printWriter = null;
        try {
            tempFile = File.createTempFile("temp", null, dir);
            tempFile.deleteOnExit();

            printWriter = new PrintWriter(tempFile);
            printWriter.println(json);

            // This close call and comment is borrowed from FileManager. Not 100% sure it that is really needed but
            // seems that had fixed in the past and we got reported issues on Windows so that fix might be still
            // required.
            // Close resources before replacing file with temp file because otherwise it causes problems on windows
            // when rename temp file
            printWriter.close();

            FileUtil.renameFile(tempFile, jsonFile);
        } catch (Throwable t) {
            log.error("storageFile " + jsonFile.toString());
            t.printStackTrace();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. We will delete it now. storageFile=" + fileName);
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }

            if (printWriter != null)
                printWriter.close();
        }
    }

    public boolean delete(String fileName) {
        File file = new File(Paths.get(dir.getAbsolutePath(), fileName + ".json").toString());
        return file.delete();
    }
}
