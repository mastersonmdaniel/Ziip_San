package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * E6 — Arquivos ou API de IDA e de VOLTA entre o tenant e a empresa
 * terceirizada.
 *
 * <p>IDA ({@link Remittance}): conteudo gerado de forma DETERMINISTICA a
 * partir das cessoes activas de um contrato — linhas ordenadas pela
 * referencia da divida, cada linha com EXACTAMENTE os campos da whitelist
 * do minimo de dados (E5), em formato textual estavel
 * ({@code debtId|unitId|amount|dueDate|debtorIdentifier}). A transmissao
 * efectiva (ficheiro enviado / chamada de API) e acto de integracao —
 * pendencia nomeada; aqui entrega-se a geracao e a prova de ida-e-volta.
 *
 * <p>VOLTA ({@link ReturnOccurrence}): ocorrencia reportada pela empresa
 * sobre uma cessao. Os tipos que mudam estado sao {@code PAYMENT} e
 * {@code INSTALLMENT_AGREEMENT} (disparam a retirada E8); {@code
 * NO_PROGRESS} regista sem retirar. Ocorrencia sobre cessao inexistente
 * ou ja retirada recusa no servico.
 */
public final class RemittanceAndReturn {

    private RemittanceAndReturn() {
    }

    public record Remittance(UUID contractId, LocalDate generatedOn,
                             List<MinimalDebtorData> entries, String content) {
        public Remittance {
            if (contractId == null || generatedOn == null || entries == null || content == null) {
                throw new ThirdPartyCollectionException("REMITTANCE_INCOMPLETE",
                        "remessa exige contrato, data de geracao, entradas e conteudo");
            }
            entries = List.copyOf(entries);
        }

        /** Linha deterministica de uma entrada — so campos da whitelist E5. */
        public static String lineOf(MinimalDebtorData d) {
            return d.debtId() + "|" + d.unitId() + "|" + d.amount().toPlainString()
                    + "|" + d.dueDate() + "|" + d.debtorIdentifier();
        }

        /** Conteudo integral: cabecalho + uma linha por entrada, ordenadas por debtId. */
        public static String contentOf(UUID contractId, LocalDate generatedOn, List<MinimalDebtorData> entries) {
            StringBuilder sb = new StringBuilder();
            sb.append("REMITTANCE|").append(contractId).append('|').append(generatedOn)
                    .append('|').append(entries.size()).append('\n');
            entries.stream()
                    .sorted(java.util.Comparator.comparing(d -> d.debtId().toString()))
                    .forEach(d -> sb.append(lineOf(d)).append('\n'));
            return sb.toString();
        }
    }

    public record ReturnOccurrence(UUID debtId, Kind kind, LocalDate occurredOn) {

        public enum Kind { PAYMENT, INSTALLMENT_AGREEMENT, NO_PROGRESS }

        public ReturnOccurrence {
            if (debtId == null)
                throw new ThirdPartyCollectionException("RETURN_DEBT_REQUIRED",
                        "ocorrencia de retorno exige a divida a que se refere");
            if (kind == null)
                throw new ThirdPartyCollectionException("RETURN_KIND_REQUIRED",
                        "ocorrencia de retorno exige o tipo declarado");
            if (occurredOn == null)
                throw new ThirdPartyCollectionException("RETURN_DATE_REQUIRED",
                        "ocorrencia de retorno exige a data declarada");
        }
    }
}
