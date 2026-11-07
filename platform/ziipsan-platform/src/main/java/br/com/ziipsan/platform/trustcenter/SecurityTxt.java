package br.com.ziipsan.platform.trustcenter;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * P3.38: geração do {@code /.well-known/security.txt} (RFC 9116).
 *
 * <p>O arquivo estático entregue em
 * {@code docs/trust-center/.well-known/security.txt} é um MODELO com
 * marcadores explícitos: o canal e a expiração reais dependem de decisão
 * do dono e da hospedagem da LC.7, e nenhum dos dois foi inventado. Na
 * publicação, o arquivo final é gerado por {@link #render} a partir da
 * {@link DisclosurePolicy} configurada — e {@code render} RECUSA política
 * não publicável, marcador de modelo e expiração inválida, em vez de
 * gerar um arquivo que um validador RFC 9116 reprovaria ou que afirmasse
 * um canal inexistente.
 *
 * <p>Campos emitidos (RFC 9116 §2): {@code Contact} e {@code Expires}
 * (obrigatórios), {@code Policy} (a política pública desta tarefa),
 * {@code Canonical} (quando a hospedagem define a URL canônica) e
 * {@code Preferred-Languages: pt-BR}. A expiração segue a RFC: no futuro
 * e a no máximo 1 ano dele (§2.5.5) — arquivo vencido ou com validade
 * longa demais é exatamente o tipo de {@code security.txt} que os
 * pesquisadores aprendem a ignorar.
 */
public final class SecurityTxt {

    /** Validade máxima do arquivo a partir da geração (RFC 9116 §2.5.5). */
    public static final Duration MAX_VALIDITY = Duration.ofDays(365);

    private SecurityTxt() {
    }

    /**
     * Renderiza o conteúdo final do {@code security.txt}. {@code now} é
     * entregue por quem chama para o teste usar relógio fixo.
     *
     * @throws IllegalStateException se a política não for publicável
     *                               (canal/prazo pendentes — ver
     *                               {@link DisclosurePolicy#requirePublishable()})
     * @throws IllegalArgumentException se a expiração não estiver no
     *                                  futuro ou passar de 1 ano
     */
    public static String render(DisclosurePolicy policy, Instant expiresAt, Instant now) {
        Objects.requireNonNull(policy, "Política de divulgação é obrigatória para gerar o security.txt");
        Objects.requireNonNull(expiresAt, "Expires do security.txt é obrigatório (RFC 9116)");
        Objects.requireNonNull(now, "now é obrigatório para validar a expiração");
        policy.requirePublishable();
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "security.txt recusado: Expires deve estar no futuro (RFC 9116 §2.5.5)");
        }
        if (expiresAt.isAfter(now.plus(MAX_VALIDITY))) {
            throw new IllegalArgumentException(
                    "security.txt recusado: Expires a mais de 1 ano no futuro (RFC 9116 §2.5.5)");
        }

        StringBuilder out = new StringBuilder();
        out.append("Contact: ").append(policy.contactUri()).append('\n');
        out.append("Expires: ").append(DateTimeFormatter.ISO_INSTANT.format(expiresAt)).append('\n');
        out.append("Policy: ").append(policy.policyUrl()).append('\n');
        if (policy.canonicalUrl() != null) {
            out.append("Canonical: ").append(policy.canonicalUrl()).append('\n');
        }
        out.append("Preferred-Languages: pt-BR\n");
        return out.toString();
    }
}
