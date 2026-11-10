package br.com.ziipsan.commercial.collection.pix;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.2 - callback Pix: forjado, repetido e fora de ordem; gravacao antes da
 * confirmacao; confirmacao GET /pix/{e2eid} antes da baixa. Valores e
 * documentos calculados a mao (CNPJ alfanumerico da P1.39: 12ABC34501DE35).
 * O txid de teste tem 27 caracteres alfanumericos, dentro do CHECK de 26-35
 * da {@code pix_charges} (correcao pos-revisao B4).
 *
 * <p>Pos-revisao: o {@link FakePsp} registra o fetch de confirmacao no MESMO
 * log de chamadas da loja ({@code store.calls}), provando a ordem completa
 * gravacao &lt; fetch de confirmacao &lt; baixa (B1); a loja simula o
 * contrato atomico da dedup da V6_3 - segunda gravacao da mesma chave lanca
 * {@code DuplicateEndToEndIdException} - para os testes de corrida (M1).
 */
class PixCallbackProcessorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SEGMENT = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String CNPJ_ALFA = "12ABC34501DE35";
    private static final String E2EID = "E1234567820261130100000000000001";
    private static final String TXID = "txidABC1234567890abcde12345";
    private static final Instant RECEIVED = Instant.parse("2026-11-30T12:00:00Z");

    static final class FakeStore implements PixCallbackProcessor.PixInboxStore {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final Map<String, UUID> inboxByKey = new ConcurrentHashMap<>();
        PixCallbackProcessor.LocalCharge charge;
        boolean routeActive = true;
        boolean simulateRaceOnSave;
        UUID raceWinnerId;
        int saves;

        @Override
        public Optional<PixCallbackProcessor.WebhookRoute> findRouteBySegment(String segment) {
            calls.add("route");
            return SEGMENT.equals(segment)
                    ? Optional.of(new PixCallbackProcessor.WebhookRoute(
                            TENANT, "PSP_X", "CONV-1", CNPJ_ALFA, routeActive))
                    : Optional.empty();
        }

        @Override
        public Optional<UUID> findInboxId(UUID tenantId, String psp, String endToEndId) {
            calls.add("findInbox");
            return Optional.ofNullable(inboxByKey.get(psp + "|" + endToEndId));
        }

        @Override
        public synchronized UUID saveInbox(UUID tenantId, String psp, String endToEndId, String txid,
                                           BigDecimal amount, Instant paidAt, String payerDocument,
                                           String source, String segment, boolean mtlsVerified,
                                           Boolean secretValid, String payloadJson, Instant receivedAt) {
            calls.add("saveInbox");
            String key = psp + "|" + endToEndId;
            if (simulateRaceOnSave) {
                // Um callback concorrente identico gravou primeiro (corrida M1).
                simulateRaceOnSave = false;
                inboxByKey.put(key, raceWinnerId);
                throw new PixCallbackProcessor.PixInboxStore.DuplicateEndToEndIdException(
                        "duplicado concorrente simulado para " + endToEndId);
            }
            if (inboxByKey.containsKey(key)) {
                // Contrato atomico da dedup (V6_3): segunda linha da mesma chave nao entra.
                throw new PixCallbackProcessor.PixInboxStore.DuplicateEndToEndIdException(
                        "endToEndId ja gravado: " + endToEndId);
            }
            saves++;
            UUID id = UUID.randomUUID();
            inboxByKey.put(key, id);
            return id;
        }

        @Override
        public void updateDecision(UUID inboxId, PixCallbackProcessor.Confirmation confirmation,
                                   PixCallbackProcessor.Decision decision) {
            calls.add("updateDecision:" + decision);
        }

        @Override
        public Optional<PixCallbackProcessor.LocalCharge> findChargeByTxid(UUID tenantId, String psp, String txid) {
            calls.add("findCharge");
            return Optional.ofNullable(charge);
        }
    }

    static final class FakePsp implements PspPixPort {
        PspPixPort.PspPix confirmed;
        boolean supportsSecret;
        List<String> sharedLog;
        int fetches;

        @Override
        public PspBehavior behavior() {
            return new PspBehavior("PSP_X", "reenvia 3x em 1h", 300, "MTLS_CHAIN",
                    supportsSecret, Optional.empty(), true, false);
        }

        @Override
        public Optional<PspPixPort.PspPix> fetchPixByEndToEndId(String endToEndId) {
            fetches++;
            if (sharedLog != null) {
                // Mesmo log da loja: a posicao do fetch na ordem fica provada (B1).
                sharedLog.add("fetchPix");
            }
            return Optional.ofNullable(confirmed);
        }

        @Override
        public List<PspPixPort.PspPix> listPixByInterval(Instant inicio, Instant fim) {
            return List.of();
        }

        @Override
        public void cancelCharge(String txid) {
        }
    }

    private static String payload(String txid) {
        String txidPart = txid == null ? "" : ",\"txid\":\"" + txid + "\"";
        return "{\"convenio\":\"CONV-1\",\"pix\":[{\"endToEndId\":\"" + E2EID + "\""
                + txidPart + ",\"valor\":\"100.00\",\"horario\":\"2026-11-30T11:59:00Z\","
                + "\"pagador\":{\"nome\":\"Padaria\",\"cnpj\":\"" + CNPJ_ALFA + "\"}}]}";
    }

    private static String payloadSemConvenio(String txid) {
        return "{\"pix\":[{\"endToEndId\":\"" + E2EID + "\",\"txid\":\"" + txid + "\""
                + ",\"valor\":\"100.00\",\"horario\":\"2026-11-30T11:59:00Z\","
                + "\"pagador\":{\"nome\":\"Padaria\",\"cnpj\":\"" + CNPJ_ALFA + "\"}}]}";
    }

    private static PixCallbackProcessor processor(FakeStore store, FakePsp psp) {
        store.charge = new PixCallbackProcessor.LocalCharge(UUID.randomUUID(), "ATIVA", null, null);
        psp.confirmed = new PspPixPort.PspPix(E2EID, TXID, new BigDecimal("100.00"),
                Instant.parse("2026-11-30T11:59:00Z"), CNPJ_ALFA, CNPJ_ALFA);
        psp.sharedLog = store.calls;
        return new PixCallbackProcessor(store, psp);
    }

    @Test
    void callbackValidoGravaAntesDeConfirmarEBaixa() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var outcome = processor(store, psp).receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries()).hasSize(1);
        assertThat(outcome.entries().get(0).decision()).isEqualTo(PixCallbackProcessor.Decision.BAIXA);
        assertThat(outcome.entries().get(0).confirmation())
                .isEqualTo(PixCallbackProcessor.Confirmation.CONFIRMADA_GET_PIX);
        assertThat(outcome.entries().get(0).recordedFirst()).isTrue();
        // Ordem completa provada no log compartilhado (B1):
        // gravar na inbox < fetch de confirmacao GET /pix/{e2eid} < decisao de baixa.
        int saveIdx = store.calls.indexOf("saveInbox");
        int fetchIdx = store.calls.indexOf("fetchPix");
        int baixaIdx = store.calls.indexOf("updateDecision:BAIXA");
        assertThat(saveIdx).as("gravacao registrada no log").isGreaterThanOrEqualTo(0);
        assertThat(fetchIdx).as("fetch de confirmacao registrado no mesmo log").isGreaterThan(saveIdx);
        assertThat(baixaIdx).as("baixa registrada depois do fetch").isGreaterThan(fetchIdx);
    }

    @Test
    void callbackForjadoSemMtlsERecusadoESemGravacao() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, false, null, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("mTLS");
        assertThat(store.saves).isZero();
    }

    @Test
    void segmentoDesconhecidoERecusado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                "Z9x8W7v6U5t4S3r2Q1p0N9m8L7k6J5h4", true, null, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("segmento");
    }

    @Test
    void segmentoAusenteOuEmBrancoERecusado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        for (String ausente : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                    ausente, true, null, payload(TXID), RECEIVED)))
                    .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                    .hasMessageContaining("segmento");
        }
        assertThat(store.saves).isZero();
    }

    @Test
    void segmentoFracoSemEntropiaMinimaERecusado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", true, null, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("segmento");
        assertThat(store.saves).isZero();
    }

    @Test
    void rotaInativaERecusada() {
        FakeStore store = new FakeStore();
        store.routeActive = false;
        var proc = processor(store, new FakePsp());
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("inativa");
        assertThat(store.saves).isZero();
    }

    @Test
    void convenioDivergenteDoCadastradoERecusado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        String forged = payload(TXID).replace("CONV-1", "CONV-9");
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, forged, RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("convenio");
        assertThat(store.saves).isZero();
    }

    @Test
    void convenioAusenteNoPayloadERecusado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payloadSemConvenio(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("convenio");
        assertThat(store.saves).isZero();
    }

    @Test
    void segredoAdicionalAusenteQuandoPspOfereceERecusado() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        psp.supportsSecret = true;
        var proc = processor(store, psp);
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("segredo");
        assertThat(store.saves).isZero();
    }

    @Test
    void segredoAdicionalInvalidoQuandoPspOfereceERecusado() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        psp.supportsSecret = true;
        var proc = processor(store, psp);
        assertThatThrownBy(() -> proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, Boolean.FALSE, payload(TXID), RECEIVED)))
                .isInstanceOf(PixCallbackProcessor.PixCallbackRejectedException.class)
                .hasMessageContaining("segredo");
        assertThat(store.saves).isZero();
    }

    @Test
    void segredoAdicionalValidoEAceito() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        psp.supportsSecret = true;
        var outcome = processor(store, psp).receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, Boolean.TRUE, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision()).isEqualTo(PixCallbackProcessor.Decision.BAIXA);
        assertThat(store.saves).isEqualTo(1);
    }

    @Test
    void callbackRepetidoNaoGravaNemBaixaDeNovo() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        var cmd = new PixCallbackProcessor.CallbackCommand(SEGMENT, true, null, payload(TXID), RECEIVED);
        var first = proc.receive(cmd);
        var second = proc.receive(cmd);
        assertThat(second.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.REPETIDO_SEM_EFEITO);
        assertThat(second.entries().get(0).inboxId()).isEqualTo(first.entries().get(0).inboxId());
        assertThat(store.saves).as("repetido nao pode gravar segunda linha").isEqualTo(1);
    }

    @Test
    void duplicadoConcorrentePerdedorViraRepetidoSemSegundaGravacao() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        UUID winnerId = UUID.randomUUID();
        store.simulateRaceOnSave = true;
        store.raceWinnerId = winnerId;
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.REPETIDO_SEM_EFEITO);
        assertThat(outcome.entries().get(0).inboxId())
                .as("perdedor referencia a linha do vencedor").isEqualTo(winnerId);
        assertThat(store.saves).as("perdedor nao gravou segunda linha").isZero();
        assertThat(store.calls).doesNotContain("updateDecision:BAIXA");
    }

    @Test
    void duplicadosConcorrentesEmParaleloGeramUmaUnicaGravacao() throws Exception {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        var cmd = new PixCallbackProcessor.CallbackCommand(SEGMENT, true, null, payload(TXID), RECEIVED);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<PixCallbackProcessor.CallbackOutcome> outcomes = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        outcomes.add(proc.receive(cmd));
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures).isEmpty();
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream()
                .flatMap(o -> o.entries().stream())
                .map(PixCallbackProcessor.EntryOutcome::decision)
                .toList())
                .containsExactlyInAnyOrder(PixCallbackProcessor.Decision.BAIXA,
                        PixCallbackProcessor.Decision.REPETIDO_SEM_EFEITO);
        assertThat(store.saves).as("corrida nao pode gravar duas linhas").isEqualTo(1);
    }

    @Test
    void foraDeOrdemSemCobrancaLocalFicaPendenteDeVinculo() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        store.charge = null; // cobranca local ainda nao registrada
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.PENDENTE_VINCULO);
    }

    @Test
    void pixSemTxidVaiParaNaoIdentificado() {
        FakeStore store = new FakeStore();
        var proc = processor(store, new FakePsp());
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(null), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.NAO_IDENTIFICADO);
    }

    @Test
    void confirmacaoDivergenteBloqueiaABaixa() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        psp.confirmed = new PspPixPort.PspPix(E2EID, TXID, new BigDecimal("90.00"),
                Instant.parse("2026-11-30T11:59:00Z"), CNPJ_ALFA, CNPJ_ALFA);
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.AGUARDANDO_CONFIRMACAO);
        assertThat(outcome.entries().get(0).confirmation())
                .isEqualTo(PixCallbackProcessor.Confirmation.DIVERGENTE);
    }

    @Test
    void confirmacaoAusenteBloqueiaABaixa() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        psp.confirmed = null; // GET /pix/{e2eid} sem retorno no PSP
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.AGUARDANDO_CONFIRMACAO);
        assertThat(outcome.entries().get(0).confirmation())
                .isEqualTo(PixCallbackProcessor.Confirmation.NAO_CONFIRMADA);
        assertThat(outcome.entries().get(0).recordedFirst()).isTrue();
        assertThat(store.calls).contains("updateDecision:AGUARDANDO_CONFIRMACAO");
        assertThat(store.calls).doesNotContain("updateDecision:BAIXA");
    }

    @Test
    void cobrancaJaPagaGeraSegundoPagamentoParaDevolucao() {
        FakeStore store = new FakeStore();
        FakePsp psp = new FakePsp();
        var proc = processor(store, psp);
        store.charge = new PixCallbackProcessor.LocalCharge(UUID.randomUUID(), "PAGA", null, null);
        var outcome = proc.receive(new PixCallbackProcessor.CallbackCommand(
                SEGMENT, true, null, payload(TXID), RECEIVED));
        assertThat(outcome.entries().get(0).decision())
                .isEqualTo(PixCallbackProcessor.Decision.DEVOLUCAO_P3_29);
    }
}
