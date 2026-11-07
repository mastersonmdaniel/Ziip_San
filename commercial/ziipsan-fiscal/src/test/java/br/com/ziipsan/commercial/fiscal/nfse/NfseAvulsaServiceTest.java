package br.com.ziipsan.commercial.fiscal.nfse;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serviço da OS gera a NFS-e: relógio injetado por etapa e CNPJ alfanumérico da P1.39 no
 * prestador e no tomador (aceite da P3.6). O CNPJ alfanumérico de referência é o exemplo da
 * documentação técnica da RFB citado no {@code DocumentNumber}: 12.ABC.345/01DE-35.
 */
class NfseAvulsaServiceTest {

    private static final String CNPJ_ALFA_PRESTADOR = "12.ABC.345/01DE-35";
    private static final String CNPJ_NUMERICO = "11.222.333/0001-81";

    private static NfseAvulsaService serviceOn(LocalDate date) {
        Clock clock = Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC);
        return new NfseAvulsaService(clock);
    }

    private static NfseAvulsaService.Comando comando(NfseEtapa etapa, boolean simples,
                                                     String prestador, String tomador) {
        return new NfseAvulsaService.Comando(UUID.randomUUID(), "SERVICO_AVULSO_TESTE", etapa,
                prestador, simples, tomador, "Serviço avulso de teste",
                new BigDecimal("150.00"), "3550308");
    }

    @Test
    @DisplayName("com relógio: antes da etapa sai sem IBS/CBS e sem rejeição; na etapa sai com os grupos")
    void clockDrivesGroupsPerStage() {
        NfseAvulsaService.NfseGerada antes = serviceOn(LocalDate.of(2026, 9, 30))
                .gerar(comando(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS, false, CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(antes.comGruposIbsCbs()).isFalse();
        assertThat(antes.rejeitadaPorAusenciaDeIbsCbs()).isFalse();
        assertThat(antes.ambiente()).isEqualTo(NfseAvulsaService.Ambiente.PRODUCAO_RESTRITA);
        assertThat(antes.status()).isEqualTo(NfseAvulsaService.Status.GERADA_NAO_TRANSMITIDA);

        NfseAvulsaService.NfseGerada naEtapa = serviceOn(LocalDate.of(2026, 10, 1))
                .gerar(comando(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS, false, CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(naEtapa.comGruposIbsCbs()).isTrue();
        assertThat(naEtapa.dataEmissao()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("CNPJ alfanumérico (P1.39) aceito no prestador E no tomador, e normalizado no payload")
    void alphanumericCnpjOnBothParties() {
        // Segundo CNPJ alfanumérico: DV fixo "26", recalculado de forma independente da
        // API da P1.39 na revisão cruzada da P3.6 (mod-11, valor = ASCII-48, pesos
        // 5..2/6..2 sobre AB1234567800: somas 361 e 412, restos 9 e 5 -> DV 2 e 6).
        // Antes o DV era gerado pela própria cnpjCheckDigits — teste circular, que
        // passaria mesmo com o algoritmo errado.
        String base12 = "AB1234567800";
        String tomadorAlfa = "AB123456780026";
        assertThat(DocumentNumber.cnpjCheckDigits(base12)).isEqualTo("26");
        assertThat(DocumentNumber.isValidCnpj(tomadorAlfa)).isTrue();

        NfseAvulsaService.NfseGerada n = serviceOn(LocalDate.of(2026, 10, 1))
                .gerar(comando(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS, false, CNPJ_ALFA_PRESTADOR, tomadorAlfa));

        assertThat(n.prestadorCnpjNormalizado()).isEqualTo("12ABC34501DE35");
        assertThat(n.tomadorCnpjNormalizado()).isEqualTo(tomadorAlfa);
        JsonNode dps = Json.readTree(n.payloadDpsJson());
        assertThat(dps.get("prestadorCnpj").asString()).isEqualTo("12ABC34501DE35");
        assertThat(dps.get("tomadorCnpj").asString()).isEqualTo(tomadorAlfa);
        assertThat(dps.get("gruposIbsCbsPresentes").asBoolean()).isTrue();
        assertThat(dps.get("ambiente").asString()).isEqualTo("PRODUCAO_RESTRITA");
    }

    @Test
    @DisplayName("CNPJ inválido no prestador ou no tomador é erro explícito (inclusive alfanumérico com DV errado)")
    void invalidCnpjRejected() {
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 10, 1))
                .gerar(comando(NfseEtapa.DEMAIS_HIPOTESES, false, "12.ABC.345/01DE-99", CNPJ_NUMERICO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prestador");
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 10, 1))
                .gerar(comando(NfseEtapa.DEMAIS_HIPOTESES, false, CNPJ_NUMERICO, "123")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tomador");
    }

    @Test
    @DisplayName("Simples Nacional com relógio em 2026: sem grupos e sem rejeição; em 01/01/2027 com grupos")
    void simplesNacionalClock() {
        NfseAvulsaService.NfseGerada em2026 = serviceOn(LocalDate.of(2026, 12, 1))
                .gerar(comando(NfseEtapa.DEMAIS_HIPOTESES, true, CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(em2026.etapaAplicavel()).isEqualTo(NfseEtapa.SIMPLES_NACIONAL);
        assertThat(em2026.comGruposIbsCbs()).isFalse();
        assertThat(em2026.rejeitadaPorAusenciaDeIbsCbs()).isFalse();

        NfseAvulsaService.NfseGerada em2027 = serviceOn(LocalDate.of(2027, 1, 1))
                .gerar(comando(NfseEtapa.DEMAIS_HIPOTESES, true, CNPJ_NUMERICO, CNPJ_NUMERICO));
        assertThat(em2027.comGruposIbsCbs()).isTrue();
    }

    @Test
    @DisplayName("comando incompleto é erro explícito, sem default silencioso")
    void incompleteCommandFails() {
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 10, 1)).gerar(
                new NfseAvulsaService.Comando(null, "X", NfseEtapa.DEMAIS_HIPOTESES,
                        CNPJ_NUMERICO, false, CNPJ_NUMERICO, "d", BigDecimal.TEN, "3550308")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceOn(LocalDate.of(2026, 10, 1)).gerar(
                new NfseAvulsaService.Comando(UUID.randomUUID(), "X", NfseEtapa.DEMAIS_HIPOTESES,
                        CNPJ_NUMERICO, false, CNPJ_NUMERICO, "d", BigDecimal.ZERO, "3550308")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
