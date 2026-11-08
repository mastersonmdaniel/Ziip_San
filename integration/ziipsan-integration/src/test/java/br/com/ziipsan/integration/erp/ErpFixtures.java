package br.com.ziipsan.integration.erp;

import br.com.ziipsan.commercial.accounting.event.AccountingEventKey;
import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator;
import br.com.ziipsan.integration.hub.IntegrationMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Massa SINTETICA declarada dos testes da ED-62 (nenhum dado de fornecedor real). */
final class ErpFixtures {
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    static ErpParameters params(String stockOfficial) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("erp.betha.layout_version", "declared-v1");
        values.put("erp.betha.stock_official", stockOfficial);
        values.put("erp.betha.transport", "API");
        return ErpParameters.fromValues(values);
    }

    /** Mapeamento 100% sintetico do teste: nomes de campo ficticios declarados. */
    static ErpLayoutMapping mapping() {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String c : new String[]{"data", "mes_contabil", "evento", "conta", "dc", "valor", "historico", "origem",
                "centro_custo", "material", "quantidade", "natureza_despesa", "referencia", "tipo_movimento",
                "contrato", "empenho", "ordem_compra_externa", "pncp"}) {
            fields.put(c, "F_" + c.toUpperCase(java.util.Locale.ROOT));
        }
        return ErpLayoutMapping.declared(ErpProvider.BETHA, "declared-v1", fields);
    }

    static ErpExportService service(FakeErpTransport fake, String stockOfficial) {
        ErpConnector connector = new ErpConnector(ErpProvider.BETHA, "declared-v1", fake);
        return new ErpExportService(connector, params(stockOfficial), mapping(), IntegrationMode.SANDBOX);
    }

    static JournalEntryGenerator.JournalEntry journalEntry() {
        return new JournalEntryGenerator.JournalEntry(AccountingEventKey.BILL_ISSUED, "1.1.01", "3.1.01",
                new BigDecimal("150.00"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                "emissao de conta", JournalEntryGenerator.Sphere.PUBLICO, JournalEntryGenerator.Scope.REGULATORY,
                null, null, "bill:42");
    }
}
