package br.com.ziipsan.core.regulatory;

import java.util.Locale;

/**
 * Parametros da ED-96, lidos do tenant (registro de parametros do
 * core) — nenhum comportamento e presumido alem do que o aceite fixa:
 * <pre>
 *   reg.contract_type         ENUM  os cinco tipos do aceite;
 *                             fato declarado, classe d, REG.
 *   reg.risk_matrix_required  BOOL  fato declarado, classe d, REG:
 *                             o instrumento exige matriz de riscos?
 * </pre>
 *
 * <p>Os dois sao FATOS DECLARADOS (classe d): a ausencia NAO e
 * convertida em valor presumido. {@code reg.contract_type} ausente =
 * tipo nao declarado no registro de parametros (o cadastro de cada
 * instrumento carrega o seu tipo; a completude anota a divergencia
 * ou a ausencia — ver {@link RegulatoryContractService#completeness}).
 * {@code reg.risk_matrix_required} ausente = exigencia nao declarada:
 * a matriz nao e cobrada na completude, mas a ausencia fica anotada.
 * Valor presente e parseado ESTRITO: desconhecido e recusado, nunca
 * interpretado em silencio (mesmo padrao de {@code TransparencyParameters}
 * da ED-99).
 */
public final class ContractParameters {

    public static final String CONTRACT_TYPE = "reg.contract_type";
    public static final String RISK_MATRIX_REQUIRED = "reg.risk_matrix_required";

    private ContractParameters() {
    }

    /**
     * Tipo declarado no registro de parametros; {@code null} quando
     * ausente/em branco (fato nao declarado). Valor presente fora do
     * dominio do aceite e recusado.
     */
    public static RegulatoryContract.ContractType declaredContractType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RegulatoryContract.ContractType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CONTRACT_TYPE + " desconhecido: " + raw
                    + " (dominio: PROGRAM_CONTRACT, CONCESSION, PPP, SUBDELEGATION, DIRECT_PROVISION)");
        }
    }

    /**
     * Exigencia declarada de matriz de riscos; {@code null} quando
     * ausente/em branco (fato nao declarado). Aceita apenas
     * {@code true}/{@code false} explicitos.
     */
    public static Boolean declaredRiskMatrixRequired(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true")) return Boolean.TRUE;
        if (v.equals("false")) return Boolean.FALSE;
        throw new IllegalArgumentException(RISK_MATRIX_REQUIRED + " deve ser true ou false declarado: " + raw);
    }
}
