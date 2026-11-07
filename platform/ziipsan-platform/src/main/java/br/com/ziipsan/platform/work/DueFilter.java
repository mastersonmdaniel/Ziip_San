package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Filtro por prazo da caixa (aceite da P2.28: "com filtro por prazo"). A data de negócio
 * é a data LOCAL do tenant (fuso do tenant), nunca a data UTC: um item que vence às
 * 23:00 de hoje em São Paulo (02:00Z de amanhã) é de HOJE para o usuário.
 *
 * <ul>
 *   <li>{@code OVERDUE}: aberto e {@code dueAt} estritamente antes de agora;</li>
 *   <li>{@code DUE_TODAY}: aberto, não vencido, com vencimento na data local de hoje;</li>
 *   <li>{@code DUE_IN_7_DAYS}: aberto, não vencido, com vencimento numa DATA local
 *       entre hoje e <b>hoje + 7 dias</b>, inclusive nas duas pontas — é janela de
 *       DATAS do calendário local (8 datas corridas: hoje, +1, ..., +7), NÃO
 *       "agora + 7×24h": um item que vence às 23:59 locais de hoje+7 entra, mesmo
 *       faltando mais de 7×24h a partir de agora; um que vence às 00:00 locais de
 *       hoje+8 não entra. Inclui os de hoje. (Rótulo da tela: "Vencem até hoje +
 *       7 dias" — alinhado pós-revisão m1, sem mudar a semântica.)</li>
 *   <li>item SEM prazo ({@code dueAt} nulo) só aparece em {@code ALL}: ausência de prazo
 *       não é "vence hoje" nem "está em dia" — não se inventa urgência.</li>
 * </ul>
 */
public enum DueFilter {
    ALL,
    OVERDUE,
    DUE_TODAY,
    DUE_IN_7_DAYS;

    public boolean matches(WorkItem item, Instant now, ZoneId tenantZone) {
        if (item == null || now == null || tenantZone == null) {
            throw new IllegalArgumentException("item, agora e fuso do tenant são obrigatórios no filtro por prazo");
        }
        return switch (this) {
            case ALL -> true;
            case OVERDUE -> item.status().isOpen() && item.dueAt() != null && item.dueAt().isBefore(now);
            case DUE_TODAY -> dueOn(item, now, tenantZone, 0);
            case DUE_IN_7_DAYS -> dueWithin(item, now, tenantZone);
        };
    }

    private boolean dueOn(WorkItem item, Instant now, ZoneId zone, int plusDays) {
        if (!item.status().isOpen() || item.dueAt() == null || item.dueAt().isBefore(now)) {
            return false;
        }
        LocalDate dueDate = item.dueAt().atZone(zone).toLocalDate();
        return dueDate.equals(now.atZone(zone).toLocalDate().plusDays(plusDays));
    }

    private boolean dueWithin(WorkItem item, Instant now, ZoneId zone) {
        if (!item.status().isOpen() || item.dueAt() == null || item.dueAt().isBefore(now)) {
            return false;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate dueDate = item.dueAt().atZone(zone).toLocalDate();
        return !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(7));
    }
}
