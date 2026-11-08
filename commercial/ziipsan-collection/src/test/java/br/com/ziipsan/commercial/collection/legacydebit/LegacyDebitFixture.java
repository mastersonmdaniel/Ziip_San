package br.com.ziipsan.commercial.collection.legacydebit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dubles em memoria da P3.24 (loja + arquivo), no padrao da fixture da P4.5. */
final class LegacyDebitFixture {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final String PAYER_CPF = "52998224725";
    static final String HASH = "a".repeat(64);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    static final class MemStore implements LegacyDebitService.Store {
        final Map<UUID, LegacyDebitAuthorization> auths = new LinkedHashMap<>();
        final Map<UUID, LegacyDebitService.LegacyDebit> debits = new LinkedHashMap<>();
        final List<LegacyConversionConsent> consents = new ArrayList<>();

        public void saveAuthorization(LegacyDebitAuthorization a) { auths.put(a.id(), a); }
        public Optional<LegacyDebitAuthorization> findAuthorization(UUID t, UUID id) {
            LegacyDebitAuthorization a = auths.get(id);
            return a != null && a.tenantId().equals(t) ? Optional.of(a) : Optional.empty();
        }
        public Optional<LegacyDebitAuthorization> findAuthorizationByEnrollment(UUID t, String e) {
            return auths.values().stream()
                    .filter(a -> a.tenantId().equals(t) && a.enrollmentId().equals(e)).findFirst();
        }
        public List<LegacyDebitAuthorization> findAuthorizations(UUID t) {
            return auths.values().stream().filter(a -> a.tenantId().equals(t)).toList();
        }
        public void saveDebit(LegacyDebitService.LegacyDebit d) { debits.put(d.id(), d); }
        public Optional<LegacyDebitService.LegacyDebit> findDebit(UUID t, UUID id) {
            LegacyDebitService.LegacyDebit d = debits.get(id);
            return d != null && d.tenantId().equals(t) ? Optional.of(d) : Optional.empty();
        }
        public void saveConsent(LegacyConversionConsent c) { consents.add(c); }
        public List<LegacyConversionConsent> findConsents(UUID t, UUID authId) {
            return consents.stream()
                    .filter(c -> c.tenantId().equals(t) && c.authorizationId().equals(authId)).toList();
        }
    }

    static final class MemFile implements Febraban150Port {
        final List<RemittanceBatch> written = new ArrayList<>();
        List<ReturnEntry> returnEntries = List.of();

        public void writeRemittance(RemittanceBatch batch) { written.add(batch); }
        public List<ReturnEntry> readReturn(UUID tenantId, String fileReference) { return returnEntries; }
    }

    static LegacyDebitParameters params(boolean enabled) {
        return LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, Boolean.toString(enabled)));
    }

    static LegacyDebitService service(MemStore store, MemFile file, boolean enabled,
                                      BcbDebitRegimeRules.Framing framing) {
        return new LegacyDebitService(store, file, params(enabled), framing);
    }

    static LegacyDebitService publicService(MemStore store, MemFile file) {
        return service(store, file, true, BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO);
    }

    /** Importa e confere um optante; bancos iguais por padrao ("001"/"001"). */
    static LegacyDebitAuthorization activeOptant(LegacyDebitService svc, String enrollment,
                                                 String payerBank, String receiverBank) {
        svc.importLegacyAuthorization(TENANT, enrollment, null, PAYER_CPF, payerBank, receiverBank,
                "LEGADO-" + enrollment, NOW, TODAY);
        return svc.conferImported(TENANT, enrollment, NOW);
    }
}
