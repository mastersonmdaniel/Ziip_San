package br.com.ziipsan.operations.metering.exchange;

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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Atomicidade da leitura terceirizada (correção RG-LOTE2/S2 da revisão geral
 * de 08/11/2026): os métodos públicos de escrita de
 * {@link OutsourcedReadingService} são {@code @Transactional} e, executados
 * através do interceptor transacional REAL do Spring (a mesma
 * {@link AnnotationTransactionAttributeSource} e o mesmo
 * {@link TransactionInterceptor} que o contexto usa), uma falha no meio da
 * sequência de escritas marca a transação para ROLLBACK e nenhuma confirma —
 * nada é dado como persistido fora da transação.
 *
 * <p>O gerenciador de transações deste teste é um dublê que só conta
 * commits e rollbacks: ele prova o comportamento do interceptor + a
 * presença efetiva do atributo de transação nos métodos. O rollback FÍSICO
 * no PostgreSQL (e o desfazer das linhas já gravadas) só a CI total, com o
 * {@code DataSourceTransactionManager} real e o bean gerenciado pelo
 * contexto, prova — registrado no relatório da correção.
 *
 * <p>Cenário espelha o de {@code OutsourcedReadingServiceTest}: relógio fixo
 * em 2026-11-10T12:00:00Z, referência 2026-11, rota R-01, fornecedor
 * LEITURASUL.
 */
class OutsourcedReadingServiceAtomicityTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VENDOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID READING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final YearMonth REFERENCE = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    /** Gerenciador dublê: nenhum recurso real — só registra o desfecho de cada transação. */
    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    }

    private ReadingVendorRepository vendors;
    private ReadingTaskRepository tasks;
    private ReadingImportBatchRepository batches;
    private ReadingImportRecordRepository records;
    private MeterReadingRepository readings;
    private ReadingAnomalyRepository anomalies;
    private RecordingTransactionManager txManager;
    private OutsourcedReadingService service;

    private ReadingVendor vendor;
    private ReadingTask task;

    private static String returnFile() {
        return VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;1234;;\n";
    }

    /** O serviço embrulhado pelo interceptor transacional do Spring, como o contexto faz com o bean. */
    private OutsourcedReadingService transactional(OutsourcedReadingService target) {
        TransactionInterceptor interceptor =
                new TransactionInterceptor((PlatformTransactionManager) txManager,
                        new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (OutsourcedReadingService) factory.getProxy();
    }

    @BeforeEach
    void setUp() {
        vendors = mock(ReadingVendorRepository.class);
        tasks = mock(ReadingTaskRepository.class);
        batches = mock(ReadingImportBatchRepository.class);
        records = mock(ReadingImportRecordRepository.class);
        readings = mock(MeterReadingRepository.class);
        anomalies = mock(ReadingAnomalyRepository.class);
        txManager = new RecordingTransactionManager();
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
    @DisplayName("os três métodos públicos de escrita têm atributo de transação na fonte que o Spring usa")
    void publicWriteMethodsCarryTransactionAttributes() throws Exception {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        assertThat(source.getTransactionAttribute(
                OutsourcedReadingService.class.getMethod("createTasks", UUID.class, UUID.class,
                        String.class, YearMonth.class, OutsourcedReadingService.CycleLink.class,
                        List.class, UUID.class),
                OutsourcedReadingService.class)).isNotNull();
        assertThat(source.getTransactionAttribute(
                OutsourcedReadingService.class.getMethod("exportFile", UUID.class, UUID.class,
                        String.class, YearMonth.class, UUID.class),
                OutsourcedReadingService.class)).isNotNull();
        assertThat(source.getTransactionAttribute(
                OutsourcedReadingService.class.getMethod("importFile", UUID.class, UUID.class,
                        String.class, String.class, String.class, UUID.class),
                OutsourcedReadingService.class)).isNotNull();
    }

    @Test
    @DisplayName("importação feliz pelo proxy transacional confirma exatamente uma transação")
    void happyImportCommitsExactlyOnce() {
        OutsourcedReadingService proxied = transactional(service);

        OutsourcedReadingService.ImportOutcome outcome =
                proxied.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null, returnFile(), ACTOR);

        assertThat(outcome.imported()).isEqualTo(1);
        assertThat(txManager.commits).hasValue(1);
        assertThat(txManager.rollbacks).hasValue(0);
    }

    @Test
    @DisplayName("falha no save da tarefa (2ª escrita da linha) desfaz a importação: rollback, sem commit")
    void failureOnTaskSaveRollsTheImportBack() {
        // A sequência da linha é readings.save -> tasks.save -> records.save:
        // a falha simulada é na SEGUNDA escrita, com a leitura já "gravada".
        when(tasks.save(any(ReadingTask.class)))
                .thenThrow(new IllegalStateException("falha simulada no save da tarefa"));
        OutsourcedReadingService proxied = transactional(service);

        assertThatThrownBy(() -> proxied.importFile(TENANT, VENDOR_ID, "volta-r01.csv", null,
                returnFile(), ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha simulada");
        // A leitura chegou a ser entregue ao repositório DENTRO da transação...
        verify(readings).save(any(MeterReading.class));
        // ...a terceira escrita nem foi tentada...
        verify(records, never()).save(any(ReadingImportRecord.class));
        // ...e a transação NÃO confirmou: rollback registrado, commit zero.
        // Com o DataSource real, é este rollback que desfaz a leitura gravada.
        assertThat(txManager.rollbacks).hasValue(1);
        assertThat(txManager.commits).hasValue(0);
    }

    @Test
    @DisplayName("falha no save da 2ª tarefa desfaz a criação do lote de tarefas inteiro")
    void failureOnSecondTaskRollsTheWholeCreationBack() {
        AtomicInteger saves = new AtomicInteger();
        when(tasks.save(any(ReadingTask.class))).thenAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw new IllegalStateException("falha simulada na segunda tarefa");
            }
            return invocation.getArgument(0);
        });
        OutsourcedReadingService proxied = transactional(service);
        OutsourcedReadingService.CycleLink link = new OutsourcedReadingService.CycleLink(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<OutsourcedReadingService.TaskAssignment> assignments = List.of(
                new OutsourcedReadingService.TaskAssignment(UNIT, INSTALLATION,
                        MeasurementKind.WATER, 1, 1200, LocalDate.of(2026, 11, 5)),
                new OutsourcedReadingService.TaskAssignment(UNIT_2, INSTALLATION,
                        MeasurementKind.WATER, 2, 900, LocalDate.of(2026, 11, 5)));

        assertThatThrownBy(() -> proxied.createTasks(TENANT, VENDOR_ID, "R-01", REFERENCE,
                link, assignments, ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("segunda tarefa");
        assertThat(txManager.rollbacks).hasValue(1);
        assertThat(txManager.commits).hasValue(0);
    }
}
