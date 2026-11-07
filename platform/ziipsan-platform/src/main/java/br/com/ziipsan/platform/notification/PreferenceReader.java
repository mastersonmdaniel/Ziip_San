package br.com.ziipsan.platform.notification;

import java.util.List;
import java.util.UUID;

/**
 * Porta de LEITURA do registro único de preferências da P3.36 (COE-19: a P3.36 é a
 * dona do registro; a P3.5 e a ED-41 leem). A implementação lê a tabela
 * {@code notification_preferences} da V1_15 — esta tarefa não grava preferência.
 * Linha ausente para (sujeito, finalidade, categoria, canal) significa "sem
 * preferência declarada": vale o padrão (canal ligado), como na ingestão da
 * própria central.
 */
public interface PreferenceReader {

    List<NotificationPreference> find(UUID tenantId, UUID customerId, UUID userId,
                                      String purposeCode, NotificationCategory category,
                                      NotificationChannel channel);
}
