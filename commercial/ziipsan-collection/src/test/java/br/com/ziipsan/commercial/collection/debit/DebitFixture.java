package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dubles em memoria da ED-27: porta legada, idempotencia e baixa, com registro de chamadas. */
final class DebitFixture {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID UNIT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String HASH = "a".repeat(64);

    static LegacyDebitParameters params(boolean enabled) {
        return LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, Boolean.toString(enabled)));
    }

    static final class FakeLegacyPort implements LegacyDebitPort {
        final List<String> calls = new ArrayList<>();
        /** Itens que de fato chegaram a porta na ultima remessa (prova da filtragem D1). */
        List<LegacyDebitItem> lastItems = List.of();
        List<LegacyAuthorization> imported = List.of();
        RemittanceResult nextRemittance = new RemittanceResult("REM-1", 0, BigDecimal.ZERO);
        ReturnBatch nextReturn = new ReturnBatch("NSA1-HASH1", List.of());

        @Override public List<LegacyAuthorization> importLegacyAuthorizations(UUID tenantId) {
            calls.add("import");
            return imported;
        }

        @Override public RemittanceResult generateRemittance(UUID tenantId, List<LegacyDebitItem> items) {
            calls.add("remittance:" + items.size());
            lastItems = List.copyOf(items);
            return new RemittanceResult("REM-1", items.size(),
                    items.stream().map(LegacyDebitItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        @Override public ReturnBatch readReturn(UUID tenantId, String returnReference) {
            calls.add("readReturn");
            return nextReturn;
        }

        @Override public void resend(UUID tenantId, String chargeIdentifier) {
            calls.add("resend:" + chargeIdentifier);
        }

        @Override public void cancelOptant(UUID tenantId, UUID unitId) {
            calls.add("cancel:" + unitId);
        }
    }

    static final class InMemoryProcessed implements DebitAccountIntegration.ProcessedReturns {
        private final Set<String> keys = new HashSet<>();

        @Override public boolean alreadyProcessed(UUID tenantId, String returnKey) {
            return keys.contains(tenantId + "|" + returnKey);
        }

        @Override public void markProcessed(UUID tenantId, String returnKey) {
            keys.add(tenantId + "|" + returnKey);
        }
    }

    static final class RecordingSink implements DebitAccountIntegration.SettlementSink {
        final Map<String, BigDecimal> settled = new HashMap<>();

        @Override public void settle(String chargeIdentifier, BigDecimal amount) {
            settled.merge(chargeIdentifier, amount, BigDecimal::add);
        }
    }

    static DebitAccountIntegration integration(FakeLegacyPort port, boolean enabled,
                                               InMemoryProcessed processed, RecordingSink sink) {
        return new DebitAccountIntegration(port, params(enabled), processed, sink);
    }

    /** Variante com qualquer SettlementSink (ex.: sink que falha no meio do lote, teste D2). */
    static DebitAccountIntegration integration(FakeLegacyPort port, boolean enabled,
                                               InMemoryProcessed processed,
                                               DebitAccountIntegration.SettlementSink sink) {
        return new DebitAccountIntegration(port, params(enabled), processed, sink);
    }

    static LegacyDebitPort.LegacyAuthorization auth(UUID unit, String payerBank, String receiverBank,
                                                    boolean active) {
        return new LegacyDebitPort.LegacyAuthorization(TENANT, unit, "MAT-" + unit, payerBank, receiverBank, active);
    }

    private DebitFixture() {
    }
}
