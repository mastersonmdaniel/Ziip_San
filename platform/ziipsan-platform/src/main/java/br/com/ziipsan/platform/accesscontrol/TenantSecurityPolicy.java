package br.com.ziipsan.platform.accesscontrol;

import java.util.Objects;

/**
 * Política de segurança do tenant lida pelo autenticador próprio no Keycloak
 * (ED-06, ADR-0040): UM realm só, e a política de CADA tenant vem dos
 * parâmetros do tenant do usuário que está autenticando — nunca de uma
 * configuração global do realm, que aplicaria a política de um tenant a outro.
 *
 * <p>Chaves (catálogo global de parâmetros da P0.19, registradas no SQL
 * pendente desta entrega):
 * <ul>
 *   <li>{@code sec.password_min_length} (INT) — tamanho mínimo da senha;</li>
 *   <li>{@code sec.password_max_age_days} (INT, nulo = sem expiração) —
 *       expiração periódica; DESLIGADA por padrão (NIST SP 800-63B-4);</li>
 *   <li>{@code sec.password_history} (INT) — quantas senhas anteriores não
 *       podem ser repetidas;</li>
 *   <li>{@code sec.lockout_attempts} (INT) — tentativas falhas até o bloqueio
 *       (o roteiro VISAN POC-05 usa 3: é VALOR do tenant no ensaio, não
 *       constante do código);</li>
 *   <li>{@code sec.user_inactivity_days} (INT, nulo = sem bloqueio por
 *       inatividade);</li>
 *   <li>{@code sec.password_composition_required} (BOOLEAN) — regra de
 *       composição (maiúscula/dígito/símbolo): contrária à recomendação do
 *       NIST; só vale com aceite registrado ({@link NistAcceptance}).
 *       CHAVE NOVA DERIVADA NESTA TAREFA (correção D6): a OPÇÃO de
 *       composição está no aceite da ED-06, mas esta chave não aparece
 *       verbatim na spec/ADR — segue o padrão {@code sec.*} das demais;</li>
 *   <li>{@code sec.sso_mode} / {@code sec.sso_enforced} / {@code sec.scim_enabled}
 *       — ver {@link TenantSsoConfig};</li>
 *   <li>{@code portal.passkey_enabled} (BOOLEAN) — passkey como MFA e como
 *       entrada no portal; a APLICAÇÃO ao portal é da ED-38, aqui só a
 *       definição da política.</li>
 * </ul>
 *
 * <p>Padrões de fábrica ALINHADOS ao NIST SP 800-63B-4 (texto do aceite):
 * expiração desligada; mínimo de 15 caracteres quando a senha é fator único
 * (caso do portal sem MFA). Com MFA exigido ao usuário, o mínimo pode ser o
 * configurado pelo tenant, nunca abaixo do piso do produto de 8 — piso que
 * vem do próprio NIST para senha como um de vários fatores.
 *
 * <p>EXCEÇÃO DECLARADA (correção D6): o padrão de fábrica de
 * {@code sec.lockout_attempts} ({@link #FACTORY_LOCKOUT_ATTEMPTS} = 5) NÃO
 * tem fonte no aceite/ADR — o 3 do POC-05 é valor do tenant do ensaio, não
 * padrão. É default sem fonte, PENDENTE DE CONFIRMAÇÃO do dono; não deve
 * ser lido como valor do plano.
 */
public record TenantSecurityPolicy(int passwordMinLength, Integer passwordMaxAgeDays,
                                   int passwordHistory, int lockoutAttempts,
                                   Integer userInactivityDays,
                                   boolean compositionRequired, boolean passkeyEnabled) {

    /** Mínimo NIST SP 800-63B-4 para senha como FATOR ÚNICO (aceite da ED-06). */
    public static final int NIST_SINGLE_FACTOR_MIN_LENGTH = 15;

    /** Piso do produto para senha quando há MFA (NIST SP 800-63B-4). */
    public static final int MFA_MIN_LENGTH_FLOOR = 8;

    /**
     * Padrão de fábrica de {@code sec.lockout_attempts}: DEFAULT SEM FONTE
     * no aceite/ADR, pendente de confirmação do dono (correção D6).
     */
    public static final int FACTORY_LOCKOUT_ATTEMPTS = 5;

    public TenantSecurityPolicy {
        if (passwordMinLength < 1) {
            throw new IllegalArgumentException("sec.password_min_length precisa ser positivo");
        }
        if (passwordHistory < 0) {
            throw new IllegalArgumentException("sec.password_history não pode ser negativo");
        }
        if (lockoutAttempts < 1) {
            throw new IllegalArgumentException("sec.lockout_attempts precisa ser ao menos 1");
        }
        if (passwordMaxAgeDays != null && passwordMaxAgeDays < 1) {
            throw new IllegalArgumentException("sec.password_max_age_days, quando presente, precisa ser positivo");
        }
        if (userInactivityDays != null && userInactivityDays < 1) {
            throw new IllegalArgumentException("sec.user_inactivity_days, quando presente, precisa ser positivo");
        }
    }

    /** Padrão de fábrica alinhado ao NIST: sem expiração, sem composição, fator único = 15. */
    public static TenantSecurityPolicy factoryDefault() {
        return new TenantSecurityPolicy(NIST_SINGLE_FACTOR_MIN_LENGTH, null, 0,
                FACTORY_LOCKOUT_ATTEMPTS, null, false, false);
    }

    /**
     * O mínimo EFETIVO: fator único (sem MFA) nunca abaixo de 15, mesmo que o
     * tenant configure menos — a configuração menor só vale para quem tem MFA.
     */
    public int effectiveMinLength(boolean mfaEnrolled) {
        return mfaEnrolled
                ? Math.max(passwordMinLength, MFA_MIN_LENGTH_FLOOR)
                : Math.max(passwordMinLength, NIST_SINGLE_FACTOR_MIN_LENGTH);
    }

    /** Expiração periódica ligada? Por padrão, NÃO (NIST). */
    public boolean periodicExpiryEnabled() {
        return passwordMaxAgeDays != null;
    }

    /**
     * Opções desta política CONTRÁRIAS à recomendação do NIST SP 800-63B-4 —
     * as que o console marca e que exigem aceite registrado do ADMIN do tenant.
     */
    public boolean contraryToNist() {
        return periodicExpiryEnabled() || compositionRequired;
    }

    public TenantSecurityPolicy requireNonNull() {
        return Objects.requireNonNull(this);
    }
}
