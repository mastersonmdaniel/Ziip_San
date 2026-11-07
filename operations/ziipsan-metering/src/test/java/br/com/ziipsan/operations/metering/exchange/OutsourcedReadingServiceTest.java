package br.com.ziipsan.operations.metering.exchange;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.ReadingImportBatch;
import br.com.ziipsan.operations.metering.domain.ReadingImportRecord;
import br.com.ziipsan.operations.metering.domain.ReadingTask;
import br.com.ziipsan.operations.metering.domain.ReadingVendor;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.repository.ReadingAnomalyRepository;
import br.com.ziipsan.operations.metering.repository.ReadingImportBatchRepository;
import br.com.ziipsan.operations.metering.repository.ReadingImportRecordRepository;
import br.com.ziipsan.operations.metering.repository.ReadingTaskRepository;
import br.com.ziipsan.operations.metering.repository.ReadingVendorRepository;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A leitura terceirizada sobre repositorios simulados (P2.7): o aceite pede
 * que o reenvio do mesmo arquivo OU do mesmo registro nao duplique leitura --
 * os dois casos tem teste proprio aqui, alem do caminho feliz da ida e da
 * volta e das recusas explicitas. Relogio fixo em 2026-11-10T12:00:00Z;
 * referencia 2026-11; rota R-01; fornecedor LEITURASUL.
 */
class OutsourcedReadingServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VENDOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID READING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final YearMonth REFERENCE = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private ReadingVendorRepository vendors;
    private ReadingTaskRepository tasks;
    private ReadingImportBatchRepository batches;
    private ReadingImportRecordRepository records;
    private MeterReadingRepository readings;
    private ReadingAnomalyRepository anomalies;
    private OutsourcedReadingService service;

    private ReadingVendor vendor;
    private ReadingTask task;

    private static String returnFile() {
        return VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;1234;;\n";
    }

    @BeforeEach
    void setUp() {
        vendors = mock(ReadingVendorRepository.class);
        tasks = mock(ReadingTaskRepository.class);
        batches = mock(ReadingImportBatchRepository.class);
        records = mock(ReadingImportRecordRepository.class);
        readings = mock(MeterReadingRepository.class);
        anomalies = mock(ReadingAnomalyRepository.class);
        service = new OutsourcedReadingService(vendors, tasks, batches, records, readings, anomalies, CLOCK);

        vendor = new ReadingVendor();
        vendor.setId(VENDOR_ID);
        vendor.setTenantId(TENANT);
        vendor.setCode("LEITURASUL");
        vendor.setName("Leitura Sul Ltda");
        vendor.setLayoutCode(VendorReadingLayout.LAYOUT_CODE);
        vendor.setActive(true);
        when(vendors.findByTenantIdAndId(TENANT, VENDOR_ID)).thenReturn(Optional.of(vendor));

        task = new ReadingTask();
        task.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
        task.setTenantId(TENANT);
        task.setVendorId(VENDOR_ID);
        task.setRouteCode("R-01");
        task.setReferenceMonth(REFERENCE);
        task.setUnitId(UNIT);
        task.setInstallationId(INSTALLATION);
        task.setKind(MeasurementKind.WATER);
        task.setSequenceNo(1);
        task.setStatus(ReadingTask.Status.EXPORTED);

        when(batches.save(any(ReadingImportBatch.class))).thenAnswer(invocation -> {
            ReadingImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(BATCH_ID);
            }
            return batch;
        });
        when(records.save(any(ReadingImportRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.save(any(ReadingTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(readings.save(any(MeterReading.class))).thenAnswer(invocation -> {
            MeterReading reading = invocation.getArgument(0);
            reading.setId(READING_ID);
            return reading;
        });
        when(tasks.findByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
                TENANT, REFERENCE.atDay(1), "R-01", UNIT, MeasurementKind.WATER))
                .thenReturn(Optional.of(task));
        when(records.findByTenantIdAndRecordKeyAndStatus(eq(TENANT), any(String.class),
                eq(ReadingImportRecord.Status.IMPORTED))).thenReturn(Optional.empty());
        when(batches.findByTenantIdAndFileSha256(eq(TENANT), any(String.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a volta importa a leitura, cumpre a tarefa e fecha o lote com os contadores")
    void importCreatesTheReadingAndCompletesTheTask() {
        OutsourcedReadingService.ImportOutcome outcome =
                service.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, returnFile(), ACTOR);

        assertThat(outcome.duplicateFile()).isFalse();
        assertThat(outcome.imported()).isEqualTo(1);
        assertThat(outcome.duplicates()).isZero();
        assertThat(outcome.errors()).isZero();
        assertThat(outcome.batch().getStatus()).isEqualTo(ReadingImportBatch.Status.PROCESSED);

        ArgumentCaptor<MeterReading> readingCaptor = ArgumentCaptor.forClass(MeterReading.class);
        verify(readings, times(1)).save(readingCaptor.capture());
        MeterReading reading = readingCaptor.getValue();
        assertThat(reading.getUnitId()).isEqualTo(UNIT);
        assertThat(reading.getReading()).isEqualTo(1234);
        assertThat(reading.getSource()).isEqualTo(MeterReading.Source.COLLECTOR);
        assertThat(reading.getIdempotencyKey()).isEqualTo(VendorReadingLayout.recordKey(
                "LEITURASUL", "R-01", REFERENCE, UNIT, MeasurementKind.WATER));

        assertThat(task.getStatus()).isEqualTo(ReadingTask.Status.READ);
        assertThat(task.getReadingId()).isEqualTo(READING_ID);

        ArgumentCaptor<ReadingImportRecord> recordCaptor = ArgumentCaptor.forClass(ReadingImportRecord.class);
        verify(records, times(1)).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo(ReadingImportRecord.Status.IMPORTED);
        assertThat(recordCaptor.getValue().getReadingId()).isEqualTo(READING_ID);
    }

    @Test
    @DisplayName("ACEITE: reenviar o MESMO arquivo nao duplica leitura -- devolve o lote e nao processa de novo")
    void resendingTheSameFileDoesNotDuplicateReadings() {
        OutsourcedReadingService.ImportOutcome first =
                service.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, returnFile(), ACTOR);
        assertThat(first.imported()).isEqualTo(1);

        // O mesmo conteudo chega de novo (ate com outro nome): o lote ja existe pelo hash.
        when(batches.findByTenantIdAndFileSha256(TENANT, VendorReadingLayout.sha256Hex(returnFile())))
                .thenReturn(Optional.of(first.batch()));

        OutsourcedReadingService.ImportOutcome second =
                service.importFile(TENANT, VENDOR_ID, "volta-r01-REENVIO.csv", null, returnFile(), ACTOR);

        assertThat(second.duplicateFile()).isTrue();
        assertThat(second.batch().getId()).isEqualTo(BATCH_ID);
        assertThat(second.imported()).isEqualTo(1);
        verify(readings, times(1)).save(any(MeterReading.class));
        verify(records, times(1)).save(any(ReadingImportRecord.class));
    }

    @Test
    @DisplayName("ACEITE: reenviar o MESMO registro em outro arquivo nao duplica leitura -- vira DUPLICATE")
    void resendingTheSameRecordInAnotherFileDoesNotDuplicateReadings() {
        ReadingImportRecord alreadyImported = new ReadingImportRecord();
        alreadyImported.setStatus(ReadingImportRecord.Status.IMPORTED);
        // O stub e escopado a chave EXATA do registro reenviado (a 1a linha): com
        // any(String.class) a 2a linha (outra unidade, sem tarefa) tambem cairia
        // como DUPLICATE e nunca chegaria ao TASK_NOT_FOUND esperado abaixo.
        when(records.findByTenantIdAndRecordKeyAndStatus(eq(TENANT),
                eq(VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE, UNIT,
                        MeasurementKind.WATER)),
                eq(ReadingImportRecord.Status.IMPORTED))).thenReturn(Optional.of(alreadyImported));

        // Outro arquivo (cabecalho igual, nome e lote diferentes; o conteudo difere pelo nome? nao:
        // o hash e do conteudo -- aqui o arquivo e outro porque traz uma segunda linha de outra
        // unidade que nao tem tarefa; o registro da unidade conhecida se repete nele).
        String anotherFile = VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;1234;;\n"
                + "2;LEITURASUL;R-01;2026-11;" + UUID.randomUUID() + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;999;;\n";

        OutsourcedReadingService.ImportOutcome outcome =
                service.importFile(TENANT, VENDOR_ID, "volta-r01-lote2.csv", null, anotherFile, ACTOR);

        assertThat(outcome.duplicateFile()).isFalse();
        assertThat(outcome.imported()).isZero();
        assertThat(outcome.duplicates()).isEqualTo(1);
        assertThat(outcome.errors()).isEqualTo(1);
        verify(readings, never()).save(any(MeterReading.class));

        ArgumentCaptor<ReadingImportRecord> captor = ArgumentCaptor.forClass(ReadingImportRecord.class);
        verify(records, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(ReadingImportRecord.Status.DUPLICATE);
        assertThat(captor.getAllValues().get(0).getReadingId()).isNull();
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(ReadingImportRecord.Status.ERROR);
        assertThat(captor.getAllValues().get(1).getErrorCode()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    @DisplayName("linha sem tarefa correspondente vira ERROR com motivo e nao vira leitura")
    void lineWithoutTaskBecomesAnErrorRecord() {
        when(tasks.findByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
                TENANT, REFERENCE.atDay(1), "R-01", UNIT, MeasurementKind.WATER))
                .thenReturn(Optional.empty());

        OutsourcedReadingService.ImportOutcome outcome =
                service.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, returnFile(), ACTOR);

        assertThat(outcome.errors()).isEqualTo(1);
        assertThat(outcome.imported()).isZero();
        verify(readings, never()).save(any(MeterReading.class));
        ArgumentCaptor<ReadingImportRecord> captor = ArgumentCaptor.forClass(ReadingImportRecord.class);
        verify(records).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReadingImportRecord.Status.ERROR);
        assertThat(captor.getValue().getErrorCode()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    @DisplayName("a ida sai no leiaute do fornecedor e marca as tarefas como EXPORTED")
    void exportWritesTheVendorLayoutAndMarksTasksExported() {
        task.setStatus(ReadingTask.Status.PENDING);
        task.setPlannedOn(LocalDate.of(2026, 11, 5));
        task.setPreviousReading(1200);
        when(tasks.findByTenantIdAndVendorIdAndRouteCodeAndReferenceMonthOrderBySequenceNoAsc(
                TENANT, VENDOR_ID, "R-01", REFERENCE.atDay(1))).thenReturn(List.of(task));

        String file = service.exportFile(TENANT, VENDOR_ID, "R-01", REFERENCE, ACTOR);

        assertThat(file).startsWith(VendorReadingLayout.HEADER + "\n");
        assertThat(file).contains("1;LEITURASUL;R-01;2026-11;" + UNIT);
        assertThat(task.getStatus()).isEqualTo(ReadingTask.Status.EXPORTED);
        assertThat(task.getExportedAt()).isNotNull();
    }

    @Test
    @DisplayName("exportar duas vezes nao duplica linha nem altera o exported_at das ja exportadas")
    void exportingTwiceDoesNotDuplicateNorRetouchExportedAt() {
        task.setStatus(ReadingTask.Status.PENDING);
        task.setPlannedOn(LocalDate.of(2026, 11, 5));
        task.setPreviousReading(1200);
        when(tasks.findByTenantIdAndVendorIdAndRouteCodeAndReferenceMonthOrderBySequenceNoAsc(
                TENANT, VENDOR_ID, "R-01", REFERENCE.atDay(1))).thenReturn(List.of(task));

        String first = service.exportFile(TENANT, VENDOR_ID, "R-01", REFERENCE, ACTOR);
        OffsetDateTime exportedAtAfterFirst = task.getExportedAt();
        assertThat(exportedAtAfterFirst).isNotNull();

        String second = service.exportFile(TENANT, VENDOR_ID, "R-01", REFERENCE, ACTOR);

        assertThat(second).isEqualTo(first);
        assertThat(second.split("\n")).hasSize(2);
        assertThat(task.getStatus()).isEqualTo(ReadingTask.Status.EXPORTED);
        assertThat(task.getExportedAt()).isEqualTo(exportedAtAfterFirst);
        // A tarefa so foi gravada na primeira exportacao: a segunda nao a regrava nem a recarimba.
        verify(tasks, times(1)).save(any(ReadingTask.class));
    }

    @Test
    @DisplayName("fornecedor com leiaute diferente do implementado e recusado, sem importar nada")
    void vendorWithAnUnknownLayoutIsRefused() {
        vendor.setLayoutCode("OUTRO_LEIAUTE_XYZ");

        assertThatThrownBy(() -> service.importFile(TENANT, VENDOR_ID, "volta.csv", null, returnFile(), ACTOR))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("OUTRO_LEIAUTE_XYZ");
        verify(batches, never()).save(any(ReadingImportBatch.class));
        verify(readings, never()).save(any(MeterReading.class));
    }

    @Test
    @DisplayName("criar a mesma tarefa duas vezes e recusado antes de gravar")
    void duplicateTaskCreationIsRefused() {
        when(tasks.existsByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
                TENANT, REFERENCE.atDay(1), "R-01", UNIT, MeasurementKind.WATER)).thenReturn(true);

        assertThatThrownBy(() -> service.createTasks(TENANT, VENDOR_ID, "R-01", REFERENCE,
                new OutsourcedReadingService.CycleLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                List.of(new OutsourcedReadingService.TaskAssignment(UNIT, INSTALLATION,
                        MeasurementKind.WATER, 1, 1200, LocalDate.of(2026, 11, 5))), ACTOR))
                .isInstanceOf(ApiException.Conflict.class);
        verify(tasks, never()).save(any(ReadingTask.class));
    }

    @Test
    @DisplayName("a propria lista com a mesma unidade e tipo duas vezes e recusada em memoria, sem gravar nada")
    void duplicateAssignmentInsideTheSameListIsRefused() {
        OutsourcedReadingService.TaskAssignment assignment = new OutsourcedReadingService.TaskAssignment(
                UNIT, INSTALLATION, MeasurementKind.WATER, 1, 1200, LocalDate.of(2026, 11, 5));

        assertThatThrownBy(() -> service.createTasks(TENANT, VENDOR_ID, "R-01", REFERENCE,
                new OutsourcedReadingService.CycleLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                List.of(assignment, assignment), ACTOR))
                .isInstanceOf(ApiException.Conflict.class);
        verify(tasks, never()).save(any(ReadingTask.class));
    }

    @Test
    @DisplayName("linha cuja tarefa pertence a outro fornecedor vira ERROR TASK_VENDOR_MISMATCH e nao vira leitura")
    void lineWhoseTaskBelongsToAnotherVendorBecomesAnErrorRecord() {
        task.setVendorId(UUID.fromString("00000000-0000-0000-0000-0000000000c2"));

        OutsourcedReadingService.ImportOutcome outcome =
                service.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, returnFile(), ACTOR);

        assertThat(outcome.errors()).isEqualTo(1);
        assertThat(outcome.imported()).isZero();
        verify(readings, never()).save(any(MeterReading.class));
        ArgumentCaptor<ReadingImportRecord> captor = ArgumentCaptor.forClass(ReadingImportRecord.class);
        verify(records).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReadingImportRecord.Status.ERROR);
        assertThat(captor.getValue().getErrorCode()).isEqualTo("TASK_VENDOR_MISMATCH");
    }

    @Test
    @DisplayName("linha com instalacao divergente da tarefa vira ERROR INSTALLATION_MISMATCH e nao vira leitura")
    void lineWithADifferentInstallationThanTheTaskBecomesAnErrorRecord() {
        UUID otherInstallation = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        String file = VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + otherInstallation
                + ";WATER;;;2026-11-05;1234;;\n";

        OutsourcedReadingService.ImportOutcome outcome =
                service.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, file, ACTOR);

        assertThat(outcome.errors()).isEqualTo(1);
        assertThat(outcome.imported()).isZero();
        verify(readings, never()).save(any(MeterReading.class));
        ArgumentCaptor<ReadingImportRecord> captor = ArgumentCaptor.forClass(ReadingImportRecord.class);
        verify(records).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReadingImportRecord.Status.ERROR);
        assertThat(captor.getValue().getErrorCode()).isEqualTo("INSTALLATION_MISMATCH");
    }
}
