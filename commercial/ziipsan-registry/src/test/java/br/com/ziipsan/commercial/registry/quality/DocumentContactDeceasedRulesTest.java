package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.ContactFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.CustomerFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.DeathAlertFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.LinkFact;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.REF;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.only;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.32 - regras de documento, contato e possivel obito sobre massa
 * sintetica: cada cenario suja um fato e confere so a regra dele.
 */
class DocumentContactDeceasedRulesTest {

    private final CadastralQualityEngine engine = new CadastralQualityEngine();

    @Test
    void documentoAusenteSemDispensaGeraAchadoEDispensadoNao() {
        var semDoc = new CustomerFact(id(1), "PF", false, null, false, null, null,
                LocalDate.of(1990, 1, 1), false);
        var dispensado = new CustomerFact(id(2), "PF", false, null, true, null, null,
                LocalDate.of(1990, 1, 1), false);
        var snapshot = CadastralSnapshot.builder().customer(semDoc).customer(dispensado).build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.DOCUMENT_MISSING);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).customerId()).isEqualTo(id(1));
        assertThat(findings.get(0).severity()).isEqualTo(QualitySeverity.HIGH);
    }

    @Test
    void documentoInvalidoPorDigitoOuNotFoundEIrregularPorSituacao() {
        var digitoRuim = new CustomerFact(id(3), "PF", true, "hash-3", false, Boolean.FALSE,
                "REGULAR", LocalDate.of(1990, 1, 1), false);
        var naoEncontrado = new CustomerFact(id(4), "PJ", true, "hash-4", false, Boolean.TRUE,
                "NOT_FOUND", LocalDate.of(1990, 1, 1), false);
        var irregular = new CustomerFact(id(5), "PF", true, "hash-5", false, Boolean.TRUE,
                "IRREGULAR", LocalDate.of(1990, 1, 1), false);
        var naoVerificavel = new CustomerFact(id(6), "PF", true, "hash-6", false, null,
                "UNABLE_TO_VERIFY", LocalDate.of(1990, 1, 1), false);
        var snapshot = CadastralSnapshot.builder()
                .customer(digitoRuim).customer(naoEncontrado).customer(irregular).customer(naoVerificavel)
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.DOCUMENT_INVALID))
                .extracting(QualityFinding::customerId)
                .containsExactlyInAnyOrder(id(3), id(4));
        assertThat(only(result, QualityRuleCode.DOCUMENT_IRREGULAR))
                .extracting(QualityFinding::customerId)
                .containsExactly(id(5));
    }

    @Test
    void contatoNaoVerificadoSoParaTitularComVinculoVigente() {
        var titular = QualityFixture.customer(id(10));
        var semVinculo = QualityFixture.customer(id(11));
        var unidade = QualityFixture.unit(id(20));
        var snapshot = CadastralSnapshot.builder()
                .customer(titular).customer(semVinculo).unit(unidade)
                .link(QualityFixture.userLink(id(20), id(10)))
                .contact(new ContactFact(id(10), "UNVERIFIED", null, null))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.CONTACT_UNVERIFIED);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).customerId()).isEqualTo(id(10));
        // Severidade vem do parametro da massa (LOW), nao da fabrica (MEDIUM).
        assertThat(findings.get(0).severity()).isEqualTo(QualitySeverity.LOW);
    }

    @Test
    void contatoVerificadoVigenteLimpaEVerificadoVencidoNao() {
        var titularOk = QualityFixture.customer(id(12));
        var titularVencido = QualityFixture.customer(id(13));
        var snapshot = CadastralSnapshot.builder()
                .customer(titularOk).customer(titularVencido)
                .unit(QualityFixture.unit(id(21))).unit(QualityFixture.unit(id(22)))
                .link(QualityFixture.userLink(id(21), id(12)))
                .link(QualityFixture.userLink(id(22), id(13)))
                .contact(QualityFixture.verifiedContact(id(12)))
                .contact(new ContactFact(id(13), "VERIFIED", null, LocalDate.of(2026, 1, 1)))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.CONTACT_UNVERIFIED))
                .extracting(QualityFinding::customerId)
                .containsExactly(id(13));
    }

    @Test
    void contatoSoJulgaTitularUserENaoQuemSoTemVinculoDeProprietario() {
        // Correcao R3: cliente ligado SO como OWNER (sem papel USER) nao e
        // o titular da regra — nao e julgado, mesmo sem contato verificado.
        var proprietario = QualityFixture.customer(id(14));
        var snapshot = CadastralSnapshot.builder()
                .customer(proprietario)
                .unit(QualityFixture.unit(id(23)))
                .link(new LinkFact(id(23), id(14), "OWNER", true))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.CONTACT_UNVERIFIED)).isEmpty();
    }

    @Test
    void contatoVerificadoAindaNaoVigenteNaoContaComoVerificado() {
        // Correcao R3: valid_from futuro (depois de REF = 2026-11-07) — o
        // contato VERIFIED ainda nao esta vigente, entao o titular e julgado.
        var titular = QualityFixture.customer(id(15));
        var snapshot = CadastralSnapshot.builder()
                .customer(titular)
                .unit(QualityFixture.unit(id(24)))
                .link(QualityFixture.userLink(id(24), id(15)))
                .contact(new ContactFact(id(15), "VERIFIED", LocalDate.of(2027, 1, 1), null))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.CONTACT_UNVERIFIED))
                .extracting(QualityFinding::customerId)
                .containsExactly(id(15));
    }

    @Test
    void possivelObitoPorValidacaoDeceasedTemPrecedenciaSobreIdade() {
        var falecido = new CustomerFact(id(30), "PF", true, "hash-30", false, Boolean.TRUE,
                "DECEASED", LocalDate.of(1920, 5, 1), false);
        var snapshot = CadastralSnapshot.builder().customer(falecido).build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.HOLDER_POSSIBLY_DECEASED);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).detail()).contains("DECEASED");
    }

    @Test
    void possivelObitoPorAlertaPendenteEConfirmadoNaoGera() {
        var pendente = QualityFixture.customer(id(31));
        var confirmado = QualityFixture.customer(id(32));
        var snapshot = CadastralSnapshot.builder()
                .customer(pendente).customer(confirmado)
                .deathAlert(new DeathAlertFact(id(31), id(40), "PENDING_REVIEW"))
                .deathAlert(new DeathAlertFact(id(32), id(41), "CONFIRMED"))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.HOLDER_POSSIBLY_DECEASED);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).customerId()).isEqualTo(id(31));
        assertThat(findings.get(0).unitId()).isEqualTo(id(40));
    }

    @Test
    void possivelObitoPorIdadeNoLimiarDoTenant() {
        // REF = 2026-11-07; limiar da massa = 100 anos.
        // Nascido em 1920-05-01 -> 106 anos: gera. Nascido em 1950-01-01 -> 76: nao gera.
        var idoso = new CustomerFact(id(33), "PF", true, "hash-33", false, Boolean.TRUE,
                "REGULAR", LocalDate.of(1920, 5, 1), false);
        var adulto = new CustomerFact(id(34), "PF", true, "hash-34", false, Boolean.TRUE,
                "REGULAR", LocalDate.of(1950, 1, 1), false);
        var snapshot = CadastralSnapshot.builder().customer(idoso).customer(adulto).build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.HOLDER_POSSIBLY_DECEASED))
                .extracting(QualityFinding::customerId)
                .containsExactly(id(33));
    }

    @Test
    void regraDesligadaNaoGeraENamedNosIgnorados() {
        var params = QualityFixture.paramsWithRules("""
                {"rules": {"DOCUMENT_MISSING": {"enabled": false},
                           "DOCUMENT_INVALID": {"enabled": false},
                           "DOCUMENT_IRREGULAR": {"enabled": false}},
                 "thresholds": {"meterVerificationIntervalYears": 7,
                                "possiblyDeceasedAgeYears": 100,
                                "undeliveredWindowDays": 90},
                 "minAreaPerEconomyM2": {"RESIDENTIAL": 20},
                 "cnaeCompatibility": {"47": ["COMMERCIAL"]}}""");
        var semDoc = new CustomerFact(id(50), "PF", false, null, false, null, null,
                LocalDate.of(1990, 1, 1), false);
        var snapshot = CadastralSnapshot.builder().customer(semDoc).build();

        var result = engine.evaluate(snapshot, params, REF);

        assertThat(result.findings()).isEmpty();
        assertThat(result.skipped()).extracting(s -> s.code().name())
                .contains("DOCUMENT_MISSING", "DOCUMENT_INVALID", "DOCUMENT_IRREGULAR");
    }
}
