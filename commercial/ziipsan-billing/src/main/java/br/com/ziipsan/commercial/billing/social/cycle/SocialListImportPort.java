package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialEligibility;

import java.time.LocalDate;
import java.util.List;

/**
 * II-3: porta de importacao da lista externa de beneficiarios e os tipos
 * do seu resultado.
 *
 * <p>A porta recebe o CONTEUDO da lista ja entregue ao sistema (ficheiro
 * recebido por qualquer canal: a submissao real a sistemas externos,
 * CadUnico incluido, e acto externo fora deste pacote) e o formato
 * DECLARADO que o descreve. O adaptador de prova
 * ({@link DeclaredCsvSocialListImporter}) le texto delimitado; a
 * integracao real liga outro adaptador atras da mesma porta quando o
 * leiaute oficial existir ([SEM FONTE], P-II3-LEIAUTE).
 *
 * <p>Minimizacao LGPD por desenho: a linha importada carrega SO indices
 * cegos (hashes HMAC 64 hex), papel, base de elegibilidade, datas do
 * beneficio e o municipio declarado. Nao ha campo para documento, nome
 * ou renda em claro - como a P1.5/V4_2 e a ED-57 ja desenharam - e o
 * resultado da importacao nao vira base paralela: ver
 * {@link SocialCycleStore}, que persiste so chaves e o resultado do
 * cruzamento.
 */
public interface SocialListImportPort {

    /**
     * Le e valida a lista. Linha invalida NAO derruba o lote: entra nas
     * recusas (linha, campo, codigo, mensagem) e as demais seguem - o
     * mesmo contrato do relatorio de recusas da P1.40/ED-57.
     * Duplicadas no ficheiro (mesma familia, pessoa e papel) sao
     * separadas em {@code duplicatesRemoved}: a primeira ocorrencia
     * vale, as restantes sao contadas e devolvidas para o relatorio do
     * ciclo, sem serem cruzadas duas vezes.
     */
    ImportOutcome importBatch(String content, DeclaredSocialListFormat format);

    /** Uma pessoa da lista importada: so indices cegos e dados do beneficio. */
    record ImportedBeneficiaryRow(String familyCodeHash, String personDocumentHash, String personRole,
                                  SocialEligibility.Basis basis, LocalDate cadunicoUpdatedOn,
                                  LocalDate benefitValidFrom, LocalDate benefitValidTo,
                                  String municipalityCode) {
        public ImportedBeneficiaryRow {
            if (familyCodeHash == null || familyCodeHash.isBlank()
                    || personDocumentHash == null || personDocumentHash.isBlank()) {
                throw new IllegalArgumentException("Linha importada sem indice cego da familia ou da pessoa");
            }
            if (personRole == null || basis == null || benefitValidFrom == null) {
                throw new IllegalArgumentException("Linha importada sem papel, base ou inicio de vigencia");
            }
            if (benefitValidTo != null && benefitValidTo.isBefore(benefitValidFrom)) {
                throw new IllegalArgumentException("Fim da vigencia do beneficio anterior ao inicio");
            }
        }

        /** A chave de deduplicacao: a composicao (familia, pessoa, papel) do UNIQUE da V4_2. */
        public String dedupKey() {
            return familyCodeHash + "/" + personDocumentHash + "/" + personRole;
        }
    }

    /** Recusa de uma linha na importacao (relatorio de recusas do ciclo). */
    record ImportRejection(int lineNumber, String field, String code, String message) {
    }

    /**
     * Resultado da importacao: aceites (deduplicados, na ordem do
     * ficheiro), duplicadas removidas (na ordem do ficheiro) e recusas.
     */
    record ImportOutcome(List<ImportedBeneficiaryRow> accepted, List<ImportedBeneficiaryRow> duplicatesRemoved,
                         List<ImportRejection> rejections) {
        public ImportOutcome {
            accepted = List.copyOf(accepted);
            duplicatesRemoved = List.copyOf(duplicatesRemoved);
            rejections = List.copyOf(rejections);
        }
    }
}
