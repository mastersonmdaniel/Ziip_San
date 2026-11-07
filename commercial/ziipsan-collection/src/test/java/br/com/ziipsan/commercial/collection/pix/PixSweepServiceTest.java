package br.com.ziipsan.commercial.collection.pix;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.2 - varredura GET /pix: recupera callback perdido e Pix sem txid, sem
 * duplicar. Pos-revisao (M4): alem do caminho composto original, cada caminho
 * de decisao da varredura tem teste proprio - pendente de vinculo, devolucao
 * da P3.29, item ja conhecido sem nova gravacao e corrida com o callback
 * (M1). A loja registra o {@code routeSegment} recebido em cada gravacao
 * para provar a fonte explicita do campo (M3): varredura grava sempre
 * {@code null}, nunca um segmento inventado.
 */
class PixSweepServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TXID = "txidABC1234567890abcde12345";
    private static final Instant INICIO = Instant.parse("2026-11-30T00:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-30T23:59:59Z");

    static final class FakeSweepStore implements PixSweepService.SweepStore {
        final Map<String, UUID> inbox = new HashMap<>();
        final Map<UUID, PixCallbackProcessor.Decision> decisions = new HashMap<>();
        final Map<String, PixCallbackProcessor.LocalCharge> chargesByTxid = new HashMap<>();
        final Map<String, String> savedSegments = new HashMap<>();
        final Set<String> raceOnSave = new HashSet<>();
        int saves;

        @Override
        public Optional<UUID> findInboxId(UUID tenantId, String psp, String endToEndId) {
            return Optional.ofNullable(inbox.get(endToEndId));
        }

        @Override
        public UUID saveInbox(UUID tenantId, String psp, PspPixPort.PspPix pix, String source,
                              String routeSegment) {
            savedSegments.put(pix.endToEndId(), routeSegment);
            if (inbox.containsKey(pix.endToEndId()) || raceOnSave.contains(pix.endToEndId())) {
                // Contrato atomico da dedup (V6_3): quem gravou primeiro venceu.
                inbox.putIfAbsent(pix.endToEndId(), UUID.randomUUID());
                throw new PixCallbackProcessor.PixInboxStore.DuplicateEndToEndIdException(
                        "duplicado na varredura: " + pix.endToEndId());
            }
            saves++;
            UUID id = UUID.randomUUID();
            inbox.put(pix.endToEndId(), id);
            return id;
        }

        @Override
        public Optional<PixCallbackProcessor.LocalCharge> findChargeByTxid(UUID t, String p, String txid) {
            return Optional.ofNullable(chargesByTxid.get(txid));
        }

        @Override
        public void updateDecision(UUID inboxId, PixCallbackProcessor.Decision decision) {
            decisions.put(inboxId, decision);
        }
    }

    static final class FakePsp implements PspPixPort {
        List<PspPix> pix = List.of();

        @Override
        public PspBehavior behavior() {
            return new PspBehavior("PSP_X", "reenvia 3x em 1h", 300, "MTLS_CHAIN",
                    false, Optional.empty(), false, false);
        }

        @Override
        public Optional<PspPix> fetchPixByEndToEndId(String e) {
            return Optional.empty();
        }

        @Override
        public List<PspPix> listPixByInterval(Instant i, Instant f) {
            return pix;
        }

        @Override
        public void cancelCharge(String txid) {
        }
    }

    private static PspPixPort.PspPix pix(String e2eid, String txid, String amount, String time) {
        return new PspPixPort.PspPix(e2eid, txid, new BigDecimal(amount),
                Instant.parse(time), null, null);
    }

    private static PixCallbackProcessor.LocalCharge charge(String status) {
        return new PixCallbackProcessor.LocalCharge(UUID.randomUUID(), status, null, null);
    }

    @Test
    void varreduraRecuperaPerdidosSemTxidENaoDuplicaConhecidos() {
        FakeSweepStore store = new FakeSweepStore();
        store.inbox.put("E2E-3", UUID.randomUUID()); // E2E-3 ja chegou por callback
        store.chargesByTxid.put(TXID, charge("ATIVA"));
        FakePsp psp = new FakePsp();
        psp.pix = List.of(
                pix("E2E-1", TXID, "100.00", "2026-11-30T10:00:00Z"),
                pix("E2E-2", null, "50.00", "2026-11-30T10:05:00Z"),
                pix("E2E-3", TXID, "70.00", "2026-11-30T10:10:00Z"));
        var svc = new PixSweepService(store, psp);
        var outcome = svc.sweep(TENANT, INICIO, FIM);
        assertThat(outcome.found()).isEqualTo(3);
        assertThat(outcome.alreadyKnown()).isEqualTo(1);
        assertThat(outcome.recovered()).isEqualTo(2);
        assertThat(outcome.unidentified()).isEqualTo(1); // E2E-2, sem txid
        assertThat(store.decisions.values()).contains(PixCallbackProcessor.Decision.BAIXA,
                PixCallbackProcessor.Decision.NAO_IDENTIFICADO);
        // Item ja conhecido nao foi gravado de novo (M4).
        assertThat(store.saves).isEqualTo(2);
        assertThat(store.savedSegments).doesNotContainKey("E2E-3");
        // Fonte explicita do route_segment da varredura: null, nunca inventado (M3).
        assertThat(store.savedSegments.get("E2E-1")).isNull();
        assertThat(store.savedSegments.get("E2E-2")).isNull();
    }

    @Test
    void varreduraItemJaConhecidoNaoGravaNemDecideDeNovo() {
        FakeSweepStore store = new FakeSweepStore();
        store.inbox.put("E2E-1", UUID.randomUUID());
        store.inbox.put("E2E-2", UUID.randomUUID());
        FakePsp psp = new FakePsp();
        psp.pix = List.of(
                pix("E2E-1", TXID, "100.00", "2026-11-30T10:00:00Z"),
                pix("E2E-2", TXID, "50.00", "2026-11-30T10:05:00Z"));
        var outcome = new PixSweepService(store, psp).sweep(TENANT, INICIO, FIM);
        assertThat(outcome.found()).isEqualTo(2);
        assertThat(outcome.alreadyKnown()).isEqualTo(2);
        assertThat(outcome.recovered()).isZero();
        assertThat(store.saves).isZero();
        assertThat(store.decisions).isEmpty();
    }

    @Test
    void varreduraSemCobrancaLocalRegistraPendenteVinculo() {
        FakeSweepStore store = new FakeSweepStore(); // nenhum txid com cobranca local
        FakePsp psp = new FakePsp();
        psp.pix = List.of(pix("E2E-9", TXID, "100.00", "2026-11-30T10:00:00Z"));
        var outcome = new PixSweepService(store, psp).sweep(TENANT, INICIO, FIM);
        assertThat(outcome.recovered()).isEqualTo(1);
        UUID inboxId = store.inbox.get("E2E-9");
        assertThat(inboxId).isNotNull();
        assertThat(store.decisions.get(inboxId))
                .isEqualTo(PixCallbackProcessor.Decision.PENDENTE_VINCULO);
    }

    @Test
    void varreduraCobrancaJaPagaRegistraDevolucaoP329() {
        FakeSweepStore store = new FakeSweepStore();
        store.chargesByTxid.put(TXID, charge("PAGA"));
        FakePsp psp = new FakePsp();
        psp.pix = List.of(pix("E2E-7", TXID, "100.00", "2026-11-30T10:00:00Z"));
        var outcome = new PixSweepService(store, psp).sweep(TENANT, INICIO, FIM);
        assertThat(outcome.recovered()).isEqualTo(1);
        UUID inboxId = store.inbox.get("E2E-7");
        assertThat(store.decisions.get(inboxId))
                .isEqualTo(PixCallbackProcessor.Decision.DEVOLUCAO_P3_29);
    }

    @Test
    void varreduraCorridaComCallbackContaComoJaConhecido() {
        FakeSweepStore store = new FakeSweepStore();
        store.raceOnSave.add("E2E-5"); // callback grava o mesmo Pix durante a varredura
        FakePsp psp = new FakePsp();
        psp.pix = List.of(pix("E2E-5", TXID, "100.00", "2026-11-30T10:00:00Z"));
        var outcome = new PixSweepService(store, psp).sweep(TENANT, INICIO, FIM);
        assertThat(outcome.found()).isEqualTo(1);
        assertThat(outcome.alreadyKnown()).isEqualTo(1);
        assertThat(outcome.recovered()).isZero();
        assertThat(store.saves).isZero();
        assertThat(store.decisions).isEmpty();
    }
}
