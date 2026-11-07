package br.com.ziipsan.commercial.collection.pixauto;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fakes em memoria da P4.5: loja e PSP, com registro de chamadas. */
final class PixAutoDebitFixture {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final String PAYER_CPF = "52998224725";
    static final String RECEIVER_CNPJ = "11222333000181";
    static final String PROOF_HASH = "a".repeat(64);
    static final Instant AUTHORIZED_AT = Instant.parse("2027-03-01T12:00:00Z");

    static final class InMemoryStore implements PixAutoDebitService.Store {
        final Map<UUID, PixAutoDebitAuthorization> authorizations = new HashMap<>();
        final Map<UUID, PixAutoDebitInstruction> instructions = new HashMap<>();
        final Map<UUID, UndueDebitRefund> refunds = new HashMap<>();

        @Override public void saveAuthorization(PixAutoDebitAuthorization a) { authorizations.put(a.id(), a); }
        @Override public Optional<PixAutoDebitAuthorization> findAuthorization(UUID t, UUID id) {
            return Optional.ofNullable(authorizations.get(id));
        }
        @Override public void saveInstruction(PixAutoDebitInstruction i) { instructions.put(i.id(), i); }
        @Override public Optional<PixAutoDebitInstruction> findInstruction(UUID t, UUID id) {
            return Optional.ofNullable(instructions.get(id));
        }
        @Override public Optional<PixAutoDebitInstruction> findInstructionByChargeIdentifier(UUID t, String cid) {
            return instructions.values().stream().filter(i -> i.chargeIdentifier().equals(cid)).findFirst();
        }
        @Override public List<PixAutoDebitInstruction> findInstructionsByAuthorization(UUID t, UUID authId) {
            List<PixAutoDebitInstruction> out = new ArrayList<>();
            for (PixAutoDebitInstruction i : instructions.values()) {
                if (i.authorizationId().equals(authId)) {
                    out.add(i);
                }
            }
            return out;
        }
        @Override public void saveRefund(UndueDebitRefund r) { refunds.put(r.id(), r); }
        @Override public Optional<UndueDebitRefund> findRefund(UUID t, UUID id) {
            return Optional.ofNullable(refunds.get(id));
        }
    }

    static final class FakePsp implements PspPixAutoDebitPort {
        final List<String> calls = new ArrayList<>();
        int debitSeq;

        @Override public String psp() { return "PSP_X"; }
        @Override public String registerAuthorization(String payer, String receiver, int journey,
                                                      boolean retryForeseen, BigDecimal maxAmount) {
            calls.add("registerAuthorization");
            return "PSP-AUTH-1";
        }
        @Override public String submitDebit(String chargeIdentifier, BigDecimal amount,
                                            LocalDate settlementDate, String payerDocument) {
            calls.add("submitDebit:" + chargeIdentifier);
            return "E2EID" + (++debitSeq);
        }
        @Override public void cancelDebit(String chargeIdentifier) {
            calls.add("cancelDebit:" + chargeIdentifier);
        }
        @Override public String requestUndueDebitRefund(String chargeIdentifier, String endToEndId, String reason) {
            calls.add("requestRefund:" + chargeIdentifier);
            return "PSP-REFUND-1";
        }
    }

    static PixAutoDebitService service(InMemoryStore store, FakePsp psp) {
        return new PixAutoDebitService(store, psp,
                new PixAutoDebitRules.RetryPolicy(3, 7, true), 2);
    }

    static PixAutoDebitService.AuthorizationRequest request(int journey, boolean qr,
                                                            boolean firstImmediate,
                                                            PixAutoDebitService.InstructionDraft first) {
        return new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT, RECEIVER_CNPJ, null,
                PAYER_CPF, journey, qr, PROOF_HASH, "jornada/registro-1", AUTHORIZED_AT,
                true, 3, null, firstImmediate, first);
    }

    static PixAutoDebitService.InstructionDraft draft(String chargeId, String amount, LocalDate settlement) {
        return new PixAutoDebitService.InstructionDraft(chargeId, "BILL", UUID.randomUUID(),
                settlement.withDayOfMonth(1), new BigDecimal(amount), BigDecimal.ZERO, BigDecimal.ZERO, settlement);
    }

    private PixAutoDebitFixture() {
    }
}
