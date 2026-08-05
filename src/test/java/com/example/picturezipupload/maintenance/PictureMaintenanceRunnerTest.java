package com.example.picturezipupload.maintenance;

import com.example.picturezipupload.config.PictureUploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PictureMaintenanceRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsCompletedCheckpointAndSkipsCompletedRerun() throws Exception {
        PictureMaintenanceProperties maintenanceProperties = properties();
        PictureUploadProperties uploadProperties = new PictureUploadProperties();
        uploadProperties.setWorkRootPath(tempDir);
        PictureMaintenanceService maintenanceService = mock(PictureMaintenanceService.class);
        PictureBackfillCheckpointStore checkpointStore = new PictureBackfillCheckpointStore();
        when(maintenanceService.backfillExistingRecords(any(), any())).thenAnswer(invocation -> {
            PictureBackfillProgressListener listener = invocation.getArgument(1);
            PictureMaintenanceReport report = new PictureMaintenanceReport();
            report.recordScanned();
            report.recordBackfilled();
            listener.onProgress(new PictureBackfillProgress(1, "voice-1", report));
            return new PictureBackfillResult(report, "voice-1", true);
        });
        PictureMaintenanceRunner runner = new PictureMaintenanceRunner(
                maintenanceProperties, uploadProperties, maintenanceService, checkpointStore);

        runner.run(null);
        runner.run(null);

        Path checkpointFile = tempDir.resolve("maintenance/backfill-medical-batch-1-dry-run.properties");
        PictureBackfillCheckpoint checkpoint = checkpointStore.load(checkpointFile).orElseThrow();
        assertThat(checkpoint.exhausted()).isTrue();
        assertThat(checkpoint.scanned()).isEqualTo(1);
        assertThat(checkpoint.backfilled()).isEqualTo(1);
        verify(maintenanceService, times(1)).backfillExistingRecords(any(), any());
    }

    private static PictureMaintenanceProperties properties() {
        PictureMaintenanceProperties properties = new PictureMaintenanceProperties();
        properties.setEnabled(true);
        properties.setMode(PictureMaintenanceMode.BACKFILL_EXISTING);
        properties.setDryRun(true);
        properties.setBusinessArea("medical");
        properties.setOperator("data-team");
        properties.setBatchId("batch-1");
        properties.setLimit(1000);
        properties.setBatchSize(100);
        properties.setProgressInterval(10);
        return properties;
    }
}
