package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo anual SINISA assistido (P4.2): pre-preenchimento, valor manual so com
 * justificativa, transicoes registradas, escopo LOCAL x REGIONAL com
 * responsavel, corte da LC.13 e conclusao com certificado datado.
 */
class SinisaCycleTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RESPONSAVEL = UUID.randomUUID();
    private static final OffsetDateTime T0 = OffsetDateTime.of(2027, 5, 13, 9, 0, 0, 0, ZoneOffset.of("-03:00"));
    /** Ano-base de corte ficticio da LC.13 para o teste: primeiro assistido = 2026. */
    private static final int CORTE = 2025;

    private static SinisaCatalog smallCatalog() {
        return SinisaCatalog.fromJson("["
                + "{\"key\": \"AGUA.VOLUME_FATURADO\", \"officialCode\": null, \"form\": \"TECNICO\","
                + " \"name\": \"Volume faturado\", \"unit\": \"m3\", \"sourceKind\": \"METERING\","
                + " \"sourceExpression\": \"metering: soma de billed_consumption do ano-base\"},"
                + "{\"key\": \"BALANCO.ATIVO_TOTAL\", \"officialCode\": null, \"form\": \"BALANCO\","
                + " \"name\": \"Ativo total\", \"unit\": \"R$\", \"sourceKind\": \"MANUAL\","
                + " \"sourceExpression\": \"manual justificado: balanco patrimonial\"}"
                + "]");
    }

    private static SinisaCycle openLocal() {
        return SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                RESPONSAVEL, "Maria Responsavel", 1, CORTE, smallCatalog());
    }

    private static SinisaSourceData sourceWithVolume() {
        return (tenantId, baseYear, variable) -> variable.key().equals("AGUA.VOLUME_FATURADO")
                ? Optional.of(new BigDecimal("7000"))
                : Optional.empty();
    }

    private static SinisaCycle completeCycle() {
        SinisaCycle cycle = openLocal();
        cycle.prefill(sourceWithVolume(), RESPONSAVEL, T0);
        cycle.setManualValue("BALANCO.ATIVO_TOTAL", new BigDecimal("123456.78"),
                "Balanco patrimonial encerrado em 31/12/2026, assinado pelo contador.",
                RESPONSAVEL, T0.plusHours(1));
        cycle.advanceToPreliminary(RESPONSAVEL, T0.plusHours(2), "Conferencia concluida.");
        cycle.registerResponse(RESPONSAVEL, T0.plusDays(30), "Sem ressalvas a versao preliminar.");
        return cycle;
    }

    @Test
    @DisplayName("pre-preenchimento usa a origem e deixa pendente o que a origem nao tem, sem inventar zero")
    void prefillUsaOrigemEPendenteSemZero() {
        SinisaCycle cycle = openLocal();

        var pending = cycle.prefill(sourceWithVolume(), RESPONSAVEL, T0);

        assertThat(cycle.value("AGUA.VOLUME_FATURADO")).isPresent();
        assertThat(cycle.value("AGUA.VOLUME_FATURADO").get().value()).isEqualByComparingTo("7000");
        assertThat(cycle.value("AGUA.VOLUME_FATURADO").get().origin()).isEqualTo(SinisaValue.Origin.AUTOMATIC);
        assertThat(cycle.value("BALANCO.ATIVO_TOTAL")).isEmpty();
        assertThat(pending).containsExactly("BALANCO.ATIVO_TOTAL");
        assertThat(cycle.pendingVariables()).containsExactly("BALANCO.ATIVO_TOTAL");
    }

    @Test
    @DisplayName("valor manual sem justificativa e recusado (regra central do aceite)")
    void manualSemJustificativaRecusado() {
        SinisaCycle cycle = openLocal();

        assertThatThrownBy(() -> cycle.setManualValue("BALANCO.ATIVO_TOTAL",
                new BigDecimal("10"), null, RESPONSAVEL, T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so e aceito com justificativa");
        assertThatThrownBy(() -> cycle.setManualValue("BALANCO.ATIVO_TOTAL",
                new BigDecimal("10"), "   ", RESPONSAVEL, T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
        assertThat(cycle.value("BALANCO.ATIVO_TOTAL")).isEmpty();
    }

    @Test
    @DisplayName("valor manual com justificativa e registrado como MANUAL, com autor e justificativa")
    void manualComJustificativaRegistrado() {
        SinisaCycle cycle = openLocal();

        cycle.setManualValue("BALANCO.ATIVO_TOTAL", new BigDecimal("999.90"),
                "Balanco assinado.", RESPONSAVEL, T0);

        SinisaValue value = cycle.value("BALANCO.ATIVO_TOTAL").orElseThrow();
        assertThat(value.isManual()).isTrue();
        assertThat(value.justification()).isEqualTo("Balanco assinado.");
        assertThat(value.authorId()).isEqualTo(RESPONSAVEL);
    }

    @Test
    @DisplayName("versao preliminar com variavel pendente e recusada nomeando a pendencia")
    void preliminarComPendenciaRecusada() {
        SinisaCycle cycle = openLocal();
        cycle.prefill(sourceWithVolume(), RESPONSAVEL, T0);

        assertThatThrownBy(() -> cycle.advanceToPreliminary(RESPONSAVEL, T0, "cedo demais"))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("BALANCO.ATIVO_TOTAL");
        assertThat(cycle.stage()).isEqualTo(SinisaCycle.Stage.RASCUNHO);
    }

    @Test
    @DisplayName("transicoes rascunho -> preliminar -> resposta -> concluido sao registradas em ordem, com ator e instante")
    void transicoesRegistradasEmOrdem() {
        SinisaCycle cycle = completeCycle();
        cycle.concludeWithCertificate(
                new ComplianceCertificate(2026, "AGUA", LocalDate.of(2026, 12, 20), null),
                RESPONSAVEL, T0.plusDays(60));

        assertThat(cycle.stage()).isEqualTo(SinisaCycle.Stage.CONCLUIDO);
        assertThat(cycle.transitions()).hasSize(3);
        assertThat(cycle.transitions().get(0).from()).isEqualTo(SinisaCycle.Stage.RASCUNHO);
        assertThat(cycle.transitions().get(0).to()).isEqualTo(SinisaCycle.Stage.PRELIMINAR);
        assertThat(cycle.transitions().get(1).from()).isEqualTo(SinisaCycle.Stage.PRELIMINAR);
        assertThat(cycle.transitions().get(1).to()).isEqualTo(SinisaCycle.Stage.RESPOSTA_REGISTRADA);
        assertThat(cycle.transitions().get(2).from()).isEqualTo(SinisaCycle.Stage.RESPOSTA_REGISTRADA);
        assertThat(cycle.transitions().get(2).to()).isEqualTo(SinisaCycle.Stage.CONCLUIDO);
        assertThat(cycle.transitions()).allSatisfy(t -> {
            assertThat(t.actorId()).isEqualTo(RESPONSAVEL);
            assertThat(t.at()).isNotNull();
        });
        assertThat(cycle.certificate()).isPresent();
        assertThat(cycle.certificate().get().issuedOn()).isEqualTo(LocalDate.of(2026, 12, 20));
    }

    @Test
    @DisplayName("transicao fora de ordem e recusada (resposta sem preliminar; conclusao sem resposta)")
    void transicaoForaDeOrdemRecusada() {
        SinisaCycle cycle = openLocal();

        assertThatThrownBy(() -> cycle.registerResponse(RESPONSAVEL, T0, "resposta"))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> cycle.concludeWithCertificate(
                new ComplianceCertificate(2026, "AGUA", LocalDate.of(2026, 12, 20), null),
                RESPONSAVEL, T0))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("resposta sem observacao e recusada; reabertura sem motivo e recusada, com motivo volta ao rascunho")
    void respostaEReaberturaExigemRegistro() {
        SinisaCycle cycle = openLocal();
        cycle.prefill(sourceWithVolume(), RESPONSAVEL, T0);
        cycle.setManualValue("BALANCO.ATIVO_TOTAL", new BigDecimal("1"),
                "Justificativa.", RESPONSAVEL, T0);
        cycle.advanceToPreliminary(RESPONSAVEL, T0, "ok");

        assertThatThrownBy(() -> cycle.registerResponse(RESPONSAVEL, T0, " "))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> cycle.reopenForCorrection(RESPONSAVEL, T0, null))
                .isInstanceOf(ApiException.class);

        cycle.reopenForCorrection(RESPONSAVEL, T0, "Divergencia apontada pela agencia reguladora.");
        assertThat(cycle.stage()).isEqualTo(SinisaCycle.Stage.RASCUNHO);
        assertThat(cycle.transitions()).hasSize(2);
        assertThat(cycle.transitions().get(1).note()).contains("agencia reguladora");
    }

    @Test
    @DisplayName("escopo LOCAL com 2 municipios e REGIONAL com 1 municipio sao recusados na abertura")
    void escopoIncompativelRecusado() {
        assertThatThrownBy(() -> SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                RESPONSAVEL, "Maria Responsavel", 2, CORTE, smallCatalog()))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("LOCAL");
        assertThatThrownBy(() -> SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"REGIONAL\"")),
                RESPONSAVEL, "Maria Responsavel", 1, CORTE, smallCatalog()))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("REGIONAL");
    }

    @Test
    @DisplayName("escopo REGIONAL com responsavel e 3 municipios abre normalmente")
    void regionalAbre() {
        SinisaCycle cycle = SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"REGIONAL\"")),
                RESPONSAVEL, "Maria Responsavel", 3, CORTE, smallCatalog());

        assertThat(cycle.scope()).isEqualTo(SinisaProviderScope.REGIONAL);
        assertThat(cycle.responsibleId()).isEqualTo(RESPONSAVEL);
        assertThat(cycle.responsibleName()).isEqualTo("Maria Responsavel");
    }

    @Test
    @DisplayName("ciclo sem responsavel e recusado")
    void semResponsavelRecusado() {
        assertThatThrownBy(() -> SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                null, null, 1, CORTE, smallCatalog()))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(((ApiException.Validation) e).errors())
                        .anySatisfy(fe -> assertThat(fe.code()).isEqualTo("sinisa-responsible-required")));
    }

    @Test
    @DisplayName("ano-base do corte ou anterior nao e assistido (LC.13): primeiro assistido e corte + 1")
    void anoBaseDoCorteRecusado() {
        assertThatThrownBy(() -> SinisaCycle.open(TENANT, CORTE,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                RESPONSAVEL, "Maria Responsavel", 1, CORTE, smallCatalog()))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("LC.13");
        assertThatThrownBy(() -> SinisaCycle.open(TENANT, CORTE - 1,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                RESPONSAVEL, "Maria Responsavel", 1, CORTE, smallCatalog()))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("certificado de outro ano-base nao conclui o ciclo")
    void certificadoDeOutroAnoRecusado() {
        SinisaCycle cycle = completeCycle();

        assertThatThrownBy(() -> cycle.concludeWithCertificate(
                new ComplianceCertificate(2025, "AGUA", LocalDate.of(2026, 12, 20), null),
                RESPONSAVEL, T0.plusDays(60)))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("2025");
        assertThat(cycle.stage()).isEqualTo(SinisaCycle.Stage.RESPOSTA_REGISTRADA);
    }
}
