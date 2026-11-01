package br.com.ziipsan.platform.security;

import java.util.Set;

/**
 * MFA (P0.15): as constantes que amarram as três pontas — parâmetro do tenant, atributo
 * do usuário no Keycloak e gate da API. Fonte única no código; o script de sincronização
 * (scripts/sync-tenant-mfa.py) e o realm JSON espelham estes valores, e o teste de
 * estrutura do realm confere o espelho.
 */
public final class MfaPolicy {

    /** Chave do parâmetro em {@code public.tenant_settings} (valor JSONB booleano). Ausente = false. */
    public static final String PARAMETER_KEY = "security.mfa_required";

    /** Atributo do usuário no Keycloak, sincronizado a partir do parâmetro (valor "true"/"false"). */
    public static final String USER_ATTRIBUTE = "mfa_required";

    /** Usuário de plataforma (sem tenant): atributo e valor que marcam quem SEMPRE tem MFA. */
    public static final String PLATFORM_USER_ATTRIBUTE = "ziipsan_user_type";
    public static final String PLATFORM_USER_VALUE = "platform";

    /**
     * Valores de {@code amr} aceitos pelo gate: os reference values do fluxo do realm
     * (OTP -> "otp", WebAuthn -> "hwk", RFC 8176). Mudar um reference value no realm
     * sem mudar este conjunto quebra o login de plataforma — os dois andam juntos.
     */
    public static final Set<String> MFA_AMR_VALUES = Set.of("otp", "hwk");

    private MfaPolicy() {
    }
}
