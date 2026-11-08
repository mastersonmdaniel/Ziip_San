package br.com.ziipsan.platform.notification.multichannel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Porta do registro de uso medido (append-only): produtores gravam fatos
 * ({@link UsageRecord}); a leitura é sempre por tenant e período. A tabela
 * ({@code msg_usage_records}) vai no SQL pendente da ED-41.
 */
public interface UsageStore {

    void append(UsageRecord record);

    List<UsageRecord> query(UUID tenantId, Instant from, Instant to);
}
