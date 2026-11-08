package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Política de privacidade do tenant PUBLICADA E VERSIONADA no portal (aceite da P3.40):
 * o portal serve a versão publicada vigente; rascunho não é exibido ao titular, e
 * publicar uma versão nova supersede a anterior sem apagá-la (a prova do que foi
 * publicado em cada época fica). O texto da política é do TENANT (controlador) — esta
 * classe não traz texto jurídico nenhum, só a régua de publicação.
 *
 * <p>Espelho da tabela {@code lgpd_privacy_policies} da {@code V23_1}, cujo índice
 * parcial garante no banco no máximo UMA publicada por tenant.
 */
public record PrivacyPolicy(int version, String title, String body, Status status,
                            Instant publishedAt) {

    /** Situação da versão da política. */
    public enum Status {
        DRAFT, PUBLISHED, SUPERSEDED
    }

    public PrivacyPolicy {
        if (version < 1) {
            throw new IllegalArgumentException("version da política começa em 1");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title da política é obrigatório");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body da política é obrigatório: política vazia "
                    + "não é publicável — o texto é do tenant (controlador)");
        }
        Objects.requireNonNull(status, "status é obrigatório");
        if (status == Status.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("política PUBLISHED exige publishedAt");
        }
        if (status == Status.DRAFT && publishedAt != null) {
            throw new IllegalArgumentException("rascunho não tem publishedAt");
        }
    }

    /** Publica esta versão no instante dado (devolve cópia PUBLISHED; o record não muda). */
    public PrivacyPolicy publish(Instant when) {
        Objects.requireNonNull(when, "when é obrigatório");
        if (status == Status.SUPERSEDED) {
            throw new IllegalStateException("versão supersedida não é republicável: crie a "
                    + "versão seguinte a partir do rascunho vigente");
        }
        if (status == Status.PUBLISHED) {
            throw new IllegalStateException("versão " + version + " já está publicada desde "
                    + publishedAt + ": republicar reescreveria a data da prova de publicação");
        }
        return new PrivacyPolicy(version, title, body, Status.PUBLISHED, when);
    }

    /** Marca esta versão como supersedida (efeito de publicar uma mais nova). */
    public PrivacyPolicy supersede() {
        if (status != Status.PUBLISHED) {
            throw new IllegalStateException("só versão PUBLISHED é supersedida (atual: " + status + ")");
        }
        return new PrivacyPolicy(version, title, body, Status.SUPERSEDED, publishedAt);
    }

    /**
     * A política que o portal exibe: a PUBLISHED de maior versão.
     *
     * @throws IllegalStateException se nenhuma versão estiver publicada — o portal não
     *                               exibe rascunho nem inventa política
     */
    public static PrivacyPolicy currentForPortal(List<PrivacyPolicy> versions) {
        Objects.requireNonNull(versions, "versions é obrigatório");
        return versions.stream()
                .filter(p -> p.status() == Status.PUBLISHED)
                .max(Comparator.comparingInt(PrivacyPolicy::version))
                .orElseThrow(() -> new IllegalStateException("nenhuma política de privacidade "
                        + "publicada para o tenant: o portal não exibe rascunho"));
    }
}
