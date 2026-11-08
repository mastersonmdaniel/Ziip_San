package br.com.ziipsan.platform.portal.consumer.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.portal.consumer.AccessibleBillService;
import br.com.ziipsan.platform.portal.consumer.AdhesionContractService;
import br.com.ziipsan.platform.portal.consumer.BillDocumentService;
import br.com.ziipsan.platform.portal.consumer.ConsumerAccessGuard;
import br.com.ziipsan.platform.portal.consumer.ConsumerDataChangeService;
import br.com.ziipsan.platform.portal.consumer.ConsumerHistoryService;
import br.com.ziipsan.platform.portal.consumer.ConsumerLinkService;
import br.com.ziipsan.platform.portal.consumer.HolderChangeService;
import br.com.ziipsan.platform.portal.consumer.LinkAttemptLimiter;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 (D5) - a borda expoe TODAS as frentes do aceite, guard antes de cada servico. */
class ConsumerPortalControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private static final byte[] XML = "<NFe>original</NFe>".getBytes(StandardCharsets.UTF_8);

    private final AtomicInteger protocolCalls = new AtomicInteger();
    private final List<AdhesionContractService.DeliveryRecord> deliveries = new ArrayList<>();

    private static FieldProtector protector() {
        byte[] enc = new byte[32]; byte[] idx = new byte[32];
        Arrays.fill(enc, (byte) 7); Arrays.fill(idx, (byte) 9);
        TenantKeyProvider keys = new TenantKeyProvider() {
            @Override public byte[] encryptionKey(UUID tenantId) { return enc.clone(); }
            @Override public byte[] blindIndexKey(UUID tenantId) { return idx.clone(); }
        };
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ConsumerPortalController controller() {
        var links = new ConsumerLinkService(protector(),
                (t, reg) -> Optional.empty(),
                new ConsumerLinkService.LinkStorePort() {
                    @Override public boolean isAlreadyLinked(UUID t, String s, UUID u) { return false; }
                    @Override public void saveLink(UUID t, String s, UUID u, String d, String c, Instant at) { }
                },
                new ConsumerLinkService.AttemptPort() {
                    @Override public int attemptsByUnit(UUID t, UUID u) { return 0; }
                    @Override public int attemptsByIp(UUID t, String ip) { return 0; }
                    @Override public int attemptsByTenant(UUID t) { return 0; }
                    @Override public void recordAttempt(UUID t, UUID u, String ip, boolean ok, boolean a) { }
                },
                new LinkAttemptLimiter.Limits(5, 5, 100, 4));
        var history = new ConsumerHistoryService(new ConsumerHistoryService.HistoryPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public List<ConsumerHistoryService.HistoryEntry> bills(UUID t, UUID u,
                    java.time.YearMonth from) { return List.of(); }
            @Override public List<ConsumerHistoryService.HistoryEntry> payments(UUID t, UUID u,
                    java.time.YearMonth from) { return List.of(); }
        });
        var documents = new BillDocumentService(new BillDocumentService.DocumentPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.of(UNIT); }
            @Override public Optional<BillDocumentService.BillDocuments> documents(UUID t, UUID b) {
                return Optional.of(new BillDocumentService.BillDocuments(true, "QUITADA",
                        new byte[]{1}, true, true, XML, sha256Hex(XML)));
            }
        }, (t, s, b, kind) -> { });
        var accessible = new AccessibleBillService(new AccessibleBillService.AccessiblePort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public boolean accessibilityIndicatorActive(UUID t, UUID u) { return false; }
            @Override public Optional<AccessibleBillService.AccessibleBill> accessibleBill(UUID t, UUID b) {
                return Optional.of(new AccessibleBillService.AccessibleBill(
                        BILL, "nov/2026", "R$ 90,00", "10/12/2026", "12 m3"));
            }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.of(UNIT); }
        }, (t, s, b, kind) -> { });
        var dataChanges = new ConsumerDataChangeService(new ConsumerDataChangeService.RequestPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public String openProtocolAndInternalOrder(UUID t, String s, UUID u, String k, String j) {
                protocolCalls.incrementAndGet();
                return "PROT-2026-1";
            }
        });
        var contracts = new AdhesionContractService(new AdhesionContractService.ContractPort() {
            @Override public Optional<AdhesionContractService.PublishedContract> currentAdhesionContract(UUID t) {
                return Optional.of(new AdhesionContractService.PublishedContract(
                        "ADHESION", "v7", "hash", Instant.now()));
            }
            @Override public Optional<AdhesionContractService.PublishedContract> currentServiceRegulation(UUID t) {
                return Optional.of(new AdhesionContractService.PublishedContract(
                        "REGULATION", "v3", "hash", Instant.now()));
            }
            @Override public void recordDelivery(UUID t, AdhesionContractService.DeliveryRecord r) {
                deliveries.add(r);
            }
            @Override public Optional<AdhesionContractService.DeliveryRecord> deliveryOf(
                    UUID t, UUID u, String v) {
                return deliveries.stream().filter(d -> d.contractVersion().equals(v)).findFirst();
            }
            @Override public void acknowledgeDelivery(UUID t, UUID u, String v, Instant at) { }
        });
        var holderChanges = new HolderChangeService(new HolderChangeService.RevocationPort() {
            @Override public int revokeAllLinksOfUnit(UUID t, UUID u, String reason, Instant at) { return 2; }
            @Override public void invalidateLinkingCode(UUID t, UUID u, Instant at) { }
        });
        return new ConsumerPortalController(links, history, documents, accessible,
                dataChanges, contracts, holderChanges);
    }

    @Test
    void convidadoERecusadoEmTodasAsFrentesNovas() {
        var c = controller();
        var guest = ConsumerAccessGuard.Channel.GUEST;
        assertThatThrownBy(() -> c.settlement(TENANT, "dono", guest, BILL))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> c.accessibleBill(TENANT, "dono", guest, BILL, true))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> c.requestDataChange(TENANT, "dono", guest, UNIT, "EMAIL", "{}"))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> c.deliverContract(TENANT, guest, UNIT,
                AdhesionContractService.DeliveryTrigger.NEW_CONNECTION))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> c.acknowledgeContract(TENANT, guest, UNIT, "v7"))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> c.holderChange(TENANT, guest, UNIT))
                .isInstanceOf(ApiException.Forbidden.class);
        // O guard roda ANTES do servico: nenhum efeito colateral aconteceu.
        assertThat(protocolCalls.get()).isZero();
        assertThat(deliveries).isEmpty();
    }

    @Test
    void frentesNovasFuncionamComContaPropria() {
        var c = controller();
        var own = ConsumerAccessGuard.Channel.OWN_ACCOUNT;
        assertThat(c.settlement(TENANT, "dono", own, BILL)).isEqualTo("QUITADA");
        assertThat(c.accessibleBill(TENANT, "dono", own, BILL, true)).contains("nov/2026");
        assertThat(c.requestDataChange(TENANT, "dono", own, UNIT, "EMAIL", "{}"))
                .isEqualTo("PROT-2026-1");
        assertThat(c.deliverContract(TENANT, own, UNIT,
                AdhesionContractService.DeliveryTrigger.NEW_CONNECTION).contractVersion())
                .isEqualTo("v7");
        assertThat(deliveries).hasSize(2);
        assertThat(c.holderChange(TENANT, own, UNIT)).isEqualTo(2);
    }
}
