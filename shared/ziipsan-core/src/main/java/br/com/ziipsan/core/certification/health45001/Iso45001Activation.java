package br.com.ziipsan.core.certification.health45001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativação do pacote ISO 45001 (ED-71; ADR-0041) no módulo
 * {@code certification} (core, {@code shared/ziipsan-core}), ao lado do
 * núcleo da P1.32 e dos pacotes irmãos {@code quality9001} (ED-69) e
 * {@code infosec27001} (ED-73).
 *
 * <p>A ativação em si NÃO é desta classe: ligar/desligar norma por tenant,
 * com escopo declarado, edição e aceite, é o núcleo de certificações da
 * P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso45001.enabled} (BOOLEAN, classe d, desligado por padrão —
 * core V3), {@code cert.iso45001.edition} (ENUM com ALLOWED_VALUES
 * {@code ["2018"]} e padrão {@code "2018"} — core V3) e pelo escopo
 * declarado {@code cert.scope_statement} (core V3). Esta classe é a régua
 * em memória que as demais classes deste pacote recebem como COMPONENTE
 * (padrão reforçado da ED-73, correção D1): sem um objeto de ativação
 * LIGADA não há construção possível de registro da norma, por fábrica ou
 * por construtor canônico. Com a norma desligada depois, os registros já
 * criados NÃO são apagados (o desligamento é do núcleo,
 * {@code cert.deactivation_keeps_records} classe a travado em
 * {@code true}, e este pacote não tem operação de exclusão — ver o SQL
 * pendente da ED-71, sem DELETE).
 *
 * <p>Edição-alvo (aceite da ED-71 e catálogo de parâmetros do core V3):
 * ISO 45001:2018 é a ÚNICA edição do parâmetro. Declarar outra edição é
 * recusado — as edições válidas são as do parâmetro, não uma lista
 * inventada aqui. Nova edição, quando publicada e incorporada ao
 * parâmetro, entra pela comparação de cláusulas de
 * {@link OhsClauseAssessment#compareEditions}.
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041 e o ciclo
 * PROPOSED → APPROVED → ACTIVE da V21_1): com a norma ligada,
 * {@code proposedBy} e {@code acceptedBy} são obrigatórios e distintos.
 */
public record Iso45001Activation(boolean enabled, String edition, String scopeStatement,
                                 UUID proposedBy, UUID acceptedBy) {

    /** Parâmetro do core V3 que liga a norma (classe d, padrão desligado). */
    public static final String ENABLED_PARAM = "cert.iso45001.enabled";
    /** Parâmetro do core V3 com a edição em uso (ENUM, ALLOWED_VALUES ["2018"], padrão "2018"). */
    public static final String EDITION_PARAM = "cert.iso45001.edition";
    /** Parâmetro do core V3 com o escopo declarado da ativação. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Edição-alvo única do aceite da ED-71 (ISO 45001:2018). */
    public static final String TARGET_EDITION = "2018";

    public Iso45001Activation {
        if (enabled) {
            if (!TARGET_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edição da ISO 45001 deve ser 2018 "
                        + "(edição-alvo), conforme " + EDITION_PARAM + ": " + edition);
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

    /** Ativação válida da norma, nos termos do aceite (edição-alvo 2018). */
    public static Iso45001Activation enabled(String scopeStatement, UUID proposedBy,
                                             UUID acceptedBy) {
        return new Iso45001Activation(true, TARGET_EDITION, scopeStatement, proposedBy, acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes são preservados. */
    public static Iso45001Activation disabled() {
        return new Iso45001Activation(false, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO 45001 com a
     * norma desligada é recusado. Desligar NÃO apaga registro — esta
     * porta só barra NASCIMENTO; leitura e preservação são do núcleo
     * (V21_1) e da migration pendente desta tarefa, que não tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO 45001 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma não nasce com a norma desligada");
        }
    }
}
