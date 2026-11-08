package br.com.ziipsan.core.certification.water46001;

import java.util.Objects;
import java.util.UUID;

/**
 * Ativacao do pacote ISO 46001 (ED-81; ADR-0041) no modulo
 * {@code certification} (core, {@code shared/ziipsan-core}), ao lado do
 * nucleo da P1.32 e dos pacotes irmaos (ED-69..ED-77, ED-80, ED-82).
 *
 * <p>A ativacao em si NAO e desta classe: ligar/desligar norma por
 * tenant e do nucleo da P1.32 (V21_1), parametrizado por
 * {@code cert.iso46001.enabled} (BOOLEAN, classe d, desligado por
 * padrao — core V3), {@code cert.iso46001.edition} (ENUM, classe d,
 * SEM padrao no core V3: as edicoes vivem no catalogo da P1.32) e
 * {@code cert.scope_statement} (core V3). Esta classe e a regua em
 * memoria que as demais classes deste pacote recebem como COMPONENTE
 * (padrao ED-71/ED-73): sem ativacao LIGADA nao nasce registro.
 *
 * <p>Edicao adotavel: SOMENTE a ISO 46001:2019 — a edicao publicada
 * vigente, que o aceite declara EM REVISAO. O projeto da edicao 2 e
 * apenas ACOMPANHADO no catalogo ({@link Iso46001Catalog}): nao e
 * adotavel e NENHUM requisito dele e prescrito neste pacote, porque
 * ele nao existe publicado.
 *
 * <p>Escopo de INSTALACAO (aceite da ED-81): alem do escopo declarado,
 * a ativacao nomeia a instalacao (referencia declarada da instalacao
 * do prestador — ETA/ETE/escritorio operacional, pelo codigo que a
 * fiacao resolve sobre o cadastro de ativos/instalacoes). Registro
 * fora da instalacao do escopo e recusado.
 *
 * <p>Proposta e aceite por pessoas diferentes (ADR-0041).
 * Desligar nao apaga registro: esta regua so barra NASCIMENTO.
 */
public record Iso46001Activation(boolean enabled, String edition, String scopeStatement,
                                 String installationScopeRef, UUID proposedBy, UUID acceptedBy) {

    public static final String ENABLED_PARAM = "cert.iso46001.enabled";
    public static final String EDITION_PARAM = "cert.iso46001.edition";
    public static final String SCOPE_PARAM = "cert.scope_statement";
    /** Unica edicao publicada adotavel (aceite ED-81: ISO 46001:2019, em revisao). */
    public static final String CURRENT_EDITION = "2019";

    public Iso46001Activation {
        if (enabled) {
            if (!CURRENT_EDITION.equals(edition)) {
                throw new IllegalArgumentException("edicao da ISO 46001 deve ser 2019 "
                        + "(unica publicada e adotavel; o projeto da edicao 2 e so acompanhado "
                        + "no catalogo, nao adotavel), conforme " + EDITION_PARAM + ": " + edition);
            }
            if (scopeStatement == null || scopeStatement.isBlank()) {
                throw new IllegalArgumentException("scopeStatement (escopo declarado, "
                        + SCOPE_PARAM + ") e obrigatorio com a norma ligada");
            }
            if (installationScopeRef == null || installationScopeRef.isBlank()) {
                throw new IllegalArgumentException("installationScopeRef (escopo de instalacao "
                        + "do aceite da ED-81) e obrigatorio com a norma ligada");
            }
            scopeStatement = scopeStatement.trim();
            installationScopeRef = installationScopeRef.trim();
            Objects.requireNonNull(proposedBy, "proposedBy e obrigatorio com a norma ligada");
            Objects.requireNonNull(acceptedBy, "acceptedBy e obrigatorio com a norma ligada");
            if (proposedBy.equals(acceptedBy)) {
                throw new IllegalArgumentException("proposta e aceite da ativacao devem ser de "
                        + "pessoas diferentes (ADR-0041): proposedBy == acceptedBy");
            }
        }
    }

    public static Iso46001Activation enabled(String scopeStatement, String installationScopeRef,
                                             UUID proposedBy, UUID acceptedBy) {
        return new Iso46001Activation(true, CURRENT_EDITION, scopeStatement,
                installationScopeRef, proposedBy, acceptedBy);
    }

    public static Iso46001Activation disabled() {
        return new Iso46001Activation(false, null, null, null, null, null);
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("pacote ISO 46001 desligado para o tenant ("
                    + ENABLED_PARAM + "): registro da norma nao nasce com a norma desligada");
        }
    }

    /** Escopo de instalacao: o registro so nasce para a instalacao ativada. */
    public void requireInstallationInScope(String installationRef) {
        requireEnabled();
        if (installationRef == null || !installationScopeRef.equals(installationRef.trim())) {
            throw new IllegalArgumentException("instalacao fora do escopo de ativacao da "
                    + "ISO 46001 do tenant: " + installationRef
                    + " (escopo: " + installationScopeRef + ")");
        }
    }
}
