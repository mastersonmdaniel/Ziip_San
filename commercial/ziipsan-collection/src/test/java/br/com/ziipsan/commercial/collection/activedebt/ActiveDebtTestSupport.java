package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class ActiveDebtTestSupport {
    static final LocalDate V1 = LocalDate.of(2026, 1, 1);
    private ActiveDebtTestSupport() {}
    static Prescription.Table table() {
        return new Prescription.Table(List.of(
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL,
                        10, "Codigo Civil, art. 205", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.TARIFF_EXECUTION,
                        null, "STJ, Temas 251 a 254 (execucao de tarifa)", false, 1, V1),
                // Linha da prova adversarial do revisor (D2): 5 anos, EXIGE analise
                // juridica — sem analise registrada, o desfecho e UNDETERMINED.
                new Prescription.RuleRow(Prescription.ProviderNature.PUBLIC_LAW, Prescription.ClaimType.AGAINST_PUBLIC_ENTITY,
                        5, "Decreto 20.910/1932", true, 1, V1)));
    }
    static Clock clockAt(LocalDate date) { return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC); }
    static PrescriptionGate gateAt(LocalDate date) { return new PrescriptionGate(table(), clockAt(date)); }
    static ActiveDebtParameters params(Map<String, String> extra) {
        var m = new java.util.HashMap<String, String>();
        m.put("da.min_filing_amount", "1000.00");
        m.put("da.prescription_alert_days", "180");
        m.put("da.max_installments", "12");
        m.put("da.min_installment", "50.00");
        m.put("da.cda_template_code", "\"CDA_TENANT_CODE\"");
        m.put("da.petition_template_code", "\"PETITION_TENANT_CODE\"");
        m.put("da.handoff_template_code", "\"HANDOFF_TENANT_CODE\"");
        m.putAll(extra);
        return ActiveDebtParameters.fromValues(m);
    }
}
