package br.com.ziipsan.core.fieldos;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IdentityScanTest {
    static final UUID U1 = UUID.randomUUID(), U2 = UUID.randomUUID();
    @Test void matchingMeterCodeConfirms() {
        IdentityScan.requireMatch(
            new IdentityScan.Expected(U1, IdentityScan.Target.METER, "HID-123"),
            new IdentityScan.Scanned(U1, IdentityScan.Target.METER, "HID-123", IdentityScan.Channel.QR));
    }
    @Test void codeFromAnotherUnitIsRefused() {
        assertThrows(IllegalStateException.class, () -> IdentityScan.requireMatch(
            new IdentityScan.Expected(U1, IdentityScan.Target.METER, "HID-123"),
            new IdentityScan.Scanned(U2, IdentityScan.Target.METER, "HID-123", IdentityScan.Channel.BARCODE)));
    }
    @Test void wrongCodeAndWrongTargetAreRefused() {
        assertThrows(IllegalStateException.class, () -> IdentityScan.requireMatch(
            new IdentityScan.Expected(U1, IdentityScan.Target.SEAL, "LAC-9"),
            new IdentityScan.Scanned(U1, IdentityScan.Target.SEAL, "LAC-8", IdentityScan.Channel.NFC)));
        assertThrows(IllegalStateException.class, () -> IdentityScan.requireMatch(
            new IdentityScan.Expected(U1, IdentityScan.Target.ASSET_PLATE, "PLQ-1"),
            new IdentityScan.Scanned(U1, IdentityScan.Target.METER, "PLQ-1", IdentityScan.Channel.QR)));
    }
}
