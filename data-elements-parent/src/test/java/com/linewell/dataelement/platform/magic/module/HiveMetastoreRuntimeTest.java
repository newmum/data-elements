package com.linewell.dataelement.platform.magic.module;

import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HiveMetastoreRuntimeTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void realRuntimeClientReadsSelectedPartitionLocationsInOneBatch(int count) throws Exception {
        HiveMetastoreRuntimeProbe.verify(directory, count);
    }
}
