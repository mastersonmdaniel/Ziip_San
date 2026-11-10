package br.com.ziipsan.platform.portal.selfcare;

import br.com.ziipsan.core.parameters.MunicipalityScopedParameterRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * MM-08 — {@link SelfCareParameters} (ED-38) com escopo municipal.
 *
 * <p>Classe NOVA ao lado das da ED-38 — nenhuma classe da ED-38 e
 * editada. Aplica a regra de resolucao com escopo do MM-02
 * ({@link MunicipalityScopedParameterRule}) SOMENTE as chaves que a
 * ED-38 ja tem (nenhuma chave nova e criada aqui):
 *
 * <ul>
 *   <li>{@code portal.idp_govbr_enabled} (booleano);</li>
 *   <li>{@code portal.idp_govbr_min_level} (texto SILVER|GOLD — a
 *       validacao do literal continua a ser da ED-38/P3.3, no
 *       consumidor {@code GovBrOnboarding});</li>
 *   <li>{@code bill.peer_comparison} (booleano);</li>
 *   <li>{@code bill.peer_min_units} (inteiro — o piso 5 continua a ser
 *       recusado/aplicado em {@code PeerComparison}, ED-38);</li>
 *   <li>{@code portal.passkey_enabled} (booleano).</li>
 * </ul>
 *
 * <p>Semantica, herdada da regra do MM-02 (nao reimplementada):
 * versao MUNICIPAL vigente no instante vence; chave sem versao
 * municipal cai na da EMPRESA; sem versao vigente em nenhum escopo,
 * delega-se a porta da empresa da ED-38 (que carrega as fabricas e a
 * ausencia vigentes — o comportamento de hoje fica intacto).
 *
 * <p>Fallback integro: com {@code municipalityIbge == null} (UC sem
 * municipio derivavel, MM-01), esta classe NUNCA consulta a porta de
 * versoes com escopo e delega tudo a porta da empresa — exactamente o
 * comportamento vigente do portal.
 *
 * <p>Valor municipal presente mas ilegivel para a chave (JSON canonico
 * inesperado) RECUSA com o nome da chave: cair silenciosamente na
 * empresa esconderia dado corrompido.
 */
public final class MunicipalSelfCareParameters implements SelfCareParameters {

    public static final String KEY_GOVBR_ENABLED = "portal.idp_govbr_enabled";
    public static final String KEY_GOVBR_MIN_LEVEL = "portal.idp_govbr_min_level";
    public static final String KEY_PEER_COMPARISON = "bill.peer_comparison";
    public static final String KEY_PEER_MIN_UNITS = "bill.peer_min_units";
    public static final String KEY_PASSKEY_ENABLED = "portal.passkey_enabled";

    /**
     * Porta das versoes com escopo de UMA chave de UM tenant (a
     * pre-condicao da regra do MM-02: a lista contem apenas versoes da
     * mesma chave do mesmo tenant). O adaptador de integracao le
     * {@code parameter_versions} ja com a coluna de escopo do MM-02.
     */
    public interface ScopedVersionsPort {
        List<MunicipalityScopedParameterRule.ScopedVersion> versions(UUID tenantId, String definitionKey);
    }

    private final SelfCareParameters company;
    private final ScopedVersionsPort scopedVersions;
    private final String municipalityIbge;
    private final Supplier<LocalDate> resolutionDate;

    public MunicipalSelfCareParameters(SelfCareParameters company, ScopedVersionsPort scopedVersions,
                                       String municipalityIbge, Supplier<LocalDate> resolutionDate) {
        if (company == null) {
            throw new IllegalArgumentException("porta de parametros da empresa (ED-38) e obrigatoria");
        }
        if (municipalityIbge != null) {
            if (!municipalityIbge.matches("[0-9]{7}")) {
                throw new IllegalArgumentException("municipality_ibge deve ter 7 digitos");
            }
            if (scopedVersions == null || resolutionDate == null) {
                throw new IllegalArgumentException(
                        "com municipio, a porta de versoes com escopo (MM-02) e a data de resolucao sao obrigatorias");
            }
        }
        this.company = company;
        this.scopedVersions = scopedVersions;
        this.municipalityIbge = municipalityIbge;
        this.resolutionDate = resolutionDate;
    }

    /** Atalho: resolucao numa data fixa (testes e chamadores com data propria). */
    public MunicipalSelfCareParameters(SelfCareParameters company, ScopedVersionsPort scopedVersions,
                                       String municipalityIbge, LocalDate resolutionDate) {
        this(company, scopedVersions, municipalityIbge, () -> resolutionDate);
    }

    /** Municipio do contexto, ou vazio no fallback-empresa. */
    public Optional<String> municipalityIbge() {
        return Optional.ofNullable(municipalityIbge);
    }

    private Optional<String> resolvedValueJson(UUID tenantId, String key) {
        if (municipalityIbge == null) {
            return Optional.empty();
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant e obrigatorio");
        }
        List<MunicipalityScopedParameterRule.ScopedVersion> versions =
                scopedVersions.versions(tenantId, key);
        if (versions == null) {
            throw new IllegalStateException("porta de versoes devolveu null para a chave " + key);
        }
        return MunicipalityScopedParameterRule
                .resolve(versions, municipalityIbge, resolutionDate.get())
                .map(MunicipalityScopedParameterRule.ScopedVersion::valueJson);
    }

    private boolean booleanValue(UUID tenantId, String key, boolean companyValue) {
        Optional<String> json = resolvedValueJson(tenantId, key);
        if (json.isEmpty()) {
            return companyValue;
        }
        String v = json.get().trim();
        if (v.equals("true")) {
            return true;
        }
        if (v.equals("false")) {
            return false;
        }
        throw new IllegalStateException(
                "valor vigente da chave " + key + " nao e booleano JSON: " + json.get());
    }

    @Override
    public boolean govbrEnabled(UUID tenantId) {
        return booleanValue(tenantId, KEY_GOVBR_ENABLED, company.govbrEnabled(tenantId));
    }

    @Override
    public Optional<String> govbrMinLevelRaw(UUID tenantId) {
        Optional<String> json = resolvedValueJson(tenantId, KEY_GOVBR_MIN_LEVEL);
        if (json.isEmpty()) {
            return company.govbrMinLevelRaw(tenantId);
        }
        return Optional.of(parseJsonString(KEY_GOVBR_MIN_LEVEL, json.get()));
    }

    @Override
    public boolean peerComparisonEnabled(UUID tenantId) {
        return booleanValue(tenantId, KEY_PEER_COMPARISON, company.peerComparisonEnabled(tenantId));
    }

    @Override
    public Optional<Integer> peerMinUnits(UUID tenantId) {
        Optional<String> json = resolvedValueJson(tenantId, KEY_PEER_MIN_UNITS);
        if (json.isEmpty()) {
            return company.peerMinUnits(tenantId);
        }
        String v = json.get().trim();
        try {
            return Optional.of(Integer.valueOf(v));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "valor vigente da chave " + KEY_PEER_MIN_UNITS + " nao e inteiro JSON: " + json.get());
        }
    }

    @Override
    public boolean passkeyEnabled(UUID tenantId) {
        return booleanValue(tenantId, KEY_PASSKEY_ENABLED, company.passkeyEnabled(tenantId));
    }

    /** String JSON canonica: aspas externas removidas; escapes minimos \" e \\ tratados. */
    private static String parseJsonString(String key, String json) {
        String v = json.trim();
        if (v.length() < 2 || v.charAt(0) != '"' || v.charAt(v.length() - 1) != '"') {
            throw new IllegalStateException(
                    "valor vigente da chave " + key + " nao e string JSON: " + json);
        }
        return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
