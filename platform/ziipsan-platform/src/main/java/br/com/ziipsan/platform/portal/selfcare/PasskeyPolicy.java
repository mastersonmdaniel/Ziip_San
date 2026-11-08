package br.com.ziipsan.platform.portal.selfcare;

import java.util.UUID;

/**
 * Passkey no portal (ED-38) pela politica da ED-06: a ED-06 definiu a
 * chave {@code portal.passkey_enabled} (fabrica false) e declarou que
 * "quem o aplica ao portal e a ED-38" — esta classe e a aplicacao.
 *
 * <p>Regras: passkey desligada na politica = cadastro e uso recusados;
 * ligada, a passkey vale como METODO DE ENTRADA e como MFA (WebAuthn com
 * verificacao de usuario obrigatoria — sem UV, a credencial nao conta
 * como MFA). Quem tem passkey ativa e tratado como "com MFA" para o
 * minimo de senha da ED-06 (piso de 8 com MFA; 15 como fator unico) —
 * a conta continua podendo entrar por senha local da P3.3.
 */
public final class PasskeyPolicy {

    public enum Use { LOGIN, MFA }

    public record Credential(boolean userVerificationPerformed, boolean active) { }

    private final SelfCareParameters params;

    public PasskeyPolicy(SelfCareParameters params) {
        if (params == null) {
            throw new IllegalArgumentException("parametros sao obrigatorios");
        }
        this.params = params;
    }

    public boolean enrollmentAllowed(UUID tenantId) {
        return params.passkeyEnabled(tenantId);
    }

    public void assertUsable(UUID tenantId, Credential credential, Use use) {
        if (use == null || credential == null) {
            throw new IllegalArgumentException("uso e credencial sao obrigatorios");
        }
        if (!params.passkeyEnabled(tenantId)) {
            throw new IllegalStateException("portal.passkey_enabled desligado: passkey recusada (politica da ED-06)");
        }
        if (!credential.active()) {
            throw new IllegalStateException("credencial passkey inativa");
        }
        if (!credential.userVerificationPerformed()) {
            throw new IllegalStateException("passkey sem verificacao de usuario nao vale como entrada/MFA");
        }
    }

    /** Passkey ativa e verificada = usuario com MFA para o minimo de senha da ED-06. */
    public boolean countsAsMfa(Credential credential) {
        return credential != null && credential.active() && credential.userVerificationPerformed();
    }
}
