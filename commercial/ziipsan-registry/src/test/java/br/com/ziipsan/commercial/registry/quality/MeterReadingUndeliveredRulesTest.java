package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.MeterFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.ReadingFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UndeliveredFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.REF;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.only;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.32 - hidrometro (ausente/vencido), leitura em N ciclos e conta nao
 * entregue (P2.8) sobre massa sintetica. Datas conferidas a mao:
 * REF = 2026-11-07; intervalo da massa = 7 anos; N = 3 ciclos; janela = 90 dias.
 */
class MeterReadingUndeliveredRulesTest {

    private final CadastralQualityEngine engine = new CadastralQualityEngine();

    @Test
    void unidadeAtivaSemHidrometroGeraEProvisoriaNao() {
        var ativa = QualityFixture.unit(id(1));
        var provisoria = new UnitFact(id(2), "PROVISIONAL", null, null, null, null, id(810),
                true, Boolean.TRUE, null, null, false);
        var comMedidor = QualityFixture.unit(id(3));
        var snapshot = CadastralSnapshot.builder()
                .unit(ativa).unit(provisoria).unit(comMedidor)
                .meter(new MeterFact(id(3), id(300), 2024, LocalDate.of(2024, 1, 10),
                        LocalDate.of(2024, 1, 10)))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.ACTIVE_UNIT_WITHOUT_METER))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(1));
    }

    @Test
    void semLeituraEmNCiclosGeraENoLimiarExatoTambem() {
        // N = 3: 3 ciclos gera; 2 nao gera; sem fato de leitura, nao julga.
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(10))).unit(QualityFixture.unit(id(11)))
                .unit(QualityFixture.unit(id(12)))
                .meter(new MeterFact(id(10), id(310), 2024, LocalDate.of(2024, 1, 1), null))
                .meter(new MeterFact(id(11), id(311), 2024, LocalDate.of(2024, 1, 1), null))
                .meter(new MeterFact(id(12), id(312), 2024, LocalDate.of(2024, 1, 1), null))
                .reading(new ReadingFact(id(10), 3))
                .reading(new ReadingFact(id(11), 2))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.UNIT_WITHOUT_READING))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(10));
    }

    @Test
    void hidrometroVencidoPelaUltimaVerificacao() {
        // Ultima verificacao 2018-03-10 + 7 anos = 2025-03-10 <= REF: vencido.
        // Ultima verificacao 2021-01-15 + 7 anos = 2028-01-15 > REF: vigente.
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(20))).unit(QualityFixture.unit(id(21)))
                .meter(new MeterFact(id(20), id(320), 2018, LocalDate.of(2018, 3, 10),
                        LocalDate.of(2018, 3, 10)))
                .meter(new MeterFact(id(21), id(321), 2021, LocalDate.of(2021, 1, 15),
                        LocalDate.of(2021, 1, 15)))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.METER_VERIFICATION_EXPIRED);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).unitId()).isEqualTo(id(20));
        assertThat(findings.get(0).detail()).contains("2025-03-10");
    }

    @Test
    void hidrometroSemVerificacaoRegistradaNaoEJulgadoComoNaED14() {
        // Correcao D3: a ED-14 (VerificationSchedule.nextVerificationOn)
        // RECUSA calcular a proxima verificacao sem uma verificacao
        // registrada — nao ha fallback para instalacao nem fabricacao.
        // Instalacao antiga (2019-06-01) e fabricacao antiga (2015) SEM
        // verificacao registrada: nenhum dos dois e julgado "vencido".
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(22))).unit(QualityFixture.unit(id(23)))
                .unit(QualityFixture.unit(id(24)))
                .meter(new MeterFact(id(22), id(322), 2019, LocalDate.of(2019, 6, 1), null))
                .meter(new MeterFact(id(23), id(323), 2015, null, null))
                .meter(new MeterFact(id(24), id(324), null, null, null))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.METER_VERIFICATION_EXPIRED)).isEmpty();
    }

    @Test
    void contaNaoEntregueNaJanelaGeraUmAchadoPorUnidadeComOMaisRecente() {
        // Janela de 90 dias antes de 2026-11-07: desde 2026-08-09.
        // Unidade 30: retornos em 2026-10-01 (DEVOLUCAO) e 2026-11-02
        // (ENDERECO_NAO_LOCALIZADO) -> 1 achado, ultimo motivo o de 11-02,
        // com a marca de endereco nao verificado.
        // Unidade 31: retorno em 2026-07-01 (129 dias) -> fora da janela.
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(30))).unit(QualityFixture.unit(id(31)))
                .undelivered(new UndeliveredFact(id(30), id(400), "DEVOLUCAO", "CORREIOS",
                        LocalDate.of(2026, 10, 1)))
                .undelivered(new UndeliveredFact(id(30), id(401), "ENDERECO_NAO_LOCALIZADO",
                        "ENTREGA_EM_CAMPO", LocalDate.of(2026, 11, 2)))
                .undelivered(new UndeliveredFact(id(31), id(402), "DEVOLUCAO", "GRAFICA",
                        LocalDate.of(2026, 7, 1)))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.BILL_UNDELIVERED);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).unitId()).isEqualTo(id(30));
        assertThat(findings.get(0).detail())
                .contains("2 retorno(s)")
                .contains("ENDERECO_NAO_LOCALIZADO")
                .contains("endereco nao verificado");
    }

    @Test
    void semLimiarDeCiclosOuDeJanelaRegraNaoRodaENamed() {
        var params = QualityFixture.paramsWithRules("{\"rules\": {}}");
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(50)))
                .meter(new MeterFact(id(50), id(350), 2024, LocalDate.of(2024, 1, 1), null))
                .reading(new ReadingFact(id(50), 99))
                .undelivered(new UndeliveredFact(id(50), id(450), "DEVOLUCAO", "CORREIOS",
                        LocalDate.of(2026, 11, 1)))
                .build();

        var result = engine.evaluate(snapshot, params, REF);

        assertThat(only(result, QualityRuleCode.UNIT_WITHOUT_READING)).isEmpty();
        assertThat(only(result, QualityRuleCode.BILL_UNDELIVERED)).isEmpty();
        assertThat(result.skipped())
                .filteredOn(s -> s.reason() == CadastralQualityEngine.SkipReason.UNCONFIGURED)
                .extracting(s -> s.code().name())
                .contains("UNIT_WITHOUT_READING", "BILL_UNDELIVERED");
    }
}
