package br.com.ziipsan.commercial.collection.legacydebit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Remessa B/C/E/F, retorno com baixa idempotente, reenvio e cancelamento. */
class LegacyDebitRemittanceReturnTest {

    private LegacyDebitService.DebitDraft draft(String enrollment, String amount) {
        return new LegacyDebitService.DebitDraft(enrollment, new BigDecimal(amount),
                LocalDate.of(2026, 11, 20));
    }

    @Test
    void remessaContemRegistrosBCEeF() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitFixture.activeOptant(svc, "M2", "001", "001");
        svc.cancelOptant(LegacyDebitFixture.TENANT, "M2", LegacyDebitFixture.NOW);
        List<LegacyDebitService.LegacyDebit> debits = svc.generateRemittance(
                LegacyDebitFixture.TENANT, "001", List.of(draft("M1", "87.50")),
                List.of("M1"), List.of("M2"), LegacyDebitFixture.TODAY);
        assertThat(debits).hasSize(1);
        List<Febraban150Port.RecordType> types = file.written.get(0).records().stream()
                .map(Febraban150Port.RemittanceRecord::type).toList();
        assertThat(types).containsExactly(Febraban150Port.RecordType.B, Febraban150Port.RecordType.E,
                Febraban150Port.RecordType.C, Febraban150Port.RecordType.F);
        // O trailer F carrega a soma de controle dos debitos E.
        assertThat(file.written.get(0).records().get(3).amount()).isEqualByComparingTo("87.50");
    }

    @Test
    void retornoBaixaUmaUnicaVezRepetidoSemEfeito() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitService.LegacyDebit debit = svc.generateRemittance(LegacyDebitFixture.TENANT,
                "001", List.of(draft("M1", "87.50")), List.of(), List.of(),
                LegacyDebitFixture.TODAY).get(0);
        file.returnEntries = List.of(new Febraban150Port.ReturnEntry(debit.id(), "M1",
                new BigDecimal("87.50"), "CODIGO-VERBATIM", true));
        assertThat(svc.processReturn(LegacyDebitFixture.TENANT, "RET1", LegacyDebitFixture.NOW))
                .extracting(LegacyDebitService.ReturnResult::outcome)
                .containsExactly(LegacyDebitService.ReturnOutcome.BAIXADO);
        // Correcao D1 da revisao: o codigo do retorno fica GUARDADO verbatim
        // no debito (nao e so transportado pela porta e descartado).
        assertThat(debit.outcomeCode()).isEqualTo("CODIGO-VERBATIM");
        assertThat(svc.processReturn(LegacyDebitFixture.TENANT, "RET1", LegacyDebitFixture.NOW))
                .extracting(LegacyDebitService.ReturnResult::outcome)
                .containsExactly(LegacyDebitService.ReturnOutcome.REPETIDO_SEM_EFEITO);
        assertThat(debit.status()).isEqualTo(LegacyDebitService.DebitStatus.BAIXADO);
        // O repetido nao tem efeito: o codigo guardado nao e sobrescrito.
        assertThat(debit.outcomeCode()).isEqualTo("CODIGO-VERBATIM");
    }

    @Test
    void retornoComValorDivergenteOuDesconhecidoNaoBaixa() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitService.LegacyDebit debit = svc.generateRemittance(LegacyDebitFixture.TENANT,
                "001", List.of(draft("M1", "87.50")), List.of(), List.of(),
                LegacyDebitFixture.TODAY).get(0);
        file.returnEntries = List.of(
                new Febraban150Port.ReturnEntry(debit.id(), "M1", new BigDecimal("80.00"), "X", true),
                new Febraban150Port.ReturnEntry(java.util.UUID.randomUUID(), "M9",
                        new BigDecimal("1.00"), "X", true));
        assertThat(svc.processReturn(LegacyDebitFixture.TENANT, "RET2", LegacyDebitFixture.NOW))
                .extracting(LegacyDebitService.ReturnResult::outcome)
                .containsExactly(LegacyDebitService.ReturnOutcome.DIVERGENTE,
                        LegacyDebitService.ReturnOutcome.NAO_IDENTIFICADO);
        assertThat(debit.status()).isEqualTo(LegacyDebitService.DebitStatus.REMETIDO);
        // Correcao D1 da revisao: tambem na divergencia o codigo verbatim do
        // retorno fica guardado no debito (trilha de auditoria do retorno).
        assertThat(debit.outcomeCode()).isEqualTo("X");
    }

    @Test
    void reenvioContaERemeteDeNovoEDebitoBaixadoNaoReenvia() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitService.LegacyDebit debit = svc.generateRemittance(LegacyDebitFixture.TENANT,
                "001", List.of(draft("M1", "87.50")), List.of(), List.of(),
                LegacyDebitFixture.TODAY).get(0);
        svc.resendDebit(LegacyDebitFixture.TENANT, debit.id(), LegacyDebitFixture.TODAY);
        assertThat(debit.remittanceCount()).isEqualTo(2);
        file.returnEntries = List.of(new Febraban150Port.ReturnEntry(debit.id(), "M1",
                new BigDecimal("87.50"), "OK", true));
        svc.processReturn(LegacyDebitFixture.TENANT, "RET3", LegacyDebitFixture.NOW);
        assertThatThrownBy(() -> svc.resendDebit(LegacyDebitFixture.TENANT, debit.id(),
                LegacyDebitFixture.TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void remessaRecusaRegistroCDeOptanteNaoCanceladoEDesconhecido() {
        // Correcao D3 da revisao: o laco C nao emite cancelamento para
        // qualquer string - so para autorizacao efetivamente CANCELADA.
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        // Optante ATIVO (nao cancelado) informado como cancelamento: recusado.
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                List.of(), List.of(), List.of("M1"), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelado");
        assertThat(file.written).isEmpty();
        // Matricula desconhecida informada como cancelamento: recusada.
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                List.of(), List.of(), List.of("M9"), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(file.written).isEmpty();
    }

    @Test
    void remessaRecusaDebitoComBancoDoRecebedorDivergenteDaRemessa() {
        // Correcao D3 da revisao: o laco E aplica a mesma checagem de banco
        // do recebedor que o laco B ja fazia.
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "237",
                List.of(draft("M1", "87.50")), List.of(), List.of(), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("banco do recebedor");
        assertThat(file.written).isEmpty();
        assertThat(store.debits).isEmpty();
    }

    @Test
    void inclusaoBEmitidaACadaChamadaSemEstadoDeInclusao() {
        // Correcao D4 da revisao: nao ha estado de inclusao - o javadoc foi
        // alinhado e o comportamento real fica fixado aqui: informada em
        // duas chamadas, a mesma matricula gera o B nas duas; a dedup da
        // lista de pendentes e de quem chama.
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        svc.generateRemittance(LegacyDebitFixture.TENANT, "001", List.of(), List.of("M1"),
                List.of(), LegacyDebitFixture.TODAY);
        svc.generateRemittance(LegacyDebitFixture.TENANT, "001", List.of(), List.of("M1"),
                List.of(), LegacyDebitFixture.TODAY);
        assertThat(file.written).hasSize(2);
        for (Febraban150Port.RemittanceBatch batch : file.written) {
            assertThat(batch.records().get(0).type()).isEqualTo(Febraban150Port.RecordType.B);
            assertThat(batch.records().get(0).enrollmentId()).isEqualTo("M1");
        }
    }

    @Test
    void cancelamentoDeOptanteEncerraAutorizacaoEImpedeNovoDebito() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitAuthorization auth = svc.cancelOptant(LegacyDebitFixture.TENANT, "M1",
                LegacyDebitFixture.NOW);
        assertThat(auth.status()).isEqualTo(LegacyDebitAuthorization.Status.CANCELADA);
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                List.of(draft("M1", "10.00")), List.of(), List.of(), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class);
    }
}
