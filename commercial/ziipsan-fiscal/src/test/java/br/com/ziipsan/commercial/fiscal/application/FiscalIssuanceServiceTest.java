package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueRequest;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueResult;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.DocumentRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.EventRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.IssuerRecord;
import br.com.ziipsan.commercial.fiscal.port.RestrictedProductionUnavailableException;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import br.com.ziipsan.commercial.fiscal.support.InMemoryFiscalRepository;
import br.com.ziipsan.commercial.fiscal.support.InMemoryXmlStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serviço de emissão/cancelamento/substituição (P1.8) sobre o repositório em memória.
 * O fluxo contra o banco de verdade está no IT (FiscalV45IT, em ziipsan-app); aqui o
 * foco é a ordem das regras, que é a mesma nos dois.
 */
class FiscalIssuanceServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ISSUER = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final String CNPJ = "12ABC34501DE35";
    private static final LocalDate COMPETENCE = LocalDate.of(2026, 11, 3);

    private InMemoryFiscalRepository repository;
    private InMemoryXmlStore xmlStore;
    private FiscalIssuanceService service;
    private SefinRestrictedClient restricted;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFiscalRepository();
        repository.addIssuer(new IssuerRecord(ISSUER, "4205407", "CNPJ", "hash-sintetico"));
        repository.ensureSeries(TENANT, ISSUER, "NFSE_NACIONAL", "00001");
        addRate("ISS", "0.02");
        addRate("IRRF", "0.015");
        addRate("CSRF", "0.0465");
        addRate("CBS", "0.009");
        addRate("IBS", "0.001");
        xmlStore = new InMemoryXmlStore();
        restricted = new SefinRestrictedClient();
        FiscalIssuerRouter router = new FiscalIssuerRouter(
                FiscalIssuanceGate.closed(),
                new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL),
                restricted);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new FiscalIssuanceService(repository, router, xmlStore, clock);
    }

    private void addRate(String code, String rate) {
        repository.addTaxRate(new TaxRateRow(code, "NATIONAL",
                LocalDate.of(2026, 1, 1), null, new BigDecimal(rate)));
    }

    private IssueRequest request(UUID sourceId) {
        return new IssueRequest(TENANT, ISSUER, CNPJ, DpsId.TYPE_CNPJ, FiscalDocumentType.NFSE_NACIONAL,
                "00001", "BILL", sourceId, new BigDecimal("5440.00"), COMPETENCE,
                Environment.SIMULATED, false, "Tomador Sintetico de Teste", true);
    }

    @Test
    void issueAuthorizesWithSequentialNumbersAndStoresSimulatorXml() {
        IssueResult first = service.issue(request(UUID.randomUUID()));
        IssueResult second = service.issue(request(UUID.randomUUID()));

        assertEquals(1L, first.number());
        assertEquals(2L, second.number());
        assertEquals("AUTHORIZED", first.status());
        assertTrue(first.protocol().startsWith("SIM-"));
        assertEquals(42, first.dpsId().length());
        assertTrue(first.dpsId().startsWith("42054072" + CNPJ + "00001"));
        assertTrue(xmlStore.stored(first.documentId()).isPresent());
        assertEquals("SIMULATOR", xmlStore.stored(first.documentId()).orElseThrow().source());
        DocumentRecord stored = repository.findDocument(TENANT, first.documentId()).orElseThrow();
        assertEquals(50, stored.accessKey().length());
        assertEquals(5, repository.taxesOf(first.documentId()).size());
    }

    @Test
    void duplicateSourceDoesNotIssueTwice() {
        UUID source = UUID.randomUUID();
        service.issue(request(source));
        assertThrows(IllegalStateException.class, () -> service.issue(request(source)));
        assertEquals(1, repository.documentCount());
    }

    @Test
    void missingRateRefusesWithoutReservingNumber() {
        InMemoryFiscalRepository emptyRates = new InMemoryFiscalRepository();
        emptyRates.addIssuer(new IssuerRecord(ISSUER, "4205407", "CNPJ", "hash-sintetico"));
        emptyRates.ensureSeries(TENANT, ISSUER, "NFSE_NACIONAL", "00001");
        FiscalIssuanceService noRateService = new FiscalIssuanceService(emptyRates,
                new FiscalIssuerRouter(FiscalIssuanceGate.closed(),
                        new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL), restricted),
                xmlStore, Clock.systemUTC());
        assertThrows(IllegalStateException.class, () -> noRateService.issue(request(UUID.randomUUID())));
        assertEquals(0, emptyRates.documentCount());
    }

    @Test
    void homologationFailsClosedWithoutConsumingNumber() {
        IssueRequest homologation = new IssueRequest(TENANT, ISSUER, CNPJ, DpsId.TYPE_CNPJ,
                FiscalDocumentType.NFSE_NACIONAL, "00001", "BILL", UUID.randomUUID(),
                new BigDecimal("100.00"), COMPETENCE, Environment.HOMOLOGATION, false, null, true);
        assertThrows(RestrictedProductionUnavailableException.class, () -> service.issue(homologation));
        assertEquals(0, repository.documentCount());
        assertEquals(0, restricted.attempts());
    }

    @Test
    void cancelIsIdempotentAndSecondCancelReturnsSameEvent() {
        IssueResult issued = service.issue(request(UUID.randomUUID()));
        EventRecord first = service.cancel(TENANT, issued.documentId(), "Erro no servico", REQUESTER);
        EventRecord second = service.cancel(TENANT, issued.documentId(), "Erro no servico", REQUESTER);

        assertEquals("ACCEPTED", first.status());
        assertEquals(first.eventId(), second.eventId());
        assertEquals(59, first.eventId().length());
        assertTrue(first.eventId().startsWith("PRE"));
        assertTrue(first.eventId().endsWith("101101"));
        assertEquals("CANCELLED", repository.findDocument(TENANT, issued.documentId()).orElseThrow().status());
    }

    @Test
    void cancelBatchRequiresSecondApprover() {
        IssueResult first = service.issue(request(UUID.randomUUID()));
        IssueResult second = service.issue(request(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> service.cancelBatch(TENANT, List.of(first.documentId(), second.documentId()),
                        "Lote", REQUESTER, null));
        List<EventRecord> events = service.cancelBatch(TENANT,
                List.of(first.documentId(), second.documentId()), "Lote", REQUESTER, UUID.randomUUID());
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e.approvedBySecond() != null));
    }

    @Test
    void substituteCancelsOriginalAndLinksReplacement() {
        IssueResult original = service.issue(request(UUID.randomUUID()));
        var replacement = service.substitute(TENANT, original.documentId(), request(UUID.randomUUID()),
                "Servico errado", REQUESTER);

        assertNotEquals(original.documentId(), replacement.replacementId());
        DocumentRecord originalRow = repository.findDocument(TENANT, original.documentId()).orElseThrow();
        assertEquals("REPLACED", originalRow.status());
        assertEquals(replacement.replacementId(), originalRow.replacedById());
        assertEquals("AUTHORIZED",
                repository.findDocument(TENANT, replacement.replacementId()).orElseThrow().status());
    }

    @Test
    void reconcileQueriesBeforeRetransmitting() {
        // Documento forcado a PENDING (queda apos o envio): a consulta volta vazia no
        // simulador (nunca autorizado) e a retransmissao autoriza sem duplicar.
        InMemoryFiscalRepository repo = repository;
        var slot = repo.reserveSlot(TENANT, ISSUER, "NFSE_NACIONAL", "00001", "BILL",
                UUID.randomUUID(), "SIMULATED", null, new BigDecimal("100.00"));
        repo.signDocument(TENANT, slot.documentId(), "42054072" + CNPJ + "00001000000000000099",
                "<DPS/>", List.of());
        repo.markPendingReconciliation(TENANT, slot.documentId());

        assertTrue(service.reconcile(TENANT, slot.documentId()).isEmpty());
        IssueResult retransmitted = service.retransmit(TENANT, slot.documentId());
        assertEquals("AUTHORIZED", retransmitted.status());
        // Segunda retransmissao e recusada: o documento ja saiu de PENDING.
        assertThrows(IllegalStateException.class, () -> service.retransmit(TENANT, slot.documentId()));
    }

    @Test
    void nfagIsRefusedUntilItsLayoutArrives() {
        IssueRequest nfag = new IssueRequest(TENANT, ISSUER, CNPJ, DpsId.TYPE_CNPJ,
                FiscalDocumentType.NFAG, "00001", "BILL", UUID.randomUUID(),
                new BigDecimal("100.00"), COMPETENCE, Environment.SIMULATED, false, null, true);
        assertThrows(IllegalStateException.class, () -> service.issue(nfag));
    }
}
