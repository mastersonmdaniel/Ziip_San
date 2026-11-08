package br.com.ziipsan.core.certification.env14001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativacao do pacote ISO 14001 (ED-70; ADR-0041) no modulo
 * {@code certification} (core, {@code shared/ziipsan-core}), ao lado do
 * nucleo da P1.32 e dos pacotes irmaos {@code quality9001} (ED-69),
 * {@code infosec27001} (ED-73) e {@code health45001} (ED-71).
 *
 * <p>A ativacao em si NAO e desta classe: ligar/desligar norma por
 * tenant, com escopo declarado, edicao e aceite, e o nucleo de
 * certificacoes da P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso14001.enabled} (BOOLEAN, classe d, desligado por
 * padrao — core V3), {@code cert.iso14001.edition} (ENUM com
 * ALLOWED_VALUES {@code ["2015", "2026"]} — core V3) e pelo escopo
 * declarado {@code cert.scope_statement} (core V3). Esta classe e a
 * regua em memoria que as demais classes deste pacote recebem como
 * COMPONENTE (padrao reforcado da ED-73, correcao D1): sem um objeto
 * de ativacao LIGADA nao ha construcao possivel de registro da norma,
 * por fabrica ou por construtor canonico. Com a norma desligada
 * depois, os registros ja criados NAO sao apagados (o desligamento e
 * do nucleo, {@code cert.deactivation_keeps_records} classe a travado
 * em {@code true}, e este pacote nao tem operacao de exclusao — ver o
 * SQL pendente da ED-70, sem DELETE).
 *
 * <p>Edicao (aceite da ED-70 e catalogo de parametros do core V3,
 * V3__tenant_parameters_catalog.sql linhas 125/136/223-224):
 * ISO 14001:2026 e a edicao-ALVO; a edicao de 2015 so vale EM
 * TRANSICAO — declarar {@code "2015"} sem a marca de transicao e
 * recusado, e declarar qualquer outra edicao e recusado. As edicoes
 * validas sao as do parametro, nao uma lista inventada aqui.
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041 e o ciclo
 * PROPOSED -&gt; APPROVED -&gt; ACTIVE da V21_1): com a norma ligada,
 * {@code proposedBy} e {@code acceptedBy} sao obrigatorios e
 * distintos.
 */
public record Iso14001Activation(boolean enabled, String edition, boolean transition,
                                 String scopeStatement, UUID proposedBy, UUID acceptedBy) {

    /** Parametro do core V3 que liga a norma (classe d, padrao desligado). */
    public static final String ENABLED_PARAM = "cert.iso14001.enabled";
    /** Parametro do core V3 com a edicao em uso (ENUM, ALLOWED_VALUES ["2015", "2026"]). */
    public static final String EDITION_PARAM = "cert.iso14001.edition";
    /** Parametro do core V3 com o escopo declarado da ativacao. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Edicao-alvo do aceite da ED-70 (ISO 14001:2026). */
    public static final String TARGET_EDITION = "2026";
    /** Edicao anterior, admitida SO em transicao (aceite da ED-70; core V3). */
    public static final String TRANSITION_EDITION = "2015";

    public Iso14001Activation {
        if (transition && !TRANSITION_EDITION.equals(edition)) {
            // Guarda semantica (correcao D2 da revisao da ED-70): a marca
            // de transicao so tem sentido na edicao anterior (2015); com a
            // edicao-alvo 2026 — ou sem edicao — ela e incoerente.
            throw new IllegalArgumentException("transition=true so vale para a edicao de "
                    + "transicao (2015): a edicao-alvo 2026 nao e transicao (aceite da ED-70)");
        }
        if (enabled) {
            if (!TARGET_EDITION.equals(edition) && !TRANSITION_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edicao da ISO 14001 deve ser 2026 "
                        + "(edicao-alvo) ou 2015 (so em transicao), conforme "
                        + EDITION_PARAM + ": " + edition);
            }
            if (TRANSITION_EDITION.equals(edition) && !transition) {
                throw new IllegalArgumentException("a edicao de 2015 da ISO 14001 so vale "
                        + "em transicao: declarar 2015 exige transition=true (aceite da ED-70)");
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado da "
                        + "ativacao, " + SCOPE_PARAM + ") e obrigatorio com a norma ligada");
            }
            scopeStatement = scopeStatement.trim();
            Objects.requireNonNull(proposedBy, "proposedBy e obrigatorio com a norma ligada "
                    + "(quem propos a ativacao)");
            Objects.requireNonNull(acceptedBy, "acceptedBy e obrigatorio com a norma ligada "
                    + "(quem aceitou a ativacao)");
            if (proposedBy.equals(acceptedBy)) {
                throw new IllegalArgumentException("proposta e aceite da ativacao devem ser de "
                        + "pessoas diferentes (ADR-0041): proposedBy == acceptedBy");
            }
        }
    }

    /** Ativacao valida na edicao-alvo (ISO 14001:2026, sem transicao). */
    public static Iso14001Activation enabledTarget(String scopeStatement, UUID proposedBy,
                                                   UUID acceptedBy) {
        return new Iso14001Activation(true, TARGET_EDITION, false, scopeStatement,
                proposedBy, acceptedBy);
    }

    /** Ativacao valida na edicao de 2015, SOMENTE em transicao declarada. */
    public static Iso14001Activation enabledInTransition(String scopeStatement, UUID proposedBy,
                                                         UUID acceptedBy) {
        return new Iso14001Activation(true, TRANSITION_EDITION, true, scopeStatement,
                proposedBy, acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes sao preservados. */
    public static Iso14001Activation disabled() {
        return new Iso14001Activation(false, null, false, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO 14001 com a
     * norma desligada e recusado. Desligar NAO apaga registro — esta
     * porta so barra NASCIMENTO; leitura e preservacao sao do nucleo
     * (V21_1) e da migration pendente desta tarefa, que nao tem DELETE.
     *
     * <p>Excecao expressa do aceite: a LEITURA do aspecto do lodo
     * ({@code SludgeAspectView}, sobre a ED-105) NAO passa por esta
     * porta — a obrigacao do lodo e REGULATORY e vale com a norma
     * ligada ou desligada.</p>
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO 14001 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma nao nasce com a norma desligada");
        }
    }
}
