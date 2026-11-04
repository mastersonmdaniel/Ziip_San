package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;
import br.com.ziipsan.commercial.fiscal.support.InMemoryFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cadastro e resolução de alíquotas com vigência (P2.4).
 *
 * <p>Valores normativos usados aqui: só os de 2026 (ano de teste: CBS 0,9% e IBS 0,1%,
 * fato normativo do plano). Todos os demais valores abaixo são VALORES DE TESTE para a
 * lógica de seleção por vigência, explicitamente marcados: o plano não publica o valor
 * da CBS plena de 2027 nem os percentuais da transição do IBS de 2029 a 2032, e nenhum
 * deles é inventado aqui como valor normativo.
 */
class TaxRateServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CBS_TEST_YEAR = "LC 214/2025 (ano de teste de 2026; fato normativo do plano)";
    private static final String CBS_FULL = "LC 214/2025 (CBS plena a partir de 01/01/2027; fato normativo do plano)";
    private static final String IBS_TRANSITION = "LC 214/2025, art. 544 (transicao do IBS; fato normativo do plano)";

    private InMemoryFiscalRepository repository;
    private TaxRateService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFiscalRepository();
        service = new TaxRateService(repository);
    }

    private void register(String code, LocalDate from, LocalDate to, String rate, String basis) {
        service.register(TENANT, code, TaxRateService.NATIONAL, from, to, new BigDecimal(rate), basis);
    }

    /** Cronograma completo da P2.4, sem sobreposição: 2026, CBS plena e transição do IBS. */
    private void registerFullSchedule() {
        register("CBS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "0.009", CBS_TEST_YEAR);
        register("CBS", LocalDate.of(2027, 1, 1), null, "0.020", CBS_FULL + " — VALOR DE TESTE");
        register("IBS", LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31), "0.001", CBS_TEST_YEAR);
        register("IBS", LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31), "0.011", IBS_TRANSITION + " — VALOR DE TESTE");
        register("IBS", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), "0.012", IBS_TRANSITION + " — VALOR DE TESTE");
        register("IBS", LocalDate.of(2031, 1, 1), LocalDate.of(2031, 12, 31), "0.013", IBS_TRANSITION + " — VALOR DE TESTE");
        register("IBS", LocalDate.of(2032, 1, 1), null, "0.014", IBS_TRANSITION + " — VALOR DE TESTE");
    }

    @Test
    void testYear2026RatesResolve() {
        register("CBS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "0.009", CBS_TEST_YEAR);
        register("IBS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "0.001", CBS_TEST_YEAR);
        assertEquals(new BigDecimal("0.009"),
                service.resolveNational(TENANT, "CBS", LocalDate.of(2026, 6, 1)));
        assertEquals(new BigDecimal("0.001"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2026, 6, 1)));
    }

    @Test
    void turnoverAt20270101ChangesCbsRate() {
        registerFullSchedule();
        assertEquals(new BigDecimal("0.009"),
                service.resolveNational(TENANT, "CBS", LocalDate.of(2026, 12, 31)));
        assertEquals(new BigDecimal("0.020"),
                service.resolveNational(TENANT, "CBS", LocalDate.of(2027, 1, 1)));
        assertEquals(new BigDecimal("0.020"),
                service.resolveNational(TENANT, "CBS", LocalDate.of(2030, 5, 20)));
    }

    @Test
    void ibsTransitionScheduleResolvesPerYear() {
        registerFullSchedule();
        assertEquals(new BigDecimal("0.001"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2028, 12, 31)));
        assertEquals(new BigDecimal("0.011"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2029, 6, 1)));
        assertEquals(new BigDecimal("0.012"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2030, 6, 1)));
        assertEquals(new BigDecimal("0.013"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2031, 6, 1)));
        assertEquals(new BigDecimal("0.014"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2032, 1, 1)));
        assertEquals(new BigDecimal("0.014"),
                service.resolveNational(TENANT, "IBS", LocalDate.of(2035, 6, 1)));
    }

    @Test
    void missingRateIsError() {
        register("CBS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "0.009", CBS_TEST_YEAR);
        assertThrows(IllegalStateException.class,
                () -> service.resolveNational(TENANT, "CBS", LocalDate.of(2025, 6, 1)));
        assertThrows(IllegalStateException.class,
                () -> service.resolveNational(TENANT, "IBS", LocalDate.of(2026, 6, 1)));
    }

    @Test
    void overlappingRegistrationIsRefusedAndAdjacentIsAccepted() {
        register("CBS", LocalDate.of(2026, 1, 1), null, "0.009", CBS_TEST_YEAR);
        assertThrows(IllegalStateException.class, () -> service.register(TENANT, "CBS",
                TaxRateService.NATIONAL, LocalDate.of(2026, 6, 1), null,
                new BigDecimal("0.010"), CBS_FULL + " — VALOR DE TESTE"));
        // Vigência adjacente (sem dia em comum) após fechar a anterior: aceita.
        InMemoryFiscalRepository second = new InMemoryFiscalRepository();
        TaxRateService secondService = new TaxRateService(second);
        secondService.register(TENANT, "CBS", TaxRateService.NATIONAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("0.009"), CBS_TEST_YEAR);
        secondService.register(TENANT, "CBS", TaxRateService.NATIONAL,
                LocalDate.of(2027, 1, 1), null,
                new BigDecimal("0.020"), CBS_FULL + " — VALOR DE TESTE");
        assertEquals(new BigDecimal("0.020"),
                secondService.resolveNational(TENANT, "CBS", LocalDate.of(2027, 1, 1)));
    }

    @Test
    void scopeIsolationAllowsSameDatesInAnotherScope() {
        register("CBS", LocalDate.of(2026, 1, 1), null, "0.009", CBS_TEST_YEAR);
        service.register(TENANT, "CBS", "ESTADO-TESTE",
                LocalDate.of(2026, 1, 1), null, new BigDecimal("0.010"), "Escopo de teste");
        assertEquals(new BigDecimal("0.009"),
                service.resolve(TENANT, "CBS", TaxRateService.NATIONAL, LocalDate.of(2026, 6, 1)));
        assertEquals(new BigDecimal("0.010"),
                service.resolve(TENANT, "CBS", "ESTADO-TESTE", LocalDate.of(2026, 6, 1)));
    }

    @Test
    void invalidRegistrationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.register(TENANT, "XYZ",
                TaxRateService.NATIONAL, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("0.009"), null));
        assertThrows(IllegalArgumentException.class, () -> service.register(TENANT, "CBS",
                "  ", LocalDate.of(2026, 1, 1), null, new BigDecimal("0.009"), null));
        assertThrows(IllegalArgumentException.class, () -> service.register(TENANT, "CBS",
                TaxRateService.NATIONAL, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31),
                new BigDecimal("0.009"), null));
        assertThrows(IllegalArgumentException.class, () -> service.register(TENANT, "CBS",
                TaxRateService.NATIONAL, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("-0.001"), null));
        assertThrows(IllegalArgumentException.class, () -> service.register(TENANT, "CBS",
                TaxRateService.NATIONAL, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("1.0001"), null));
        assertThrows(NullPointerException.class, () -> service.register(TENANT, "CBS",
                TaxRateService.NATIONAL, null, null, new BigDecimal("0.009"), null));
        // Alíquota zero é válida (tributo desligado para a vigência).
        service.register(TENANT, "IBS", TaxRateService.NATIONAL,
                LocalDate.of(2026, 1, 1), null, BigDecimal.ZERO, "Desligado em teste");
        assertEquals(BigDecimal.ZERO,
                service.resolveNational(TENANT, "IBS", LocalDate.of(2026, 6, 1)));
    }

    @Test
    void informativeFlagFollowsTestYear() {
        assertEquals(true, TaxRateValidity.cbsIbsInformative(LocalDate.of(2026, 12, 31)));
        assertEquals(false, TaxRateValidity.cbsIbsInformative(LocalDate.of(2027, 1, 1)));
    }
}
