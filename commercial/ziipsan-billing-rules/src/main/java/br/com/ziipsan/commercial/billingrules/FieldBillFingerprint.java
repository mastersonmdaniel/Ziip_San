package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fingerprint canonico da conta de campo (ED-19): SHA-256 sobre uma
 * serializacao canonica e estavel da entrada + do resultado (chaves de
 * parametro ordenadas, decimais em {@code toPlainString} apos escala 2,
 * separador unico). Dois calculos com a mesma entrada produzem o mesmo
 * fingerprint em qualquer JVM/Android — e assim que o servidor reconhece a
 * conta recalculada como identica a impressa sem comparar campo a campo
 * (a comparacao campo a campo existe para EXPLICAR a divergencia:
 * {@link DivergenceEvaluator}).
 */
public final class FieldBillFingerprint {

    private FieldBillFingerprint() {
    }

    /** Forma canonica (texto) que entra no hash — exposta para o teste diferencial. */
    public static String canonicalForm(FieldBillInput in, FieldBillResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(RulesVersion.CONTRACT).append('|');
        sb.append("task=").append(in.taskId()).append('|');
        sb.append("unit=").append(in.unitId()).append('|');
        sb.append("inst=").append(in.installationId()).append('|');
        sb.append("prev=").append(in.previousReading()).append('|');
        sb.append("curr=").append(in.currentReading()).append('|');
        sb.append("digits=").append(in.meterDigits()).append('|');
        sb.append("econ=").append(in.economies()).append('|');
        sb.append("due=").append(in.dueDate()).append('|');
        FieldTariffSnapshot s = in.snapshot();
        sb.append("tariffVersion=").append(s.tariffVersionId()).append('|');
        sb.append("category=").append(s.categoryId()).append('|');
        sb.append("min=").append(s.minimumM3PerEconomy()).append('|');
        sb.append("fixedPer=").append(dec(s.fixedChargePerEconomy())).append('|');
        sb.append("sewerOn=").append(s.sewerEnabled()).append('|');
        sb.append("sewerPct=").append(s.sewerEnabled() ? dec(s.sewerPct()) : "-").append('|');
        sb.append("brackets=");
        for (TariffBracket b : s.brackets()) {
            sb.append(b.isOpen() ? "*" : b.widthM3()).append(':').append(dec(b.pricePerM3())).append(',');
        }
        sb.append('|');
        sb.append("params=");
        for (Map.Entry<String, String> e : new TreeMap<>(s.parameterVersions()).entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(',');
        }
        sb.append('|');
        sb.append("debits=");
        for (AmountLine l : in.debits()) {
            sb.append(l.code()).append(':').append(dec(l.amount())).append(',');
        }
        sb.append('|');
        sb.append("credits=");
        for (AmountLine l : in.credits()) {
            sb.append(l.code()).append(':').append(dec(l.amount())).append(',');
        }
        sb.append('|');
        sb.append("consumption=").append(result.consumptionM3()).append('|');
        sb.append("billed=").append(result.billedM3()).append('|');
        sb.append("water=").append(dec(result.waterValue())).append('|');
        sb.append("fixed=").append(dec(result.fixedValue())).append('|');
        sb.append("sewer=").append(dec(result.sewerValue())).append('|');
        sb.append("debitsValue=").append(dec(result.debitsValue())).append('|');
        sb.append("creditsApplied=").append(dec(result.creditsApplied())).append('|');
        sb.append("creditsNotApplied=").append(dec(result.creditsNotApplied())).append('|');
        sb.append("total=").append(dec(result.total()));
        return sb.toString();
    }

    public static String of(FieldBillInput in, FieldBillResult result) {
        return ArtifactHash.sha256Hex(canonicalForm(in, result).getBytes(StandardCharsets.UTF_8));
    }

    private static String dec(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
