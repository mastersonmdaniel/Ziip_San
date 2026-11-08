package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.pixauto.AccountDebitAuthorizationGate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.debit.DebitFixture.TENANT;
import static br.com.ziipsan.commercial.collection.debit.DebitFixture.UNIT;
import static br.com.ziipsan.commercial.collection.debit.DebitRegulatoryCalendar.AgreementKind.GENERAL;
import static br.com.ziipsan.commercial.collection.debit.DebitRegulatoryCalendar.AgreementKind.PUBLIC_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebitAccountIntegrationTest {

    /** Item vinculado a unidade da autorizacao elegivel padrao (UNIT). */
    private static LegacyDebitPort.LegacyDebitItem item(String id, String amount) {
        return item(UNIT, id, amount);
    }

    /** Item vinculado a uma unidade/autorizacao especifica (vinculo exigido pela correcao D1). */
    private static LegacyDebitPort.LegacyDebitItem item(UUID unitId, String id, String amount) {
        return new LegacyDebitPort.LegacyDebitItem(unitId, id, UUID.randomUUID(),
                LocalDate.of(2026, 12, 1), new BigDecimal(amount), LocalDate.of(2026, 12, 20));
    }

    @Test
    void autorizacaoNovaPorFebraban150ERecusadaPelaTravaDaP45() {
        assertThatThrownBy(() -> DebitAccountIntegration.requireNewAuthorizationChannel(
                AccountDebitAuthorizationGate.DebitChannel.FEBRABAN_150_LEGADO))
                .isInstanceOf(AccountDebitAuthorizationGate.LegacyDebitAuthorizationRejectedException.class);
        // Pix Automatico passa pela mesma trava, sem excecao
        DebitAccountIntegration.requireNewAuthorizationChannel(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO);
    }

    @Test
    void operacaoLegadaComParametroDesligadoERecusada() {
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), false,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        assertThatThrownBy(() -> svc.requireLegacyOperable(PUBLIC_SERVICE, LocalDate.of(2026, 12, 1)))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class)
                .hasMessageContaining("pay.legacy_debit_enabled");
    }

    @Test
    void operacaoLegadaNaDataDoDesligamentoERecusadaUmTestePorRegra() {
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        svc.requireLegacyOperable(PUBLIC_SERVICE, LocalDate.of(2026, 12, 31));
        assertThatThrownBy(() -> svc.requireLegacyOperable(PUBLIC_SERVICE, LocalDate.of(2027, 1, 1)))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class)
                .hasMessageContaining("2027-01-01");
        svc.requireLegacyOperable(GENERAL, LocalDate.of(2027, 6, 30));
        assertThatThrownBy(() -> svc.requireLegacyOperable(GENERAL, LocalDate.of(2027, 7, 1)))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class)
                .hasMessageContaining("2027-07-01");
    }

    @Test
    void elegibilidadeLegadaExigeAutorizacaoAtivaEMesmoBanco() {
        var ativaMesmoBanco = DebitFixture.auth(UNIT, "001", "001", true);
        var ativaOutroBanco = DebitFixture.auth(UUID.randomUUID(), "237", "001", true);
        var inativaMesmoBanco = DebitFixture.auth(UUID.randomUUID(), "001", "001", false);
        assertThat(DebitAccountIntegration.eligibleLegacyAuthorizations(
                List.of(ativaMesmoBanco, ativaOutroBanco, inativaMesmoBanco)))
                .containsExactly(ativaMesmoBanco);
    }

    @Test
    void remessaSemNenhumaAutorizacaoElegivelERecusadaSemChamarAPorta() {
        var port = new DebitFixture.FakeLegacyPort();
        var svc = DebitFixture.integration(port, true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        assertThatThrownBy(() -> svc.generateLegacyRemittance(TENANT, PUBLIC_SERVICE,
                LocalDate.of(2026, 12, 1),
                List.of(DebitFixture.auth(UNIT, "237", "001", true)),
                List.of(item("CHG-1", "100.00"))))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class);
        assertThat(port.calls).isEmpty();
    }

    @Test
    void remessaDoCicloPassaPelaPortaDaP324() {
        var port = new DebitFixture.FakeLegacyPort();
        var svc = DebitFixture.integration(port, true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        var result = svc.generateLegacyRemittance(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 1),
                List.of(DebitFixture.auth(UNIT, "001", "001", true)),
                List.of(item("CHG-1", "100.00"), item("CHG-2", "50.00")));
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.total()).isEqualByComparingTo("150.00");
        assertThat(result.excludedCount()).isZero();
        assertThat(port.calls).containsExactly("remittance:2");
    }

    @Test
    void remessaExcluiItensDeOptantesInelegiveisEContaOsExcluidos() {
        // Correcao D1 (sonda do revisor): 1 autorizacao elegivel e itens de
        // varios optantes - so o item da autorizacao elegivel pode chegar
        // a porta; os demais sao excluidos e contados, nao repassados.
        var port = new DebitFixture.FakeLegacyPort();
        var svc = DebitFixture.integration(port, true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        UUID outroBanco = UUID.randomUUID();
        UUID inativo = UUID.randomUUID();
        UUID desconhecido = UUID.randomUUID();
        var result = svc.generateLegacyRemittance(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 1),
                List.of(DebitFixture.auth(UNIT, "001", "001", true),
                        DebitFixture.auth(outroBanco, "237", "001", true),
                        DebitFixture.auth(inativo, "001", "001", false)),
                List.of(item(UNIT, "CHG-ELEGIVEL", "100.00"),
                        item(outroBanco, "CHG-OUTRO-BANCO", "50.00"),
                        item(inativo, "CHG-INATIVO", "70.00"),
                        item(desconhecido, "CHG-SEM-AUTORIZACAO", "30.00"),
                        new LegacyDebitPort.LegacyDebitItem(null, "CHG-SEM-VINCULO",
                                UUID.randomUUID(), LocalDate.of(2026, 12, 1),
                                new BigDecimal("10.00"), LocalDate.of(2026, 12, 20))));
        // a porta recebeu SOMENTE o item elegivel
        assertThat(port.lastItems).extracting(LegacyDebitPort.LegacyDebitItem::chargeIdentifier)
                .containsExactly("CHG-ELEGIVEL");
        assertThat(port.calls).containsExactly("remittance:1");
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(result.total()).isEqualByComparingTo("100.00");
        // 4 excluidos: outro banco, inativo, sem autorizacao e sem vinculo
        assertThat(result.excludedCount()).isEqualTo(4);
    }

    @Test
    void remessaSoComItensInelegiveisERecusadaSemChamarAPorta() {
        // Variante da sonda do revisor: 1 autorizacao elegivel no cadastro,
        // mas NENHUM dos 5 itens arbitrarios pertence a ela - nada chega a porta.
        var port = new DebitFixture.FakeLegacyPort();
        var svc = DebitFixture.integration(port, true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        assertThatThrownBy(() -> svc.generateLegacyRemittance(TENANT, PUBLIC_SERVICE,
                LocalDate.of(2026, 12, 1),
                List.of(DebitFixture.auth(UNIT, "001", "001", true)),
                List.of(item(UUID.randomUUID(), "ARB-1", "10.00"),
                        item(UUID.randomUUID(), "ARB-2", "10.00"),
                        item(UUID.randomUUID(), "ARB-3", "10.00"),
                        item(UUID.randomUUID(), "ARB-4", "10.00"),
                        item(UUID.randomUUID(), "ARB-5", "10.00"))))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class);
        assertThat(port.calls).isEmpty();
        assertThat(port.lastItems).isEmpty();
    }

    @Test
    void retornoProcessadoBaixaUmaUnicaVezMesmoReprocessado() {
        var processed = new DebitFixture.InMemoryProcessed();
        var sink = new DebitFixture.RecordingSink();
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true, processed, sink);
        var batch = new LegacyDebitPort.ReturnBatch("NSA7-HASH7", List.of(
                new LegacyDebitPort.ReturnLine("CHG-1", true, null),
                new LegacyDebitPort.ReturnLine("CHG-2", false, "SALDO_INSUFICIENTE")));
        Map<String, BigDecimal> amounts = Map.of("CHG-1", new BigDecimal("100.00"),
                "CHG-2", new BigDecimal("50.00"));
        var first = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 21), batch, amounts);
        assertThat(first.outcome()).isEqualTo(DebitAccountIntegration.ReturnProcessingOutcome.PROCESSADO);
        assertThat(first.settledCount()).isEqualTo(1);
        assertThat(first.rejectedCount()).isEqualTo(1);
        assertThat(first.settledTotal()).isEqualByComparingTo("100.00");
        var second = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 21), batch, amounts);
        assertThat(second.outcome()).isEqualTo(DebitAccountIntegration.ReturnProcessingOutcome.REPETIDO_SEM_EFEITO);
        // a baixa continua sendo uma so: 100.00, nao 200.00
        assertThat(sink.settled.get("CHG-1")).isEqualByComparingTo("100.00");
    }

    @Test
    void retornoComValorFaltanteNoMeioNaoBaixaNadaEReprocessadoBaixaUmaVez() {
        // Correcao D2 - sonda do revisor: linhas A e B liquidadas, mapa so
        // com A. No desenho anterior A baixava e a excecao deixava a chave
        // sem marca: o reprocessamento baixava A de novo (200 em vez de 100).
        // Com a pre-validacao completa, a 1a passagem NAO baixa nada.
        var processed = new DebitFixture.InMemoryProcessed();
        var sink = new DebitFixture.RecordingSink();
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true, processed, sink);
        var batch = new LegacyDebitPort.ReturnBatch("NSA8-HASH8", List.of(
                new LegacyDebitPort.ReturnLine("CHG-A", true, null),
                new LegacyDebitPort.ReturnLine("CHG-B", true, null)));
        assertThatThrownBy(() -> svc.processLegacyReturn(TENANT, PUBLIC_SERVICE,
                LocalDate.of(2026, 12, 21), batch, Map.of("CHG-A", new BigDecimal("100.00"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHG-B");
        // nenhuma baixa parcial ficou para tras
        assertThat(sink.settled).isEmpty();
        // reprocessamento com o mapa completo: cada linha baixa exatamente 1x
        var result = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 21), batch,
                Map.of("CHG-A", new BigDecimal("100.00"), "CHG-B", new BigDecimal("50.00")));
        assertThat(result.outcome()).isEqualTo(DebitAccountIntegration.ReturnProcessingOutcome.PROCESSADO);
        assertThat(result.settledCount()).isEqualTo(2);
        assertThat(sink.settled.get("CHG-A")).isEqualByComparingTo("100.00");
        assertThat(sink.settled.get("CHG-B")).isEqualByComparingTo("50.00");
    }

    @Test
    void retornoComFalhaDoSinkNoMeioEReprocessadoRetomaSemDuplicar() {
        // Correcao D2 - falha DURANTE o laço (o sink lanca na 2a linha):
        // a 1a linha ja baixada esta marcada POR DEBITO, entao a retomada
        // pula ela e baixa so as restantes - cada linha exatamente 1x.
        var processed = new DebitFixture.InMemoryProcessed();
        var recording = new DebitFixture.RecordingSink();
        var failOnChg2 = new java.util.concurrent.atomic.AtomicBoolean(true);
        DebitAccountIntegration.SettlementSink flaky = (chargeId, amount) -> {
            if (chargeId.equals("CHG-2") && failOnChg2.getAndSet(false)) {
                throw new IllegalStateException("falha simulada do sink em CHG-2");
            }
            recording.settle(chargeId, amount);
        };
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true, processed, flaky);
        var batch = new LegacyDebitPort.ReturnBatch("NSA9-HASH9", List.of(
                new LegacyDebitPort.ReturnLine("CHG-1", true, null),
                new LegacyDebitPort.ReturnLine("CHG-2", true, null),
                new LegacyDebitPort.ReturnLine("CHG-3", true, null)));
        Map<String, BigDecimal> amounts = Map.of("CHG-1", new BigDecimal("100.00"),
                "CHG-2", new BigDecimal("50.00"), "CHG-3", new BigDecimal("25.00"));
        assertThatThrownBy(() -> svc.processLegacyReturn(TENANT, PUBLIC_SERVICE,
                LocalDate.of(2026, 12, 21), batch, amounts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHG-2");
        // CHG-1 baixou antes da falha; CHG-2/CHG-3 nao
        assertThat(recording.settled).containsOnlyKeys("CHG-1");
        // retomada do MESMO lote: CHG-1 nao baixa de novo, CHG-2 e CHG-3 baixam 1x
        var resumed = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 21), batch, amounts);
        assertThat(resumed.outcome()).isEqualTo(DebitAccountIntegration.ReturnProcessingOutcome.PROCESSADO);
        assertThat(resumed.settledCount()).isEqualTo(2);
        assertThat(resumed.settledTotal()).isEqualByComparingTo("75.00");
        assertThat(recording.settled.get("CHG-1")).isEqualByComparingTo("100.00");
        assertThat(recording.settled.get("CHG-2")).isEqualByComparingTo("50.00");
        assertThat(recording.settled.get("CHG-3")).isEqualByComparingTo("25.00");
    }

    @Test
    void mesmoDebitoEmOutroArquivoDeRetornoNaoBaixaDeNovo() {
        // Correcao D3 - alinhamento com a P3.24: la a idempotencia e POR
        // DEBITO (estado BAIXADO do debito), nao por arquivo. Aqui, o mesmo
        // identificador vindo num SEGUNDO arquivo tambem nao baixa 2x.
        var processed = new DebitFixture.InMemoryProcessed();
        var sink = new DebitFixture.RecordingSink();
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true, processed, sink);
        Map<String, BigDecimal> amounts = Map.of("CHG-1", new BigDecimal("100.00"));
        var first = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 21),
                new LegacyDebitPort.ReturnBatch("NSA1-HASH1", List.of(
                        new LegacyDebitPort.ReturnLine("CHG-1", true, null))), amounts);
        assertThat(first.settledCount()).isEqualTo(1);
        var second = svc.processLegacyReturn(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 22),
                new LegacyDebitPort.ReturnBatch("NSA2-HASH2", List.of(
                        new LegacyDebitPort.ReturnLine("CHG-1", true, null))), amounts);
        assertThat(second.settledCount()).isZero();
        assertThat(second.settledTotal()).isEqualByComparingTo("0");
        assertThat(sink.settled.get("CHG-1")).isEqualByComparingTo("100.00");
    }

    @Test
    void reenvioECancelamentoDepoisDoDesligamentoSaoRecusados() {
        var port = new DebitFixture.FakeLegacyPort();
        var svc = DebitFixture.integration(port, true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        svc.resendLegacy(TENANT, PUBLIC_SERVICE, LocalDate.of(2026, 12, 10), "CHG-1");
        assertThatThrownBy(() -> svc.resendLegacy(TENANT, PUBLIC_SERVICE, LocalDate.of(2027, 1, 1), "CHG-1"))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class);
        assertThatThrownBy(() -> svc.cancelLegacyOptant(TENANT, PUBLIC_SERVICE, LocalDate.of(2027, 1, 1), UNIT))
                .isInstanceOf(DebitAccountIntegration.LegacyDebitRefusedException.class);
        assertThat(port.calls).containsExactly("resend:CHG-1");
    }

    @Test
    void conversaoSoSeConsumaComConsentimentoGuardadoComProva() {
        var svc = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());
        var semProva = svc.convertOptant(UNIT, null, null);
        assertThat(semProva.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CANDIDATO);
        var hashInvalido = svc.convertOptant(UNIT, "abc", "protocolo-1");
        assertThat(hashInvalido.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CANDIDATO);
        var convertido = svc.convertOptant(UNIT, DebitFixture.HASH, "protocolo-1");
        assertThat(convertido.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CONVERTIDO);
        assertThat(DebitAccountIntegration.pendingConversion(List.of(semProva, convertido)))
                .containsExactly(UNIT);
    }
}
