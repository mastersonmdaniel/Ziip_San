package br.com.ziipsan.commercial.billing.content;

import java.time.LocalDate;

/**
 * Um campo obrigatorio da fatura (P2.32, CAD-96/RAS-04): a chave estavel que
 * o modelo da conta precisa conter, a norma que o exige (lei ou ato da ERI do
 * perfil da P2.31), a referencia (artigo) e se o campo vive num bloco travado
 * do motor da P2.30 (bloco LOCKED de origem SYSTEM, que a P2.30 reservou a
 * esta tarefa).
 *
 * <p>NENHUM texto de lei e reproduzido aqui: so a citacao da norma e do
 * artigo, como manda a spec. O conteudo juridico do campo (o texto que a
 * fatura imprime) e dado do tenant/fabricante no modelo aprovado, nunca
 * inventado por esta classe.
 */
public record BillFieldRequirement(
        String fieldKey,
        String profileKey,
        String sourceNorm,
        String sourceReference,
        boolean lockedBlock,
        Origin origin,
        LocalDate validFrom,
        LocalDate validTo) {

    public enum Origin {
        /** Lei/decreto federal: vale para todo perfil. */
        FEDERAL,
        /** Ato da ERI do perfil (P2.31), conforme o texto lido no mapa de ERIs. */
        REGULATORY_PROFILE
    }

    public BillFieldRequirement {
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey e obrigatoria");
        }
        if (sourceNorm == null || sourceNorm.isBlank()) {
            throw new IllegalArgumentException("sourceNorm e obrigatoria para " + fieldKey
                    + ": campo obrigatorio sem norma declarada nao entra no catalogo");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin e obrigatoria para " + fieldKey);
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo anterior a validFrom em " + fieldKey);
        }
    }

    /** A vigencia cobre a data (versao do dado: troca de perfil/norma por data). */
    public boolean covers(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date e obrigatoria");
        }
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }
}
