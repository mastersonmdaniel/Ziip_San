package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ações do operador da P2.33: reenvio idempotente (aceite expresso), inutilização só
 * de faixa não usada com o evento registrado conforme o leiaute em homologação
 * (tpAmb=2), cancelamento pendente, permissões próprias, motivo e tarefas da P2.28.
 */
class FiscalOperatorServiceTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID ISSUER = UUID.randomUUID();

    private static final class ReadMemoria implements FiscalDocumentReadPort {
        final Map<UUID, FiscalDocumentView> docs = new HashMap<>();
        Set<Long> used = Set.of();

        @Override
        public List<FiscalDocumentView> listByTenant(UUID tenantId) {
            return List.copyOf(docs.values());
        }

        @Override
        public Optional<FiscalDocumentView> findById(UUID tenantId, UUID documentId) {
            return Optional.ofNullable(docs.get(documentId));
        }

        @Override
        public Set<Long> usedNumbers(UUID tenantId, UUID issuerId, int serie) {
            return used;
        }
    }

    private static final class TrailMemoria implements FiscalOperatorTrailPort {
        final List<FiscalOperatorAction> actions = new ArrayList<>();

        @Override
        public void record(FiscalOperatorAction action) {
            actions.add(action);
        }

        @Override
        public void updateStatus(UUID tenantId, UUID actionId, FiscalOperatorActionStatus status, String protocol) {
        }

        @Override
        public Optional<FiscalOperatorAction> findByIdempotencyKey(UUID tenantId, String key) {
            return actions.stream().filter(a -> a.idempotencyKey().equals(key)).findFirst();
        }

        @Override
        public Optional<FiscalOperatorAction> findPendingCancellation(UUID tenantId, UUID fiscalDocumentId) {
            return actions.stream().filter(a -> fiscalDocumentId.equals(a.fiscalDocumentId())
                    && a.type() == FiscalOperatorActionType.CANCELLATION_REQUEST
                    && a.status() == FiscalOperatorActionStatus.PENDING).findFirst();
        }

        @Override
        public List<FiscalOperatorAction> findByDocument(UUID tenantId, UUID fiscalDocumentId) {
            return actions.stream().filter(a -> fiscalDocumentId.equals(a.fiscalDocumentId())).toList();
        }
    }

    private static final class WorkMemoria implements FiscalWorkItemPort {
        final List<String> opened = new ArrayList<>();
        final List<String> closed = new ArrayList<>();

        @Override
        public UUID openPendency(UUID tenantId, FiscalPendencyKind kind, UUID fiscalDocumentId,
                                 UUID contractResponsibleId, UUID requesterId) {
            opened.add(kind + ":" + fiscalDocumentId + ":" + contractResponsibleId);
            return UUID.randomUUID();
        }

        @Override
        public void closePendency(UUID tenantId, FiscalPendencyKind kind, UUID fiscalDocumentId, String closeReason) {
            closed.add(kind + ":" + fiscalDocumentId + ":" + closeReason);
        }
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-12-10T13:00:00Z"), ZoneOffset.UTC);
    }

    private static FiscalOperatorService service(ReadMemoria read, TrailMemoria trail, WorkMemoria work,
                                                 FiscalResendGateway resend, FiscalVoidingGateway voiding,
                                                 FiscalCancellationGateway cancellation) {
        return new FiscalOperatorService(clock(), read, trail, resend, voiding, cancellation, work);
    }

    @Test
    @DisplayName("reenvio repetido não gera segundo documento: a porta é chamada uma vez e a mesma ação volta")
    void repeatedResendIsIdempotent() {
        var doc = FiscalQueueTestSupport.nfage(NfageStatus.RESERVED, true, false, null,
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var read = new ReadMemoria();
        read.docs.put(doc.id(), doc);
        var trail = new TrailMemoria();
        AtomicInteger gatewayCalls = new AtomicInteger();
        FiscalResendGateway gateway = accessKey -> {
            gatewayCalls.incrementAndGet();
            return new FiscalResendGateway.ResendOutcome(NfageStatus.AUTHORIZED, "PROT-1", null, null);
        };
        var svc = service(read, trail, new WorkMemoria(), gateway, null, null);

        var first = svc.resend(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), "SEFAZ voltou");
        var second = svc.resend(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), "SEFAZ voltou");

        assertThat(gatewayCalls).hasValue(1);
        assertThat(trail.actions).hasSize(1);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(FiscalOperatorActionStatus.COMPLETED);
        // Um único documento o tempo todo: nenhum documento novo foi criado na leitura.
        assertThat(read.docs).hasSize(1);
    }

    @Test
    @DisplayName("regressão D2: reenvio de TRANSMITTED/PENDING grava estadoFila como JSON null, nunca a string \"null\"")
    void resendOfTransmittedRecordsNullQueueState() {
        var transmitted = FiscalQueueTestSupport.nfage(NfageStatus.TRANSMITTED);
        var pending = FiscalQueueTestSupport.nfage(NfageStatus.PENDING_RECONCILIATION);
        var read = new ReadMemoria();
        read.docs.put(transmitted.id(), transmitted);
        read.docs.put(pending.id(), pending);
        var svc = service(read, new TrailMemoria(), new WorkMemoria(), null, null, null);

        for (var doc : List.of(transmitted, pending)) {
            var action = svc.resend(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                    Set.of(FiscalOperatorPermissions.RESEND), "retransmitir após retorno da SEFAZ");
            assertThat(action.detailJson()).doesNotContain("\"null\"");
            JsonNode detail = Json.readTree(action.detailJson());
            // Sem estado nesta fila: a ausência é o nulo do JSON, presente como nó nulo.
            assertThat(detail.get("estadoFila").isNull()).isTrue();
            assertThat(detail.get("chaveAcesso").asText()).isEqualTo(doc.accessKey());
        }
    }

    @Test
    @DisplayName("reenvio de documento autorizado ou rejeitado é recusado (rejeitado é terminal na P2.6)")
    void resendOfSettledDocumentRejected() {
        var authorized = FiscalQueueTestSupport.nfage(NfageStatus.AUTHORIZED);
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var read = new ReadMemoria();
        read.docs.put(authorized.id(), authorized);
        read.docs.put(rejected.id(), rejected);
        var svc = service(read, new TrailMemoria(), new WorkMemoria(), null, null, null);
        assertThatThrownBy(() -> svc.resend(FiscalQueueTestSupport.TENANT, authorized.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), "motivo"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> svc.resend(FiscalQueueTestSupport.TENANT, rejected.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), "motivo"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cada ação exige a SUA permissão e motivo: permissão de outra ação não autoriza")
    void ownPermissionAndReasonRequired() {
        var doc = FiscalQueueTestSupport.nfage(NfageStatus.RESERVED, true, false, null,
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var read = new ReadMemoria();
        read.docs.put(doc.id(), doc);
        var svc = service(read, new TrailMemoria(), new WorkMemoria(), null, null, null);
        assertThatThrownBy(() -> svc.resend(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.VOID_NUMBER), "motivo"))
                .isInstanceOf(SecurityException.class).hasMessageContaining(FiscalOperatorPermissions.RESEND);
        assertThatThrownBy(() -> svc.resend(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> svc.voidNumbers(FiscalQueueTestSupport.TENANT, ISSUER, 1, 50, 60, ACTOR,
                Set.of(FiscalOperatorPermissions.RESEND), "motivo", 2))
                .isInstanceOf(SecurityException.class).hasMessageContaining(FiscalOperatorPermissions.VOID_NUMBER);
    }

    @Test
    @DisplayName("inutilização em homologação (tpAmb=2) registra o evento conforme o leiaute e conclui com o protocolo da porta")
    void voidingInHomologationRecordsLayoutEvent() {
        var read = new ReadMemoria();
        read.used = Set.of(10L, 11L, 20L);
        var trail = new TrailMemoria();
        List<FiscalVoidingGateway.VoidingEvent> events = new ArrayList<>();
        FiscalVoidingGateway gateway = event -> {
            events.add(event);
            return new FiscalVoidingGateway.VoidingResult("PROT-INUT-1");
        };
        var svc = service(read, trail, new WorkMemoria(), null, gateway, null);

        var action = svc.voidNumbers(FiscalQueueTestSupport.TENANT, ISSUER, 1, 12, 19, ACTOR,
                Set.of(FiscalOperatorPermissions.VOID_NUMBER), "numeração pulada na reserva", 2);

        assertThat(action.status()).isEqualTo(FiscalOperatorActionStatus.COMPLETED);
        assertThat(action.protocol()).isEqualTo("PROT-INUT-1");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).tpAmb()).isEqualTo(2);
        // O detalhe gravado na trilha é JSON lido pela Json do core, com os campos do evento.
        JsonNode detail = Json.readTree(action.detailJson());
        assertThat(detail.get("evento").asText()).isEqualTo("INUTILIZACAO_NUMERACAO");
        assertThat(detail.get("serie").asInt()).isEqualTo(1);
        assertThat(detail.get("numeroInicial").asLong()).isEqualTo(12L);
        assertThat(detail.get("numeroFinal").asLong()).isEqualTo(19L);
        assertThat(detail.get("tpAmb").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("inutilização de faixa com número usado é recusada antes de qualquer registro")
    void voidingUsedRangeRejected() {
        var read = new ReadMemoria();
        read.used = Set.of(10L, 11L, 20L);
        var trail = new TrailMemoria();
        var svc = service(read, trail, new WorkMemoria(), null, null, null);
        assertThatThrownBy(() -> svc.voidNumbers(FiscalQueueTestSupport.TENANT, ISSUER, 1, 12, 21, ACTOR,
                Set.of(FiscalOperatorPermissions.VOID_NUMBER), "motivo", 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20");
        assertThat(trail.actions).isEmpty();
    }

    @Test
    @DisplayName("cancelamento pendente: nasce PENDING, gera tarefa do responsável e repetido devolve o mesmo pedido")
    void cancellationPendingCreatesWorkItem() {
        var doc = FiscalQueueTestSupport.nfage(NfageStatus.AUTHORIZED);
        var read = new ReadMemoria();
        read.docs.put(doc.id(), doc);
        var trail = new TrailMemoria();
        var work = new WorkMemoria();
        var svc = service(read, trail, work, null, null, null);

        var first = svc.requestCancellation(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.REQUEST_CANCELLATION), "emitida para a conta errada", RESPONSIBLE);
        var second = svc.requestCancellation(FiscalQueueTestSupport.TENANT, doc.id(), ACTOR,
                Set.of(FiscalOperatorPermissions.REQUEST_CANCELLATION), "emitida para a conta errada", RESPONSIBLE);

        assertThat(first.status()).isEqualTo(FiscalOperatorActionStatus.PENDING);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(work.opened).containsExactly(
                FiscalPendencyKind.CANCELLATION_PENDING + ":" + doc.id() + ":" + RESPONSIBLE);
    }

    @Test
    @DisplayName("pendência de rejeição e de contingência geram tarefa; documento autorizado encerra os itens")
    void pendencyLifecycle() {
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var contingent = FiscalQueueTestSupport.nfage(NfageStatus.RESERVED, true, false, null,
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var read = new ReadMemoria();
        read.docs.put(rejected.id(), rejected);
        read.docs.put(contingent.id(), contingent);
        var work = new WorkMemoria();
        var svc = service(read, new TrailMemoria(), work, null, null, null);

        assertThat(svc.registerPendency(FiscalQueueTestSupport.TENANT, rejected.id(), RESPONSIBLE, ACTOR))
                .contains(FiscalPendencyKind.REJECTION);
        assertThat(svc.registerPendency(FiscalQueueTestSupport.TENANT, contingent.id(), RESPONSIBLE, ACTOR))
                .contains(FiscalPendencyKind.CONTINGENCY);
        assertThat(work.opened).hasSize(2);

        // O dicionário passa a devolver o documento como AUTHORIZED (mesmo id):
        // é esse o fato que encerra os itens (aceite).
        var authorized = new FiscalDocumentView(contingent.id(), contingent.tenantId(), contingent.kind(),
                NfageStatus.AUTHORIZED, false, contingent.accessKey(), contingent.serie(), contingent.numero(),
                contingent.competence(), contingent.billingGroupId(), false, null, false, null, null,
                contingent.sourceType(), contingent.sourceId(), contingent.issuedAt());
        read.docs.put(authorized.id(), authorized);
        svc.onDocumentAuthorized(FiscalQueueTestSupport.TENANT, contingent.id());
        assertThat(work.closed).contains(
                FiscalPendencyKind.CONTINGENCY + ":" + contingent.id() + ":DOCUMENT_AUTHORIZED");
    }

    @Test
    @DisplayName("sem porta de tarefas ligada, registrar pendência é erro explícito — nunca item fingido")
    void pendencyWithoutWorkPortIsExplicitError() {
        var rejected = FiscalQueueTestSupport.nfage(NfageStatus.REJECTED, false, false, "204",
                YearMonth.of(2026, 12), FiscalQueueTestSupport.GROUP);
        var read = new ReadMemoria();
        read.docs.put(rejected.id(), rejected);
        var svc = new FiscalOperatorService(clock(), read, new TrailMemoria(), null);
        assertThatThrownBy(() -> svc.registerPendency(FiscalQueueTestSupport.TENANT, rejected.id(), RESPONSIBLE, ACTOR))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("P2.28");
    }
}
