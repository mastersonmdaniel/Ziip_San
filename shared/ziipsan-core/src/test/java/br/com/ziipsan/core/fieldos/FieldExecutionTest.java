package br.com.ziipsan.core.fieldos;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FieldExecutionTest {
    static FieldExecution exec(boolean blur) {
        return new FieldExecution(UUID.randomUUID(), ServiceOrderStatus.SCHEDULED, Optional.of(blur));
    }
    static FieldExecution.Photo photo(boolean blurred) {
        return new FieldExecution.Photo(UUID.randomUUID(), FieldExecution.PhotoScan.CLEARED, blurred);
    }
    @Test void fullFlowWithBeforeAndAfterPhotosIssuesReceipt() {
        var e = exec(false);
        assertEquals(FieldExecution.VisibleState.EN_ROUTE, e.state());
        e.arriveOnSite();
        e.start(photo(false));
        var r = e.complete(photo(false), Instant.now());
        assertEquals(FieldExecution.VisibleState.COMPLETED, e.state());
        assertNotNull(r.beforePhotoId()); assertNotNull(r.afterPhotoId());
        assertEquals(ServiceOrderStatus.EXECUTED, e.engineStatus());
    }
    @Test void cannotStartBeforeArriving() {
        assertThrows(IllegalStateException.class, () -> exec(false).start(photo(false)));
    }
    @Test void quarantinedPhotoDoesNotStartExecution() {
        var e = exec(false); e.arriveOnSite();
        var q = new FieldExecution.Photo(UUID.randomUUID(), FieldExecution.PhotoScan.QUARANTINE, false);
        assertThrows(IllegalStateException.class, () -> e.start(q));
    }
    @Test void blurRequiredRejectsUnblurredPhoto() {
        var e = exec(true); e.arriveOnSite();
        assertThrows(IllegalStateException.class, () -> e.start(photo(false)));
        e.start(photo(true));
        assertEquals(FieldExecution.VisibleState.IN_EXECUTION, e.state());
    }
    @Test void draftOrderHasNoFieldExecution() {
        assertThrows(IllegalStateException.class,
            () -> new FieldExecution(UUID.randomUUID(), ServiceOrderStatus.DRAFT, Optional.of(false)));
    }
    @Test void absentBlurParameterIsRefusedNotSilentlyOff() {
        // D-3: ausencia de field.photo_blur_faces recusa a execucao; falso
        // EXPLICITO e legitimo e nao equivale a ausencia.
        var ex = assertThrows(IllegalStateException.class,
            () -> new FieldExecution(UUID.randomUUID(), ServiceOrderStatus.SCHEDULED, Optional.empty()));
        assertTrue(ex.getMessage().contains("field.photo_blur_faces"),
                "recusa deve nomear o parametro: " + ex.getMessage());
        assertThrows(IllegalStateException.class,
            () -> FieldOsParameters.requirePhotoBlurFaces(Optional.empty()));
        assertFalse(FieldOsParameters.requirePhotoBlurFaces(Optional.of(false)));
        assertTrue(FieldOsParameters.requirePhotoBlurFaces(Optional.of(true)));
        assertFalse(exec(false).blurRequired());
    }
    @Test void faceBlurPortIsInvokedWhenRegisteringPhoto() {
        // D-2: o registro da foto com desfoque exigido INVOCA a porta e a
        // flag da foto e o resultado da porta.
        var calls = new AtomicInteger();
        FieldOsPorts.FaceBlurPort port = (t, doc) -> { calls.incrementAndGet(); return true; };
        var tenant = UUID.randomUUID();
        var doc = UUID.randomUUID();
        var registered = FieldExecution.registerPhoto(tenant, doc,
                FieldExecution.PhotoScan.CLEARED, true, port);
        assertEquals(1, calls.get());
        assertTrue(registered.facesBlurred());
        assertEquals(doc, registered.documentId());
        // pelo fluxo de execucao: start atraves do elo aplica e aceita
        var e = exec(true); e.arriveOnSite();
        e.startApplyingBlur(tenant, UUID.randomUUID(), FieldExecution.PhotoScan.CLEARED, port);
        assertEquals(2, calls.get());
        assertEquals(FieldExecution.VisibleState.IN_EXECUTION, e.state());
        var r = e.completeApplyingBlur(tenant, UUID.randomUUID(), FieldExecution.PhotoScan.CLEARED, port, Instant.now());
        assertEquals(3, calls.get());
        assertNotNull(r);
    }
    @Test void faceBlurPortReturningFalseStillBlocksPhoto() {
        // D-2 sem mudar a semantica da flag: porta invocada mas devolvendo
        // falso => foto sem desfoque => inicio recusado como antes.
        var calls = new AtomicInteger();
        FieldOsPorts.FaceBlurPort port = (t, doc) -> { calls.incrementAndGet(); return false; };
        var e = exec(true); e.arriveOnSite();
        assertThrows(IllegalStateException.class,
            () -> e.startApplyingBlur(UUID.randomUUID(), UUID.randomUUID(), FieldExecution.PhotoScan.CLEARED, port));
        assertEquals(1, calls.get());
        assertEquals(FieldExecution.VisibleState.ON_SITE, e.state());
    }
    @Test void blurRequiredWithoutPortIsRefusedAndNotRequiredSkipsPort() {
        // D-2: sem porta e com desfoque exigido, registrar e recusado.
        assertThrows(IllegalStateException.class, () -> FieldExecution.registerPhoto(
                UUID.randomUUID(), UUID.randomUUID(), FieldExecution.PhotoScan.CLEARED, true, null));
        // sem exigencia, a porta nao e invocada e a foto nasce sem desfoque
        var calls = new AtomicInteger();
        FieldOsPorts.FaceBlurPort port = (t, doc) -> { calls.incrementAndGet(); return true; };
        var registered = FieldExecution.registerPhoto(UUID.randomUUID(), UUID.randomUUID(),
                FieldExecution.PhotoScan.CLEARED, false, port);
        assertEquals(0, calls.get());
        assertFalse(registered.facesBlurred());
    }
}
