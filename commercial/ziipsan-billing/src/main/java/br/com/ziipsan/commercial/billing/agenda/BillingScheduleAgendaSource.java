package br.com.ziipsan.commercial.billing.agenda;

import br.com.ziipsan.core.agenda.AgendaEvent;
import br.com.ziipsan.core.agenda.AgendaEventSource;
import br.com.ziipsan.core.agenda.AgendaEventTypes;
import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
import br.com.ziipsan.commercial.billing.repository.CycleScheduleItemRepository;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Fonte de agenda do cronograma de faturamento (P2.5 -> P3.39): projeta os
 * itens do cronograma mensal ({@code cycle_schedule_items}, V4_3) como
 * eventos do tipo registrado {@code billing-schedule}. SOMENTE LEITURA —
 * comandar/realizar atividade continua no CycleService da P2.5.
 *
 * <p>Projecao: o inicio e a DATA PREVISTA ({@code plannedOn}) a meia-noite
 * no fuso do tenant (o cronograma e mensal, por data — nao ha hora no dado
 * da fonte e nenhuma e inventada); {@code endsAt} nulo (evento de dia);
 * itens CANCELLED nao entram (nao sao agenda de ninguem); o endereco do
 * registro aponta para o cronograma do grupo na referencia.
 *
 * <p>Leitura por janela: o contrato da porta ({@code AgendaEventSource}) e
 * o intervalo semiaberto {@code [from, to)} sobre o INSTANTE de inicio do
 * evento — e e assim que esta fonte filtra: projeta o inicio de cada item
 * ({@code plannedOn} a meia-noite no fuso do tenant) e compara o instante
 * com a janela, SEM truncar a janela por data local (truncar perdia os
 * eventos do ultimo dia cujo inicio cai dentro da janela — defeito D1 da
 * revisao cruzada da P3.39, corrigido nesta frente). Custo declarado: o
 * repositorio da P2.5 le por referencia/grupo e nao tem consulta por faixa
 * de {@code plannedOn}, logo a varredura pagina TODOS os itens e o filtro
 * por instante e em memoria — volume de cronograma e por grupo x
 * referencia (dezenas de linhas), mas a consulta dedicada por faixa de
 * {@code plannedOn} e a evolucao nomeada se o volume mudar (pendencia no
 * relatorio da P3.39). O fuso e o do tenant, injetado na fiacao (mesmo
 * padrao da central P3.36).
 */
public final class BillingScheduleAgendaSource implements AgendaEventSource {

    private static final int PAGE_SIZE = 500;

    private final CycleScheduleItemRepository repository;
    private final ZoneId tenantZone;

    public BillingScheduleAgendaSource(CycleScheduleItemRepository repository, ZoneId tenantZone) {
        if (repository == null || tenantZone == null) {
            throw new IllegalArgumentException("repositorio do cronograma e fuso do tenant sao obrigatorios");
        }
        this.repository = repository;
        this.tenantZone = tenantZone;
    }

    @Override
    public String typeCode() {
        return AgendaEventTypes.BILLING_SCHEDULE.code();
    }

    @Override
    public List<AgendaEvent> eventsInWindow(UUID tenantId, Instant from, Instant to) {
        if (tenantId == null || from == null || to == null) {
            throw new IllegalArgumentException("tenant e janela sao obrigatorios");
        }
        List<AgendaEvent> result = new java.util.ArrayList<>();
        // Varredura paginada COMPLETA (nunca so a primeira pagina): o filtro
        // por tenant e por JANELA e em memoria porque o repositorio da P2.5
        // nao tem consulta por faixa de plannedOn — ver a pendencia no
        // javadoc. A janela NAO e convertida para datas: o filtro compara o
        // instante de inicio projetado com [from, to), como manda o contrato
        // da porta (correcao do D1: converter `to` para data local excluia
        // todo evento do dia local de `to`, mesmo com inicio dentro da
        // janela).
        for (int page = 0; ; page++) {
            var slice = repository.findAll(PageRequest.of(page, PAGE_SIZE));
            slice.stream()
                    .filter(i -> tenantId.equals(i.getTenantId()))
                    .filter(i -> !"CANCELLED".equals(i.getStatus()))
                    .filter(i -> {
                        Instant start = i.getPlannedOn().atStartOfDay(tenantZone).toInstant();
                        return !start.isBefore(from) && start.isBefore(to);
                    })
                    .map(this::toEvent)
                    .forEach(result::add);
            if (!slice.hasNext()) {
                return List.copyOf(result);
            }
        }
    }

    /** Projecao pura de um item do cronograma (testavel sem repositorio). */
    AgendaEvent toEvent(CycleScheduleItemEntity item) {
        Instant start = item.getPlannedOn().atStartOfDay(tenantZone).toInstant();
        return new AgendaEvent(item.getId(), item.getTenantId(), typeCode(),
                "Cronograma de faturamento", start, null,
                "CYCLE_SCHEDULE_ITEM", item.getId(),
                "/billing/cycles/" + item.getReferenceId() + "/groups/" + item.getGroupId());
    }
}
