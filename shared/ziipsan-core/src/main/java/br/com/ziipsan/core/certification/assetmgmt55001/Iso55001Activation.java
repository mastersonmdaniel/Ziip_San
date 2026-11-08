package br.com.ziipsan.core.certification.assetmgmt55001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativação do pacote ISO 55001 (ED-72; ADR-0041) no módulo
 * {@code certification} (core, {@code shared/ziipsan-core}), ao lado dos
 * pacotes irmãos {@code infosec27001} (ED-73) e {@code quality9001} (ED-69).
 *
 * <p>A ativação em si NÃO é desta classe: ligar/desligar norma por tenant,
 * com escopo declarado, edição e aceite, é o núcleo de certificações da
 * P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso55001.enabled} (classe d, desligado por padrão — core V3),
 * {@code cert.iso55001.edition} (ENUM com ALLOWED_VALUES {@code ["2024"]} e
 * padrão {@code "2024"} — core V3) e pelo escopo declarado
 * {@code cert.scope_statement} (core V3). Esta classe é a régua em memória
 * que as demais classes deste pacote recebem para decidir se um registro
 * DA ISO 55001 pode nascer: sem a norma ligada, nenhum registro do pacote
 * nasce; com a norma desligada depois, os registros já criados NÃO são
 * apagados (o desligamento é do núcleo,
 * {@code cert.deactivation_keeps_records} classe a travado em {@code true},
 * e este pacote não tem operação de exclusão — ver o SQL pendente da
 * ED-72, sem DELETE).
 *
 * <p>Edição-alvo (aceite da ED-72 e catálogo de parâmetros do core V3):
 * ISO 55001:2024. A edição do parâmetro é só {@code "2024"}: declarar outra
 * edição é recusado — as edições válidas são as do parâmetro, não uma
 * lista inventada aqui.
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041 e o ciclo
 * PROPOSED → APPROVED → ACTIVE da V21_1): com a norma ligada,
 * {@code proposedBy} e {@code acceptedBy} são obrigatórios e distintos.
 */
public record Iso55001Activation(boolean enabled, String edition,
                                 String scopeStatement, UUID proposedBy, UUID acceptedBy) {

    /** Parâmetro do core V3 que liga a norma (classe d, padrão desligado). */
    public static final String ENABLED_PARAM = "cert.iso55001.enabled";
    /** Parâmetro do core V3 com a edição em uso (ENUM, ALLOWED_VALUES ["2024"], padrão "2024"). */
    public static final String EDITION_PARAM = "cert.iso55001.edition";
    /** Parâmetro do core V3 com o escopo declarado da ativação. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Edição-alvo única do aceite da ED-72 (ISO 55001:2024). */
    public static final String TARGET_EDITION = "2024";

    public Iso55001Activation {
        if (enabled) {
            if (!TARGET_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edição da ISO 55001 deve ser 2024 "
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

    /** Ativação válida da norma, nos termos do aceite (edição-alvo 2024). */
    public static Iso55001Activation enabled(String scopeStatement, UUID proposedBy,
                                             UUID acceptedBy) {
        return new Iso55001Activation(true, TARGET_EDITION, scopeStatement,
                proposedBy, acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes são preservados. */
    public static Iso55001Activation disabled() {
        return new Iso55001Activation(false, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO 55001 com a
     * norma desligada é recusado. Desligar NÃO apaga registro — esta porta
     * só barra NASCIMENTO; leitura e preservação são do núcleo (V21_1) e da
     * migration pendente desta tarefa, que não tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO 55001 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma não nasce com a norma desligada");
        }
    }
}
