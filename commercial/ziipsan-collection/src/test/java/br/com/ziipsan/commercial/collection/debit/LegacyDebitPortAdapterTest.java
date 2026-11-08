package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.BcbDebitRegimeRules;
import br.com.ziipsan.commercial.collection.legacydebit.Febraban150Port;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitAuthorization;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.debit.DebitFixture.TENANT;
import static br.com.ziipsan.commercial.collection.debit.DebitFixture.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B2-5: adaptador da {@link LegacyDebitPort} sobre o servico real da
 * P3.24 - ponta a ponta com os tipos reais (chargeIdentifier = UUID do
 * debito; autorizacao por unidade; retorno traduzido do arquivo).
 */
class LegacyDebitPortAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID UNIT2 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private LegacyDebitService realService(DebitFixture.MemLegacyStore store, DebitFixture.MemFile file) {
        return new LegacyDebitService(store, file, DebitFixture.params(true),
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO);
    }

    private void activeOptant(LegacyDebitService svc, String enrollment, UUID unit) {
        svc.importLegacyAuthorization(TENANT, enrollment, unit, "52998224725", "001", "001",
                "LEGADO-" + enrollment, NOW, TODAY);
        svc.conferImported(TENANT, enrollment, NOW);
    }

    private LegacyDebitPort.LegacyDebitItem item(UUID unit, String charge, String amount) {
        return new LegacyDebitPort.LegacyDebitItem(unit, charge, UUID.randomUUID(),
                LocalDate.of(2026, 12, 1), new BigDecimal(amount), LocalDate.of(2026, 11, 20));
    }

    @Test
    void importacaoListaAutorizacoesReaisComVinculoEStatus() {
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc, "M1", UNIT);
        activeOptant(svc, "M2", UNIT2);
        var adapter = new LegacyDebitPortAdapter(svc, new DebitFixture.MemFile(), CLOCK);

        List<LegacyDebitPort.LegacyAuthorization> imported = adapter.importLegacyAuthorizations(TENANT);

        assertThat(imported).hasSize(2);
        assertThat(imported).extracting(LegacyDebitPort.LegacyAuthorization::enrollmentRef)
                .containsExactlyInAnyOrder("M1", "M2");
        assertThat(imported).extracting(LegacyDebitPort.LegacyAuthorization::unitId)
                .containsExactlyInAnyOrder(UNIT, UNIT2);
        assertThat(imported).allMatch(LegacyDebitPort.LegacyAuthorization::active);
        assertThat(imported).allMatch(LegacyDebitPort.LegacyAuthorization::sameBank);
    }

    @Test
    void remessaGeraDebitosReaisExcluiSemVinculoETraduzIdentificadores() {
        var store = new DebitFixture.MemLegacyStore();
        var file = new DebitFixture.MemFile();
        var svc = realService(store, file);
        activeOptant(svc, "M1", UNIT);
        activeOptant(svc, "M2", UNIT2);
        var adapter = new LegacyDebitPortAdapter(svc, file, CLOCK);

        var result = adapter.generateRemittance(TENANT, List.of(
                item(UNIT, "CHG-1", "100.00"),
                item(UNIT2, "CHG-2", "50.00"),
                item(UUID.randomUUID(), "CHG-SEM-AUTORIZACAO", "30.00")));

        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.total()).isEqualByComparingTo("150.00");
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(store.debits).hasSize(2);
        assertThat(result.remittanceId()).isNotNull();
        // O identificador de cobranca passa a ser o UUID do debito criado.
        assertThat(result.remittanceId())
                .isIn(store.debits.keySet().stream().map(UUID::toString).toList());
    }

    @Test
    void retornoLidoTraduzDebitIdParaChargeIdentifierEReenvioUsaOUuid() {
        var store = new DebitFixture.MemLegacyStore();
        var file = new DebitFixture.MemFile();
        var svc = realService(store, file);
        activeOptant(svc, "M1", UNIT);
        var adapter = new LegacyDebitPortAdapter(svc, file, CLOCK);
        adapter.generateRemittance(TENANT, List.of(item(UNIT, "CHG-1", "100.00")));
        UUID debitId = store.debits.keySet().stream().findFirst().orElseThrow();

        file.returnEntries = List.of(new Febraban150Port.ReturnEntry(debitId, "M1",
                new BigDecimal("100.00"), "OK", true));
        var batch = adapter.readReturn(TENANT, "NSA7-HASH7");

        assertThat(batch.returnKey()).isEqualTo("NSA7-HASH7");
        assertThat(batch.lines()).hasSize(1);
        assertThat(batch.lines().get(0).chargeIdentifier()).isEqualTo(debitId.toString());
        assertThat(batch.lines().get(0).settled()).isTrue();
        assertThat(batch.lines().get(0).bankReasonCode()).isEqualTo("OK");

        adapter.resend(TENANT, debitId.toString());
        assertThat(store.debits.get(debitId).remittanceCount()).isEqualTo(2);
        assertThatThrownBy(() -> adapter.resend(TENANT, "CHG-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void cancelamentoPorUnidadeEncerraAAutorizacaoReal() {
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc, "M1", UNIT);
        var adapter = new LegacyDebitPortAdapter(svc, new DebitFixture.MemFile(), CLOCK);

        adapter.cancelOptant(TENANT, UNIT);

        assertThat(store.auths.values().stream().findFirst().orElseThrow().status())
                .isEqualTo(LegacyDebitAuthorization.Status.CANCELADA);
        assertThatThrownBy(() -> adapter.cancelOptant(TENANT, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
