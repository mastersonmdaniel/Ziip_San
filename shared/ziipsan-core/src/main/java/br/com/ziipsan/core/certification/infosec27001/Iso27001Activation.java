package br.com.ziipsan.core.certification.infosec27001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativação do pacote ISO/IEC 27001 (ED-73; ADR-0041) no módulo
 * {@code certification} (core, {@code shared/ziipsan-core}), ao lado do
 * núcleo da P1.32 e dos pacotes irmãos {@code quality9001} (ED-69).
 *
 * <p>A ativação em si NÃO é desta classe: ligar/desligar norma por tenant,
 * com escopo declarado, edição e aceite, é o núcleo de certificações da
 * P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso27001.enabled} (classe d, desligado por padrão — core V3),
 * {@code cert.iso27001.edition} (ENUM com ALLOWED_VALUES {@code ["2022"]} e
 * padrão {@code "2022"} — core V3) e pelo escopo declarado
 * {@code cert.scope_statement} (core V3). Esta classe é a régua em memória
 * que as demais classes deste pacote recebem para decidir se um registro
 * DA ISO/IEC 27001 pode nascer: sem a norma ligada, nenhum registro do
 * pacote nasce; com a norma desligada depois, os registros já criados NÃO
 * são apagados (o desligamento é do núcleo,
 * {@code cert.deactivation_keeps_records} classe a travado em {@code true},
 * e este pacote não tem operação de exclusão — ver o SQL pendente da
 * ED-73, sem DELETE).
 *
 * <p>Edição-alvo (aceite da ED-73 e catálogo de parâmetros do core V3):
 * ISO/IEC 27001:2022 com a Emenda 1:2024. A edição do parâmetro é só
 * {@code "2022"} e a emenda declarada é só {@code "1:2024"}: declarar outra
 * edição ou outra emenda é recusado — as edições válidas são as do
 * parâmetro, não uma lista inventada aqui. Não há edição anterior em
 * transição neste pacote: o aceite fixa a edição-alvo única.
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041 e o ciclo
 * PROPOSED → APPROVED → ACTIVE da V21_1): com a norma ligada,
 * {@code proposedBy} e {@code acceptedBy} são obrigatórios e distintos.
 */
public record Iso27001Activation(boolean enabled, String edition, String amendment,
                                 String scopeStatement, UUID proposedBy, UUID acceptedBy) {

    /** Parâmetro do core V3 que liga a norma (classe d, padrão desligado). */
    public static final String ENABLED_PARAM = "cert.iso27001.enabled";
    /** Parâmetro do core V3 com a edição em uso (ENUM, ALLOWED_VALUES ["2022"], padrão "2022"). */
    public static final String EDITION_PARAM = "cert.iso27001.edition";
    /** Parâmetro do core V3 com o escopo declarado da ativação. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Edição-alvo única do aceite da ED-73 (ISO/IEC 27001:2022). */
    public static final String TARGET_EDITION = "2022";
    /** Emenda que acompanha a edição-alvo (Emenda 1:2024, citada no core V3). */
    public static final String TARGET_AMENDMENT = "1:2024";

    public Iso27001Activation {
        if (enabled) {
            if (!TARGET_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edição da ISO/IEC 27001 deve ser 2022 "
                        + "(edição-alvo), conforme " + EDITION_PARAM + ": " + edition);
            }
            if (!TARGET_AMENDMENT.equals(amendment)) {
                throw new IllegalArgumentException("a edição-alvo é a ISO/IEC 27001:2022 com a "
                        + "Emenda 1:2024: amendment deve ser \"" + TARGET_AMENDMENT + "\": "
                        + amendment);
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado da ativação, "
                        + SCOPE_PARAM + ") é obrigatório com a norma ligada");
            }
            scopeStatement = scopeStatement.trim();
            Objects.requireNonNull(proposedBy, "proposedBy é obrigatório com a norma ligada "
                    + "(quem propôs a ativação)");
            Objects.requireNonNull(acceptedBy, "acceptedBy é obrigatório com a norma ligada "
                    + "(quem aceitou a ativação)");
            if (proposedBy.equals(acceptedBy)) {
                throw new IllegalArgumentException("proposta e aceite da ativação devem ser de "
                        + "pessoas diferentes (ADR-0041): proposedBy == acceptedBy");
            }
        }
    }

    /** Ativação válida da norma, nos termos do aceite (edição-alvo 2022 + Emenda 1:2024). */
    public static Iso27001Activation enabled(String scopeStatement, UUID proposedBy,
                                             UUID acceptedBy) {
        return new Iso27001Activation(true, TARGET_EDITION, TARGET_AMENDMENT, scopeStatement,
                proposedBy, acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes são preservados. */
    public static Iso27001Activation disabled() {
        return new Iso27001Activation(false, null, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO/IEC 27001 com a
     * norma desligada é recusado. Desligar NÃO apaga registro — esta porta
     * só barra NASCIMENTO; leitura e preservação são do núcleo (V21_1) e da
     * migration pendente desta tarefa, que não tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO/IEC 27001 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma não nasce com a norma desligada");
        }
    }
}
