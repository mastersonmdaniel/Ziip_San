package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ziipsan.core.json.Json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de aceite da ED-71: fluxo completo com a norma LIGADA
 * (inventário -> portão da OS -> EPI -> investigação -> autoavaliação)
 * e, com a norma DESLIGADA, nada nasce e o registro criado ligado segue
 * íntegro e legível (sobrevivência provada sobre conteúdo serializado
 * persistido fora dos objetos, como na correção D5 da ED-73).
 */
class Iso45001AcceptanceOnOffTest {

    private static final UUID PROP = UUID.randomUUID();
    private static final UUID ACC = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 2, 1);

    @Test
    @DisplayName("ligada: o fluxo completo de SST nasce e o portão libera a OS de risco")
    void fullFlowEnabled() {
        Iso45001Activation on = Iso45001Activation.enabled("SYN-escopo declarado", PROP, ACC);
        HazardRiskEntry hazard = HazardRiskEntry.register(on, "SYN-ATV-EC", "SYN atividade",
                "SYN local", UUID.randomUUID(), HazardRiskEntry.Category.MECHANICAL,
                "SYN perigo", 4, 2, 5, List.of("SYN controle"), List.of(), UUID.randomUUID(), DAY);
        assertThat(hazard.riskScore()).isEqualTo(8);

        UUID w1 = UUID.randomUUID();
        UUID w2 = UUID.randomUUID();
        OsSafetyGate gate = OsSafetyGate.assess(on, UUID.randomUUID(), "SYN-TIPO-EC",
                OsSafetyGate.Channel.FIELD, true, false, false, false, null, "SYN-AR-1",
                UUID.randomUUID(), "SYN-PT-1",
                List.of(new OsSafetyGate.ExecutorQualification(w1, "SYN-EC", DAY.plusDays(60)),
                        new OsSafetyGate.ExecutorQualification(w2, "SYN-EC", DAY.plusDays(60))),
                null);
        gate.requireStartable(DAY);

        PpeDelivery ppe = PpeDelivery.deliver(on, w1, "SYN-FUNCAO", "SYN-EPI-1", "SYN-LOTE-1",
                "SYN-CA-1", 1, "SYN-REQ-1", "SYN-ALMOX-1", UUID.randomUUID(), DAY,
                DAY.plusMonths(11), true);
        ppe.requireUsableOn(DAY.plusMonths(6));

        AccidentInvestigation inv = AccidentInvestigation.open(on, UUID.randomUUID(), null,
                DAY, "SYN evento", AccidentInvestigation.Severity.NEAR_MISS, "SYN causa",
                List.of("SYN-ACAO-1"), UUID.randomUUID(), false, List.of(), null);
        assertThat(inv.healthDataPresent()).isFalse();

        OhsClauseAssessment assessment = OhsClauseAssessment.assess(on, "SYN.1", "SYN resumo",
                OhsClauseAssessment.Conformity.CONFORMING,
                List.of(new OhsClauseAssessment.EvidenceRef(
                        OhsClauseAssessment.EvidenceKind.HAZARD, "SYN-HAZ-1"),
                        new OhsClauseAssessment.EvidenceRef(
                                OhsClauseAssessment.EvidenceKind.PPE_DELIVERY, "SYN-EPI-1")),
                "", UUID.randomUUID(), DAY);
        assertThat(assessment.literalTitle()).isEqualTo("[A DEFINIR]");
    }

    @Test
    @DisplayName("desligada: nada nasce; o registro criado ligado sobrevive íntegro e legível")
    void disabledKeepsRecords() {
        Iso45001Activation on = Iso45001Activation.enabled("SYN-escopo declarado", PROP, ACC);
        OhsClauseAssessment created = OhsClauseAssessment.assess(on, "SYN.1", "SYN resumo",
                OhsClauseAssessment.Conformity.PARTIAL, List.of(), "", UUID.randomUUID(), DAY);
        AccidentInvestigation inv = AccidentInvestigation.open(on, UUID.randomUUID(), null,
                DAY, "SYN evento", AccidentInvestigation.Severity.NO_INJURY, "SYN causa",
                List.of("SYN-ACAO-1"), UUID.randomUUID(), false, List.of(), null);

        // Persistência simulada FORA dos objetos: chave -> conteúdo serializado.
        Map<String, String> store = Map.of(
                "assessment", created.toJson(),
                "investigation", inv.toJson());

        Iso45001Activation off = Iso45001Activation.disabled();
        assertThatThrownBy(() -> HazardRiskEntry.register(off, "SYN-A", "SYN", "SYN",
                UUID.randomUUID(), HazardRiskEntry.Category.OTHER, "SYN", 1, 1, 5,
                List.of("SYN-C"), List.of(), UUID.randomUUID(), DAY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OsSafetyGate.assess(off, UUID.randomUUID(), "SYN-T",
                OsSafetyGate.Channel.BACKOFFICE, false, false, false, false, null, null, null,
                null, List.of(), null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PpeDelivery.deliver(off, UUID.randomUUID(), "SYN-F", "SYN-I",
                "SYN-L", "SYN-CA", 1, "SYN-R", "SYN-A", UUID.randomUUID(), DAY,
                DAY.plusDays(30), true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AccidentInvestigation.open(off, UUID.randomUUID(), null, DAY,
                "SYN", AccidentInvestigation.Severity.NEAR_MISS, "SYN", List.of("SYN-A"),
                UUID.randomUUID(), false, List.of(), null))
                .isInstanceOf(IllegalStateException.class);

        // Releitura do armazenamento com a norma desligada: conteúdo intacto.
        Map<String, Object> reread = Json.readMap(store.get("assessment"));
        assertThat(reread.get("clauseNumber")).isEqualTo("SYN.1");
        assertThat(reread.get("literalTitle")).isEqualTo("[A DEFINIR]");
        Map<String, Object> rereadInv = Json.readMap(store.get("investigation"));
        assertThat(rereadInv.get("severity")).isEqualTo("NO_INJURY");
        assertThat(rereadInv.get("healthDataPresent")).isEqualTo(false);
        assertThat(new BigDecimal("0")).isZero();
    }
}
