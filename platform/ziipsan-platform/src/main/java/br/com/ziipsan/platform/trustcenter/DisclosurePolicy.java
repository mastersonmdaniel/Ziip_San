package br.com.ziipsan.platform.trustcenter;

import java.time.Duration;
import java.util.Objects;

/**
 * P3.38: política de divulgação responsável como CONFIGURAÇÃO — canal,
 * página da política e prazos. É um record de parâmetros, e não um texto
 * fixo no código, por uma razão declarada: nem a spec da P3.38 nem os
 * ADR-0020/0021 fixam o canal oficial nem o prazo de resposta ao
 * pesquisador. Inventar um e-mail ou um número de dias para "cumprir" o
 * aceite publicaria um compromisso que o dono não assumiu.
 *
 * <p>Enquanto a decisão não existe, os campos ficam {@code null} e
 * {@link #isPublishable()} é {@code false}: o {@code security.txt} não é
 * gerado (ver {@link SecurityTxt}) e o Trust Center exibe a seção de
 * divulgação como pendente de decisão — nunca com um canal ou prazo
 * fictício. Os testes usam valores marcados como fixture de teste, que
 * não são o canal nem o prazo de produção.
 *
 * @param contactUri canal oficial de relato (URI {@code mailto:} ou
 *                   {@code https:}); {@code null} = decisão pendente
 * @param policyUrl URL pública da política de divulgação responsável
 *                  ({@code docs/trust-center/politica-divulgacao-responsavel.md}
 *                  publicada); obrigatoriamente {@code https:} — é o
 *                  campo {@code Policy} da RFC 9116 (§2.5.4), que não
 *                  admite {@code mailto:}; {@code null} = ainda não
 *                  publicada
 * @param canonicalUrl URL canônica do {@code security.txt} publicado;
 *                     obrigatoriamente {@code https:} — campo
 *                     {@code Canonical} da RFC 9116 (§2.5.3);
 *                     {@code null} = hospedagem (LC.7) ainda não definiu
 * @param acknowledgementTarget prazo-alvo da confirmação de recebimento
 *                              ao pesquisador; {@code null} = decisão
 *                              pendente do dono (sem prazo publicado)
 * @param coordinatedDisclosureTarget prazo-alvo da divulgação coordenada
 *                                    após a correção; {@code null} =
 *                                    decisão pendente do dono
 */
public record DisclosurePolicy(
        String contactUri,
        String policyUrl,
        String canonicalUrl,
        Duration acknowledgementTarget,
        Duration coordinatedDisclosureTarget) {

    public DisclosurePolicy {
        if (contactUri != null) {
            requireUri(contactUri, "contactUri");
        }
        if (policyUrl != null) {
            requireHttpsUri(policyUrl, "policyUrl");
        }
        if (canonicalUrl != null) {
            requireHttpsUri(canonicalUrl, "canonicalUrl");
        }
        if (acknowledgementTarget != null && (acknowledgementTarget.isZero() || acknowledgementTarget.isNegative())) {
            throw new IllegalArgumentException("Prazo de confirmação de recebimento deve ser positivo");
        }
        if (coordinatedDisclosureTarget != null
                && (coordinatedDisclosureTarget.isZero() || coordinatedDisclosureTarget.isNegative())) {
            throw new IllegalArgumentException("Prazo de divulgação coordenada deve ser positivo");
        }
    }

    private static void requireUri(String value, String field) {
        if (value.isBlank() || value.contains("<") || value.contains(">") || value.contains(" ")) {
            throw new IllegalArgumentException(
                    field + " deve ser uma URI sem espaços nem marcadores de modelo: " + value);
        }
        if (!value.startsWith("mailto:") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(
                    field + " deve começar com mailto: ou https:// (canal verificável): " + value);
        }
    }

    /**
     * Variante estrita para os campos que a RFC 9116 define como URI
     * {@code https:} — {@code Policy} (§2.5.4) e {@code Canonical}
     * (§2.5.3). Correção D3 da revisão cruzada M3: {@code mailto:} nesses
     * campos geraria um {@code security.txt} que um validador reprova;
     * o {@code Contact} (§2.5.2) continua aceitando {@code mailto:}.
     */
    private static void requireHttpsUri(String value, String field) {
        if (value.isBlank() || value.contains("<") || value.contains(">") || value.contains(" ")) {
            throw new IllegalArgumentException(
                    field + " deve ser uma URI sem espaços nem marcadores de modelo: " + value);
        }
        if (!value.startsWith("https://")) {
            throw new IllegalArgumentException(
                    field + " deve começar com https:// (RFC 9116: Policy e Canonical são URIs https:): "
                            + value);
        }
    }

    /** Política ainda sem nenhuma decisão: nada é publicável. */
    public static DisclosurePolicy pending() {
        return new DisclosurePolicy(null, null, null, null, null);
    }

    public boolean hasContact() {
        return contactUri != null;
    }

    public boolean hasAcknowledgementTarget() {
        return acknowledgementTarget != null;
    }

    /**
     * Publicável = existe canal E prazo de confirmação assumido. Sem os
     * dois, o aceite ("canal, prazo de resposta") não está cumprido e o
     * {@code security.txt} não pode nascer afirmando o que não existe.
     */
    public boolean isPublishable() {
        return hasContact() && hasAcknowledgementTarget();
    }

    /** Exige a política publicável; recusa explícita, nunca arquivo pela metade. */
    public void requirePublishable() {
        if (!isPublishable()) {
            throw new IllegalStateException(
                    "Política de divulgação não publicável: canal oficial e/ou prazo de confirmação "
                            + "ainda são decisão pendente do dono (P3.38) — nenhum canal ou prazo foi inventado");
        }
        Objects.requireNonNull(policyUrl,
                "policyUrl é obrigatória para publicar: a política pública precisa estar no ar");
    }
}
