package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Projeção de leitura de um documento fiscal para a fila do operador (P2.33), sobre o
 * modelo de documento fiscal da P2.6: o documento em si continua em
 * {@code fiscal_documents} (V4_5) + extensão do modelo 75 (V4_7); esta projeção é o
 * que a porta de leitura ({@link FiscalDocumentReadPort}) monta na integração —
 * nenhuma coluna nova é presumida aqui.
 *
 * @param kind                    NFAg-e ou NFS-e avulsa (P3.6), pelo mesmo modelo
 * @param nfageStatus             estado do dicionário (só NFAg-e; nulo na avulsa)
 * @param avulsaTransmitted       avulsa já transmitida (só NFS-e avulsa)
 * @param competence              competência (mês de referência) do documento, do
 *                                {@code issued_at} no fuso America/Sao_Paulo (DAD-18)
 * @param billingGroupId          grupo de faturamento da conta de origem (filtro
 *                                "grupo" do aceite); nulo quando a origem não tem
 *                                grupo (ex.: NFS-e avulsa de OS) — filtro por grupo
 *                                exclui documento sem grupo, nunca o presume
 * @param contingency             emissão em contingência (detalhe do modelo 75, V4_7)
 * @param contingencyStartedAt    início da contingência (para o tempo decorrido)
 * @param cancellationPending     há pedido de cancelamento pendente na V4_11
 * @param rejectionCode           {@code cStat} da rejeição (só REJECTED)
 * @param sourceType              origem do documento ({@code BILL} na NFAg-e da P2.6)
 * @param sourceId                id da origem (a conta, na NFAg-e)
 */
public record FiscalDocumentView(UUID id, UUID tenantId, FiscalDocumentKind kind,
                                 NfageStatus nfageStatus, boolean avulsaTransmitted,
                                 String accessKey, int serie, long numero,
                                 YearMonth competence, UUID billingGroupId,
                                 boolean contingency, OffsetDateTime contingencyStartedAt,
                                 boolean cancellationPending,
                                 String rejectionCode, String rejectionMessage,
                                 String sourceType, UUID sourceId,
                                 OffsetDateTime issuedAt) {

    public FiscalDocumentView {
        Objects.requireNonNull(id, "Documento fiscal é obrigatório");
        Objects.requireNonNull(tenantId, "Tenant é obrigatório");
        Objects.requireNonNull(kind, "Tipo de documento é obrigatório");
        Objects.requireNonNull(competence, "Competência do documento é obrigatória");
        if (kind == FiscalDocumentKind.NFAGE) {
            Objects.requireNonNull(nfageStatus, "Documento NFAg-e exige o estado do dicionário (P2.6)");
        }
        if (contingency && contingencyStartedAt == null) {
            throw new IllegalArgumentException(
                    "Documento em contingência exige o início da contingência (tempo decorrido do aceite)");
        }
        if (nfageStatus == NfageStatus.REJECTED
                && (rejectionCode == null || rejectionCode.isBlank())) {
            throw new IllegalArgumentException("Documento rejeitado exige o código de rejeição (cStat) da SEFAZ");
        }
    }
}
