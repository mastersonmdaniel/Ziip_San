package br.com.ziipsan.platform.ai;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** Varredura de retencao dos logs de IA (PLT-18; SG-701): chamador REAL de
 * AiConversationLog.retentionExpired. Seleciona, entre os logs lidos pela fiacao, os vencidos
 * pelo prazo ai.log_retention_days e os entrega a acao de expurgo fornecida pelo chamador.
 * CORRECAO ED-43 (m6): retentionExpired nao tinha chamador — a peca existia morta. O AGENDAMENTO
 * e a exclusao fisica seguem na fiacao (P-ED43-02); a regra de selecao vive e e testada aqui. */
public final class AiRetentionSweeper {
    private AiRetentionSweeper() {}
    public static List<AiConversationLog> expiredAmong(List<AiConversationLog> logs, int retentionDays, Instant now) {
        if (logs == null) return List.of();
        return logs.stream().filter(l -> AiConversationLog.retentionExpired(l, retentionDays, now)).toList();
    }
    /** Executa a varredura: aplica purgeAction a cada log vencido e devolve quantos foram purgados. */
    public static int sweep(List<AiConversationLog> logs, int retentionDays, Instant now, Consumer<AiConversationLog> purgeAction) {
        var expired = expiredAmong(logs, retentionDays, now);
        if (purgeAction != null) expired.forEach(purgeAction);
        return expired.size();
    }
}
