package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueRequest;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueResult;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.DocumentRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.IssuerRecord;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.TaxLine;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import br.com.ziipsan.commercial.fiscal.relief.ReliefTaxHighlight;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import br.com.ziipsan.commercial.fiscal.support.InMemoryFiscalRepository;
import br.com.ziipsan.commercial.fiscal.support.InMemoryXmlStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correccao R-04 (REVISAO-PA-02) ao nivel da EMISSAO VIVA: o
 * {@link FiscalIssuanceService} consome o destaque CBS/IBS de UMA so via
 * ({@link ReliefTaxHighlight#cbsIbsForCompetence}) — as linhas do documento
 * e o XML levam exactamente os montantes dessa via, uma unica vez, e o
 * total/liquido da conta nao absorve CBS/IBS (sem dupla contagem com o
 * flip de 2027 da P2.4).
 */
class ReliefHighlightIssuanceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ISSUER = UUID.randomUUID();
    private static final String CNPJ = "12ABC34501DE35";

    private InMemoryFiscalRepository repository;
    private FiscalIssuanceService service;

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
        FiscalIssuerRouter router = new FiscalIssuerRouter(
                FiscalIssuanceGate.closed(),
                new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL),
                new SefinRestrictedClient());
        Clock clock = Clock.fixed(Instant.parse("2027-01-15T13:00:00Z"),
                ZoneId.of("America/Sao_Paulo"));
        service = new FiscalIssuanceService(repository, router, new InMemoryXmlStore(), clock);
    }

    private void addRate(String code, String rate) {
        repository.addTaxRate(new TaxRateRow(code, "NATIONAL",
                LocalDate.of(2026, 1, 1), null, new BigDecimal(rate)));
    }

    private IssueRequest request(LocalDate competence) {
        return new IssueRequest(TENANT, ISSUER, CNPJ, DpsId.TYPE_CNPJ,
                FiscalDocumentType.NFSE_NACIONAL, "00001", "BILL", UUID.randomUUID(),
                new BigDecimal("5440.00"), competence, Environment.SIMULATED, false,
                "Tomador Sintetico de Teste", true);
    }

    @Test
    @DisplayName("emissao 2027: CBS/IBS sao as linhas da via unica (destaque, informative=false), uma vez cada; liquido sem CBS/IBS")
    void issuance2027UsesSingleHighlightSource() {
        IssueResult issued = service.issue(request(LocalDate.of(2027, 1, 15)));
        List<TaxLine> taxes = repository.taxesOf(issued.documentId());
        assertEquals(5, taxes.size());
        List<TaxLine> cbs = taxes.stream().filter(t -> t.taxCode().equals("CBS")).toList();
        List<TaxLine> ibs = taxes.stream().filter(t -> t.taxCode().equals("IBS")).toList();
        assertEquals(1, cbs.size(), "CBS exactamente uma vez (sem dupla contagem)");
        assertEquals(1, ibs.size(), "IBS exactamente uma vez (sem dupla contagem)");

        var expected = ReliefTaxHighlight.cbsIbsForCompetence(new BigDecimal("5440.00"),
                new BigDecimal("0.009"), new BigDecimal("0.001"), LocalDate.of(2027, 1, 15));
        assertEquals(0, cbs.get(0).amount().compareTo(expected.get(0).amount())); // 48,96
        assertEquals(0, ibs.get(0).amount().compareTo(expected.get(1).amount())); // 5,44
        assertFalse(cbs.get(0).informative());
        assertFalse(ibs.get(0).informative());

        DocumentRecord stored = repository.findDocument(TENANT, issued.documentId()).orElseThrow();
        assertEquals(0, stored.gross().compareTo(new BigDecimal("5440.00")));
        // O XML leva os mesmos montantes da via unica, uma unica vez, e o
        // liquido = bruto - IRRF - CSRF (5440,00 - 81,60 - 252,96): CBS/IBS
        // destacados por fora NAO entram no total — nada e somado duas vezes.
        String xml = stored.unsignedXml();
        assertTrue(xml.contains("<vLiquido>5105.44</vLiquido>"),
                "liquido sem CBS/IBS: " + xml);
        assertEquals(1, xml.split("<vCbs>48.96</vCbs>", -1).length - 1);
        assertEquals(1, xml.split("<vIbs>5.44</vIbs>", -1).length - 1);
    }

    @Test
    @DisplayName("emissao 2026 (ano de teste): a mesma via devolve informativo=true — comportamento da P2.4 preservado")
    void issuance2026StaysInformative() {
        IssueResult issued = service.issue(request(LocalDate.of(2026, 11, 3)));
        List<TaxLine> taxes = repository.taxesOf(issued.documentId());
        assertTrue(taxes.stream().filter(t -> t.taxCode().equals("CBS")).findFirst().orElseThrow()
                .informative());
        assertTrue(taxes.stream().filter(t -> t.taxCode().equals("IBS")).findFirst().orElseThrow()
                .informative());
    }
}
