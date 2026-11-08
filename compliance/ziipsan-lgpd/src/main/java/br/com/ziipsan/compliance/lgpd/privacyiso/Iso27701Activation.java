package br.com.ziipsan.compliance.lgpd.privacyiso;

import java.util.Objects;

/**
 * Ativação do pacote ISO/IEC 27701 (ED-74; ADR-0041) — SÓ o que a norma pede
 * além da LGPD. Direitos do titular, registro de operações de tratamento e
 * canal do titular são obrigação de todo tenant e ficam na P3.40
 * ({@code privacy.PrivacyAvailability}: sempre ativo, sem pacote nem flag);
 * incidente com dado pessoal e modelo de RIPD ficam na P3.10. Nada neste
 * pacote liga, desliga ou condiciona a P3.40.
 *
 * <p>A ativação em si NÃO é desta classe: ligar/desligar norma por tenant,
 * com escopo declarado, edição e aceite por pessoas diferentes, é o núcleo
 * de certificações da P1.32 ({@code CertificationService} no core, tabelas
 * {@code cert_standard_activation} da V21_1), parametrizado por
 * {@code cert.iso27701.enabled} (classe d, desligado por padrão — core V3) e
 * {@code cert.iso27701.edition} (ENUM com ALLOWED_VALUES ["2019", "2025"] —
 * core V3). Esta classe é a régua em memória que as demais classes deste
 * pacote recebem para decidir se um registro DA 27701 pode nascer: sem a
 * norma ligada, nenhum registro do pacote nasce; com a norma desligada
 * depois, os registros já criados NÃO são apagados (o desligamento é do
 * núcleo, {@code cert.deactivation_keeps_records} classe a travado em true,
 * e este pacote não tem operação de exclusão).
 *
 * <p>Edições (aceite da ED-74 e catálogo de parâmetros do core V3):
 * ISO/IEC 27701:2025 é a edição-alvo; a de 2019 só em transição. Declarar
 * 2019 como alvo, ou 2025 como transição, é recusado — a incoerência não
 * pode virar registro. Qualquer outra edição é recusada: as edições válidas
 * são as do parâmetro, não uma lista inventada aqui.
 *
 * <p>Escopos: com a norma ligada, dois textos são obrigatórios e distintos —
 * o escopo declarado da ativação ({@code cert.scope_statement} / coluna
 * {@code scope_statement} da V21_1) e o escopo do sistema de gestão da
 * privacidade da informação (SGPI/PIMS) desta tarefa: quais tratamentos,
 * unidades e fronteiras o sistema de gestão cobre. Sem o segundo, o pacote
 * não sabe sobre o que avaliar cláusulas — e escopo vazio não é declaração.
 */
public record Iso27701Activation(boolean enabled, String edition, EditionUse editionUse,
                                 String scopeStatement, String pimsScope) {

    /** Uso da edição: ALVO (a edição corrente do pacote) ou TRANSIÇÃO (a anterior, a caminho da alvo). */
    public enum EditionUse {
        TARGET, TRANSITION
    }

    /** Parâmetro do core V3 que liga a norma (classe d, padrão desligado). */
    public static final String ENABLED_PARAM = "cert.iso27701.enabled";
    /** Parâmetro do core V3 com a edição em uso (ENUM, ALLOWED_VALUES ["2019", "2025"]). */
    public static final String EDITION_PARAM = "cert.iso27701.edition";
    /** Edição-alvo do aceite da ED-74. */
    public static final String TARGET_EDITION = "2025";
    /** Edição anterior, aceita só em transição. */
    public static final String TRANSITION_EDITION = "2019";

    public Iso27701Activation {
        if (enabled) {
            if (!TARGET_EDITION.equals(edition) && !TRANSITION_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edição da ISO/IEC 27701 deve ser 2025 (alvo) "
                        + "ou 2019 (transição), conforme " + EDITION_PARAM + ": " + edition);
            }
            Objects.requireNonNull(editionUse, "editionUse é obrigatório com a norma ligada");
            if (TARGET_EDITION.equals(edition) && editionUse != EditionUse.TARGET) {
                throw new IllegalArgumentException("a edição 2025 é a edição-alvo: não pode ser "
                        + "declarada em transição");
            }
            if (TRANSITION_EDITION.equals(edition) && editionUse != EditionUse.TRANSITION) {
                throw new IllegalArgumentException("a edição 2019 só é aceita em transição "
                        + "para a 2025 (edição-alvo)");
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado da ativação, "
                        + "cert.scope_statement) é obrigatório com a norma ligada");
            }
            if (pimsScope == null || pimsScope.isBlank()) {
                throw new IllegalArgumentException("pimsScope (escopo do sistema de gestão da "
                        + "privacidade da informação) é obrigatório com a norma ligada");
            }
        }
    }

    /** Ativação válida da norma, nos termos do aceite (edição-alvo ou transição declarada). */
    public static Iso27701Activation enabled(String edition, EditionUse editionUse,
                                             String scopeStatement, String pimsScope) {
        return new Iso27701Activation(true, edition, editionUse, scopeStatement, pimsScope);
    }

    /** Norma desligada: nenhum registro do pacote nasce; os existentes são preservados. */
    public static Iso27701Activation disabled() {
        return new Iso27701Activation(false, null, null, null, null);
    }

    /**
     * Porta das demais classes do pacote: registro da 27701 com a norma
     * desligada é recusado. Desligar NÃO apaga registro — esta porta só
     * barra NASCIMENTO; leitura e preservação são do núcleo (V21_1) e da
     * migration pendente desta tarefa, que não tem DELETE.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO/IEC 27701 desligado para o tenant "
                    + "(" + ENABLED_PARAM + "): registro da norma não nasce com a norma desligada");
        }
    }
}
