package br.com.ziipsan.commercial.billing.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-22: registro S3.17, OS SET/REMOVE, lote OS-mae/OS-filhas, teto de meses e os sete indicadores. */
class SpecialSituationServiceTest {

    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID OS = UUID.randomUUID();

    private static SpecialSituationService service(int maxMonths) {
        return new SpecialSituationService(SpecialSituationParameters.fromValues(
                Map.of(SpecialSituationParameters.MAX_MONTHS, String.valueOf(maxMonths))));
    }

    private static SpecialSituationService.SetRequest request(SpecialSituationType type, YearMonth start,
                                                              YearMonth end, String legalBasis) {
        return new SpecialSituationService.SetRequest(UNIT, SpecialSituationRecord.Domain.BILLING, type,
                "motivo do atendimento", legalBasis, start, end, null, null, OS);
    }

    @Test
    @DisplayName("tipo expoe os sete indicadores; isencao isenta e exige base legal")
    void sevenIndicators() {
        assertThat(SpecialSituationType.EXEMPTION.indicators()).hasSize(7);
        assertThat(SpecialSituationType.EXEMPTION.exemptsCharges()).isTrue();
        assertThat(SpecialSituationType.EXEMPTION.requiresLegalBasis()).isTrue();
        assertThat(SpecialSituationType.FIXED_VOLUME.fixesWaterVolume()).isTrue();
        assertThat(SpecialSituationType.BILLING_PAUSED.pausesBilling()).isTrue();
    }

    @Test
    @DisplayName("SET inclui com todos os campos do S3.17; vigencia inicio..fim inclusive")
    void setAndValidity() {
        // FIXED_VOLUME agora exige ao menos um volume (D2): o cenario fixa agua = 10 m3.
        SpecialSituationService.SetRequest fixedVolume = new SpecialSituationService.SetRequest(UNIT,
                SpecialSituationRecord.Domain.BILLING, SpecialSituationType.FIXED_VOLUME,
                "motivo do atendimento", null, YearMonth.of(2026, 3), YearMonth.of(2026, 8),
                10, null, OS);
        SpecialSituationRecord record = service(12).set(fixedVolume, UUID.randomUUID());
        assertThat(record.fixedWaterVolume()).isEqualTo(10);
        assertThat(record.appliesTo(YearMonth.of(2026, 2))).isFalse();
        assertThat(record.appliesTo(YearMonth.of(2026, 3))).isTrue();
        assertThat(record.appliesTo(YearMonth.of(2026, 8))).isTrue();
        assertThat(record.appliesTo(YearMonth.of(2026, 9))).isFalse();
        assertThat(record.openingOsId()).isEqualTo(OS);
        assertThat(record.durationMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("D2: FIXED_VOLUME com os dois volumes nulos e recusado; com so agua ou so esgoto, incluido")
    void fixedVolumeNeedsAtLeastOneVolume() {
        assertThatThrownBy(() -> service(12).set(
                request(SpecialSituationType.FIXED_VOLUME, YearMonth.of(2026, 3), YearMonth.of(2026, 8), null),
                UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nenhum volume fixo");
        SpecialSituationService.SetRequest sewerOnly = new SpecialSituationService.SetRequest(UNIT,
                SpecialSituationRecord.Domain.BILLING, SpecialSituationType.FIXED_VOLUME,
                "motivo do atendimento", null, YearMonth.of(2026, 3), YearMonth.of(2026, 8),
                null, 7, OS);
        SpecialSituationRecord record = service(12).set(sewerOnly, UUID.randomUUID());
        assertThat(record.fixedWaterVolume()).isNull();
        assertThat(record.fixedSewerVolume()).isEqualTo(7);
    }

    @Test
    @DisplayName("D3: recusa em lote lida dos indicadores como dados - conjunto sintetico sem allowsBatch e recusado")
    void batchRefusalFromIndicatorsAsData() {
        // Tipo sintetico declarado NESTE teste (nao e um tipo real da tabela):
        // todos os indicadores desligados, em especial allowsBatch=false.
        SpecialSituationType.Indicators syntheticNoBatch = new SpecialSituationType.Indicators(
                false, false, false, false, false, false, false);
        assertThatThrownBy(() -> SpecialSituationService.requireBatchAllowed(syntheticNoBatch, "SINTETICO_SEM_LOTE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nao admite inclusao em lote");
        SpecialSituationType.Indicators syntheticWithBatch = new SpecialSituationType.Indicators(
                false, false, false, false, false, false, true);
        assertThatCode(() -> SpecialSituationService.requireBatchAllowed(syntheticWithBatch, "SINTETICO_COM_LOTE"))
                .doesNotThrowAnyException();
        // Declaracao explicita do risco (D3): na tabela REAL atual os quatro
        // tipos declaram allowsBatch=true - nenhum tipo real recusa lote
        // enquanto o 23b nao for conferido; o teste acima prova o ramo sem
        // falsificar esta tabela.
        assertThat(SpecialSituationType.FIXED_VOLUME.asIndicators().allowsBatch()).isTrue();
        assertThat(SpecialSituationType.BILLING_PAUSED.asIndicators().allowsBatch()).isTrue();
        assertThat(SpecialSituationType.EXEMPTION.asIndicators().allowsBatch()).isTrue();
        assertThat(SpecialSituationType.REGISTERED_ONLY.asIndicators().allowsBatch()).isTrue();
    }

    @Test
    @DisplayName("isencao sem base legal e recusada; com base legal e vigencia, incluida")
    void exemptionNeedsLegalBasis() {
        assertThatThrownBy(() -> service(12).set(
                request(SpecialSituationType.EXEMPTION, YearMonth.of(2026, 1), YearMonth.of(2026, 12), null),
                UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base legal");
        SpecialSituationRecord ok = service(12).set(
                request(SpecialSituationType.EXEMPTION, YearMonth.of(2026, 1), YearMonth.of(2026, 12),
                        "Lei municipal citada no atendimento"), UUID.randomUUID());
        assertThat(ok.type()).isEqualTo(SpecialSituationType.EXEMPTION);
    }

    @Test
    @DisplayName("duracao alem de special_situation.max_months e recusada (13 meses com teto 12)")
    void maxMonthsCap() {
        assertThatThrownBy(() -> service(12).set(
                request(SpecialSituationType.REGISTERED_ONLY, YearMonth.of(2026, 1), YearMonth.of(2027, 1), null),
                UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_months");
    }

    @Test
    @DisplayName("lote: OS-mae gera uma OS-filha por unidade, todas com o batchId; REMOVE encerra pela OS de encerramento")
    void batchAndRemove() {
        UUID batch = UUID.randomUUID();
        UUID unit2 = UUID.randomUUID();
        SpecialSituationService svc = service(24);
        List<SpecialSituationRecord> records = svc.setBatch(List.of(
                request(SpecialSituationType.BILLING_PAUSED, YearMonth.of(2026, 5), null, null),
                new SpecialSituationService.SetRequest(unit2, SpecialSituationRecord.Domain.BILLING,
                        SpecialSituationType.BILLING_PAUSED, "obra na rede", null,
                        YearMonth.of(2026, 5), null, null, null, OS)),
                List.of(UUID.randomUUID(), UUID.randomUUID()), batch);
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(r -> batch.equals(r.batchId()));
        UUID closingOs = UUID.randomUUID();
        SpecialSituationRecord removed = svc.remove(records.get(0), YearMonth.of(2026, 7), closingOs);
        assertThat(removed.withdrawnMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(removed.closingOsId()).isEqualTo(closingOs);
        assertThat(removed.appliesTo(YearMonth.of(2026, 8))).isFalse();
        assertThatThrownBy(() -> svc.remove(removed, YearMonth.of(2026, 8), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem OS de abertura nao inclui (inclucao e retirada sao por OS)")
    void openingOsRequired() {
        SpecialSituationService.SetRequest noOs = new SpecialSituationService.SetRequest(UNIT,
                SpecialSituationRecord.Domain.CHARGING, SpecialSituationType.REGISTERED_ONLY,
                "motivo", null, YearMonth.of(2026, 1), null, null, null, null);
        assertThatThrownBy(() -> service(12).set(noOs, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SPECIAL_SITUATION_SET");
    }
}
