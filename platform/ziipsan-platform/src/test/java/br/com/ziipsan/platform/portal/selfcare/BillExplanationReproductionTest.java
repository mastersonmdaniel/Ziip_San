package br.com.ziipsan.platform.portal.selfcare;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BillExplanationReproductionTest {
    /**
     * Calculo gravado CONGELADO (golden da correcao D2): os valores de
     * entrada abaixo sao literais fixos e a explicacao esperada e a
     * literal gravada em {@link #EXPECTED_FACTORS}/{@link #EXPECTED_FULL_TEXT}
     * — nada e derivado chamando a funcao sob teste. Variacoes conferidas
     * a mao: consumo 18 contra 12 no mes anterior = +50%; 18 contra a
     * media 14 = +28,6% (400/14 = 28,571..., 1 casa, HALF_UP); componentes
     * 90,00 + 72,00 + 10,00 + 3,00 - 5,00 = 170,00 = total gravado.
     */
    static BillExplanation.RecordedCalculation recordedCalculation() {
        return new BillExplanation.RecordedCalculation(35, 30,
                new BigDecimal("18"), new BigDecimal("12"), new BigDecimal("14"),
                "FAIXA_2", new BigDecimal("4.5"),
                new BigDecimal("90.00"), new BigDecimal("72.00"), new BigDecimal("10.00"),
                new BigDecimal("3.00"), new BigDecimal("5.00"), new BigDecimal("170.00"));
    }

    static final List<BillExplanation.Factor> EXPECTED_FACTORS = List.of(
            new BillExplanation.Factor("READING_DAYS",
                    "Periodo de leitura de 35 dias (mes anterior: 30 dias)."),
            new BillExplanation.Factor("CONSUMPTION_VS_PREVIOUS",
                    "Consumo de 18 m3 contra 12 m3 no mes anterior (+50%)."),
            new BillExplanation.Factor("CONSUMPTION_VS_AVERAGE",
                    "Consumo contra a media gravada de 14 m3 (+28,6%)."),
            new BillExplanation.Factor("BAND",
                    "Faixa tarifaria atingida: FAIXA_2."),
            new BillExplanation.Factor("ADJUSTMENT",
                    "Reajuste tarifario aplicado de 4,5%."),
            new BillExplanation.Factor("COMPONENTS",
                    "Composicao: agua R$ 90,00, esgoto R$ 72,00, servicos R$ 10,00, "
                            + "encargos R$ 3,00, creditos R$ 5,00. Total R$ 170,00."));

    static final String EXPECTED_FULL_TEXT =
            "Periodo de leitura de 35 dias (mes anterior: 30 dias). "
                    + "Consumo de 18 m3 contra 12 m3 no mes anterior (+50%). "
                    + "Consumo contra a media gravada de 14 m3 (+28,6%). "
                    + "Faixa tarifaria atingida: FAIXA_2. "
                    + "Reajuste tarifario aplicado de 4,5%. "
                    + "Composicao: agua R$ 90,00, esgoto R$ 72,00, servicos R$ 10,00, "
                    + "encargos R$ 3,00, creditos R$ 5,00. Total R$ 170,00.";

    @Test void reproducaoIdenticaDaContaGravada() {
        var explanation = BillExplanation.explain(recordedCalculation());
        // Cada variacao/fator asserido contra o golden literal, um a um.
        assertEquals(EXPECTED_FACTORS.size(), explanation.factors().size());
        for (int i = 0; i < EXPECTED_FACTORS.size(); i++) {
            assertEquals(EXPECTED_FACTORS.get(i).code(), explanation.factors().get(i).code(),
                    "codigo do fator " + i);
            assertEquals(EXPECTED_FACTORS.get(i).text(), explanation.factors().get(i).text(),
                    "texto do fator " + EXPECTED_FACTORS.get(i).code());
        }
        assertEquals(EXPECTED_FACTORS, explanation.factors());
        assertEquals(EXPECTED_FULL_TEXT, explanation.fullText());
    }
    @Test void componentesQueNaoFechamRecusam() {
        var bad = new BillExplanation.RecordedCalculation(30, 30,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                "FAIXA_1", BigDecimal.ZERO, new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("99.00"));
        assertThrows(IllegalStateException.class, () -> BillExplanation.explain(bad));
    }
    @Test void funcaoDoAssistenteExigeVinculo() {
        var fn = AssistantBillFunction.over(
                (t, b) -> Optional.of(recordedCalculation()),
                (t, s, b) -> "dono".equals(s));
        assertNotNull(fn.explainBill(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.BILL));
        assertThrows(IllegalStateException.class,
                () -> fn.explainBill(Ed38TestSupport.TENANT, "outro", Ed38TestSupport.BILL));
        assertThrows(IllegalStateException.class,
                () -> AssistantBillFunction.over((t, b) -> Optional.empty(), (t, s, b) -> true)
                        .explainBill(Ed38TestSupport.TENANT, "dono", UUID.randomUUID()));
    }
}
