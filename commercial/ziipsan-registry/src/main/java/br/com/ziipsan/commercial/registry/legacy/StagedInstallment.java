package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma prestação a vencer do legado, encenada para a P2.10 (P1.2).
 *
 * <p>A v0 NÃO aplica prestações: elas ficam em staging com a referência ao
 * acordo do legado ({@code legacyAgreementRef}) para a carga da P2.10, que
 * as migrará como débitos a cobrar com origem parcelamento
 * ({@code source_type}) e a mesma referência. O vínculo com a unidade é
 * pela chave legada ({@code unitLegacyKey}); a resolução para a unidade
 * encenada acontece na carga, e a prestação sem unidade correspondente é
 * recusada na carga (contada, não inventada).
 */
public record StagedInstallment(
        UUID id,
        UUID batchId,
        UUID stagingUnitId,
        String unitLegacyKey,
        String legacyAgreementRef,
        int installmentNo,
        LocalDate dueDate,
        long amountCents) {
}
