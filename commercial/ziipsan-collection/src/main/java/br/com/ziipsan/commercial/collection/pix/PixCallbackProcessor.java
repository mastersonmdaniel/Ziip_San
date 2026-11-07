package br.com.ziipsan.commercial.collection.pix;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Recebimento do callback Pix (P3.2) - o fluxo normado e, nesta ordem:
 *
 * <ol>
 *   <li><b>Rota</b>: o segmento aleatorio da URL resolve tenant, convenio e PSP.
 *       Segmento ausente, sem a entropia minima de {@link WebhookSegmentPolicy},
 *       desconhecido ou de rota inativa = callback recusado. O convenio e
 *       conferido SEMPRE: payload sem o campo {@code convenio}, ou com valor
 *       diferente do cadastrado, e recusado sem gravar nada como valido.</li>
 *   <li><b>Origem</b>: mTLS com o certificado do PSP e obrigatorio; onde o PSP
 *       oferecer segredo adicional ({@link PspPixPort.PspBehavior}), ele tambem
 *       e exigido. NAO existe assinatura do PSP no padrao do Banco Central -
 *       nenhum campo de assinatura e lido ou presumido.</li>
 *   <li><b>Gravacao</b>: cada Pix e gravado na {@code pix_inbox} ANTES de
 *       qualquer confirmacao ou baixa. E essa gravacao que autoriza a resposta
 *       200 em menos de 2 s: a camada HTTP responde assim que este metodo
 *       devolve a gravacao (a ordem completa gravacao &lt; confirmacao &lt;
 *       baixa e provada em teste pelo log compartilhado de chamadas).</li>
 *   <li><b>Idempotencia</b>: {@code (tenant, psp, endToEndId)} ja gravado =
 *       {@link Decision#REPETIDO_SEM_EFEITO}, sem segunda gravacao e sem
 *       segunda baixa. A consulta previa NAO e atomica, por isso a porta de
 *       gravacao tem contrato atomico (tabela de dedup nao particionada da
 *       V6_3): se dois callbacks duplicados correrem em paralelo, o perdedor
 *       recebe {@link PixInboxStore.DuplicateEndToEndIdException} da gravacao
 *       e o processador o converte no mesmo {@code REPETIDO_SEM_EFEITO},
 *       relendo o identificador do vencedor.</li>
 *   <li><b>Confirmacao</b>: sem mecanismo equivalente no PSP, a baixa exige
 *       {@code GET /pix/{e2eid}} conferindo valor e endToEndId; divergencia ou
 *       ausencia deixa a entrada {@link Decision#AGUARDANDO_CONFIRMACAO}.</li>
 *   <li><b>Vinculo</b>: sem {@code txid} = nao identificado (fila da P3.29);
 *       com {@code txid} ainda sem cobranca local = fora de ordem, fica
 *       {@link Decision#PENDENTE_VINCULO} para a varredura religar; cobranca
 *       ja paga (inclusive pelo boleto do documento hibrido) = segundo
 *       pagamento, {@link Decision#DEVOLUCAO_P3_29}.</li>
 * </ol>
 *
 * <p>JSON somente pela classe {@link Json} do core. Documento do pagador,
 * quando presente, e validado pela P1.39 (CPF ou CNPJ alfanumerico).
 */
public final class PixCallbackProcessor {

    public enum Decision {
        BAIXA, REPETIDO_SEM_EFEITO, PENDENTE_VINCULO, NAO_IDENTIFICADO,
        DEVOLUCAO_P3_29, AGUARDANDO_CONFIRMACAO
    }

    public enum Confirmation { CONFIRMADA_GET_PIX, CONFIRMADA_EQUIVALENTE, NAO_CONFIRMADA, DIVERGENTE }

    /** Recusa do callback (forjado ou incoerente): nada e baixado nem gravado como valido. */
    public static final class PixCallbackRejectedException extends RuntimeException {
        public PixCallbackRejectedException(String reason) {
            super(reason);
        }
    }

    /**
     * Rota resolvida pelo segmento da URL, como cadastrada - inclusive a flag
     * {@code active}: o adaptador NAO filtra rota inativa por conta propria;
     * quem a recusa e o processador, para a recusa ser regra testavel.
     */
    public record WebhookRoute(UUID tenantId, String psp, String agreementCode, String receiverDocument,
                               boolean active) {
    }

    /** Cobranca local encontrada pelo txid. */
    public record LocalCharge(UUID chargeId, String status, UUID hybridDocumentId, String hybridStatus) {
    }

    /**
     * @param segment           segmento aleatorio da URL chamada
     * @param mtlsVerified      a camada de borda validou o certificado do PSP (mTLS)
     * @param secretValid       segredo adicional conferido; nulo quando nao houve apresentacao
     * @param payloadJson       corpo bruto do callback (array {@code pix} do padrao BCB)
     * @param receivedAt        instante de recebimento (relogio da borda, injetado)
     */
    public record CallbackCommand(String segment, boolean mtlsVerified, Boolean secretValid,
                                  String payloadJson, Instant receivedAt) {
    }

    /** @param recordedFirst verdadeiro: a gravacao na pix_inbox precedeu confirmacao e baixa */
    public record EntryOutcome(String endToEndId, UUID inboxId, Decision decision,
                               Confirmation confirmation, boolean recordedFirst) {
    }

    public record CallbackOutcome(UUID tenantId, String psp, List<EntryOutcome> entries) {
    }

    /** Porta de persistencia/consulta do callback. */
    public interface PixInboxStore {

        /**
         * Sinal de corrida de duplicados: a gravacao encontrou a chave
         * {@code (tenant, psp, endToEndId)} ja gravada por um callback
         * concorrente que venceu a corrida. O adaptador lanca esta excecao a
         * partir do conflito real do banco (ver o contrato de
         * {@link #saveInbox}); o processador a converte em
         * {@link Decision#REPETIDO_SEM_EFEITO}.
         */
        final class DuplicateEndToEndIdException extends RuntimeException {
            public DuplicateEndToEndIdException(String message) {
                super(message);
            }
        }

        Optional<WebhookRoute> findRouteBySegment(String segment);

        Optional<UUID> findInboxId(UUID tenantId, String psp, String endToEndId);

        /**
         * Grava na {@code pix_inbox}. CONTRATO ATOMICO (correcao M1
         * pos-revisao): a consulta previa do processador nao e atomica e o
         * indice unico da {@code pix_inbox} particionada inclui
         * {@code received_at}, logo NAO impede duas linhas do mesmo
         * {@code endToEndId} em particoes distintas. O adaptador DEVE, na
         * MESMA transacao da gravacao, gravar antes a chave na tabela NAO
         * particionada {@code pix_inbox_dedup} (PK
         * {@code (tenant_id, psp, end_to_end_id)}, criada na V6_3) usando
         * ON CONFLICT DO NOTHING ou captura do erro de unicidade: em
         * conflito, lanca {@link DuplicateEndToEndIdException} sem gravar a
         * segunda linha. (Equivalente aceito na integracao: lock consultivo
         * transacional por {@code (psp, endToEndId)} cobrindo consulta +
         * gravacao.) A fiacao JPA/JDBC deste contrato e pendencia nomeada da
         * integracao; a regra de tratamento da corrida esta aqui e em teste.
         */
        UUID saveInbox(UUID tenantId, String psp, String endToEndId, String txid, BigDecimal amount,
                       Instant paidAt, String payerDocument, String source, String segment,
                       boolean mtlsVerified, Boolean secretValid, String payloadJson, Instant receivedAt);

        void updateDecision(UUID inboxId, Confirmation confirmation, Decision decision);

        Optional<LocalCharge> findChargeByTxid(UUID tenantId, String psp, String txid);
    }

    private final PixInboxStore store;
    private final PspPixPort pspPort;

    public PixCallbackProcessor(PixInboxStore store, PspPixPort pspPort) {
        this.store = Objects.requireNonNull(store, "PixInboxStore e obrigatorio");
        this.pspPort = Objects.requireNonNull(pspPort, "PspPixPort e obrigatorio");
    }

    public CallbackOutcome receive(CallbackCommand cmd) {
        Objects.requireNonNull(cmd, "comando do callback e obrigatorio");
        Objects.requireNonNull(cmd.receivedAt(), "instante de recebimento e obrigatorio");
        if (cmd.segment() == null || cmd.segment().isBlank()) {
            throw new PixCallbackRejectedException("callback sem segmento de rota");
        }
        if (!WebhookSegmentPolicy.hasRequiredEntropy(cmd.segment())) {
            throw new PixCallbackRejectedException("segmento de webhook sem a entropia minima "
                    + "exigida (WebhookSegmentPolicy): segmento fraco ou previsivel nao resolve rota");
        }
        WebhookRoute route = store.findRouteBySegment(cmd.segment())
                .orElseThrow(() -> new PixCallbackRejectedException(
                        "segmento de webhook desconhecido: " + cmd.segment()));
        if (!route.active()) {
            throw new PixCallbackRejectedException("rota de webhook inativa para o segmento informado");
        }
        PspPixPort.PspBehavior behavior = pspPort.behavior();
        if (!behavior.psp().equals(route.psp())) {
            throw new PixCallbackRejectedException("PSP da rota (" + route.psp()
                    + ") difere do PSP da porta (" + behavior.psp() + ")");
        }
        // Origem: mTLS sempre; segredo adicional onde o PSP oferecer.
        if (!cmd.mtlsVerified()) {
            throw new PixCallbackRejectedException("callback sem mTLS do PSP (forjado ou mal configurado)");
        }
        if (behavior.supportsAdditionalSecret() && !Boolean.TRUE.equals(cmd.secretValid())) {
            throw new PixCallbackRejectedException("segredo adicional do PSP ausente ou invalido");
        }
        if (!DocumentNumber.isValidCnpj(route.receiverDocument())) {
            throw new PixCallbackRejectedException("CNPJ do recebedor da rota invalido (P1.39)");
        }

        JsonNode root = Json.readTree(cmd.payloadJson());
        JsonNode pixArray = root.get("pix");
        if (pixArray == null || !pixArray.isArray() || pixArray.isEmpty()) {
            throw new PixCallbackRejectedException("payload sem array 'pix' nao vazio");
        }
        // Convenio conferido SEMPRE com o cadastrado (correcao M2
        // pos-revisao): payload sem o campo, em branco ou divergente e
        // recusado - a conferencia nao e condicional a declaracao do payload.
        JsonNode agreementNode = root.get("convenio");
        if (agreementNode == null || !agreementNode.isTextual() || agreementNode.textValue().isBlank()
                || !agreementNode.textValue().equals(route.agreementCode())) {
            throw new PixCallbackRejectedException("convenio do callback ausente ou divergente "
                    + "do convenio cadastrado da rota (" + route.agreementCode() + ")");
        }

        List<EntryOutcome> entries = new ArrayList<>();
        for (JsonNode pixNode : pixArray) {
            entries.add(processEntry(route, behavior, pixNode, cmd));
        }
        return new CallbackOutcome(route.tenantId(), route.psp(), List.copyOf(entries));
    }

    private EntryOutcome processEntry(WebhookRoute route, PspPixPort.PspBehavior behavior,
                                      JsonNode pixNode, CallbackCommand cmd) {
        String e2eid = text(pixNode, "endToEndId");
        if (e2eid == null || e2eid.isBlank()) {
            throw new PixCallbackRejectedException("Pix do callback sem endToEndId");
        }
        JsonNode amountNode = pixNode.get("valor");
        if (amountNode == null || !amountNode.isTextual() && !amountNode.isNumber()) {
            throw new PixCallbackRejectedException("Pix " + e2eid + " sem valor valido");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountNode.asText());
        } catch (NumberFormatException e) {
            throw new PixCallbackRejectedException("Pix " + e2eid + " com valor nao numerico: "
                    + amountNode.asText());
        }
        if (amount.signum() <= 0) {
            throw new PixCallbackRejectedException("Pix " + e2eid + " com valor nao positivo: " + amount);
        }
        String horario = text(pixNode, "horario");
        if (horario == null) {
            throw new PixCallbackRejectedException("Pix " + e2eid + " sem horario");
        }
        Instant paidAt = Instant.parse(horario);
        String txid = text(pixNode, "txid");
        String payerDocument = payerDocumentOf(pixNode);

        // Idempotencia ANTES de gravar: repetido nao grava nem baixa de novo.
        Optional<UUID> existing = store.findInboxId(route.tenantId(), route.psp(), e2eid);
        if (existing.isPresent()) {
            return new EntryOutcome(e2eid, existing.get(), Decision.REPETIDO_SEM_EFEITO,
                    Confirmation.NAO_CONFIRMADA, false);
        }

        // GRAVACAO PRIMEIRO: e ela que autoriza o 200 em menos de 2 s.
        // Corrida de duplicados (M1): se um callback concorrente identico
        // gravou primeiro, a porta lanca DuplicateEndToEndIdException pelo
        // contrato atomico da dedup; o perdedor vira REPETIDO_SEM_EFEITO com
        // o identificador do vencedor, sem segunda linha e sem baixa.
        UUID inboxId;
        try {
            inboxId = store.saveInbox(route.tenantId(), route.psp(), e2eid, txid, amount, paidAt,
                    payerDocument, "CALLBACK", cmd.segment(), cmd.mtlsVerified(), cmd.secretValid(),
                    cmd.payloadJson(), cmd.receivedAt());
        } catch (PixInboxStore.DuplicateEndToEndIdException corrida) {
            Optional<UUID> winner = store.findInboxId(route.tenantId(), route.psp(), e2eid);
            if (winner.isPresent()) {
                return new EntryOutcome(e2eid, winner.get(), Decision.REPETIDO_SEM_EFEITO,
                        Confirmation.NAO_CONFIRMADA, false);
            }
            throw corrida;
        }

        // Confirmacao antes da baixa, quando o PSP nao oferece equivalente.
        Confirmation confirmation;
        if (behavior.requiresGetPixConfirmation()) {
            Optional<PspPixPort.PspPix> confirmed = pspPort.fetchPixByEndToEndId(e2eid);
            if (confirmed.isEmpty()) {
                confirmation = Confirmation.NAO_CONFIRMADA;
                store.updateDecision(inboxId, confirmation, Decision.AGUARDANDO_CONFIRMACAO);
                return new EntryOutcome(e2eid, inboxId, Decision.AGUARDANDO_CONFIRMACAO, confirmation, true);
            }
            PspPixPort.PspPix remote = confirmed.get();
            if (!remote.endToEndId().equals(e2eid) || remote.amount().compareTo(amount) != 0) {
                confirmation = Confirmation.DIVERGENTE;
                store.updateDecision(inboxId, confirmation, Decision.AGUARDANDO_CONFIRMACAO);
                return new EntryOutcome(e2eid, inboxId, Decision.AGUARDANDO_CONFIRMACAO, confirmation, true);
            }
            confirmation = Confirmation.CONFIRMADA_GET_PIX;
        } else {
            confirmation = Confirmation.CONFIRMADA_EQUIVALENTE;
        }

        Decision decision = decide(route, txid);
        store.updateDecision(inboxId, confirmation, decision);
        return new EntryOutcome(e2eid, inboxId, decision, confirmation, true);
    }

    private Decision decide(WebhookRoute route, String txid) {
        if (txid == null || txid.isBlank()) {
            return Decision.NAO_IDENTIFICADO;
        }
        Optional<LocalCharge> charge = store.findChargeByTxid(route.tenantId(), route.psp(), txid);
        if (charge.isEmpty()) {
            // Fora de ordem: o Pix chegou antes do registro local da cobranca.
            return Decision.PENDENTE_VINCULO;
        }
        LocalCharge local = charge.get();
        if ("PAGA".equals(local.status()) || "BAIXADO_BOLETO".equals(local.hybridStatus())) {
            return Decision.DEVOLUCAO_P3_29;
        }
        return Decision.BAIXA;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String payerDocumentOf(JsonNode pixNode) {
        JsonNode pagador = pixNode.get("pagador");
        if (pagador == null) {
            return null;
        }
        String doc = text(pagador, "cnpj");
        if (doc == null) {
            doc = text(pagador, "cpf");
        }
        if (doc == null) {
            return null;
        }
        if (!DocumentNumber.isValidCnpj(doc) && !DocumentNumber.isValidCpf(doc)) {
            throw new PixCallbackRejectedException("documento do pagador invalido (P1.39): "
                    + DocumentNumber.normalize(doc));
        }
        return DocumentNumber.normalize(doc);
    }
}
