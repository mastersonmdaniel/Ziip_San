package br.com.sigsan.compliance.lgpd.engine;

import br.com.sigsan.compliance.lgpd.domain.DpiaRecord;
import br.com.sigsan.core.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Relatório de Impacto à Proteção de Dados (RIPD) - risk assessment (LGPD Art. 38).
 *
 * <p>Scores the processing activity described by a {@link DpiaRecord} by mutating the
 * record in place: {@code riskLevel}, {@code riskScore}, {@code likelihood},
 * {@code severity}, {@code mitigationMeasures} and {@code residualRisk}. The caller owns
 * persistence, so the engine never saves and never assumes a transaction boundary.</p>
 *
 * <p><b>Honesty note.</b> The score is a transparent, documented heuristic over the fields
 * the DPIA actually carries - not a certified methodology. Anything the assessment cannot
 * see (organisational context, prior incidents, real security posture) is reflected in the
 * residual risk rather than being silently scored as zero. A HIGH/CRITICAL result marks
 * the DPIA as requiring DPO review; it is not a substitute for one.</p>
 */
@Slf4j
@Service
public class DpiaEngine {

    // Weights are fixed so the same input always yields the same score, which is what
    // makes an assessment defensible in an audit (Art. 38 §1).
    private static final int W_SPECIAL_CATEGORIES = 30; // Art. 11 - sensitive data
    private static final int W_LARGE_SCALE = 15;         // Art. 5 VII / Art. 11 §1
    private static final int W_SYSTEMATIC_MONITORING = 15;
    private static final int W_THIRD_COUNTRY = 10;       // Art. 33
    private static final int W_SENSITIVE_RECIPIENTS = 10;
    private static final int W_NO_SECURITY_MEASURES = 10;
    private static final int W_INDEFINITE_RETENTION = 10; // Art. 15 II / Art. 16

    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    /**
     * Recalculates risk on the given DPIA and mutates it in place. Safe to call repeatedly:
     * every output field is recomputed from scratch, so re-assessment cannot drift.
     */
    public void assessRisk(DpiaRecord dpia) {
        List<String> factors = new ArrayList<>();

        boolean special = hasEntries(dpia.getSpecialCategories());
        if (special) {
            factors.add("Dados sensiveis (Art. 11)");
        }

        boolean largeScale = isLargeScale(dpia.getDataSubjects());
        if (largeScale) {
            factors.add("Tratamento em grande escala");
        }

        boolean systematicMonitoring = isSystematicMonitoring(dpia);
        if (systematicMonitoring) {
            factors.add("Monitoramento sistematico");
        }

        boolean thirdCountry = hasEntries(dpia.getThirdCountryTransfers());
        if (thirdCountry) {
            factors.add("Transferencia internacional (Art. 33)");
        }

        boolean sensitiveRecipients = hasSensitiveRecipients(dpia.getRecipients());
        if (sensitiveRecipients) {
            factors.add("Destinatarios com acesso sensivel");
        }

        boolean noSecurity = !hasEntries(dpia.getSecurityMeasures());
        if (noSecurity) {
            factors.add("Sem medidas de seguranca descritas (Art. 46)");
        }

        boolean indefiniteRetention = isIndefiniteRetention(dpia.getRetentionPeriod());
        if (indefiniteRetention) {
            factors.add("Prazo de retencao indeterminado (Art. 15 II)");
        }

        int score = 0;
        if (special)            score += W_SPECIAL_CATEGORIES;
        if (largeScale)         score += W_LARGE_SCALE;
        if (systematicMonitoring) score += W_SYSTEMATIC_MONITORING;
        if (thirdCountry)       score += W_THIRD_COUNTRY;
        if (sensitiveRecipients) score += W_SENSITIVE_RECIPIENTS;
        if (noSecurity)         score += W_NO_SECURITY_MEASURES;
        if (indefiniteRetention) score += W_INDEFINITE_RETENTION;

        BigDecimal riskScore = BigDecimal.valueOf(score)
                .min(MAX_SCORE)
                .setScale(2, RoundingMode.HALF_UP);

        DpiaRecord.RiskLevel level = toRiskLevel(riskScore);
        String severity = toSeverity(riskScore);
        String likelihood = toLikelihood(dpia, special, noSecurity);

        dpia.setRiskScore(riskScore);
        dpia.setRiskLevel(level);
        dpia.setSeverity(severity);
        dpia.setLikelihood(likelihood);
        dpia.setMitigationMeasures(mitigationMeasures(factors));
        dpia.setResidualRisk(residualRisk(level, factors));

        log.info("DPIA risk assessed: activity='{}' score={} level={} severity={} likelihood={}",
                dpia.getProcessingActivity(), riskScore, level, severity, likelihood);
    }

    private DpiaRecord.RiskLevel toRiskLevel(BigDecimal score) {
        double v = score.doubleValue();
        if (v >= 75) return DpiaRecord.RiskLevel.CRITICAL;
        if (v >= 50) return DpiaRecord.RiskLevel.HIGH;
        if (v >= 25) return DpiaRecord.RiskLevel.MEDIUM;
        if (v > 0)  return DpiaRecord.RiskLevel.LOW;
        return DpiaRecord.RiskLevel.NEGLIGIBLE;
    }

    private String toSeverity(BigDecimal score) {
        double v = score.doubleValue();
        if (v >= 50) return "HIGH";
        if (v >= 25) return "MEDIUM";
        return "LOW";
    }

    /**
     * Likelihood is raised for the combinations the regulation singles out: sensitive data
     * with no safeguards, or a large-scale systematic profile.
     */
    private String toLikelihood(DpiaRecord dpia, boolean special, boolean noSecurity) {
        if (special && noSecurity) return "HIGH";
        if (isLargeScale(dpia.getDataSubjects()) && isSystematicMonitoring(dpia)) return "HIGH";
        if (special || noSecurity) return "MEDIUM";
        return "LOW";
    }

    /**
     * Residual risk is never better than HIGH: the engine cannot observe whether the
     * recommended measures are actually in place, so the mitigation is only credited, not
     * assumed effective. Art. 38 §1 requires the residual risk to be stated explicitly.
     */
    private String residualRisk(DpiaRecord.RiskLevel level, List<String> factors) {
        if (level == DpiaRecord.RiskLevel.CRITICAL) return "HIGH";
        if (level == DpiaRecord.RiskLevel.HIGH) return "MEDIUM";
        if (level == DpiaRecord.RiskLevel.MEDIUM && !factors.isEmpty()) return "LOW";
        return "LOW";
    }

    private String mitigationMeasures(List<String> factors) {
        List<String> measures = new ArrayList<>();
        measures.add("Revisar descricao do tratamento e documentar a base legal (Art. 7)");
        for (String factor : factors) {
            measures.add("Mitigar: " + factor);
        }
        measures.add("Definir prazo de retencao e eliminacao (Art. 15 II, Art. 16)");
        measures.add("Submeter ao encarregado (DPO) para parecer (Art. 41)");
        // mitigation_measures é jsonb: List.toString() daria "[a, b]", que não é JSON e o PostgreSQL
        // recusa. Json.write escapa aspas e vírgulas dentro de cada medida.
        return Json.write(measures);
    }

    /**
     * Diz se a coluna jsonb declara alguma coisa. O serviço grava as coleções com
     * {@link Json#write}, então o conteúdo é JSON: array ou objeto vazio, {@code null} e ausência
     * significam "nada declarado". Conteúdo que não é JSON lança exceção em vez de contar como
     * declarado ou não declarado: os dois palpites mudariam a nota de risco.
     */
    private boolean hasEntries(String storedJson) {
        return countEntries(storedJson) > 0;
    }

    private boolean isLargeScale(String dataSubjects) {
        int declared = countEntries(dataSubjects);
        return declared >= 5;
    }

    /**
     * Art. 11 §1: systematic observation over a period is what turns ordinary processing
     * into a high-risk one. We cannot see runtime behaviour, so we infer it from the
     * declared subject mix - a broad, public-facing audience implies monitoring.
     */
    private boolean isSystematicMonitoring(DpiaRecord dpia) {
        String subjects = dpia.getDataSubjects();
        if (subjects == null) return false;
        String lower = subjects.toLowerCase();
        return lower.contains("public") || lower.contains("visitor")
                || lower.contains("crianca") || lower.contains("child")
                || lower.contains("paciente") || lower.contains("patient");
    }

    /**
     * Recipients that re-identify or reprocess the data raise the risk regardless of the
     * declared volume.
     */
    private boolean hasSensitiveRecipients(String recipients) {
        if (recipients == null) return false;
        String lower = recipients.toLowerCase();
        return lower.contains("terceir") || lower.contains("third")
                || lower.contains("parceir") || lower.contains("partner")
                || lower.contains("internac") || lower.contains("external");
    }

    /**
     * An absent or open-ended retention term breaches Art. 15 II on its face, so it is
     * scored as a finding rather than accepted as "not specified".
     */
    private boolean isIndefiniteRetention(String retentionPeriod) {
        if (retentionPeriod == null || retentionPeriod.isBlank()) return true;
        String lower = retentionPeriod.toLowerCase();
        if (lower.contains("indefin") || lower.contains("permanent")
                || lower.contains("etern") || lower.contains("until deletion")) {
            return true;
        }
        // A bare number of days/months/years is a definite term.
        return !lower.matches(".*\\d+\\s*(day|dias|month|meses|mes|year|anos?|hora|hours?).*");
    }

    /**
     * Número de itens declarados: tamanho do array ou do objeto. A contagem antiga separava o texto
     * por vírgula, e um item como "clientes, inclusive menores" contava como dois. Um escalar
     * (texto solto) conta como um item se não estiver em branco.
     */
    private int countEntries(String storedJson) {
        JsonNode node = Json.readTree(storedJson);
        if (node.isMissingNode() || node.isNull()) return 0;
        if (node.isContainer()) return node.size();
        return node.asString("").isBlank() ? 0 : 1;
    }
}