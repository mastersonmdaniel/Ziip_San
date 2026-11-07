package br.com.ziipsan.commercial.accounting.managerial;

import br.com.ziipsan.commercial.accounting.managerial.ManagerialCosts.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ManagerialCostsTest {
    private static BigDecimal d(String v){ return new BigDecimal(v); }
    @Test @DisplayName("a mao: custo/m3 = 10000/2000 = 5,0000; custo/ligacao = 10000/500 = 20,0000; margem = (1000-750)/1000 = 0,2500; subsidio = 1000-600 = 400")
    void unitCosts() {
        assertThat(ManagerialCosts.costPerBilledCubicMeter(d("10000.00"), d("2000"))).isEqualByComparingTo("5.0000");
        assertThat(ManagerialCosts.costPerConnection(d("10000.00"), 500)).isEqualByComparingTo("20.0000");
        assertThat(ManagerialCosts.margin(d("1000.00"), d("750.00"))).isEqualByComparingTo("0.2500");
        assertThat(ManagerialCosts.subsidyCost(d("1000.00"), d("600.00"))).isEqualByComparingTo("400.00");
    }
    @Test @DisplayName("OS a mao: material 100 + mao de obra 2h x 50 (vigente) + terceiros 30 = 230, sem frota (ED-59 inexistente); com frota 20 = 250")
    void serviceOrderCost() {
        var rates = List.of(new HourlyRate("ENCANADOR", d("50.00"), LocalDate.of(2026,1,1), null));
        var labor = List.of(new LaborLine("ENCANADOR", d("2"), LocalDate.of(2026,3,10)));
        assertThat(ManagerialCosts.serviceOrderTotalCost(new ServiceOrderCost(d("100.00"), labor, rates, null, d("30.00")))).isEqualByComparingTo("230.00");
        assertThat(ManagerialCosts.serviceOrderTotalCost(new ServiceOrderCost(d("100.00"), labor, rates, d("20.00"), d("30.00")))).isEqualByComparingTo("250.00");
    }
    @Test @DisplayName("rateio BY_CONNECTIONS a mao: 1000 entre 300 e 100 ligacoes = 750 e 250")
    void allocation() {
        var out = ManagerialCosts.allocate(d("1000.00"), AllocationMethod.BY_CONNECTIONS, Map.of("A", d("300"), "B", d("100")), null);
        assertThat(out.get("A")).isEqualByComparingTo("750.00");
        assertThat(out.get("B")).isEqualByComparingTo("250.00");
    }
    @Test @DisplayName("rateio com divisao NAO exata nao perde centavos: 100,00 em 3 partes iguais = 33,34 + 33,33 + 33,33 = 100,00 (residuo na maior parcela)")
    void allocationResidueGoesToLargestShare() {
        var keys = new LinkedHashMap<String, BigDecimal>();
        keys.put("A", d("1")); keys.put("B", d("1")); keys.put("C", d("1"));
        var out = ManagerialCosts.allocate(d("100.00"), AllocationMethod.BY_CONNECTIONS, keys, null);
        assertThat(out.get("A")).isEqualByComparingTo("33.34");
        assertThat(out.get("B")).isEqualByComparingTo("33.33");
        assertThat(out.get("C")).isEqualByComparingTo("33.33");
        assertThat(out.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }
    @Test @DisplayName("por categoria de verdade: RESIDENCIAL (custo 10000, m3 2000, 500 lig, receita 20000) e COMERCIAL (750, 100, 10, 1000) com indicadores separados")
    void byCategory() {
        var results = ManagerialCosts.byCategory(List.of(
            new CategoryCost("RESIDENCIAL", d("20000.00"), d("10000.00"), d("2000"), 500),
            new CategoryCost("COMERCIAL", d("1000.00"), d("750.00"), d("100"), 10)));
        assertThat(results).hasSize(2);
        var res = results.get(0);
        assertThat(res.category()).isEqualTo("RESIDENCIAL");
        assertThat(res.costPerBilledCubicMeter()).isEqualByComparingTo("5.0000");
        assertThat(res.costPerConnection()).isEqualByComparingTo("20.0000");
        assertThat(res.margin()).isEqualByComparingTo("0.5000");
        var com = results.get(1);
        assertThat(com.category()).isEqualTo("COMERCIAL");
        assertThat(com.costPerBilledCubicMeter()).isEqualByComparingTo("7.5000");
        assertThat(com.costPerConnection()).isEqualByComparingTo("75.0000");
        assertThat(com.margin()).isEqualByComparingTo("0.2500");
    }
    @Test @DisplayName("cost.hourly_rates na virada de vigencia: 50 ate 30/06 e 60 de 01/07; OS de agosto usa 60 (2h x 60 = 120; total 100 + 120 + 30 = 250)")
    void hourlyRateTurnover() {
        var rates = List.of(
            new HourlyRate("ENCANADOR", d("50.00"), LocalDate.of(2026,1,1), LocalDate.of(2026,6,30)),
            new HourlyRate("ENCANADOR", d("60.00"), LocalDate.of(2026,7,1), null));
        var labor = List.of(new LaborLine("ENCANADOR", d("2"), LocalDate.of(2026,8,10)));
        assertThat(ManagerialCosts.serviceOrderTotalCost(new ServiceOrderCost(d("100.00"), labor, rates, null, d("30.00")))).isEqualByComparingTo("250.00");
    }
    @Test @DisplayName("cost.hourly_rates sobrepostas para a mesma funcao sao ambiguidade recusada")
    void overlappingHourlyRatesRefused() {
        var rates = List.of(
            new HourlyRate("ENCANADOR", d("50.00"), LocalDate.of(2026,1,1), null),
            new HourlyRate("ENCANADOR", d("55.00"), LocalDate.of(2026,6,1), null));
        var labor = List.of(new LaborLine("ENCANADOR", d("1"), LocalDate.of(2026,8,10)));
        assertThatThrownBy(() -> ManagerialCosts.serviceOrderTotalCost(new ServiceOrderCost(d("0.00"), labor, rates, null, d("0.00"))))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("validacoes (L2): custo-hora negativo, vigencia invertida e horas nao positivas sao recusados")
    void validations() {
        assertThatThrownBy(() -> new HourlyRate("ENCANADOR", d("-1.00"), LocalDate.of(2026,1,1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HourlyRate("ENCANADOR", d("50.00"), LocalDate.of(2026,7,1), LocalDate.of(2026,1,1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaborLine("ENCANADOR", d("0"), LocalDate.of(2026,3,10))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceOrderCost(null, List.of(), List.of(), null, d("0.00"))).isInstanceOf(IllegalArgumentException.class);
    }
}
