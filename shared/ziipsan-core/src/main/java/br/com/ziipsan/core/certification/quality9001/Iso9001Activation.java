package br.com.ziipsan.core.certification.quality9001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativação do pacote ISO 9001 (ED-69; ADR-0041) no módulo
 * {@code certification} (core, {@code shared/ziipsan-core}).
 *
 * <p>A ativação em si NÃO é desta classe: ligar/desligar norma por tenant,
 * com escopo declarado, edição e aceite, é o núcleo de certificações da
 * P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso9001.enabled} (classe d, desligado por padrão — core V3),
 * {@code cert.iso9001.edition} (ENUM com ALLOWED_VALUES
 * {@code ["2015", "2026"]} — core V3) e pelo escopo declarado
 * {@code cert.scope_statement} (core V3). Esta classe é a régua em memória
 * que as demais classes deste pacote recebem para decidir se um registro
 * DA ISO 9001 pode nascer: sem a norma ligada, nenhum registro do pacote
 * nasce; com a norma desligada depois, os registros já criados NÃO são
 * apagados (o desligamento é do núcleo,
 * {@code cert.deactivation_keeps_records} classe a travado em
 * {@code true}, e este pacote não tem operação de exclusão — ver o SQL
 * pendente da ED-69, sem DELETE).
 *
 * <p>Proposta e aceite por pessoas diferentes (aceite da ED-69; ADR-0041
 * e o ciclo PROPOSED → APPROVED → ACTIVE da V21_1, onde quem propõe não
 * aprova): com a norma ligada, {@code proposedBy} e {@code acceptedBy}
 * são obrigatórios e distintos. Declarar o aceite pela mesma pessoa que
 * propôs é recusado — a incoerência não pode virar registro.
 *
 * <p>Edições (aceite da ED-69 e catálogo de parâmetros do core V3):
 * ISO 9001:2026 é a edição-alvo; a de 2015 só em transição. Declarar 2015
 * como alvo, ou 2026 como transição, é recusado. Qualquer outra edição é
 * recusada: as edições válidas são as do parâmetro, não uma lista
 * inventada aqui.
 */
public record Iso9001Activation(boolean enabled, String edition, EditionUse editionUse,
                                String scopeStatement, UUID proposedBy, UUID acceptedBy) {

    /** Uso da edição: ALVO (a edição corrente do pacote) ou TRANSIÇÃO (a anterior, a caminho da alvo). */
    public enum EditionUse {
        TARGET, TRANSITION
    }

    /** Parâmetro do core V3 que liga a norma (classe d, padrão desligado). */
    public static final String ENABLED_PARAM = "cert.iso9001.enabled";
    /** Parâmetro do core V3 com a edição em uso (ENUM, ALLOWED_VALUES ["2015", "2026"]). */
    public static final String EDITION_PARAM = "cert.iso9001.edition";
    /** Parâmetro do core V3 com o escopo declarado da ativação. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Edição-alvo do aceite da ED-69. */
    public static final String TARGET_EDITION = "2026";
    /** Edição anterior, aceita só em transição. */
    public static final String TRANSITION_EDITION = "2015";

    public Iso9001Activation {
        if (enabled) {
            if (!TARGET_EDITION.equals(edition) && !TRANSITION_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edição da ISO 9001 deve ser 2026 (alvo) "
                        + "ou 2015 (transição), conforme " + EDITION_PARAM + ": " + edition);
            }
            Objects.requireNonNull(editionUse, "editionUse é obrigatório com a norma ligada");
            if (TARGET_EDITION.equals(edition) && editionUse != EditionUse.TARGET) {
                throw new IllegalArgumentException("a edição 2026 é a edição-alvo: não pode ser "
                        + "declarada em transição");
            }
            if (TRANSITION_EDITION.equals(edition) && editionUse != EditionUse.TRANSITION) {
                throw new IllegalArgumentException("a edição 2015 só é aceita em transição "
                        + "para a 2026 (edição-alvo)");
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado da ativação, "
                        + SCOPE_PARAM + ") é obrigatório com a norma ligada");
            }
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

    /** Ativação válida da norma, nos termos do aceite (edição-alvo ou transição declarada). */
    public static Iso9001Activation enabled(String edition, EditionUse editionUse,
                                            String scopeStatement, UUID proposedBy,
                                            UUID acceptedBy) {
        return new Iso9001Activation(true, edition, editionUse, scopeStatement, proposedBy,
                acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes são preservados. */
    public static Iso9001Activation disabled() {
        return new Iso9001Activation(false, null, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO 9001 com a norma
     * desligada é recusado. Desligar NÃO apaga registro — esta porta só
     * barra NASCIMENTO; leitura e preservação são do núcleo (V21_1) e da
     * migration pendente desta tarefa, que não tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO 9001 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma não nasce com a norma desligada");
        }
    }
}
