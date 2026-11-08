package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.32 - quality.rules: ausencia e erro explicito; limiar nunca presumido. */
class QualityParametersTest {

    @Test
    void leLimiaresMapasESeveridadeDoJsonViaClasseJsonDoCore() {
        var params = QualityFixture.fullParams();
        assertThat(params.cyclesWithoutReading()).hasValue(3);
        assertThat(params.meterVerificationIntervalYears()).hasValue(7);
        assertThat(params.possiblyDeceasedAgeYears()).hasValue(100);
        assertThat(params.undeliveredWindowDays()).hasValue(90);
        assertThat(params.minAreaPerEconomyM2("RESIDENTIAL"))
                .contains(new BigDecimal("20"));
        assertThat(params.allowedClassesForCnaeDivision("47"))
                .contains(Set.of("COMMERCIAL"));
        // Severidade sobrescrita no parametro e a de fabrica das demais.
        assertThat(params.severityFor(QualityRuleCode.CONTACT_UNVERIFIED))
                .isEqualTo(QualitySeverity.LOW);
        assertThat(params.severityFor(QualityRuleCode.DOCUMENT_MISSING))
                .isEqualTo(QualitySeverity.HIGH);
        assertThat(params.ruleEnabled(QualityRuleCode.DOCUMENT_MISSING)).isTrue();
    }

    @Test
    void parametroAusenteEErroExplicitoNuncaPresumido() {
        assertThatThrownBy(() -> QualityParameters.fromValues(Map.of()))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> QualityParameters.fromValues(Map.of("outra.chave", "{}")))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    void limiarAusenteVoltaVazioParaOMotorRegistrarNaoConfigurado() {
        var params = QualityFixture.paramsWithRules("{\"rules\": {}}");
        assertThat(params.cyclesWithoutReading()).isEmpty();
        assertThat(params.meterVerificationIntervalYears()).isEmpty();
        assertThat(params.hasCnaeCompatibility()).isFalse();
        assertThat(params.hasAreaThresholds()).isFalse();
        assertThat(params.allowedClassesForCnaeDivision("47")).isEmpty();
    }

    @Test
    void regraDesligadaPeloTenantERespeitada() {
        var params = QualityFixture.paramsWithRules(
                "{\"rules\": {\"CONTACT_UNVERIFIED\": {\"enabled\": false}}}");
        assertThat(params.ruleEnabled(QualityRuleCode.CONTACT_UNVERIFIED)).isFalse();
        assertThat(params.ruleEnabled(QualityRuleCode.DOCUMENT_MISSING)).isTrue();
    }

    @Test
    void severidadeForaDoCatalogoEErroExplicito() {
        var params = QualityFixture.paramsWithRules(
                "{\"rules\": {\"DOCUMENT_MISSING\": {\"severity\": \"ALTISSIMA\"}}}");
        assertThatThrownBy(() -> params.severityFor(QualityRuleCode.DOCUMENT_MISSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALTISSIMA");
    }

    @Test
    void limiarNaoPositivoEErroExplicito() {
        var params = QualityFixture.paramsWithRules(
                "{\"thresholds\": {\"undeliveredWindowDays\": 0}}");
        assertThatThrownBy(params::undeliveredWindowDays)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undeliveredWindowDays");
    }

    @Test
    void intervaloDeVerificacaoAcimaDeSeteAnosEErroExplicitoComoNaED14() {
        // Correcao D3: a trava 1..7 da ED-14 (padrao 7, so reduz) vale
        // tambem para o quality.rules — 15 ou 8 anos nao passam.
        var quinze = QualityFixture.paramsWithRules(
                "{\"thresholds\": {\"meterVerificationIntervalYears\": 15}}");
        assertThatThrownBy(quinze::meterVerificationIntervalYears)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("meterVerificationIntervalYears")
                .hasMessageContaining("15");
        var oito = QualityFixture.paramsWithRules(
                "{\"thresholds\": {\"meterVerificationIntervalYears\": 8}}");
        assertThatThrownBy(oito::meterVerificationIntervalYears)
                .isInstanceOf(IllegalStateException.class);
        // Nos extremos do intervalo valido, le normalmente.
        var um = QualityFixture.paramsWithRules(
                "{\"thresholds\": {\"meterVerificationIntervalYears\": 1}}");
        assertThat(um.meterVerificationIntervalYears()).hasValue(1);
        var sete = QualityFixture.paramsWithRules(
                "{\"thresholds\": {\"meterVerificationIntervalYears\": 7}}");
        assertThat(sete.meterVerificationIntervalYears()).hasValue(7);
    }

    @Test
    void limiarPorRegraTemPrecedenciaSobreOGlobal() {
        var params = QualityFixture.paramsWithRules("""
                {"rules": {"UNIT_WITHOUT_READING": {"thresholds": {"cyclesWithoutReading": 5}}},
                 "thresholds": {"cyclesWithoutReading": 3}}""");
        assertThat(params.cyclesWithoutReading()).hasValue(5);
    }
}
