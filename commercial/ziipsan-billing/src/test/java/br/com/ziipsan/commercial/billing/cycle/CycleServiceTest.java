package br.com.ziipsan.commercial.billing.cycle;

import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.commercial.billing.domain.BillingGroupEntity;
import br.com.ziipsan.commercial.billing.domain.BillingReferenceEntity;
import br.com.ziipsan.commercial.billing.domain.CycleActivityEntity;
import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
import br.com.ziipsan.commercial.billing.repository.BillRepository;
import br.com.ziipsan.commercial.billing.repository.BillingGroupRepository;
import br.com.ziipsan.commercial.billing.repository.BillingReferenceRepository;
import br.com.ziipsan.commercial.billing.repository.CycleActivityRepository;
import br.com.ziipsan.commercial.billing.repository.CycleScheduleItemRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.parameters.TenantTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O servico de ciclo sobre repositorios simulados (P2.5/F1): as recusas do
 * aceite acontecem antes de qualquer gravacao ou despacho, e o caminho feliz
 * do faturamento despacha uma unica vez pela fundacao da P2.1, com a
 * referencia de ciclo do grupo. Relogio fixo em 2026-11-10T12:00:00Z; mes de
 * referencia 2026-11; grupo G1 na rota R-01.
 */
class CycleServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), java.time.ZoneOffset.UTC);
    private static final TenantTime TENANT_TIME = TenantTime.factoryDefault(CLOCK);

    private BillingReferenceRepository references;
    private BillingGroupRepository groups;
    private CycleActivityRepository activities;
    private CycleScheduleItemRepository items;
    private BillRepository bills;
    private CycleBatchGateway gateway;
    private CycleService service;

    private BillingReferenceEntity reference;
    private BillingGroupEntity group;
    private Map<CycleActivityCode, CycleActivityEntity> catalogByCode;
    private Map<CycleActivityCode, CycleScheduleItemEntity> itemByCode;

    @BeforeEach
    void setUp() {
        references = mock(BillingReferenceRepository.class);
        groups = mock(BillingGroupRepository.class);
        activities = mock(CycleActivityRepository.class);
        items = mock(CycleScheduleItemRepository.class);
        bills = mock(BillRepository.class);
        gateway = mock(CycleBatchGateway.class);
        service = new CycleService(references, groups, activities, items, bills, gateway, CLOCK);

        reference = new BillingReferenceEntity();
        reference.setId(REFERENCE_ID);
        reference.setTenantId(TENANT);
        reference.setReferenceMonth(MONTH.atDay(1));
        reference.setStatus(BillingReferenceStatus.OPEN.name());

        group = new BillingGroupEntity();
        group.setId(GROUP_ID);
        group.setTenantId(TENANT);
        group.setCode("G1");
        group.setName("Grupo 1");
        group.setRouteCode("R-01");
        group.setActive(true);

        catalogByCode = new EnumMap<>(CycleActivityCode.class);
        Map<CycleActivityCode, UUID> ids = new EnumMap<>(CycleActivityCode.class);
        for (ProductActivityCatalog.Definition definition : ProductActivityCatalog.definitions()) {
            ids.put(definition.code(), UUID.randomUUID());
        }
        for (ProductActivityCatalog.Definition definition : ProductActivityCatalog.definitions()) {
            CycleActivityEntity entity = new CycleActivityEntity();
            entity.setId(ids.get(definition.code()));
            entity.setTenantId(TENANT);
            entity.setCode(definition.code().name());
            entity.setName(definition.name());
            entity.setSequenceOrder((short) definition.sequenceOrder());
            entity.setMandatory(definition.mandatory());
            entity.setRepeatable(definition.repeatable());
            entity.setPredecessorId(definition.predecessor() == null ? null : ids.get(definition.predecessor()));
            catalogByCode.put(definition.code(), entity);
        }

        itemByCode = new EnumMap<>(CycleActivityCode.class);
        for (CycleActivityCode code : CycleActivityCode.values()) {
            CycleScheduleItemEntity item = new CycleScheduleItemEntity();
            item.setId(UUID.randomUUID());
            item.setTenantId(TENANT);
            item.setReferenceId(REFERENCE_ID);
            item.setGroupId(GROUP_ID);
            item.setActivityId(catalogByCode.get(code).getId());
            item.setOccurrenceNo((short) 1);
            item.setStatus(ScheduleItemStatus.PENDING.name());
            item.setPlannedOn(LocalDate.of(2026, 11, 5));
            itemByCode.put(code, item);
        }

        when(activities.findByTenantIdOrderBySequenceOrderAsc(TENANT))
                .thenReturn(List.of(catalogByCode.get(CycleActivityCode.READING),
                        catalogByCode.get(CycleActivityCode.TRANSMISSION),
                        catalogByCode.get(CycleActivityCode.BILLING),
                        catalogByCode.get(CycleActivityCode.CLOSING)));
        when(groups.findByTenantIdAndId(TENANT, GROUP_ID)).thenReturn(Optional.of(group));
        when(references.findByTenantIdAndId(TENANT, REFERENCE_ID)).thenReturn(Optional.of(reference));
        when(items.findByTenantIdAndReferenceIdAndGroupIdOrderByOccurrenceNoAsc(TENANT, REFERENCE_ID, GROUP_ID))
                .thenAnswer(invocation -> List.of(
                        itemByCode.get(CycleActivityCode.READING),
                        itemByCode.get(CycleActivityCode.TRANSMISSION),
                        itemByCode.get(CycleActivityCode.BILLING),
                        itemByCode.get(CycleActivityCode.CLOSING)));
        when(items.save(any(CycleScheduleItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (CycleActivityCode code : CycleActivityCode.values()) {
            when(activities.findByTenantIdAndId(TENANT, catalogByCode.get(code).getId()))
                    .thenReturn(Optional.of(catalogByCode.get(code)));
            when(items.findByTenantIdAndId(TENANT, itemByCode.get(code).getId()))
                    .thenReturn(Optional.of(itemByCode.get(code)));
        }
    }

    private void done(CycleActivityCode code) {
        itemByCode.get(code).setStatus(ScheduleItemStatus.DONE.name());
    }

    // ------------------------------------------------------------------ comando

    @Test
    @DisplayName("precedencia: comandar a transmissao com a leitura pendente recusa e nao grava nem despacha")
    void commandOutOfOrderIsRefused() {
        UUID transmissionItem = itemByCode.get(CycleActivityCode.TRANSMISSION).getId();

        assertThatThrownBy(() -> service.commandActivity(TENANT, transmissionItem, ACTOR, TENANT_TIME))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("cycle-predecessor-not-done"));
        verify(items, never()).save(any());
        verify(gateway, never()).dispatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("rota nao transmitida: comandar o faturamento com a transmissao pendente recusa sem despachar o lote")
    void billingCommandRefusedWhenRouteNotTransmitted() {
        done(CycleActivityCode.READING);
        UUID billingItem = itemByCode.get(CycleActivityCode.BILLING).getId();

        assertThatThrownBy(() -> service.commandActivity(TENANT, billingItem, ACTOR, TENANT_TIME))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("cycle-route-not-transmitted"));
        verify(gateway, never()).dispatch(any(), any(), any(), any(), any(), any());
        verify(items, never()).save(any());
    }

    @Test
    @DisplayName("faturamento com a rota transmitida: despacha uma vez pela P2.1 (ciclo do grupo, rota do grupo) e marca COMMANDED com o run")
    void billingCommandDispatchesOnceAndMarksCommanded() {
        done(CycleActivityCode.READING);
        done(CycleActivityCode.TRANSMISSION);
        when(gateway.dispatch(eq(TENANT), eq("P2.5-2026-11-G1-BILLING"), eq(List.of("R-01")),
                eq(TENANT_TIME), eq(ACTOR), eq(null)))
                .thenReturn(new CycleBatchGateway.Dispatch(CycleBatchGateway.Outcome.ACCEPTED, RUN_ID));

        CycleScheduleItemEntity commanded = service.commandActivity(
                TENANT, itemByCode.get(CycleActivityCode.BILLING).getId(), ACTOR, TENANT_TIME);

        assertThat(commanded.getStatus()).isEqualTo(ScheduleItemStatus.COMMANDED.name());
        assertThat(commanded.getJobRunId()).isEqualTo(RUN_ID);
        assertThat(commanded.getCommandedBy()).isEqualTo(ACTOR);
        assertThat(commanded.getCommandedAt()).isEqualTo(
                java.time.OffsetDateTime.ofInstant(Instant.parse("2026-11-10T12:00:00Z"), java.time.ZoneOffset.UTC));
        verify(gateway).dispatch(eq(TENANT), eq("P2.5-2026-11-G1-BILLING"), eq(List.of("R-01")),
                eq(TENANT_TIME), eq(ACTOR), eq(null));
    }

    @Test
    @DisplayName("disparo duplicado recusado pela fundacao (P2.1) recusa o comando e nao marca o item")
    void duplicateDispatchRefusesTheCommand() {
        done(CycleActivityCode.READING);
        done(CycleActivityCode.TRANSMISSION);
        when(gateway.dispatch(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CycleBatchGateway.Dispatch(CycleBatchGateway.Outcome.REFUSED_DUPLICATE, RUN_ID));

        assertThatThrownBy(() -> service.commandActivity(
                TENANT, itemByCode.get(CycleActivityCode.BILLING).getId(), ACTOR, TENANT_TIME))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("cycle-batch-duplicate"));
        verify(items, never()).save(any());
        assertThat(itemByCode.get(CycleActivityCode.BILLING).getStatus())
                .isEqualTo(ScheduleItemStatus.PENDING.name());
    }

    @Test
    @DisplayName("comando em referencia fechada recusa antes de qualquer despacho")
    void commandInClosedReferenceIsRefused() {
        reference.setStatus(BillingReferenceStatus.CLOSED.name());

        assertThatThrownBy(() -> service.commandActivity(
                TENANT, itemByCode.get(CycleActivityCode.READING).getId(), ACTOR, TENANT_TIME))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("billing-reference-closed"));
        verify(items, never()).save(any());
    }

    // ------------------------------------------------------------------ geracao

    @Test
    @DisplayName("referencia fechada: a guarda de geracao recusa mesmo sem conta existente")
    void generationGuardRefusesClosedReference() {
        reference.setStatus(BillingReferenceStatus.CLOSED.name());
        when(references.findByTenantIdAndReferenceMonth(TENANT, MONTH.atDay(1)))
                .thenReturn(Optional.of(reference));
        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.assertNewBillAllowed(TENANT, UUID.randomUUID(), MONTH))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("billing-reference-closed"));
    }

    @Test
    @DisplayName("ciclo repetido nao gera segunda conta: com conta viva na referencia, a guarda recusa")
    void repeatedCycleDoesNotGenerateSecondBill() {
        when(references.findByTenantIdAndReferenceMonth(TENANT, MONTH.atDay(1)))
                .thenReturn(Optional.of(reference));
        BillEntity existing = new BillEntity();
        existing.setStatus("NORMAL");
        UUID unit = UUID.randomUUID();
        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(TENANT, unit, MONTH.atDay(1)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.assertNewBillAllowed(TENANT, unit, MONTH))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("bill-already-billed"));
    }

    @Test
    @DisplayName("guarda de geracao admite: sem conta viva (CREATE) e substituindo a pre-faturada (REPLACE_PRE_BILLED)")
    void generationGuardAdmits() {
        when(references.findByTenantIdAndReferenceMonth(TENANT, MONTH.atDay(1)))
                .thenReturn(Optional.of(reference));
        UUID unit = UUID.randomUUID();

        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(TENANT, unit, MONTH.atDay(1)))
                .thenReturn(List.of());
        assertThat(service.assertNewBillAllowed(TENANT, unit, MONTH))
                .isEqualTo(CycleCommandRule.GenerationDecision.CREATE);

        BillEntity cancelled = new BillEntity();
        cancelled.setStatus("CANCELLED");
        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(TENANT, unit, MONTH.atDay(1)))
                .thenReturn(List.of(cancelled));
        assertThat(service.assertNewBillAllowed(TENANT, unit, MONTH))
                .isEqualTo(CycleCommandRule.GenerationDecision.CREATE);

        BillEntity preBilled = new BillEntity();
        preBilled.setStatus("PRE_BILLED");
        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(TENANT, unit, MONTH.atDay(1)))
                .thenReturn(List.of(preBilled));
        assertThat(service.assertNewBillAllowed(TENANT, unit, MONTH))
                .isEqualTo(CycleCommandRule.GenerationDecision.REPLACE_PRE_BILLED);
    }

    // --------------------------------------------------------------- fechamento

    @Test
    @DisplayName("fechamento com grupo pendente recusa e a referencia continua aberta")
    void closeRefusedWhileGroupPending() {
        done(CycleActivityCode.READING);
        done(CycleActivityCode.TRANSMISSION);
        done(CycleActivityCode.BILLING);
        // CLOSING do grupo continua PENDING.
        when(items.findByTenantIdAndReferenceId(TENANT, REFERENCE_ID))
                .thenReturn(List.of(itemByCode.get(CycleActivityCode.READING),
                        itemByCode.get(CycleActivityCode.TRANSMISSION),
                        itemByCode.get(CycleActivityCode.BILLING),
                        itemByCode.get(CycleActivityCode.CLOSING)));

        assertThatThrownBy(() -> service.closeReference(TENANT, REFERENCE_ID, ACTOR))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code())
                        .isEqualTo("billing-reference-groups-pending"));
        verify(references, never()).save(any());
        assertThat(reference.getStatus()).isEqualTo(BillingReferenceStatus.OPEN.name());
    }

    @Test
    @DisplayName("fechamento com todos os grupos concluidos fecha a referencia com autor e instante")
    void closeSucceedsWhenAllGroupsDone() {
        for (CycleActivityCode code : CycleActivityCode.values()) {
            done(code);
        }
        when(items.findByTenantIdAndReferenceId(TENANT, REFERENCE_ID))
                .thenReturn(List.of(itemByCode.get(CycleActivityCode.READING),
                        itemByCode.get(CycleActivityCode.TRANSMISSION),
                        itemByCode.get(CycleActivityCode.BILLING),
                        itemByCode.get(CycleActivityCode.CLOSING)));
        when(references.save(any(BillingReferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BillingReferenceEntity closed = service.closeReference(TENANT, REFERENCE_ID, ACTOR);

        assertThat(closed.getStatus()).isEqualTo(BillingReferenceStatus.CLOSED.name());
        assertThat(closed.getClosedBy()).isEqualTo(ACTOR);
        assertThat(closed.getClosedAt()).isEqualTo(
                java.time.OffsetDateTime.ofInstant(Instant.parse("2026-11-10T12:00:00Z"), java.time.ZoneOffset.UTC));
    }

    // ----------------------------------------------------------------- abertura

    @Test
    @DisplayName("abertura sem a data prevista de atividade obrigatoria recusa antes de gravar a referencia")
    void openRefusesMissingPlannedDateForMandatoryActivity() {
        when(references.findByTenantIdAndReferenceMonth(TENANT, MONTH.atDay(1)))
                .thenReturn(Optional.empty());
        when(groups.findByTenantIdAndActiveTrueOrderByCodeAsc(TENANT)).thenReturn(List.of(group));
        Map<CycleActivityCode, LocalDate> planned = new EnumMap<>(CycleActivityCode.class);
        planned.put(CycleActivityCode.READING, LocalDate.of(2026, 11, 3));
        planned.put(CycleActivityCode.TRANSMISSION, LocalDate.of(2026, 11, 4));
        planned.put(CycleActivityCode.CLOSING, LocalDate.of(2026, 11, 28));
        // BILLING sem data prevista: atividade obrigatoria.

        assertThatThrownBy(() -> service.openReference(TENANT, MONTH, planned, ACTOR))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("cycle-planned-date-missing"));
        verify(references, never()).save(any());
        verify(items, never()).save(any());
    }
}
