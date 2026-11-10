package br.com.ziipsan.commercial.collection.cession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E4 (decisao 62) — ciclo de vida mecanico do recebivel na
 * antecipacao, com TRAVA contra dupla cessao. Produtor (execucao ED-103,
 * decisao 62): sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Estados do REGISTO de cessao de um recebivel:
 * <pre>
 *   (sem registo activo = DISPONIVEL)
 *   reservar:   DISPONIVEL -&gt; RESERVADO
 *   ceder:      RESERVADO  -&gt; CEDIDO
 *   libertar:   RESERVADO  -&gt; LIBERTADO   (terminal; cancela a reserva)
 *   liquidar:   CEDIDO     -&gt; LIQUIDADO   (terminal)
 *   reverter:   CEDIDO     -&gt; REVERTIDO   (terminal; SO com motivo
 *                                            explicito registado em evento)
 * </pre>
 * De CEDIDO so se sai por LIQUIDADO ou por reversao explicita registada —
 * nunca silenciosamente. LIBERTADO/REVERTIDO/LIQUIDADO sao terminais: o
 * recebivel volta a estar DISPONIVEL para um NOVO registo (novo ciclo),
 * mas o registo terminal nunca muda outra vez. Qualquer outra transicao
 * e recusada com erro tipificado {@code ILLEGAL_TRANSITION}; reservar um
 * recebivel com registo activo (RESERVADO ou CEDIDO) e recusado com
 * {@code DOUBLE_CESSION_BLOCKED} — a trava.
 *
 * <p>Cada transicao grava um {@link CessionEvent} com actor, instante e
 * motivo (obrigatorio na reversao). A forma juridica da operacao
 * (FIDC, cessao fiduciaria, conta vinculada) NAO esta aqui: e destino
 * declarado da exportacao/cessao, responsabilidade do cliente
 * (decisao 62) — regra dependente da forma = [SEM FONTE].
 */
public final class CessionRegistry {

    public enum Status { RESERVADO, CEDIDO, LIQUIDADO, LIBERTADO, REVERTIDO }

    /** Evento de transicao, com motivo quando a transicao o exige. */
    public record CessionEvent(Status from, Status to, String actor, Instant at, String reason) {}

    /** Registo de cessao de UM recebivel (um ciclo de vida). */
    public record Registration(UUID id, UUID tenantId, String receivableId, String debtorKey,
                               BigDecimal amount, String destinationLabel,
                               Status status, List<CessionEvent> events) {
        public Registration {
            Objects.requireNonNull(id, "id do registo e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(status, "estado e obrigatorio");
            events = List.copyOf(events);
        }

        /** Activo = trava ligada: RESERVADO ou CEDIDO. */
        public boolean active() {
            return status == Status.RESERVADO || status == Status.CEDIDO;
        }

        Registration transitioned(Status to, String actor, Instant at, String reason) {
            var ev = new java.util.ArrayList<>(events);
            ev.add(new CessionEvent(status, to, actor, at, reason));
            return new Registration(id, tenantId, receivableId, debtorKey, amount,
                    destinationLabel, to, ev);
        }
    }

    private final CessionStore store;

    public CessionRegistry(CessionStore store) {
        this.store = Objects.requireNonNull(store, "loja da cessao e obrigatoria");
    }

    /**
     * Reserva um recebivel DISPONIVEL. A unicidade do registo activo e
     * imposta atomicamente pela loja ({@code tryCreateActive}); duas
     * reservas concorrentes do mesmo recebivel produzem exactamente um
     * sucesso — a outra recebe {@code DOUBLE_CESSION_BLOCKED}.
     */
    public Registration reserve(UUID tenantId, String receivableId, String debtorKey,
                                BigDecimal amount, String destinationLabel,
                                String actor, Instant at) {
        if (receivableId == null || receivableId.isBlank())
            throw new ReceivablesCessionException("CESSION_RECEIVABLE_REQUIRED", "reserva exige o recebivel");
        if (amount == null || amount.signum() <= 0)
            throw new ReceivablesCessionException("CESSION_AMOUNT_INVALID", "reserva exige valor positivo");
        if (destinationLabel == null || destinationLabel.isBlank())
            throw new ReceivablesCessionException("CESSION_DESTINATION_REQUIRED",
                    "reserva exige o destino declarado da operacao (forma juridica e do cliente — decisao 62)");
        var reg = new Registration(UUID.randomUUID(), tenantId, receivableId, debtorKey, amount,
                destinationLabel, Status.RESERVADO,
                List.of(new CessionEvent(null, Status.RESERVADO, actor, at, "RESERVA")));
        if (!store.tryCreateActive(reg))
            throw new ReceivablesCessionException("DOUBLE_CESSION_BLOCKED",
                    "recebivel " + receivableId + " ja tem registo activo (trava de dupla cessao)");
        return reg;
    }

    public Registration cede(UUID tenantId, String receivableId, String actor, Instant at) {
        return transition(tenantId, receivableId, Status.RESERVADO, Status.CEDIDO, actor, at, "CESSAO", false);
    }

    public Registration release(UUID tenantId, String receivableId, String actor, Instant at) {
        return transition(tenantId, receivableId, Status.RESERVADO, Status.LIBERTADO, actor, at, "LIBERTACAO", false);
    }

    public Registration liquidate(UUID tenantId, String receivableId, String actor, Instant at) {
        return transition(tenantId, receivableId, Status.CEDIDO, Status.LIQUIDADO, actor, at, "LIQUIDACAO", false);
    }

    /** Reversao de um CEDIDO: exige motivo explicito — nunca silenciosa. */
    public Registration revert(UUID tenantId, String receivableId, String reason, String actor, Instant at) {
        if (reason == null || reason.isBlank())
            throw new ReceivablesCessionException("CESSION_REVERSAL_REASON_REQUIRED",
                    "reversao de cedido exige motivo explicito registado");
        return transition(tenantId, receivableId, Status.CEDIDO, Status.REVERTIDO, actor, at, reason, true);
    }

    private Registration transition(UUID tenantId, String receivableId, Status from, Status to,
                                    String actor, Instant at, String reason, boolean isReversal) {
        Registration current = store.activeRegistration(tenantId, receivableId)
                .orElseThrow(() -> new ReceivablesCessionException("CESSION_NO_ACTIVE_REGISTRATION",
                        "recebivel " + receivableId + " nao tem registo activo"));
        if (current.status() != from)
            throw new ReceivablesCessionException("ILLEGAL_TRANSITION",
                    "transicao ilegal: " + current.status() + " -> " + to + " no recebivel " + receivableId
                            + (isReversal ? "" : " (esperado a partir de " + from + ")"));
        Registration next = current.transitioned(to, actor, at, reason);
        if (!store.replace(current, next))
            throw new ReceivablesCessionException("CESSION_CONCURRENT_MODIFICATION",
                    "registo do recebivel " + receivableId + " mudou concorrentemente; transicao nao aplicada");
        return next;
    }

    /** Baixas esperadas da reconciliacao (E5): registos CEDIDO do tenant. */
    public List<Registration> expectedSettlements(UUID tenantId) {
        return store.registrations(tenantId).stream()
                .filter(r -> r.status() == Status.CEDIDO)
                .toList();
    }
}
