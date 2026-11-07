package br.com.ziipsan.commercial.collection.pix;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Varredura periodica de {@code GET /pix} por intervalo (P3.2): recupera o que
 * o callback nao entregou - Pix sem {@code txid} e callbacks perdidos.
 *
 * <p>Para cada Pix devolvido pelo PSP no intervalo {@code [inicio, fim]}:
 * ja gravado na {@code pix_inbox} = ignorado (a varredura nunca duplica);
 * ausente = gravado com origem {@code SWEEP} e mesma decisao do callback
 * (sem txid = nao identificado; cobranca ja paga = devolucao da P3.29;
 * cobranca inexistente = pendente de vinculo). A varredura NAO autentica
 * nada: ela consulta o PSP pela API autenticada da integracao, nao recebe
 * chamada externa. Pela mesma razao ela nao faz confirmacao individual
 * {@code GET /pix/{e2eid}}: a propria listagem autenticada {@code GET /pix}
 * e a fonte do dado gravado.
 *
 * <p><b>Segmento de rota (correcao M3 pos-revisao):</b> a varredura nao nasce
 * de uma rota de webhook, logo nao tem segmento a registrar. A fonte do campo
 * {@code pix_inbox.route_segment} e explicita: a gravacao da varredura passa
 * {@link #NO_ROUTE_SEGMENT} ({@code null}) e a coluna e anulavel na V6_3
 * justamente para isso, com CHECK de que {@code CALLBACK} sempre tem
 * segmento. Nada de valor inventado/sentinela gravado no banco.
 *
 * <p><b>Corrida com o callback (M1):</b> vale o mesmo contrato atomico da
 * porta do callback - se o callback (ou outra varredura) gravar o mesmo
 * {@code endToEndId} primeiro, a porta lanca
 * {@link PixCallbackProcessor.PixInboxStore.DuplicateEndToEndIdException} e
 * o item conta como ja conhecido, sem duplicar.
 */
public final class PixSweepService {

    /**
     * Fonte explicita do {@code route_segment} das linhas de varredura: nao
     * ha segmento, porque a origem nao e uma rota de webhook. A coluna e
     * anulavel na V6_3 para {@code source <> 'CALLBACK'}.
     */
    public static final String NO_ROUTE_SEGMENT = null;

    public record SweepOutcome(int found, int recovered, int alreadyKnown, int unidentified,
                               List<UUID> recoveredInboxIds) {
    }

    /** Porta de persistencia da varredura (mesma pix_inbox do callback). */
    public interface SweepStore {
        Optional<UUID> findInboxId(UUID tenantId, String psp, String endToEndId);

        /**
         * Grava na {@code pix_inbox} sob o mesmo contrato atomico de
         * {@link PixCallbackProcessor.PixInboxStore#saveInbox} (dedup da
         * V6_3; conflito = {@code DuplicateEndToEndIdException}).
         *
         * @param routeSegment segmento da rota de origem; a varredura passa
         *                     sempre {@link PixSweepService#NO_ROUTE_SEGMENT}
         *                     ({@code null}), e o adaptador persiste NULL -
         *                     nunca um valor inventado.
         */
        UUID saveInbox(UUID tenantId, String psp, PspPixPort.PspPix pix, String source,
                       String routeSegment);

        Optional<PixCallbackProcessor.LocalCharge> findChargeByTxid(UUID tenantId, String psp, String txid);

        void updateDecision(UUID inboxId, PixCallbackProcessor.Decision decision);
    }

    private final SweepStore store;
    private final PspPixPort pspPort;

    public PixSweepService(SweepStore store, PspPixPort pspPort) {
        this.store = Objects.requireNonNull(store, "SweepStore e obrigatorio");
        this.pspPort = Objects.requireNonNull(pspPort, "PspPixPort e obrigatorio");
    }

    public SweepOutcome sweep(UUID tenantId, Instant inicio, Instant fim) {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(inicio, "inicio do intervalo e obrigatorio");
        Objects.requireNonNull(fim, "fim do intervalo e obrigatorio");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("intervalo da varredura exige fim depois de inicio");
        }
        String psp = pspPort.behavior().psp();
        List<PspPixPort.PspPix> found = pspPort.listPixByInterval(inicio, fim);
        int recovered = 0;
        int alreadyKnown = 0;
        int unidentified = 0;
        List<UUID> ids = new ArrayList<>();
        for (PspPixPort.PspPix pix : found) {
            if (store.findInboxId(tenantId, psp, pix.endToEndId()).isPresent()) {
                alreadyKnown++;
                continue;
            }
            UUID inboxId;
            try {
                inboxId = store.saveInbox(tenantId, psp, pix, "SWEEP", NO_ROUTE_SEGMENT);
            } catch (PixCallbackProcessor.PixInboxStore.DuplicateEndToEndIdException corrida) {
                // Callback ou varredura concorrente gravou primeiro (M1).
                alreadyKnown++;
                continue;
            }
            PixCallbackProcessor.Decision decision = decide(tenantId, psp, pix.txid());
            store.updateDecision(inboxId, decision);
            if (decision == PixCallbackProcessor.Decision.NAO_IDENTIFICADO) {
                unidentified++;
            }
            recovered++;
            ids.add(inboxId);
        }
        return new SweepOutcome(found.size(), recovered, alreadyKnown, unidentified, List.copyOf(ids));
    }

    private PixCallbackProcessor.Decision decide(UUID tenantId, String psp, String txid) {
        if (txid == null || txid.isBlank()) {
            return PixCallbackProcessor.Decision.NAO_IDENTIFICADO;
        }
        Optional<PixCallbackProcessor.LocalCharge> charge = store.findChargeByTxid(tenantId, psp, txid);
        if (charge.isEmpty()) {
            return PixCallbackProcessor.Decision.PENDENTE_VINCULO;
        }
        PixCallbackProcessor.LocalCharge local = charge.get();
        if ("PAGA".equals(local.status()) || "BAIXADO_BOLETO".equals(local.hybridStatus())) {
            return PixCallbackProcessor.Decision.DEVOLUCAO_P3_29;
        }
        return PixCallbackProcessor.Decision.BAIXA;
    }
}
