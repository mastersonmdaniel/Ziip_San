package br.com.ziipsan.commercial.billing.bill;

import java.util.UUID;

/**
 * Origem de crédito do catálogo {@code credit_origins} (DAD-20, P2.5; catálogo classe a):
 * substitui o {@code origin} em texto livre de {@code credit_receivables}. Como todo catálogo do
 * produto, começa vazio e é política do sistema/tenant — nenhum código é semeado nem embutido
 * no código.
 *
 * <p>Não confundir com {@link ReceivableSourceType}: a origem do catálogo diz <i>o que</i> é o
 * crédito (a natureza cadastrada), enquanto o {@code source_type}/{@code source_id} dizem
 * <i>de qual objeto</i> este lançamento concreto nasceu (uma OS, um acordo, uma importação...).
 *
 * <p>Este record é a fotografia do item do catálogo já resolvida pelo chamador, como em
 * {@link DebitType}.
 */
public record CreditOrigin(UUID id, String code) {

    public CreditOrigin {
        if (id == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Origem de crédito sem identificação ou código (catálogo credit_origins)");
        }
    }
}
