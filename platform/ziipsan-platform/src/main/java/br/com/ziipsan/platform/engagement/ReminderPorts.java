package br.com.ziipsan.platform.engagement;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Portas dos lembretes da ED-41 consumidos pela ED-101. O envio em si e da
 * ED-41 ({@code MultichannelDispatchService}, com opt-in por canal e
 * finalidade, teto mensal e modelos da P2.30): o adaptador da integracao
 * traduz {@link ReminderPort} para o disparo da ED-41 sobre o evento do
 * catalogo da P1.38 que a integracao registrar para lembretes de meta
 * (pendencia P-ED101-05 — nenhum nome de evento e inventado aqui).
 * Variaveis do modelo: dados do PROPRIO usuario (meta, consumo, periodo)
 * e, SOMENTE no lembrete de relatorio disponivel, os AGREGADOS JA
 * PUBLICADOS daquele relatorio ({@code peerAverageM3} e {@code position},
 * publicados sob o piso de grupo da ED-38/O9-3). O lembrete nunca carrega
 * dado individual de terceiro nem dado de coorte — correcao D3 da
 * revisao da ED-101 (a redacao anterior negava qualquer dado de pares,
 * o que nao era verdade para os agregados publicados).
 */
public final class ReminderPorts {

    private ReminderPorts() {
    }

    public enum ReminderKind {GOAL_ACHIEVED, GOAL_EXCEEDED, PEER_REPORT_AVAILABLE}

    /** Despacho de um lembrete pela infraestrutura da ED-41. */
    public interface ReminderPort {
        void requestReminder(UUID tenantId, UUID customerId, UUID unitId, ReminderKind kind,
                             YearMonth period, Map<String, String> variables);
    }

    /** Registro de lembretes ja despachados (deduplicacao por tipo/periodo). */
    public interface ReminderLog {
        boolean alreadySent(UUID tenantId, UUID unitId, ReminderKind kind, YearMonth period);

        void record(UUID tenantId, UUID unitId, ReminderKind kind, YearMonth period);
    }
}
