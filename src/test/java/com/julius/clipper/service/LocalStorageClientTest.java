package com.julius.clipper.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class LocalStorageClientTest extends AbstractStorageClientContractTest {

    private Path tempStorageDir;
    private StorageClient client;

    @BeforeEach
    public void setUp() throws IOException {
        tempStorageDir = Files.createTempDirectory("local_storage_test_root_");
        client = new LocalStorageClient(tempStorageDir.toString(), null);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (Files.exists(tempStorageDir)) {
            Files.walk(tempStorageDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Override
    protected StorageClient getClient() {
        return client;
    }
}
