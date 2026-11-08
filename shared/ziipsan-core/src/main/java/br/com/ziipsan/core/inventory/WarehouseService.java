package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico do almoxarifado (ED-58, ADR-0032): estoque por local, entradas,
 * ajustes, requisicao com alcada, reserva, entrega, encerramento com
 * baixa/devolucao, cancelamento, transferencia em transito, inventario e
 * entrada por NF-e. TODA operacao grava movimento no ledger e linha de
 * auditoria; alertas vao para a lista que alimenta o painel do tenant e a
 * aba Estoque da Central.
 *
 * <p>Regras duras do brief desta tarefa:
 * <ul>
 *   <li>baixa AUTOMATICA (encerramento alem do entregue; campo e da
 *       FieldWriteOffService) nunca deixa o fisico negativo nem dispensa
 *       alcada: a falta vira pendencia de regularizacao + alerta;</li>
 *   <li>item controlado NUNCA tem aprovacao automatica;</li>
 *   <li>requisitante NUNCA aprova a propria requisicao (403 + auditoria);</li>
 *   <li>nenhum valor de alcada/prazo/ponto e inventado: vem de
 *       {@link InventoryParameters} ou do item, e a ausencia recusa.</li>
 * </ul>
 */
public final class WarehouseService {

    /** Ator registrado nas aprovacoes automaticas (registro exigido pelo aceite). */
    public static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final InventoryStore store;
    private final InventoryParameters parameters;
    private final InventoryPorts.SupplierLookup suppliers;

    public WarehouseService(InventoryStore store, InventoryParameters parameters, InventoryPorts.SupplierLookup suppliers) {
        this.store = Objects.requireNonNull(store, "store e obrigatorio");
        this.parameters = Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        this.suppliers = suppliers; // so exigido na entrada por NF-e
    }

    // ---------- catalogos ----------
    public void registerLocation(UUID tenant, InventoryItem.StockLocation loc, UUID actor, Instant at) {
        store.putLocation(tenant, loc); audit(tenant, "LOCATION_REGISTERED", actor, loc.code(), loc.name(), false, at);
    }
    public void registerItem(UUID tenant, InventoryItem.Item item, UUID actor, Instant at) {
        store.putItem(tenant, item); audit(tenant, "ITEM_REGISTERED", actor, item.code(), item.name(), false, at);
    }

    // ---------- entradas e ajustes ----------
    public void entry(UUID tenant, String itemCode, String location, BigDecimal qty, BigDecimal unitCost,
                      String reference, UUID actor, Instant at) {
        InventoryItem.Item item = requireItem(tenant, itemCode);
        requireLocation(tenant, location);
        StockBalance b = store.balance(tenant, itemCode, location);
        b.applyEntry(qty, unitCost);
        move(tenant, StockMovement.Type.ENTRY, item, location, qty, unitCost, reference, actor, null, at);
        refreshNegative(tenant, b, at);
        audit(tenant, "STOCK_ENTRY", actor, itemCode + "@" + location, "qty=" + qty + " custo=" + unitCost, false, at);
    }

    /** Ajuste manual: justificativa OBRIGATORIA (aceite); sinal livre, sempre auditado. */
    public void adjust(UUID tenant, String itemCode, String location, BigDecimal signedQty, String justification,
                       UUID actor, Instant at) {
        if (justification == null || justification.isBlank())
            throw new InventoryExceptions.ValidationException("JUSTIFICATION_REQUIRED", "ajuste de estoque exige justificativa");
        InventoryItem.Item item = requireItem(tenant, itemCode);
        requireLocation(tenant, location);
        StockBalance b = store.balance(tenant, itemCode, location);
        b.applyAdjustment(signedQty);
        move(tenant, StockMovement.Type.ADJUSTMENT, item, location, signedQty, null, null, actor, justification, at);
        refreshNegative(tenant, b, at);
        audit(tenant, "STOCK_ADJUSTMENT", actor, itemCode + "@" + location, "qty=" + signedQty + ": " + justification, false, at);
    }

    // ---------- requisicao e alcada ----------
    public record RequisitionLineInput(String itemCode, BigDecimal qty) {}

    public Requisition createRequisition(UUID tenant, UUID orderId, UUID requesterId, String location,
                                         List<RequisitionLineInput> inputs, Instant at) {
        requireLocation(tenant, location);
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("requisicao exige ao menos uma linha");
        List<Requisition.Line> lines = new ArrayList<>();
        for (RequisitionLineInput in : inputs) {
            InventoryItem.Item item = requireItem(tenant, in.itemCode());
            if (!item.active()) throw new InventoryExceptions.ValidationException("ITEM_INACTIVE", "item inativo: " + in.itemCode());
            lines.add(new Requisition.Line(in.itemCode(), in.qty(), store.balance(tenant, in.itemCode(), location).averageCost()));
        }
        Requisition req = new Requisition(UUID.randomUUID(), tenant, orderId, requesterId, location, lines, at);
        store.saveRequisition(req);
        audit(tenant, "REQUISITION_CREATED", requesterId, req.id().toString(),
                "OS " + orderId + " total=" + req.totalValue(), false, at);
        return req;
    }

    /**
     * Tentativa de aprovacao automatica: so sem item controlado, com base
     * de custo (medio > 0 em todas as linhas — sem custo, o "valor" seria
     * zero ficticio e a faixa nao significaria nada) e total dentro de
     * inv.auto_approval_limit. A aprovacao e REGISTRADA (ator SYSTEM,
     * modo AUTO, limite na nota) e ja reserva.
     */
    public boolean tryAutoApproval(UUID tenant, UUID requisitionId, Instant at) {
        Requisition req = requireRequisition(tenant, requisitionId);
        if (req.status() != Requisition.Status.PENDING_APPROVAL) return false;
        boolean anyControlled = req.lines().stream().anyMatch(l -> requireItem(tenant, l.itemCode()).controlled());
        boolean costBasis = req.lines().stream().allMatch(l -> l.unitAvgCost().signum() > 0);
        if (anyControlled || !costBasis) return false;
        BigDecimal limit = InventoryParameters.requireAutoLimit(parameters.autoApprovalLimit(tenant));
        if (!ApprovalPolicy.autoApprovable(req.totalValue(), false, limit)) return false;
        req.markApproved(Requisition.ApprovalMode.AUTO, SYSTEM_ACTOR,
                "aprovacao automatica: total " + req.totalValue() + " <= " + InventoryParameters.AUTO_APPROVAL_LIMIT + "=" + limit, at);
        store.saveRequisition(req);
        applyReservation(req, SYSTEM_ACTOR, at);
        audit(tenant, "REQUISITION_AUTO_APPROVED", SYSTEM_ACTOR, req.id().toString(),
                "total=" + req.totalValue() + " limite=" + limit, false, at);
        return true;
    }

    /** Aprovacao humana pela faixa de valor e pelo papel (inv.approval_bands). */
    public void approve(UUID tenant, UUID requisitionId, UUID approverId, java.util.Set<String> approverRoles, Instant at) {
        Requisition req = requireRequisition(tenant, requisitionId);
        if (req.requesterId().equals(approverId)) {
            audit(tenant, "REQUISITION_APPROVAL_DENIED", approverId, req.id().toString(),
                    "SELF_APPROVAL_FORBIDDEN: requisitante nao aprova a propria requisicao", true, at);
            throw new InventoryExceptions.AccessDeniedException("SELF_APPROVAL_FORBIDDEN",
                    "requisitante nao aprova a propria requisicao (403)");
        }
        if (req.status() != Requisition.Status.PENDING_APPROVAL)
            throw new IllegalStateException("requisicao em " + req.status() + " nao pode ser aprovada");
        List<InventoryParameters.ApprovalBand> bands = InventoryParameters.requireBands(parameters.approvalBands(tenant));
        String role = ApprovalPolicy.requiredRole(req.totalValue(), bands);
        if (approverRoles == null || !approverRoles.contains(role)) {
            audit(tenant, "REQUISITION_APPROVAL_DENIED", approverId, req.id().toString(),
                    "APPROVER_ROLE_REQUIRED: faixa exige " + role, true, at);
            throw new InventoryExceptions.AccessDeniedException("APPROVER_ROLE_REQUIRED",
                    "aprovacao deste valor exige o papel " + role + " (403)");
        }
        req.markApproved(Requisition.ApprovalMode.HUMAN, approverId, "faixa: papel " + role, at);
        store.saveRequisition(req);
        applyReservation(req, approverId, at);
        audit(tenant, "REQUISITION_APPROVED", approverId, req.id().toString(), "papel=" + role + " total=" + req.totalValue(), false, at);
    }

    public void reject(UUID tenant, UUID requisitionId, UUID approverId, String reason, Instant at) {
        Requisition req = requireRequisition(tenant, requisitionId);
        if (req.requesterId().equals(approverId)) {
            audit(tenant, "REQUISITION_REJECTION_DENIED", approverId, req.id().toString(), "SELF_DECISION_FORBIDDEN", true, at);
            throw new InventoryExceptions.AccessDeniedException("SELF_APPROVAL_FORBIDDEN",
                    "requisitante nao decide a propria requisicao (403); para desistir, cancele a requisicao");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("motivo da rejeicao e obrigatorio");
        req.markRejected(approverId, reason, at);
        store.saveRequisition(req);
        audit(tenant, "REQUISITION_REJECTED", approverId, req.id().toString(), reason, false, at);
    }

    private void applyReservation(Requisition req, UUID actor, Instant at) {
        for (Requisition.Line l : req.lines()) {
            StockBalance b = store.balance(req.tenantId(), l.itemCode(), req.locationCode());
            b.applyReservation(l.qty());
            move(req.tenantId(), StockMovement.Type.RESERVATION, requireItem(req.tenantId(), l.itemCode()),
                    req.locationCode(), l.qty(), null, "REQ " + req.id(), actor, null, at);
            refreshNegative(req.tenantId(), b, at);
            checkReorder(req.tenantId(), b, at);
        }
    }

    // ---------- entrega, encerramento, cancelamento ----------
    /** Entrega registrada: consome reserva e fisico; so de requisicao aprovada. */
    public void deliver(UUID tenant, UUID requisitionId, UUID actor, Instant at) {
        Requisition req = requireRequisition(tenant, requisitionId);
        if (req.status() != Requisition.Status.APPROVED) throw new IllegalStateException("entrega exige requisicao APPROVED: " + req.status());
        for (Requisition.Line l : req.lines()) {
            StockBalance b = store.balance(tenant, l.itemCode(), req.locationCode());
            b.applyDelivery(l.qty());
            move(tenant, StockMovement.Type.DELIVERY, requireItem(tenant, l.itemCode()), req.locationCode(),
                    l.qty(), null, "REQ " + req.id(), actor, null, at);
            refreshNegative(tenant, b, at);
        }
        req.markDelivered(); store.saveRequisition(req);
        audit(tenant, "REQUISITION_DELIVERED", actor, req.id().toString(), "OS " + req.orderId(), false, at);
    }

    public record CloseLine(String itemCode, BigDecimal deliveredQty, BigDecimal usedQty,
                            BigDecimal returnedQty, BigDecimal extraWrittenQty, BigDecimal shortfallQty,
                            BigDecimal lineCost) {}
    public record CloseResult(UUID orderId, BigDecimal totalMaterialCost, List<CloseLine> lines) {}

    /**
     * Encerramento da OS: baixa a quantidade USADA e devolve a SOBRA, com
     * custo apropriado na OS (usado x custo medio vigente). Usado acima do
     * entregue e baixa AUTOMATICA: piso zero no fisico, falta vira
     * pendencia de regularizacao + alerta (nunca negativo silencioso).
     */
    public CloseResult closeOrder(UUID tenant, UUID orderId, Map<String, BigDecimal> usedQtyByItem, UUID actor, Instant at) {
        Objects.requireNonNull(usedQtyByItem, "quantidades usadas sao obrigatorias no encerramento");
        List<Requisition> delivered = store.requisitionsByOrder(tenant, orderId).stream()
                .filter(r -> r.status() == Requisition.Status.DELIVERED).toList();
        if (delivered.isEmpty()) throw new InventoryExceptions.ValidationException("NO_DELIVERED_REQUISITION",
                "encerramento sem requisicao entregue para a OS " + orderId);
        // Correcao M1 da revisao ED-58: o mapa e POR ITEM (chave itemCode, sem
        // requisicao), logo o usado vale UMA vez por item na OS inteira. O
        // codigo anterior aplicava o total do item a CADA linha de CADA
        // requisicao (2+ requisicoes do mesmo item contavam o usado em dobro:
        // sobra nunca devolvida e custo dobrado). Agora: PRE-VALIDA tudo,
        // AGREGA o entregue do item em todas as requisicoes entregues da OS e
        // baixa/devolve UMA vez por item (por local, quando o item foi
        // entregue de mais de um local, o usado e alocado entre os locais).
        for (Requisition req : delivered) {
            for (Requisition.Line l : req.lines()) {
                BigDecimal used = usedQtyByItem.get(l.itemCode());
                if (used == null) throw new InventoryExceptions.ValidationException("USED_QTY_MISSING",
                        "encerramento exige o usado de " + l.itemCode() + " (previsto x usado da ED-36)");
                if (used.signum() < 0) throw new IllegalArgumentException("usado nao pode ser negativo: " + l.itemCode());
            }
        }
        // item -> (local -> entregue agregado), preservando a ordem de encontro
        Map<String, Map<String, BigDecimal>> deliveredByItemLocation = new java.util.LinkedHashMap<>();
        for (Requisition req : delivered)
            for (Requisition.Line l : req.lines())
                deliveredByItemLocation.computeIfAbsent(l.itemCode(), k -> new java.util.LinkedHashMap<>())
                        .merge(req.locationCode(), l.qty(), BigDecimal::add);
        String reqRefs = delivered.stream().map(r -> "REQ " + r.id()).reduce((a, b) -> a + "," + b).orElse("OS " + orderId);
        List<CloseLine> out = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Map<String, BigDecimal>> e : deliveredByItemLocation.entrySet()) {
            String itemCode = e.getKey();
            BigDecimal used = usedQtyByItem.get(itemCode);
            BigDecimal deliveredTotal = e.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal returnedSum = BigDecimal.ZERO, extraSum = BigDecimal.ZERO, shortfallSum = BigDecimal.ZERO;
            BigDecimal costSum = BigDecimal.ZERO;
            BigDecimal remainingUsed = used;
            List<Map.Entry<String, BigDecimal>> byLocation = new ArrayList<>(e.getValue().entrySet());
            for (int i = 0; i < byLocation.size(); i++) {
                String location = byLocation.get(i).getKey();
                BigDecimal deliveredHere = byLocation.get(i).getValue();
                // ultimo local absorve o restante do usado (inclusive o extra alem do entregue)
                BigDecimal usedHere = (i == byLocation.size() - 1) ? remainingUsed : remainingUsed.min(deliveredHere);
                remainingUsed = remainingUsed.subtract(usedHere.min(deliveredHere));
                StockBalance b = store.balance(tenant, itemCode, location);
                BigDecimal returned = BigDecimal.ZERO, extraWritten = BigDecimal.ZERO, shortfall = BigDecimal.ZERO;
                if (usedHere.compareTo(deliveredHere) <= 0) {
                    returned = deliveredHere.subtract(usedHere);
                    if (returned.signum() > 0) {
                        b.applyReturn(returned);
                        move(tenant, StockMovement.Type.RETURN, requireItem(tenant, itemCode), location,
                                returned, null, reqRefs, actor, "sobra devolvida no encerramento", at);
                    }
                } else {
                    BigDecimal extra = usedHere.subtract(deliveredHere);
                    extraWritten = b.applyAutomaticWriteOff(extra);
                    if (extraWritten.signum() > 0)
                        move(tenant, StockMovement.Type.WRITE_OFF_USED, requireItem(tenant, itemCode), location,
                                extraWritten, null, reqRefs, actor, "usado alem do entregue", at);
                    shortfall = extra.subtract(extraWritten);
                    if (shortfall.signum() > 0) registerShortfall(tenant, itemCode, location, shortfall, "OS " + orderId, at);
                }
                costSum = costSum.add(usedHere.multiply(b.averageCost()).setScale(2, RoundingMode.HALF_UP));
                returnedSum = returnedSum.add(returned); extraSum = extraSum.add(extraWritten); shortfallSum = shortfallSum.add(shortfall);
                refreshNegative(tenant, b, at);
                checkReorder(tenant, b, at);
            }
            total = total.add(costSum);
            InventoryItem.Item item = requireItem(tenant, itemCode);
            store.addTaskConsumption(new InventoryStore.TaskConsumption(tenant, null, orderId, itemCode, used, item.kind() == InventoryItem.Kind.PPE, at));
            out.add(new CloseLine(itemCode, deliveredTotal, used, returnedSum, extraSum, shortfallSum,
                    costSum.setScale(2, RoundingMode.HALF_UP)));
        }
        for (Requisition req : delivered) { req.markClosed(); store.saveRequisition(req); }
        audit(tenant, "ORDER_CLOSED_STOCK", actor, orderId.toString(), "custo de material apropriado=" + total, false, at);
        return new CloseResult(orderId, total.setScale(2, RoundingMode.HALF_UP), List.copyOf(out));
    }

    /** OS cancelada libera a reserva (e requisicao pendente apenas cancela). */
    public void cancelOrder(UUID tenant, UUID orderId, UUID actor, Instant at) {
        // Correcao M4 da revisao ED-58: PRE-VALIDA o estado de TODAS as
        // requisicoes da OS ANTES de mutar qualquer uma. OS mista
        // (APPROVED + DELIVERED) e recusada inteira: nenhuma reserva e
        // liberada, nenhuma requisicao e cancelada e a auditoria
        // ORDER_CANCELLED_STOCK so e gravada no cancelamento valido.
        List<Requisition> reqs = store.requisitionsByOrder(tenant, orderId);
        for (Requisition req : reqs) {
            if (req.status() == Requisition.Status.DELIVERED) {
                throw new InventoryExceptions.ValidationException("ORDER_ALREADY_DELIVERED",
                        "OS com material entregue nao cancela a reserva: feche por encerramento/devolucao");
            }
        }
        for (Requisition req : reqs) {
            if (req.status() == Requisition.Status.APPROVED) {
                for (Requisition.Line l : req.lines()) {
                    StockBalance b = store.balance(tenant, l.itemCode(), req.locationCode());
                    b.applyReservationRelease(l.qty());
                    move(tenant, StockMovement.Type.RESERVATION_RELEASE, requireItem(tenant, l.itemCode()), req.locationCode(),
                            l.qty(), null, "REQ " + req.id(), actor, "OS cancelada", at);
                    refreshNegative(tenant, b, at);
                }
            }
            if (req.status() == Requisition.Status.PENDING_APPROVAL || req.status() == Requisition.Status.APPROVED) {
                req.markCancelled(); store.saveRequisition(req);
            }
            // demais estados (REJECTED/CLOSED/CANCELLED) sao inocuos; DELIVERED ja foi recusado na pre-validacao
        }
        audit(tenant, "ORDER_CANCELLED_STOCK", actor, orderId.toString(), "reservas liberadas", false, at);
    }

    // ---------- transferencia entre locais (estado de transito) ----------
    public InventoryStore.Transfer startTransfer(UUID tenant, String itemCode, BigDecimal qty,
                                                 String from, String to, UUID actor, Instant at) {
        InventoryItem.Item item = requireItem(tenant, itemCode);
        requireLocation(tenant, from); requireLocation(tenant, to);
        if (from.equals(to)) throw new IllegalArgumentException("transferencia exige origem diferente do destino");
        StockBalance origin = store.balance(tenant, itemCode, from);
        if (origin.physical().compareTo(qty) < 0)
            throw new InventoryExceptions.ValidationException("INSUFFICIENT_STOCK_FOR_TRANSFER",
                    "fisico insuficiente em " + from + " para transferir " + qty + " de " + itemCode);
        origin.applyAdjustment(qty.negate());
        InventoryStore.Transfer tr = new InventoryStore.Transfer(UUID.randomUUID(), tenant, itemCode, qty,
                from, to, InventoryStore.Transfer.Status.IN_TRANSIT, at, null);
        store.saveTransfer(tr);
        move(tenant, StockMovement.Type.TRANSFER_OUT, item, from, qty, null, "TRF " + tr.id(), actor, "destino " + to, at);
        refreshNegative(tenant, origin, at);
        audit(tenant, "TRANSFER_STARTED", actor, tr.id().toString(), itemCode + " " + from + "->" + to + " qty=" + qty, false, at);
        return tr;
    }

    public void confirmTransfer(UUID tenant, UUID transferId, UUID actor, Instant at) {
        InventoryStore.Transfer tr = store.transfer(tenant, transferId)
                .orElseThrow(() -> new InventoryExceptions.NotFoundException("TRANSFER_NOT_FOUND", "transferencia nao encontrada: " + transferId));
        if (tr.status() != InventoryStore.Transfer.Status.IN_TRANSIT) throw new IllegalStateException("transferencia em " + tr.status() + " nao pode ser recebida");
        StockBalance origin = store.balance(tenant, tr.itemCode(), tr.fromLocation());
        StockBalance dest = store.balance(tenant, tr.itemCode(), tr.toLocation());
        dest.applyEntry(tr.qty(), origin.averageCost());
        store.saveTransfer(new InventoryStore.Transfer(tr.id(), tenant, tr.itemCode(), tr.qty(), tr.fromLocation(),
                tr.toLocation(), InventoryStore.Transfer.Status.RECEIVED, tr.createdAt(), at));
        move(tenant, StockMovement.Type.TRANSFER_IN, requireItem(tenant, tr.itemCode()), tr.toLocation(), tr.qty(),
                null, "TRF " + tr.id(), actor, "origem " + tr.fromLocation(), at);
        refreshNegative(tenant, dest, at);
        audit(tenant, "TRANSFER_RECEIVED", actor, tr.id().toString(), tr.itemCode() + " qty=" + tr.qty(), false, at);
    }

    public void cancelTransfer(UUID tenant, UUID transferId, UUID actor, Instant at) {
        InventoryStore.Transfer tr = store.transfer(tenant, transferId)
                .orElseThrow(() -> new InventoryExceptions.NotFoundException("TRANSFER_NOT_FOUND", "transferencia nao encontrada: " + transferId));
        if (tr.status() != InventoryStore.Transfer.Status.IN_TRANSIT) throw new IllegalStateException("transferencia em " + tr.status() + " nao pode ser cancelada");
        StockBalance origin = store.balance(tenant, tr.itemCode(), tr.fromLocation());
        origin.applyReturn(tr.qty());
        store.saveTransfer(new InventoryStore.Transfer(tr.id(), tenant, tr.itemCode(), tr.qty(), tr.fromLocation(),
                tr.toLocation(), InventoryStore.Transfer.Status.CANCELLED, tr.createdAt(), at));
        move(tenant, StockMovement.Type.RETURN, requireItem(tenant, tr.itemCode()), tr.fromLocation(), tr.qty(),
                null, "TRF " + tr.id(), actor, "transferencia cancelada", at);
        audit(tenant, "TRANSFER_CANCELLED", actor, tr.id().toString(), tr.itemCode() + " qty=" + tr.qty(), false, at);
    }

    // ---------- inventario ----------
    /** Contagem: divergencia e ajuste — justificativa obrigatoria quando ha diferenca. */
    public InventoryStore.InventoryCount inventoryCount(UUID tenant, String location, Map<String, BigDecimal> countedByItem,
                                                          String justification, UUID actor, Instant at) {
        requireLocation(tenant, location);
        List<InventoryStore.CountLine> lines = new ArrayList<>();
        boolean anyDiff = false;
        for (Map.Entry<String, BigDecimal> e : countedByItem.entrySet()) {
            if (e.getValue() == null || e.getValue().signum() < 0) throw new IllegalArgumentException("contagem nao pode ser negativa: " + e.getKey());
            BigDecimal system = store.balance(tenant, e.getKey(), location).physical();
            InventoryStore.CountLine line = new InventoryStore.CountLine(e.getKey(), system, e.getValue());
            lines.add(line);
            if (line.difference().signum() != 0) anyDiff = true;
        }
        if (anyDiff && (justification == null || justification.isBlank()))
            throw new InventoryExceptions.ValidationException("JUSTIFICATION_REQUIRED", "inventario com divergencia exige justificativa");
        for (InventoryStore.CountLine line : lines) {
            if (line.difference().signum() == 0) continue;
            StockBalance b = store.balance(tenant, line.itemCode(), location);
            b.applyAdjustment(line.difference());
            move(tenant, StockMovement.Type.INVENTORY_COUNT, requireItem(tenant, line.itemCode()), location,
                    line.difference(), null, "INV " + location, actor, justification, at);
            refreshNegative(tenant, b, at);
        }
        InventoryStore.InventoryCount count = new InventoryStore.InventoryCount(UUID.randomUUID(), tenant, location,
                List.copyOf(lines), justification, actor, at);
        store.saveCount(count);
        audit(tenant, "INVENTORY_COUNTED", actor, count.id().toString(), "local=" + location + " linhas=" + lines.size(), false, at);
        return count;
    }

    // ---------- lotes ----------
    public void registerLot(UUID tenant, LotControl.Lot lot, UUID actor, Instant at) {
        InventoryItem.Item item = requireItem(tenant, lot.itemCode());
        requireLocation(tenant, lot.locationCode());
        LotControl.requireValid(item, lot);
        store.saveLot(lot);
        audit(tenant, "LOT_REGISTERED", actor, lot.itemCode(), "lote=" + lot.lotCode() + " validade=" + lot.expiryDate(), false, at);
    }

    // ---------- entrada por NF-e (P3.31) ----------
    public record NfeReceiptResult(NfeEntry.ParsedNfe nfe, UUID supplierId, NfeEntry.Conference conference, boolean posted, boolean duplicate) {
        /** Compatibilidade com a forma original de 4 componentes (duplicate=false). */
        public NfeReceiptResult(NfeEntry.ParsedNfe nfe, UUID supplierId, NfeEntry.Conference conference, boolean posted) {
            this(nfe, supplierId, conference, posted, false);
        }
    }

    /**
     * Entrada pela NF-e do fornecedor do cadastro da P3.31: valida o
     * emitente pelo DOCUMENTO, le o XML e confere item/quantidade contra
     * o esperado. Divergencia NAO posta entrada (alerta + auditoria).
     * Fronteira O9-4: nenhum contractorRef e resolvido aqui — a consulta
     * e so por documento, como o SupplierRecord da P3.31 permite.
     */
    public NfeReceiptResult receiveFromNfe(UUID tenant, String xml, Map<String, BigDecimal> expectedByCode,
                                           String location, UUID actor, Instant at) {
        requireLocation(tenant, location);
        if (suppliers == null) throw new IllegalStateException("SupplierLookup (P3.31) nao fiado: entrada por NF-e nao opera");
        NfeEntry.ParsedNfe nfe = NfeEntry.parse(xml);
        UUID supplierId = suppliers.findSupplierIdByDocument(tenant, nfe.supplierDocument())
                .orElseThrow(() -> new InventoryExceptions.ValidationException("SUPPLIER_NOT_REGISTERED",
                        "emitente da NF-e nao esta no cadastro de fornecedores da P3.31: " + nfe.supplierDocument()));
        if (!suppliers.isActive(tenant, supplierId))
            throw new InventoryExceptions.ValidationException("SUPPLIER_INACTIVE", "fornecedor inativo na P3.31: " + supplierId);
        // Correcao M3 (idempotencia): reenvio da MESMA chave de acesso ja
        // postada NAO duplica a entrada — devolve duplicate=true sem postar.
        // A chave so e marcada DEPOIS de uma postagem com sucesso: divergencia
        // ou recusa de validacao NAO queimam a chave (o reenvio corrigido posta).
        if (store.nfeEntryProcessed(tenant, nfe.accessKey())) {
            audit(tenant, "NFE_ENTRY_DUPLICATE_IGNORED", actor, nfe.accessKey(),
                    "reenvio idempotente por chave de acesso: nenhuma entrada nova", false, at);
            return new NfeReceiptResult(nfe, supplierId, NfeEntry.check(nfe, expectedByCode), false, true);
        }
        NfeEntry.Conference conf = NfeEntry.check(nfe, expectedByCode);
        if (!conf.confered()) {
            alert(tenant, InventoryPorts.AlertKind.NFE_DIVERGENCE, null, location,
                    "NF-e " + nfe.accessKey() + " divergente: " + conf.divergences(), at);
            audit(tenant, "NFE_DIVERGENCE", actor, nfe.accessKey(), conf.divergences().toString(), false, at);
            return new NfeReceiptResult(nfe, supplierId, conf, false);
        }
        // Correcao M3 (validacao ANTES de postar): TODAS as linhas sao
        // validadas antes de qualquer entry — catalogo, quantidade/custo e,
        // para item de lote (reagente/quimico) com <rastro>, a validade
        // (dVal) presente e parseavel. Rastro sem dVal recusa a NF-e inteira
        // com NFE_LOT_EXPIRY_MISSING e o fisico fica intacto (antes: a
        // entrada postava e LocalDate.parse(null) lancava NPE depois).
        Map<String, java.time.LocalDate> parsedLotExpiry = new java.util.HashMap<>();
        for (NfeEntry.NfeItem it : nfe.items()) {
            InventoryItem.Item item = store.item(tenant, it.productCode())
                    .orElseThrow(() -> new InventoryExceptions.ValidationException("ITEM_NOT_IN_CATALOG",
                            "item da NF-e fora do catalogo do almoxarifado: " + it.productCode()));
            if (it.qty() == null || it.qty().signum() <= 0 || it.unitValue() == null || it.unitValue().signum() < 0)
                throw new InventoryExceptions.ValidationException("NFE_ITEM_INVALID",
                        "item da NF-e com quantidade/custo invalido: " + it.productCode());
            if (item.lotTracked() && it.lotCode() != null && item.kind() != InventoryItem.Kind.PPE) {
                if (it.lotExpiry() == null || it.lotExpiry().isBlank())
                    throw new InventoryExceptions.ValidationException("NFE_LOT_EXPIRY_MISSING",
                            "NF-e recusada antes de postar: item de lote " + it.productCode()
                                    + " traz <rastro> com lote " + it.lotCode() + " sem validade (dVal) — fisico intacto");
                try {
                    parsedLotExpiry.put(it.productCode() + "|" + it.lotCode(), java.time.LocalDate.parse(it.lotExpiry()));
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new InventoryExceptions.ValidationException("NFE_LOT_EXPIRY_INVALID",
                            "NF-e recusada antes de postar: validade (dVal) ilegivel do lote " + it.lotCode()
                                    + " de " + it.productCode() + ": " + it.lotExpiry());
                }
            }
        }
        for (NfeEntry.NfeItem it : nfe.items()) {
            InventoryItem.Item item = store.item(tenant, it.productCode()).orElseThrow();
            entry(tenant, it.productCode(), location, it.qty(), it.unitValue(), "NFE " + nfe.accessKey(), actor, at);
            if (item.lotTracked() && it.lotCode() != null && item.kind() != InventoryItem.Kind.PPE) {
                // Lote da NF-e (rastro) so para reagente/quimico; EPI registra o lote com CA a parte (o CA nao viaja na NF-e).
                store.saveLot(new LotControl.Lot(tenant, it.productCode(), location, it.lotCode(),
                        parsedLotExpiry.get(it.productCode() + "|" + it.lotCode()), null, it.qty()));
            }
        }
        store.markNfeEntryProcessed(tenant, nfe.accessKey());
        audit(tenant, "NFE_ENTRY_POSTED", actor, nfe.accessKey(), "fornecedor=" + supplierId + " local=" + location, false, at);
        return new NfeReceiptResult(nfe, supplierId, conf, true);
    }

    // ---------- internos ----------
    void registerShortfall(UUID tenant, String itemCode, String location, BigDecimal missing, String reference, Instant at) {
        store.addPendency(new InventoryStore.ShortfallPendency(UUID.randomUUID(), tenant, itemCode, location, missing, reference, at));
        alert(tenant, InventoryPorts.AlertKind.WRITE_OFF_SHORTFALL, itemCode, location,
                "baixa automatica sem fisico: faltam " + missing + " (" + reference + ") — pendencia de regularizacao", at);
    }

    void refreshNegative(UUID tenant, StockBalance b, Instant at) {
        if (b.available().signum() < 0 || b.physical().signum() < 0) {
            boolean wasKnown = store.negativeSince(tenant, b.itemCode(), b.locationCode()).isPresent();
            store.markNegativeSince(tenant, b.itemCode(), b.locationCode(), at);
            if (!wasKnown) alert(tenant, InventoryPorts.AlertKind.NEGATIVE_BALANCE, b.itemCode(), b.locationCode(),
                    "saldo negativo: fisico=" + b.physical() + " reservado=" + b.reserved() + " disponivel=" + b.available(), at);
        } else {
            store.clearNegativeSince(tenant, b.itemCode(), b.locationCode());
        }
    }

    void checkReorder(UUID tenant, StockBalance b, Instant at) {
        store.item(tenant, b.itemCode()).ifPresent(item -> {
            if (item.reorderPoint() != null && b.available().compareTo(item.reorderPoint()) <= 0)
                alert(tenant, InventoryPorts.AlertKind.REORDER_POINT, b.itemCode(), b.locationCode(),
                        "disponivel " + b.available() + " <= ponto de reposicao " + item.reorderPoint(), at);
        });
    }

    InventoryItem.Item requireItem(UUID tenant, String code) {
        return store.item(tenant, code).orElseThrow(() ->
                new InventoryExceptions.NotFoundException("ITEM_NOT_FOUND", "item nao cadastrado no almoxarifado: " + code));
    }
    private InventoryItem.StockLocation requireLocation(UUID tenant, String code) {
        return store.location(tenant, code).orElseThrow(() ->
                new InventoryExceptions.NotFoundException("LOCATION_NOT_FOUND", "local de estoque nao cadastrado: " + code));
    }
    private Requisition requireRequisition(UUID tenant, UUID id) {
        return store.requisition(tenant, id).orElseThrow(() ->
                new InventoryExceptions.NotFoundException("REQUISITION_NOT_FOUND", "requisicao nao encontrada: " + id));
    }
    private void move(UUID tenant, StockMovement.Type type, InventoryItem.Item item, String location, BigDecimal qty,
                      BigDecimal unitCost, String reference, UUID actor, String reason, Instant at) {
        store.appendMovement(new StockMovement(UUID.randomUUID(), tenant, type, item.code(), location, qty, unitCost, reference, actor, reason, at));
    }
    void audit(UUID tenant, String action, UUID actor, String reference, String detail, boolean denied, Instant at) {
        store.appendAudit(new InventoryPorts.AuditEntry(tenant, action, actor, reference, detail, denied, at));
    }
    void alert(UUID tenant, InventoryPorts.AlertKind kind, String item, String location, String detail, Instant at) {
        store.appendAlert(new InventoryPorts.Alert(tenant, kind, item, location, detail, at));
    }
}
