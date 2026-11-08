package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Apuracao auditavel para regulacao por incentivo (GES-30): carrega a
 * versao da formula, o congelamento do periodo (fechamento da P3.41 -
 * apuracao sem fechamento e recusada), a trilha ao dado primario e um hash
 * SHA-256 do conteudo canonico. A exportacao ao verificador independente e
 * JSON (so pela classe Json do core), sem dado pessoal por construcao:
 * so codigos, valores, versoes e referencias. A leitura pelo perfil externo
 * da ED-75 entra pela porta {@link #exportForExternalProfile(String)}:
 * perfil REGULATOR le; qualquer outro perfil e recusado aqui - o mecanismo
 * de acesso da ED-75 nao e reimplementado, so consumido como fronteira.
 */
public record IncentiveAssessment(UUID id, UUID tenantId, String indicatorCode, int formulaVersion,
                                  LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                  BigDecimal frozenValue, List<PrimaryDataLink> lineage,
                                  String contentHash) {

    public IncentiveAssessment {
        if (tenantId == null) {
            throw new IndicatorValidationException("ASSESSMENT_TENANT_REQUIRED", "apuracao sem tenant");
        }
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("ASSESSMENT_INDICATOR_REQUIRED", "apuracao sem indicador");
        }
        if (formulaVersion < 1) {
            throw new IndicatorValidationException("ASSESSMENT_FORMULA_VERSION_REQUIRED",
                    "apuracao de " + indicatorCode + " sem a versao da formula (GES-30)");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IndicatorValidationException("ASSESSMENT_PERIOD_INVALID",
                    "apuracao de " + indicatorCode + " com periodo invalido");
        }
        if (frozenValue == null) {
            throw new IndicatorValidationException("ASSESSMENT_NOT_FROZEN",
                    "apuracao de " + indicatorCode + " sem valor congelado: regulacao por "
                            + "incentivo exige o fechamento do periodo da P3.41 (GES-30)");
        }
        if (lineage == null || lineage.isEmpty()) {
            throw new IndicatorValidationException("ASSESSMENT_LINEAGE_REQUIRED",
                    "apuracao de " + indicatorCode + " sem trilha ao dado primario (GES-30)");
        }
        lineage = List.copyOf(lineage);
        String expected = computeHash(tenantId, indicatorCode, formulaVersion, periodStart,
                periodEnd, areaCode, frozenValue, lineage);
        if (contentHash == null || contentHash.isBlank()) {
            contentHash = expected;
        } else if (!contentHash.equals(expected)) {
            throw new IndicatorValidationException("ASSESSMENT_HASH_MISMATCH",
                    "hash da apuracao diverge do conteudo: apuracao adulterada ou corrompida (GES-30)");
        }
    }

    public static IncentiveAssessment create(UUID tenantId, String indicatorCode, int formulaVersion,
                                             LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                             BigDecimal frozenValue, List<PrimaryDataLink> lineage) {
        return new IncentiveAssessment(UUID.randomUUID(), tenantId, indicatorCode, formulaVersion,
                periodStart, periodEnd, areaCode, frozenValue, lineage, null);
    }

    public static String computeHash(UUID tenantId, String indicatorCode, int formulaVersion,
                                     LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                     BigDecimal frozenValue, List<PrimaryDataLink> lineage) {
        Map<String, String> canonical = new TreeMap<>();
        canonical.put("tenantId", String.valueOf(tenantId));
        canonical.put("indicatorCode", indicatorCode);
        canonical.put("formulaVersion", Integer.toString(formulaVersion));
        canonical.put("periodStart", String.valueOf(periodStart));
        canonical.put("periodEnd", String.valueOf(periodEnd));
        canonical.put("areaCode", areaCode == null ? "" : areaCode);
        canonical.put("frozenValue", frozenValue.toPlainString());
        StringBuilder links = new StringBuilder();
        for (PrimaryDataLink link : lineage) {
            links.append(link.kind()).append(':')
                    .append(link.primaryId() == null ? "" : link.primaryId()).append(':')
                    .append(link.primaryCode() == null ? "" : link.primaryCode()).append(';');
        }
        canonical.put("lineage", links.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Json.write(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    /** Exportacao ao verificador independente: agregada, sem dado pessoal. */
    public String exportJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("indicatorCode", indicatorCode);
        out.put("formulaVersion", formulaVersion);
        out.put("periodStart", periodStart.toString());
        out.put("periodEnd", periodEnd.toString());
        out.put("areaCode", areaCode);
        out.put("frozenValue", frozenValue.toPlainString());
        out.put("contentHash", contentHash);
        out.put("lineage", lineage.stream()
                .map(l -> Map.<String, Object>of("kind", l.kind().name(),
                        "ref", l.primaryId() == null ? l.primaryCode() : l.primaryId().toString()))
                .toList());
        return Json.write(out);
    }

    /** Leitura pelo perfil externo da ED-75 (GES-22/GES-30): so REGULATOR. */
    public String exportForExternalProfile(String externalProfile) {
        if (!"REGULATOR".equals(externalProfile)) {
            throw new IndicatorValidationException("EXTERNAL_PROFILE_NOT_AUTHORIZED",
                    "perfil externo " + externalProfile + " nao le apuracao regulatoria; "
                            + "o mecanismo de acesso e o da ED-75, aqui so consumido");
        }
        return exportJson();
    }
}
