package br.com.ziipsan.platform.central;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Instantâneo AGREGADO do ciclo de faturamento de um tenant, como a
 * Central o consome (isolamento da V9_1/ADR-0018: tenant_id, datas
 * previstas e situações — nenhum dado pessoal, nenhuma conta, nenhum
 * valor). É o espelho, na fronteira da Central, do cronograma da P2.5:
 * {@code cycle_schedule_items} (V4_3) com {@code planned_on} e
 * {@code status}, e a obrigatoriedade de {@code cycle_activities}.
 *
 * <p><b>Grupo (correção pós-revisão M-2):</b> o ciclo da P2.5 é por
 * GRUPO ({@code cycle_schedule_items.group_id} — cada item pertence a
 * um grupo de faturamento da referência). Por isso cada
 * {@link ScheduleItemView} carrega o seu {@code groupId} (UUID):
 * o RB-07 manda "identificar a atividade vencida mais antiga <b>e o
 * grupo</b>", e sem o grupo no instantâneo esse passo não era
 * executável a partir do sinal.
 *
 * <p>Quem produz o instantâneo é a fiação de integração (coletor da
 * Central lendo agregados, no modelo das funções {@code platform_stats.*});
 * esta classe só valida a forma: situação fora do conjunto fechado da
 * V4_3 ou item sem data prevista/grupo são erro explícito.
 *
 * <p><b>Tenant sem cronograma (correção pós-revisão m-3):</b> a lista
 * de itens VAZIA é aceita e significa, explicitamente, "tenant sem
 * cronograma nesta referência" (ex.: recém-implantado, ainda sem grupo
 * de faturamento). A avaliação de um instantâneo vazio devolve "sem
 * atraso" ({@code Optional.empty()}), para que um tenant sem
 * cronograma NÃO aborte a coleta dos demais. O contrato do coletor é:
 * só montar o instantâneo vazio quando a ausência de cronograma foi
 * confirmada na fonte — ausência de DADOS continua erro: lista
 * {@code null} é recusada, e item malformado é recusado um a um.
 * (Desenho anterior — vazio = exceção — foi substituído exatamente
 * para a exceção de um tenant não derrubar a coleta dos outros.)
 */
public record BillingCycleSnapshot(UUID tenantId, YearMonth referenceMonth, List<ScheduleItemView> items) {

    /** Situações do CHECK de {@code cycle_schedule_items.status} (V4_3). */
    public static final Set<String> ITEM_STATUSES = Set.of("PENDING", "COMMANDED", "DONE", "CANCELLED");

    public BillingCycleSnapshot {
        Objects.requireNonNull(tenantId, "tenant_id é obrigatório no instantâneo do ciclo");
        Objects.requireNonNull(referenceMonth, "reference_month é obrigatório no instantâneo do ciclo");
        Objects.requireNonNull(items,
                "items é obrigatório no instantâneo do ciclo: lista vazia explícita significa "
                        + "tenant sem cronograma na referência; ausência de dados (null) é erro");
        items = List.copyOf(items);
    }

    /** Um item do cronograma: grupo, atividade, obrigatoriedade, situação e data prevista. */
    public record ScheduleItemView(UUID groupId, String activityCode, boolean mandatory, String status,
                                   LocalDate plannedOn) {
        public ScheduleItemView {
            Objects.requireNonNull(groupId,
                    "group_id é obrigatório no item do cronograma (o ciclo da P2.5 é por grupo)");
            if (activityCode == null || activityCode.isBlank()) {
                throw new IllegalArgumentException("activity_code é obrigatório no item do cronograma");
            }
            if (status == null || !ITEM_STATUSES.contains(status)) {
                throw new IllegalArgumentException(
                        "status fora do conjunto da V4_3 (PENDING/COMMANDED/DONE/CANCELLED): " + status);
            }
            Objects.requireNonNull(plannedOn, "planned_on é obrigatório no item do cronograma");
        }

        /** Realizado ou cancelado: fora do atraso, qualquer que seja a data. */
        public boolean isSettled() {
            return "DONE".equals(status) || "CANCELLED".equals(status);
        }
    }
}
