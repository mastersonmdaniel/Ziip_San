package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.TelemetryReading;
import br.com.ziipsan.operations.metering.repository.MeasuringPointRepository;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Atomicidade da promoção da VEE (correção RG-LOTE2/S2 da revisão geral de
 * 08/11/2026): {@code TelemetryVeeService.promote} é {@code @Transactional}
 * e, executado através do interceptor transacional REAL do Spring (a mesma
 * {@link AnnotationTransactionAttributeSource} e o mesmo
 * {@link TransactionInterceptor} que o contexto usa), uma falha na segunda
 * escrita (marcar a telemetria como PROMOTED, depois de a leitura de
 * faturamento já ter sido entregue ao repositório) marca a transação para
 * ROLLBACK — o par "promoveu × marcou promovido" não fica pela metade.
 *
 * <p>O gerenciador de transações deste teste é um dublê que só conta
 * commits e rollbacks: ele prova o comportamento do interceptor + a
 * presença efetiva do atributo de transação no método. O rollback FÍSICO
 * no PostgreSQL só a CI total, com o gerenciador real e o bean gerenciado
 * pelo contexto, prova — registrado no relatório da correção.
 */
class TelemetryVeeServiceAtomicityTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID READING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
    private static final UUID BILLING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a6");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime READ_AT = OffsetDateTime.parse("2026-11-10T08:00:00-03:00");

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

    private TelemetryReadingRepository readings;
    private MeasuringPointRepository points;
    private MeterReadingRepository billingReadings;
    private ReadingHistoryLookup history;
    private RecordingTransactionManager txManager;
    private TelemetryVeeService service;
    private TelemetryReading reading;

    /** O serviço embrulhado pelo interceptor transacional do Spring, como o contexto faz com o bean. */
    private TelemetryVeeService transactional(TelemetryVeeService target) {
        TransactionInterceptor interceptor =
                new TransactionInterceptor((PlatformTransactionManager) txManager,
                        new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (TelemetryVeeService) factory.getProxy();
    }

    @BeforeEach
    void setUp() {
        readings = mock(TelemetryReadingRepository.class);
        points = mock(MeasuringPointRepository.class);
        billingReadings = mock(MeterReadingRepository.class);
        history = mock(ReadingHistoryLookup.class);
        txManager = new RecordingTransactionManager();
        service = new TelemetryVeeService(readings, points, billingReadings, history, CLOCK);

        reading = new TelemetryReading();
        reading.setId(READING_ID);
        reading.setTenantId(TENANT);
        reading.setMeasuringPointId(POINT);
        reading.setChannelId(CHANNEL);
        reading.setReferenceMonth(YearMonth.of(2026, 11).atDay(1));
        reading.setReadAt(READ_AT);
        reading.setDialReading(1050);
        reading.setDataState(DataState.MEASURED);
        reading.setVeeStatus(VeeStatus.VALIDATED);
        reading.setIdempotencyKey("AMR-0001");
        reading.setReceivedAt(READ_AT);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));
        when(readings.save(any(TelemetryReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeasuringPoint point = new MeasuringPoint();
        point.setId(POINT);
        point.setTenantId(TENANT);
        point.setUnitId(UNIT);
        point.setInstallationId(INSTALLATION);
        point.setKind(MeasurementKind.WATER);
        when(points.findByTenantIdAndId(TENANT, POINT)).thenReturn(Optional.of(point));
        when(billingReadings.save(any(MeterReading.class))).thenAnswer(invocation -> {
            MeterReading billing = invocation.getArgument(0);
            billing.setId(BILLING_ID);
            return billing;
        });
    }

    @Test
    @DisplayName("promote tem atributo de transação na fonte que o Spring usa")
    void promoteCarriesATransactionAttribute() throws Exception {
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
        assertThat(source.getTransactionAttribute(
                TelemetryVeeService.class.getMethod("promote", UUID.class, UUID.class),
                TelemetryVeeService.class)).isNotNull();
    }

    @Test
    @DisplayName("promoção feliz pelo proxy transacional confirma exatamente uma transação")
    void happyPromoteCommitsExactlyOnce() {
        TelemetryVeeService proxied = transactional(service);

        MeterReading promoted = proxied.promote(TENANT, READING_ID);

        assertThat(promoted.getId()).isEqualTo(BILLING_ID);
        assertThat(reading.getVeeStatus()).isEqualTo(VeeStatus.PROMOTED);
        assertThat(txManager.commits).hasValue(1);
        assertThat(txManager.rollbacks).hasValue(0);
    }

    @Test
    @DisplayName("falha ao marcar PROMOTED (2ª escrita) desfaz a promoção: rollback, sem commit")
    void failureOnSecondWriteRollsThePromotionBack() {
        when(readings.save(any(TelemetryReading.class)))
                .thenThrow(new IllegalStateException("falha simulada ao marcar PROMOTED"));
        TelemetryVeeService proxied = transactional(service);

        assertThatThrownBy(() -> proxied.promote(TENANT, READING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha simulada");
        // A leitura de faturamento chegou a ser entregue ao repositório
        // DENTRO da transação...
        verify(billingReadings).save(any(MeterReading.class));
        // ...e a transação NÃO confirmou: rollback registrado, commit zero.
        // Com o DataSource real, é este rollback que desfaz a leitura criada.
        assertThat(txManager.rollbacks).hasValue(1);
        assertThat(txManager.commits).hasValue(0);
    }
}
