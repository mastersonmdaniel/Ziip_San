package br.com.ziipsan.commercial.billing.agenda;

import br.com.ziipsan.core.agenda.AgendaEvent;
import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
import br.com.ziipsan.commercial.billing.repository.CycleScheduleItemRepository;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Projecao do cronograma da P2.5 para a agenda da P3.39: a data prevista
 * vira o inicio a meia-noite no fuso do tenant (nenhuma hora inventada —
 * o cronograma e por data); cancelado nao e projetado pela fonte.
 *
 * <p>Os testes de {@code eventsInWindow} sao a correcao do defeito D1 da
 * revisao cruzada: a janela e o intervalo semiaberto {@code [from, to)}
 * sobre o instante de inicio projetado, sem truncar por data local.
 */
class BillingScheduleAgendaSourceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static CycleScheduleItemEntity item(String status, LocalDate plannedOn) {
        return item(UUID.randomUUID(), status, plannedOn);
    }

    private static CycleScheduleItemEntity item(UUID tenantId, String status, LocalDate plannedOn) {
        CycleScheduleItemEntity e = new CycleScheduleItemEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setReferenceId(UUID.randomUUID());
        e.setGroupId(UUID.randomUUID());
        e.setActivityId(UUID.randomUUID());
        e.setStatus(status);
        e.setPlannedOn(plannedOn);
        return e;
    }

    /** Repositorio dublê que nunca e chamado nestes testes (so a projecao pura). */
    private static CycleScheduleItemRepository unusedRepository() {
        return (CycleScheduleItemRepository) Proxy.newProxyInstance(
                BillingScheduleAgendaSourceTest.class.getClassLoader(),
                new Class<?>[]{CycleScheduleItemRepository.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("duble nao chamado");
                });
    }

    /**
     * Repositorio dublê que serve {@code findAll(Pageable)} a partir da
     * lista dada, respeitando pagina/tamanho (a fonte varre paginado).
     */
    private static CycleScheduleItemRepository repositoryWith(List<CycleScheduleItemEntity> items) {
        return (CycleScheduleItemRepository) Proxy.newProxyInstance(
                BillingScheduleAgendaSourceTest.class.getClassLoader(),
                new Class<?>[]{CycleScheduleItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAll") && args != null && args.length == 1
                            && args[0] instanceof Pageable page) {
                        int fromIdx = (int) page.getOffset();
                        int toIdx = Math.min(fromIdx + page.getPageSize(), items.size());
                        List<CycleScheduleItemEntity> content =
                                fromIdx >= items.size() ? List.of() : items.subList(fromIdx, toIdx);
                        return new PageImpl<>(content, page, items.size());
                    }
                    throw new UnsupportedOperationException("duble nao serve " + method.getName());
                });
    }

    @Test
    void plannedDateBecomesStartOfDayInTenantZone() {
        BillingScheduleAgendaSource source =
                new BillingScheduleAgendaSource(unusedRepository(), SP);
        CycleScheduleItemEntity e = item("PENDING", LocalDate.parse("2026-03-15"));
        AgendaEvent event = source.toEvent(e);
        // 2026-03-15 00:00 em America/Sao_Paulo (UTC-3) = 03:00Z.
        assertThat(event.startsAt()).isEqualTo("2026-03-15T03:00:00Z");
        assertThat(event.endsAt()).isNull();
        assertThat(event.typeCode()).isEqualTo("billing-schedule");
        assertThat(event.recordAddress())
                .isEqualTo("/billing/cycles/" + e.getReferenceId() + "/groups/" + e.getGroupId());
    }

    @Test
    void sourceDeclaresTheBornBillingType() {
        BillingScheduleAgendaSource source =
                new BillingScheduleAgendaSource(unusedRepository(), SP);
        assertThat(source.typeCode()).isEqualTo("billing-schedule");
    }

    @Test
    void eventOnLastDayInsideWindowIsIncluded() {
        // Sonda do revisor (D1): item em 2026-04-15 comeca 03:00Z; a janela
        // [2026-04-01T00:00Z, 2026-04-15T13:00Z) o contem (13:00Z = 10:00 em
        // America/Sao_Paulo). A versao defeituosa truncava `to` para a data
        // local 2026-04-15 e devolvia 0; o esperado e 1.
        UUID tenant = UUID.randomUUID();
        BillingScheduleAgendaSource source = new BillingScheduleAgendaSource(
                repositoryWith(List.of(item(tenant, "PENDING", LocalDate.parse("2026-04-15")))), SP);
        List<AgendaEvent> out = source.eventsInWindow(tenant,
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-15T13:00:00Z"));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).startsAt()).isEqualTo("2026-04-15T03:00:00Z");
    }

    @Test
    void eventStartingExactlyAtToIsExcluded() {
        // Intervalo semiaberto: inicio == to fica FORA. Item em 2026-04-15
        // comeca 2026-04-15T03:00Z; janela termina exatamente nesse instante.
        UUID tenant = UUID.randomUUID();
        BillingScheduleAgendaSource source = new BillingScheduleAgendaSource(
                repositoryWith(List.of(item(tenant, "PENDING", LocalDate.parse("2026-04-15")))), SP);
        List<AgendaEvent> out = source.eventsInWindow(tenant,
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-15T03:00:00Z"));
        assertThat(out).isEmpty();
    }

    @Test
    void eventBeforeFromIsExcluded() {
        // Item em 2026-03-31 comeca 2026-03-31T03:00Z, antes de
        // from = 2026-04-01T00:00Z: fica fora da janela.
        UUID tenant = UUID.randomUUID();
        BillingScheduleAgendaSource source = new BillingScheduleAgendaSource(
                repositoryWith(List.of(item(tenant, "PENDING", LocalDate.parse("2026-03-31")))), SP);
        List<AgendaEvent> out = source.eventsInWindow(tenant,
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));
        assertThat(out).isEmpty();
    }

    @Test
    void eventStartingExactlyAtFromIsIncluded() {
        // Intervalo semiaberto: inicio == from fica DENTRO. Item em
        // 2026-04-01 comeca 2026-04-01T03:00Z; from e exatamente esse instante.
        UUID tenant = UUID.randomUUID();
        BillingScheduleAgendaSource source = new BillingScheduleAgendaSource(
                repositoryWith(List.of(item(tenant, "PENDING", LocalDate.parse("2026-04-01")))), SP);
        List<AgendaEvent> out = source.eventsInWindow(tenant,
                Instant.parse("2026-04-01T03:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));
        assertThat(out).hasSize(1);
    }

    @Test
    void eventsInWindowSkipsCancelledAndOtherTenants() {
        // A janela passa pelo caminho real da fonte (eventsInWindow):
        // CANCELLED nao entra e item de outro tenant nao entra, mesmo com a
        // data dentro da janela; o PENDING do tenant entra.
        UUID tenant = UUID.randomUUID();
        BillingScheduleAgendaSource source = new BillingScheduleAgendaSource(
                repositoryWith(List.of(
                        item(tenant, "CANCELLED", LocalDate.parse("2026-04-10")),
                        item(UUID.randomUUID(), "PENDING", LocalDate.parse("2026-04-10")),
                        item(tenant, "PENDING", LocalDate.parse("2026-04-10")))), SP);
        List<AgendaEvent> out = source.eventsInWindow(tenant,
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).tenantId()).isEqualTo(tenant);
    }
}
