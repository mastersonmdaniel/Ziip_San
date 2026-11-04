package br.com.ziipsan.core.protocol;

/**
 * Value of the tenant parameter {@code sac.extension_policy}
 * (PARAMETROS-DO-TENANT, 20d §5, ATD-05; factory default NONE; Lei
 * 13.460, art. 16). NONE refuses every extension; ONCE_JUSTIFIED
 * allows exactly one justified extension per protocol; PER_ERI_RULE
 * allows extensions under the regulator's rule — the rule text itself
 * is tenant policy data held outside this front, so the engine allows
 * the extension and still records reason, author and notice on the
 * extension event, never touching the original deadline.
 */
public enum ExtensionPolicy {
    NONE,
    ONCE_JUSTIFIED,
    PER_ERI_RULE
}
