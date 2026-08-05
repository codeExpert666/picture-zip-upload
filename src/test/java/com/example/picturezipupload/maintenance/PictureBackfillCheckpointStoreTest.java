package com.example.picturezipupload.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureBackfillCheckpointStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsCheckpointAtomically() throws Exception {
        Path checkpointFile = tempDir.resolve("maintenance/backfill.properties");
        PictureMaintenanceReport report = new PictureMaintenanceReport();
        report.recordScanned();
        report.recordBackfilled();
        PictureBackfillCheckpoint checkpoint = PictureBackfillCheckpoint.initial(
                        "medical", "batch-1", "data-team", false, 300_000, null)
                .advance("voice-100", report, false);
        PictureBackfillCheckpointStore store = new PictureBackfillCheckpointStore();

        store.save(checkpointFile, checkpoint);

        assertThat(store.load(checkpointFile)).contains(checkpoint);
    }

    @Test
    void rejectsCheckpointFromAnotherRun() {
        PictureBackfillCheckpoint checkpoint = PictureBackfillCheckpoint.initial(
                "medical", "batch-1", "data-team", false, 300_000, null);

        assertThatThrownBy(() -> checkpoint.validate(
                "medical", "batch-2", "data-team", false, 300_000, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch-id");
    }
}
