package br.com.ziipsan.core.inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rastreio do hidrometro SERIALIZADO no almoxarifado (ED-58): numero de
 * serie e LACRE acompanhados do recebimento a sucata, como cadeia de
 * custodia de ESTOQUE (local, lacre, responsavel). O ciclo de vida do
 * hidrometro INSTALADO (instalacao/retirada/verificacao) continua na
 * ED-14 — aqui nao ha transicao de estado de medicao, so de custodia;
 * a instalacao aparece como SAIDA para a OS que a ED-14 referencia.
 */
public final class SerializedMeterTracking {

    public enum Custody { RECEIVED, STORED, IN_TRANSIT, ISSUED_TO_ORDER, RETURNED, SCRAPPED }
    public enum EventType { RECEIPT, STORE, TRANSFER_OUT, TRANSFER_IN, ISSUE, RETURN, SEAL_CHANGE, SCRAP }

    public record SerialEvent(EventType type, String fromLocation, String toLocation,
                              String sealCode, String reason, UUID actorId, Instant at) {}

    public static final class SerialUnit {
        private final UUID tenantId;
        private final String serialNumber;
        private final String itemCode;
        private String sealCode;
        private String locationCode;
        private Custody custody;
        private final List<SerialEvent> history = new ArrayList<>();

        private SerialUnit(UUID tenantId, String serialNumber, String itemCode, String sealCode, String locationCode) {
            this.tenantId = tenantId; this.serialNumber = serialNumber; this.itemCode = itemCode;
            this.sealCode = sealCode; this.locationCode = locationCode; this.custody = Custody.RECEIVED;
        }
        public UUID tenantId() { return tenantId; }
        public String serialNumber() { return serialNumber; }
        public String itemCode() { return itemCode; }
        public String sealCode() { return sealCode; }
        public String locationCode() { return locationCode; }
        public Custody custody() { return custody; }
        public List<SerialEvent> history() { return List.copyOf(history); }
    }

    private final Map<String, SerialUnit> units = new LinkedHashMap<>();
    private static String key(UUID t, String serial) { return t + "|" + serial; }

    public SerialUnit receive(UUID tenantId, String serialNumber, String itemCode, String sealCode,
                              String locationCode, UUID actorId, Instant at) {
        if (serialNumber == null || serialNumber.isBlank()) throw new IllegalArgumentException("numero de serie e obrigatorio");
        if (sealCode == null || sealCode.isBlank()) throw new IllegalArgumentException("lacre do recebimento e obrigatorio");
        if (locationCode == null || locationCode.isBlank()) throw new IllegalArgumentException("local do recebimento e obrigatorio");
        String k = key(tenantId, serialNumber);
        if (units.containsKey(k)) throw new IllegalStateException("serial ja rastreado neste tenant: " + serialNumber);
        SerialUnit u = new SerialUnit(tenantId, serialNumber, itemCode, sealCode, locationCode);
        u.history.add(new SerialEvent(EventType.RECEIPT, null, locationCode, sealCode, null, actorId, at));
        u.custody = Custody.STORED;
        units.put(k, u);
        return u;
    }

    public SerialUnit unit(UUID tenantId, String serialNumber) {
        SerialUnit u = units.get(key(tenantId, serialNumber));
        if (u == null) throw new InventoryExceptions.NotFoundException("SERIAL_NOT_FOUND", "serial nao rastreado: " + serialNumber);
        return u;
    }

    public SerialUnit transfer(UUID tenantId, String serialNumber, String toLocation, UUID actorId, Instant at) {
        SerialUnit u = unit(tenantId, serialNumber);
        requireNotScrapped(u, "transferir");
        u.history.add(new SerialEvent(EventType.TRANSFER_OUT, u.locationCode, toLocation, u.sealCode, null, actorId, at));
        u.locationCode = toLocation; u.custody = Custody.IN_TRANSIT;
        u.history.add(new SerialEvent(EventType.TRANSFER_IN, null, toLocation, u.sealCode, null, actorId, at));
        u.custody = Custody.STORED;
        return u;
    }

    public SerialUnit issueToOrder(UUID tenantId, String serialNumber, UUID orderId, UUID actorId, Instant at) {
        SerialUnit u = unit(tenantId, serialNumber);
        requireNotScrapped(u, "entregar");
        Objects.requireNonNull(orderId, "OS da entrega e obrigatoria");
        u.history.add(new SerialEvent(EventType.ISSUE, u.locationCode, null, u.sealCode, "OS " + orderId, actorId, at));
        u.custody = Custody.ISSUED_TO_ORDER;
        return u;
    }

    public SerialUnit changeSeal(UUID tenantId, String serialNumber, String newSeal, String reason, UUID actorId, Instant at) {
        SerialUnit u = unit(tenantId, serialNumber);
        requireNotScrapped(u, "trocar o lacre");
        if (newSeal == null || newSeal.isBlank()) throw new IllegalArgumentException("novo lacre e obrigatorio");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("motivo da troca de lacre e obrigatorio");
        u.history.add(new SerialEvent(EventType.SEAL_CHANGE, u.locationCode, u.locationCode, newSeal,
                "lacre anterior " + u.sealCode + ": " + reason, actorId, at));
        u.sealCode = newSeal;
        return u;
    }

    /** Sucata: terminal, com motivo obrigatorio — fecha a cadeia recebimento -> sucata. */
    public SerialUnit scrap(UUID tenantId, String serialNumber, String reason, UUID actorId, Instant at) {
        SerialUnit u = unit(tenantId, serialNumber);
        requireNotScrapped(u, "sucatear");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("motivo da sucata e obrigatorio");
        u.history.add(new SerialEvent(EventType.SCRAP, u.locationCode, null, u.sealCode, reason, actorId, at));
        u.custody = Custody.SCRAPPED;
        return u;
    }

    private static void requireNotScrapped(SerialUnit u, String op) {
        if (u.custody == Custody.SCRAPPED) throw new IllegalStateException("serial sucateado nao pode " + op + ": " + u.serialNumber);
    }
}
