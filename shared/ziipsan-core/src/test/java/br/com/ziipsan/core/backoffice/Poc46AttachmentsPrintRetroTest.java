package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** POC-46 (anexos) + impressao P2.30 com QR + baixa posterior com prazos contados da execucao. */
class Poc46AttachmentsPrintRetroTest {

    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DOC = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID EXECUTOR = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Test
    void attachmentOnlyWhenScanCleared() {
        OrderAttachment.Attachment quarantine = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.QUARANTINE, "image/jpeg", 1024);
        assertThrows(IllegalStateException.class, () -> OrderAttachment.requireLinkable(quarantine));
        OrderAttachment.Attachment infected = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.DOCUMENT, OrderAttachment.ScanStatus.INFECTED, "application/pdf", 2048);
        assertThrows(IllegalStateException.class, () -> OrderAttachment.requireLinkable(infected));
        OrderAttachment.Attachment cleared = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.CLEARED, "image/jpeg", 1024);
        assertSame(cleared, OrderAttachment.requireLinkable(cleared));
    }

    @Test
    void printRequestCarriesQrOpeningTheOrder() {
        WorkOrderPrint.PrintRequest r = WorkOrderPrint.forOrder("OS_PADRAO", ORDER, Map.of("equipe", "Equipe 1"));
        assertEquals("/os/" + ORDER, r.qrPayload());
        assertEquals(List.of(ORDER), r.orderIds());
        WorkOrderPrint.PrintRequest route = WorkOrderPrint.teamRoute("ROTEIRO_EQUIPE",
                List.of(ORDER, DOC), Map.of());
        assertEquals(2, route.orderIds().size());
        assertEquals("/os/" + ORDER, route.qrPayload());
        assertThrows(IllegalArgumentException.class, () -> WorkOrderPrint.forOrder(" ", ORDER, Map.of()));
    }

    private static RetroactiveClose.Close close(ServiceOrderEnums.ExecutionMode mode, String contractorRef,
                                                LocalDateTime executedAt, LocalDateTime registeredAt) {
        return new RetroactiveClose.Close(ORDER, mode, contractorRef, EXECUTOR, executedAt, registeredAt,
                List.of(new PlannedMaterials.Line("CANO", new java.math.BigDecimal("5"), new java.math.BigDecimal("5"))), DOC);
    }

    @Test
    void retroactiveCloseWindowAndModes() {
        LocalDateTime executed = LocalDateTime.of(2026, 11, 6, 10, 0);
        // 3 dias depois: dentro de so.retroactive_close_days = 3
        assertNotNull(RetroactiveClose.validate(close(ServiceOrderEnums.ExecutionMode.FIELD, null,
                executed, LocalDateTime.of(2026, 11, 9, 9, 0)), 3));
        // 4 dias depois: fora da janela
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(close(
                ServiceOrderEnums.ExecutionMode.FIELD, null, executed, LocalDateTime.of(2026, 11, 10, 9, 0)), 3));
        // execucao no futuro
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(close(
                ServiceOrderEnums.ExecutionMode.FIELD, null, LocalDateTime.of(2026, 11, 10, 9, 0), LocalDateTime.of(2026, 11, 9, 9, 0)), 3));
        // empreiteira sem referencia e modo interno sao recusados
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(close(
                ServiceOrderEnums.ExecutionMode.CONTRACTOR, null, executed, LocalDateTime.of(2026, 11, 9, 9, 0)), 3));
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(close(
                ServiceOrderEnums.ExecutionMode.INTERNAL, null, executed, LocalDateTime.of(2026, 11, 9, 9, 0)), 3));
        assertNotNull(RetroactiveClose.validate(close(ServiceOrderEnums.ExecutionMode.CONTRACTOR, "EMP-77",
                executed, LocalDateTime.of(2026, 11, 9, 9, 0)), 3));
    }

    @Test
    void retroactiveClosePhotoMustBeClearedAttachment() {
        // Correcao D4: a foto da baixa posterior e o anexo composto — nao um UUID solto.
        RetroactiveClose.Close c = close(ServiceOrderEnums.ExecutionMode.FIELD, null,
                LocalDateTime.of(2026, 11, 6, 10, 0), LocalDateTime.of(2026, 11, 9, 9, 0));
        OrderAttachment.Attachment cleared = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.CLEARED, "image/jpeg", 1024);
        assertNotNull(RetroactiveClose.validate(c, 3, cleared));
        // Quarentena ou infectada: a baixa NAO vale, mesmo com a baixa em si valida.
        OrderAttachment.Attachment quarantine = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.QUARANTINE, "image/jpeg", 1024);
        assertThrows(IllegalStateException.class, () -> RetroactiveClose.validate(c, 3, quarantine));
        OrderAttachment.Attachment infected = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.INFECTED, "image/jpeg", 1024);
        assertThrows(IllegalStateException.class, () -> RetroactiveClose.validate(c, 3, infected));
        // Anexo CLEARED mas que nao e a foto declarada na baixa: recusado.
        OrderAttachment.Attachment otherDoc = new OrderAttachment.Attachment(ORDER, EXECUTOR,
                OrderAttachment.Kind.PHOTO, OrderAttachment.ScanStatus.CLEARED, "image/jpeg", 1024);
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(c, 3, otherDoc));
        // Anexo da foto mas de tipo errado (documento, nao foto): recusado.
        OrderAttachment.Attachment wrongKind = new OrderAttachment.Attachment(ORDER, DOC,
                OrderAttachment.Kind.DOCUMENT, OrderAttachment.ScanStatus.CLEARED, "image/jpeg", 1024);
        assertThrows(IllegalArgumentException.class, () -> RetroactiveClose.validate(c, 3, wrongKind));
    }

    @Test
    void deadlinesCountFromRecordedExecutionNotRegistration() {
        RetroactiveClose.Close c = close(ServiceOrderEnums.ExecutionMode.FIELD, null,
                LocalDateTime.of(2026, 11, 6, 10, 0), LocalDateTime.of(2026, 11, 9, 9, 0));
        // SLA de religacao de 24h (rec.sla_hours da P3.4): vence 07/11 10:00 — da EXECUCAO, nao do registro (09/11)
        assertEquals(LocalDateTime.of(2026, 11, 7, 10, 0), RetroactiveClose.deadlineFromExecution(c, 24));
        assertTrue(RetroactiveClose.missedDeadline(c, LocalDateTime.of(2026, 11, 7, 11, 0), 24));
        assertFalse(RetroactiveClose.missedDeadline(c, LocalDateTime.of(2026, 11, 7, 9, 0), 24));
    }
}
