package br.com.ziipsan.commercial.collection.legacydebit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2-4 (idempotencia da remessa por draft) e B2-2 (idempotencia do
 * consentimento por prova) — correcoes da rodada 2 da auditoria continua.
 */
class LegacyDebitRemittanceIdempotencyTest {

    private LegacyDebitService.DebitDraft draft(String enrollment, String amount) {
        return new LegacyDebitService.DebitDraft(enrollment, new BigDecimal(amount),
                LocalDate.of(2026, 11, 20));
    }

    @Test
    void reexecucaoComOsMesmosDraftsNaoDuplicaDebitosNemRegistrosE() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitFixture.activeOptant(svc, "M2", "001", "001");

        List<LegacyDebitService.LegacyDebit> first = svc.generateRemittance(
                LegacyDebitFixture.TENANT, "001",
                List.of(draft("M1", "87.50"), draft("M2", "10.00")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);
        List<LegacyDebitService.LegacyDebit> second = svc.generateRemittance(
                LegacyDebitFixture.TENANT, "001",
                List.of(draft("M1", "87.50"), draft("M2", "10.00")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);

        // Mesmos debitos, sem duplicar no store.
        assertThat(second).extracting(LegacyDebitService.LegacyDebit::id)
                .containsExactlyElementsOf(first.stream().map(LegacyDebitService.LegacyDebit::id).toList());
        assertThat(store.debits).hasSize(2);
        // O 2o arquivo nao tem registro E novo e o trailer F soma zero (so debitos novos).
        assertThat(file.written).hasSize(2);
        assertThat(file.written.get(1).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.E)).isEmpty();
        assertThat(file.written.get(1).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.F).findFirst().orElseThrow().amount())
                .isEqualByComparingTo("0");
        // O 1o arquivo segue integro: 2 registros E e F = 97.50.
        assertThat(file.written.get(0).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.E)).hasSize(2);
        assertThat(file.written.get(0).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.F).findFirst().orElseThrow().amount())
                .isEqualByComparingTo("97.50");
    }

    @Test
    void reexecucaoParcialCriaSoODebitoNovo() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        LegacyDebitFixture.activeOptant(svc, "M2", "001", "001");

        svc.generateRemittance(LegacyDebitFixture.TENANT, "001", List.of(draft("M1", "87.50")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);
        List<LegacyDebitService.LegacyDebit> second = svc.generateRemittance(
                LegacyDebitFixture.TENANT, "001",
                List.of(draft("M1", "87.50"), draft("M2", "10.00")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);

        assertThat(store.debits).hasSize(2);
        assertThat(second).hasSize(2);
        // So o debito novo ganhou registro E no 2o arquivo; F = 10.00.
        assertThat(file.written.get(1).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.E)).hasSize(1);
        assertThat(file.written.get(1).records().stream()
                .filter(r -> r.type() == Febraban150Port.RecordType.F).findFirst().orElseThrow().amount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void mesmoDraftComValorDistintoEOutroDebito() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");

        svc.generateRemittance(LegacyDebitFixture.TENANT, "001", List.of(draft("M1", "87.50")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);
        svc.generateRemittance(LegacyDebitFixture.TENANT, "001", List.of(draft("M1", "90.00")),
                List.of(), List.of(), LegacyDebitFixture.TODAY);

        assertThat(store.debits).hasSize(2);
    }

    @Test
    void mesmoConsentimentoRegistradoDuasVezesNaoDuplica() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");

        LegacyConversionConsent first = svc.registerConversionConsent(LegacyDebitFixture.TENANT,
                "M1", LegacyDebitFixture.HASH, "protocolo-1", "atendimento", LegacyDebitFixture.NOW);
        LegacyConversionConsent second = svc.registerConversionConsent(LegacyDebitFixture.TENANT,
                "M1", LegacyDebitFixture.HASH, "protocolo-1", "atendimento", LegacyDebitFixture.NOW);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(store.consents).hasSize(1);
        assertThat(store.auths.values().stream().findFirst().orElseThrow().status())
                .isEqualTo(LegacyDebitAuthorization.Status.CONVERTIDA_PIX);
    }
}
