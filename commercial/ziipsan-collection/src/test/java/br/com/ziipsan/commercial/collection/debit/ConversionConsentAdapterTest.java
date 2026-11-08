package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.BcbDebitRegimeRules;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyConversionConsent;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static br.com.ziipsan.commercial.collection.debit.DebitFixture.HASH;
import static br.com.ziipsan.commercial.collection.debit.DebitFixture.TENANT;
import static br.com.ziipsan.commercial.collection.debit.DebitFixture.UNIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B2-2: UM so store de consentimento de conversao - o canonico
 * {@code legacy_debit_conversion_consents} (V6_4 da P3.24) - com o
 * adaptador da ED-27 sobre ele. A tabela duplicada
 * {@code debit_conversion_consent} foi removida do SQL pendente.
 */
class ConversionConsentAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 11, 8);

    private LegacyDebitService realService(DebitFixture.MemLegacyStore store, DebitFixture.MemFile file) {
        return new LegacyDebitService(store, file, DebitFixture.params(true),
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO);
    }

    private void activeOptant(LegacyDebitService svc) {
        svc.importLegacyAuthorization(TENANT, "M1", UNIT, "52998224725", "001", "001",
                "LEGADO-M1", NOW, TODAY);
        svc.conferImported(TENANT, "M1", NOW);
    }

    @Test
    void conversaoComProvaPersisteNoStoreCanonicoDaP324() {
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc);
        var adapter = new CanonicalConversionConsentAdapter(store);
        var integration = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());

        var decision = integration.convertOptantAndPersist(TENANT, UNIT, "M1", HASH,
                "protocolo-1", "atendimento", NOW, adapter);

        assertThat(decision.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CONVERTIDO);
        // Guardado UMA vez, no store da P3.24 (o canonico).
        assertThat(store.consents).hasSize(1);
        LegacyConversionConsent saved = store.consents.get(0);
        assertThat(saved.enrollmentId()).isEqualTo("M1");
        assertThat(saved.proofHash()).isEqualTo(HASH);
        assertThat(adapter.findConsent(TENANT, "M1")).contains(saved);
    }

    @Test
    void mesmoConsentimentoPersistidoDuasVezesNaoDuplica() {
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc);
        var adapter = new CanonicalConversionConsentAdapter(store);
        var integration = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());

        var first = integration.convertOptantAndPersist(TENANT, UNIT, "M1", HASH,
                "protocolo-1", "atendimento", NOW, adapter);
        var second = integration.convertOptantAndPersist(TENANT, UNIT, "M1", HASH,
                "protocolo-1", "atendimento", NOW, adapter);

        assertThat(first.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CONVERTIDO);
        assertThat(second.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CONVERTIDO);
        assertThat(store.consents).hasSize(1);
    }

    @Test
    void conversaoSemProvaNaoPersisteNada() {
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc);
        var adapter = new CanonicalConversionConsentAdapter(store);
        var integration = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());

        var decision = integration.convertOptantAndPersist(TENANT, UNIT, "M1", null, null,
                "atendimento", NOW, adapter);

        assertThat(decision.status()).isEqualTo(DebitAccountIntegration.ConversionStatus.CANDIDATO);
        assertThat(store.consents).isEmpty();
    }

    @Test
    void consentimentoDeMatriculaNaoImportadaERecusado() {
        var store = new DebitFixture.MemLegacyStore();
        var adapter = new CanonicalConversionConsentAdapter(store);
        var integration = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());

        assertThatThrownBy(() -> integration.convertOptantAndPersist(TENANT, UNIT, "M9", HASH,
                "protocolo-1", "atendimento", NOW, adapter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M9");
        assertThat(store.consents).isEmpty();
    }

    @Test
    void consentimentoGuardadoNoAdaptadorEConvertidoPeloServicoSemDuplicar() {
        // Composicao: o adaptador guarda o registro; o servico da P3.24
        // converte a autorizacao sobre o MESMO registro (mesma prova).
        var store = new DebitFixture.MemLegacyStore();
        var svc = realService(store, new DebitFixture.MemFile());
        activeOptant(svc);
        var adapter = new CanonicalConversionConsentAdapter(store);
        var integration = DebitFixture.integration(new DebitFixture.FakeLegacyPort(), true,
                new DebitFixture.InMemoryProcessed(), new DebitFixture.RecordingSink());

        integration.convertOptantAndPersist(TENANT, UNIT, "M1", HASH,
                "protocolo-1", "atendimento", NOW, adapter);
        LegacyConversionConsent converted = svc.registerConversionConsent(TENANT, "M1", HASH,
                "protocolo-1", "atendimento", NOW);

        assertThat(store.consents).hasSize(1);
        assertThat(converted.id()).isEqualTo(store.consents.get(0).id());
        assertThat(store.auths.values().stream().findFirst().orElseThrow().status())
                .isEqualTo(br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitAuthorization.Status.CONVERTIDA_PIX);
    }
}
