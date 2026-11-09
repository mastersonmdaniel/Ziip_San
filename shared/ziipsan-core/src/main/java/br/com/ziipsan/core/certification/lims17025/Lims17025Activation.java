package br.com.ziipsan.core.certification.lims17025;

import java.util.Objects;
import java.util.UUID;

/**
 * Regua de ativacao do pacote ISO/IEC 17025 (ED-51; ADR-0041), no mesmo
 * desenho da regua da ED-69 ({@code Iso9001Activation}).
 *
 * <p>A ativacao em si NAO e desta classe: ligar/desligar norma por
 * tenant, com escopo declarado, edicao e aceite, e o nucleo de
 * certificacoes da P1.32 ({@code CertificationService} no core, tabela
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso17025.enabled} (classe d, desligado por padrao — core
 * V3), {@code cert.iso17025.edition} (ENUM; as edicoes vivem no
 * catalogo da P1.32, core V9_8 — core V3) e pelo escopo declarado
 * {@code cert.scope_statement} (core V3). Esta classe e a regua em
 * memoria que as demais classes deste pacote recebem para decidir se
 * um registro DA ISO/IEC 17025 pode nascer: sem a norma ligada, nenhum
 * registro do pacote nasce; com a norma desligada depois, os registros
 * ja criados NAO sao apagados (o desligamento e do nucleo,
 * {@code cert.deactivation_keeps_records} classe a travado em
 * {@code true}, e este pacote nao tem operacao de exclusao — ver o SQL
 * pendente da ED-51, sem DELETE).
 *
 * <p>Edicao: o aceite da ED-51 fixa expressamente "ISO/IEC 17025:2017
 * ativa no modulo de certificacoes" — por isso, com a norma ligada, a
 * unica edicao aceita por esta regua e {@code 2017}. Qualquer outra
 * edicao declarada e recusada aqui; uma edicao futura so entra quando
 * o aceite/catalogo a nomear (nada de lista inventada).
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041 e o ciclo
 * PROPOSED -&gt; APPROVED -&gt; ACTIVE da V21_1, onde quem propoe nao
 * aprova): com a norma ligada, {@code proposedBy} e {@code acceptedBy}
 * sao obrigatorios e distintos.
 */
public record Lims17025Activation(boolean enabled, String edition, String scopeStatement,
                                  UUID proposedBy, UUID acceptedBy) {

    /** Parametro do core V3 que liga a norma (classe d, padrao desligado). */
    public static final String ENABLED_PARAM = "cert.iso17025.enabled";
    /** Parametro do core V3 com a edicao em uso (edicoes no catalogo da P1.32, core V9_8). */
    public static final String EDITION_PARAM = "cert.iso17025.edition";
    /** Parametro do core V3 com o escopo declarado da ativacao. */
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Norma apoiada por este pacote (aceite da ED-51). */
    public static final String STANDARD = "ISO/IEC 17025";
    /** Edicao fixada pelo aceite da ED-51: "ISO/IEC 17025:2017 ativa". */
    public static final String REQUIRED_EDITION = "2017";

    public Lims17025Activation {
        if (enabled) {
            if (!REQUIRED_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edicao da ISO/IEC 17025 deve ser "
                        + REQUIRED_EDITION + " (edicao fixada no aceite da ED-51, via "
                        + EDITION_PARAM + "): " + edition);
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado da ativacao, "
                        + SCOPE_PARAM + ") e obrigatorio com a norma ligada");
            }
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

    /** Ativacao valida da norma, nos termos do aceite (edicao 2017, escopo declarado). */
    public static Lims17025Activation enabled(String scopeStatement, UUID proposedBy, UUID acceptedBy) {
        return new Lims17025Activation(true, REQUIRED_EDITION, scopeStatement, proposedBy, acceptedBy);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes sao preservados. */
    public static Lims17025Activation disabled() {
        return new Lims17025Activation(false, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da ISO/IEC 17025 com
     * a norma desligada e recusado. Desligar NAO apaga registro — esta
     * porta so barra NASCIMENTO; leitura e preservacao sao do nucleo
     * (V21_1) e do SQL pendente desta tarefa, que nao tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO/IEC 17025 desligado para o tenant ("
                    + ENABLED_PARAM + "): registro da norma nao nasce com a norma desligada");
        }
    }
}
