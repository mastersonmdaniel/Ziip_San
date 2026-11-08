package br.com.ziipsan.commercial.collection.legacydebit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Campanha de conversao ao Pix Automatico com consentimento guardado. */
class LegacyDebitConversionTest {

    @Test
    void consentimentoGuardadoConverteESaiDoLegado() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitAuthorization auth = LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        assertThat(svc.conversionCampaignTargets(LegacyDebitFixture.TENANT)).containsExactly(auth);
        LegacyConversionConsent consent = svc.registerConversionConsent(LegacyDebitFixture.TENANT,
                "M1", LegacyDebitFixture.HASH, "registro-consentimento/M1", "CANAL-DECLARADO",
                LegacyDebitFixture.NOW);
        assertThat(store.findConsents(LegacyDebitFixture.TENANT, auth.id())).containsExactly(consent);
        assertThat(auth.status()).isEqualTo(LegacyDebitAuthorization.Status.CONVERTIDA_PIX);
        assertThat(svc.conversionCampaignTargets(LegacyDebitFixture.TENANT)).isEmpty();
        // Convertido nao gera mais debito legado.
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                java.util.List.of(new LegacyDebitService.DebitDraft("M1",
                        new java.math.BigDecimal("10.00"), java.time.LocalDate.of(2026, 11, 20))),
                java.util.List.of(), java.util.List.of(), LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consentimentoSemProvaERecusadoENadaMuda() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store,
                new LegacyDebitFixture.MemFile());
        LegacyDebitAuthorization auth = LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        assertThatThrownBy(() -> svc.registerConversionConsent(LegacyDebitFixture.TENANT, "M1",
                "hash-curto", "ref", "CANAL", LegacyDebitFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prova");
        assertThat(auth.status()).isEqualTo(LegacyDebitAuthorization.Status.ATIVA_LEGADO);
        assertThat(store.consents).isEmpty();
    }
}
