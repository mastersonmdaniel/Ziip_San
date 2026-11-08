package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.CustomerFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.LinkFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.REF;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.only;
import static org.assertj.core.api.Assertions.assertThat;

/** P3.32 - duplicidades (documento e inscricao) e unidade sem usuario. */
class DuplicateAndUserRulesTest {

    private final CadastralQualityEngine engine = new CadastralQualityEngine();

    private CustomerFact customerWithHash(java.util.UUID customerId, String hash) {
        return new CustomerFact(customerId, "PF", true, hash, false, Boolean.TRUE,
                "REGULAR", LocalDate.of(1990, 1, 1), false);
    }

    @Test
    void documentoDuplicadoGeraParaOsExcedentesNaoParaOPrimeiro() {
        // Tres clientes ativos com o mesmo indice cego: o primeiro (menor
        // id) e a referencia; os outros dois sao os achados.
        var snapshot = CadastralSnapshot.builder()
                .customer(customerWithHash(id(3), "hash-igual"))
                .customer(customerWithHash(id(1), "hash-igual"))
                .customer(customerWithHash(id(2), "hash-igual"))
                .customer(customerWithHash(id(4), "hash-outro"))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.DUPLICATE_DOCUMENT);
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(QualityFinding::customerId)
                .containsExactlyInAnyOrder(id(2), id(3));
        assertThat(findings.get(0).severity()).isEqualTo(QualitySeverity.CRITICAL);
        // A evidencia traz so o prefixo do indice cego, nunca documento em claro.
        assertThat(findings.get(0).detail()).contains("hash-igu");
    }

    @Test
    void clienteExcluidoNaoContaComoDuplicidade() {
        var excluido = new CustomerFact(id(5), "PF", true, "hash-igual", false, Boolean.TRUE,
                "REGULAR", LocalDate.of(1990, 1, 1), true);
        var snapshot = CadastralSnapshot.builder()
                .customer(customerWithHash(id(6), "hash-igual"))
                .customer(excluido)
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.DUPLICATE_DOCUMENT)).isEmpty();
    }

    @Test
    void inscricaoDuplicadaGeraParaAUnidadeExcedente() {
        var quadra = id(100);
        var snapshot = CadastralSnapshot.builder()
                .unit(new UnitFact(id(10), "ACTIVE", quadra, 5, 2, null, id(820),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(11), "ACTIVE", quadra, 5, 2, null, id(821),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(12), "ACTIVE", quadra, 5, 3, null, id(822),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(13), "ACTIVE", null, null, null, null, id(823),
                        true, Boolean.TRUE, null, null, false))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.DUPLICATE_INSCRIPTION))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(11));
    }

    @Test
    void inscricaoComProvisoriaNaoGeraDuplicidade() {
        // Correcao D5: a regra e sobre unidades ATIVAS. Par ACTIVE +
        // PROVISIONAL com a mesma inscricao: nenhum achado. Trio com duas
        // ACTIVE e uma PROVISIONAL: 1 achado, so para a ACTIVE excedente.
        var quadra = id(100);
        var par = CadastralSnapshot.builder()
                .unit(new UnitFact(id(14), "ACTIVE", quadra, 7, 1, null, id(825),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(15), "PROVISIONAL", quadra, 7, 1, null, id(826),
                        true, Boolean.TRUE, null, null, false))
                .build();
        assertThat(only(engine.evaluate(par, QualityFixture.fullParams(), REF),
                QualityRuleCode.DUPLICATE_INSCRIPTION)).isEmpty();

        var trio = CadastralSnapshot.builder()
                .unit(new UnitFact(id(16), "ACTIVE", quadra, 8, 1, null, id(827),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(17), "PROVISIONAL", quadra, 8, 1, null, id(828),
                        true, Boolean.TRUE, null, null, false))
                .unit(new UnitFact(id(18), "ACTIVE", quadra, 8, 1, null, id(829),
                        true, Boolean.TRUE, null, null, false))
                .build();
        assertThat(only(engine.evaluate(trio, QualityFixture.fullParams(), REF),
                QualityRuleCode.DUPLICATE_INSCRIPTION))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(18));
    }

    @Test
    void unidadeAtivaSemUsuarioGeraEComUsuarioOuProvisoriaNao() {
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(20)))
                .unit(QualityFixture.unit(id(21)))
                .unit(new UnitFact(id(22), "PROVISIONAL", null, null, null, null, id(824),
                        true, Boolean.TRUE, null, null, false))
                .unit(QualityFixture.unit(id(23)))
                .customer(QualityFixture.customer(id(30)))
                .link(QualityFixture.userLink(id(21), id(30)))
                .link(new LinkFact(id(23), id(30), "OWNER", true))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        // 20: nenhum vinculo. 23: so OWNER (usuario e papel USER). 21: com
        // USER vigente. 22: PROVISIONAL nao e julgada.
        assertThat(only(result, QualityRuleCode.UNIT_WITHOUT_USER))
                .extracting(QualityFinding::unitId)
                .containsExactlyInAnyOrder(id(20), id(23));
    }

    @Test
    void vinculoDeUsuarioEncerradoNaoConta() {
        var snapshot = CadastralSnapshot.builder()
                .unit(QualityFixture.unit(id(25)))
                .customer(QualityFixture.customer(id(31)))
                .link(new LinkFact(id(25), id(31), "USER", false))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.UNIT_WITHOUT_USER))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(25));
    }

    @Test
    void massaLimpaNaoGeraNenhumAchado() {
        // Unidade completa + titular completo: a varredura inteira volta vazia.
        var snapshot = CadastralSnapshot.builder()
                .customer(QualityFixture.customer(id(40)))
                .unit(QualityFixture.unit(id(41)))
                .link(QualityFixture.userLink(id(41), id(40)))
                .contact(QualityFixture.verifiedContact(id(40)))
                .economy(QualityFixture.economies(id(41), "RESIDENTIAL", 1))
                .meter(new CadastralSnapshot.MeterFact(id(41), id(340), 2025,
                        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 1)))
                .reading(new CadastralSnapshot.ReadingFact(id(41), 0))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(result.findings()).isEmpty();
        assertThat(result.skipped()).isEmpty();
    }
}
