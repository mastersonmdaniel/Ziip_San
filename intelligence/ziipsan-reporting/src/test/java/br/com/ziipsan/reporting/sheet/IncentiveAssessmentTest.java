package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncentiveAssessmentTest {

    private final UUID tenant = UUID.randomUUID();
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = LocalDate.of(2026, 12, 31);

    private List<PrimaryDataLink> lineage() {
        return List.of(new PrimaryDataLink(PrimaryDataLink.Kind.NETWORK_INCIDENT,
                UUID.randomUUID(), "OC-SINT-1"));
    }

    @Test
    void apuracaoComHashEstavelETrilha() {
        IncentiveAssessment a = IncentiveAssessment.create(tenant, "NR9.I04.INTERMITENCIA", 2,
                start, end, null, new BigDecimal("12.5"), lineage());
        assertEquals(64, a.contentHash().length());
        IncentiveAssessment same = IncentiveAssessment.create(tenant, "NR9.I04.INTERMITENCIA", 2,
                start, end, null, new BigDecimal("12.5"),
                List.of(new PrimaryDataLink(PrimaryDataLink.Kind.NETWORK_INCIDENT,
                        a.lineage().get(0).primaryId(), "OC-SINT-1")));
        // Mesmo conteudo (alem do id proprio) = mesmo hash de conteudo.
        assertEquals(a.contentHash(), same.contentHash());
        assertTrue(a.exportJson().contains(a.contentHash()));
    }

    @Test
    void hashDivergenteRecusa() {
        assertThrows(IndicatorValidationException.class, () -> new IncentiveAssessment(
                UUID.randomUUID(), tenant, "X", 1, start, end, null, BigDecimal.ONE, lineage(),
                "0".repeat(64)));
    }

    @Test
    void semCongelamentoSemTrilhaSemFormulaRecusa() {
        assertThrows(IndicatorValidationException.class, () -> IncentiveAssessment.create(
                tenant, "X", 1, start, end, null, null, lineage()));
        assertThrows(IndicatorValidationException.class, () -> IncentiveAssessment.create(
                tenant, "X", 1, start, end, null, BigDecimal.ONE, List.of()));
        assertThrows(IndicatorValidationException.class, () -> IncentiveAssessment.create(
                tenant, "X", 0, start, end, null, BigDecimal.ONE, lineage()));
    }

    @Test
    void leituraExternaSoRegulador() {
        IncentiveAssessment a = IncentiveAssessment.create(tenant, "X", 1, start, end, null,
                BigDecimal.ONE, lineage());
        assertTrue(a.exportForExternalProfile("REGULATOR").contains("\"X\""));
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> a.exportForExternalProfile("CERTIFICATION_AUDITOR"));
        assertEquals("EXTERNAL_PROFILE_NOT_AUTHORIZED", e.code());
    }

    @Test
    void simulacaoExigeFatoresDeclaradosENuncaPresumeQPU() {
        // Sem perf.* declarado: recusa - inclusive para a chave "qualidade" generica.
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> TariffEffectSimulation.simulate("{}", new BigDecimal("1000"), List.of("qualidade")));
        assertEquals("PERF_FACTOR_NOT_DECLARED", e.code());
        // Com fator declarado pela agencia (massa sintetica): 1000 x 2,5% = 25.
        String params = "{\"perf.qualidade\": {\"value\": 2.5, \"origin\": \"AGENCIA-SINTETICA\"}}";
        TariffEffectSimulation.Simulation s = TariffEffectSimulation.simulate(
                params, new BigDecimal("1000"), List.of("qualidade"));
        assertEquals(0, new BigDecimal("25").compareTo(s.effectAmount().stripTrailingZeros()));
        // loss.target_* sem origem recusa; com origem+vigencia declaradas, le.
        assertThrows(IndicatorValidationException.class,
                () -> SheetParameters.lossTarget("{}", "loss.target_anual"));
        SheetParameters.DeclaredTarget t = SheetParameters.lossTarget(
                "{\"loss.target_anual\": {\"value\": 25, \"origin\": \"AGENCIA-SINTETICA\", \"validFrom\": \"2026-01-01\"}}",
                "loss.target_anual");
        assertEquals(0, new BigDecimal("25").compareTo(t.value()));
    }
}
