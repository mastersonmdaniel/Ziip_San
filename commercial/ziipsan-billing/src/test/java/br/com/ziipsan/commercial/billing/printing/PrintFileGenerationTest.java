package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.DefaultBillContentService;
import br.com.ziipsan.commercial.billing.content.LegalMessageCatalog;
import br.com.ziipsan.commercial.billing.content.RequiredBillContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Arquivo para grafica (P2.8): o arquivo carrega o conteudo minimo e as
 * mensagens legais da P2.32 (chaves e versoes, sem texto proprio), recusa
 * modelo incompleto, codigo ausente, braile sem parametro e valor fora do
 * leiaute.
 */
class PrintFileGenerationTest {

    private final PrintFileService service = new DefaultPrintFileService(new DefaultBillContentService());

    private static final UUID BILL_1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID BILL_2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT_2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    @Test
    @DisplayName("arquivo carrega conteudo minimo e mensagens da P2.32 como chaves, sem texto legal proprio")
    void fileCarriesP232ContentWithoutOwnText() {
        PrintFile file = service.generate(PrintingFixtures.request(true,
                PrintingFixtures.bill(BILL_1, UNIT_1, "000001-1", AccessiblePrintFormat.NONE, "VINC-EXEMPLO-1"),
                PrintingFixtures.bill(BILL_2, UNIT_2, "000002-2", AccessiblePrintFormat.NONE, "VINC-EXEMPLO-2")));
        String[] lines = file.content().split("\n");
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).startsWith("HDR|")
                .contains("LAYOUT=" + PrintLayout.LAYOUT_VERSION)
                .contains("PERFIL=" + PrintingFixtures.PROFILE)
                .contains("CONTEUDO_V=" + RequiredBillContent.CATALOG_VERSION)
                .contains("MENSAGENS_V=" + LegalMessageCatalog.CATALOG_VERSION);
        for (int i = 1; i <= 2; i++) {
            assertThat(lines[i]).startsWith("CTA|SEQ=" + i)
                    .contains("CAMPOS=")
                    .contains(RequiredBillContent.REGULATORY_MODEL_IDENTIFICATION)
                    .contains(RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK)
                    .contains(RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK)
                    .contains(RequiredBillContent.ANNUAL_DISCHARGE_DECLARATION)
                    .contains(RequiredBillContent.HOLDER_PRINTED_NAME)
                    .contains(RequiredBillContent.CHARGES_DISCLOSURE)
                    .contains(RequiredBillContent.RECONNECTION_SLA_DISCLOSURE)
                    .contains(RequiredBillContent.CUT_NOTICE_DISCLOSURE)
                    .contains("MENSAGENS=" + LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION + "@1,"
                            + LegalMessageCatalog.WATER_QUALITY_BLOCK + "@2")
                    .contains("TOTAL=123.45")
                    .contains("COMPETENCIA=2026-05")
                    .contains("VENCIMENTO=2026-05-20");
        }
        assertThat(lines[3]).startsWith("TRL|REGISTROS=2|SHA256=")
                .matches("TRL\\|REGISTROS=2\\|SHA256=[0-9a-f]{64}");
        // Sem texto proprio: nenhuma norma nem frase legal entra no arquivo —
        // so as chaves dos catalogos da P2.32.
        assertThat(file.content())
                .doesNotContain("Lei 12.007")
                .doesNotContain("Decreto 5.440")
                .doesNotContain("quitacao de debitos")
                .doesNotContain("qualidade da agua deve");
        // A prova de emissao volta para o chamador persistir (P2.32).
        assertThat(file.proofs()).hasSize(2);
        assertThat(file.proofs().get(0).messages())
                .extracting(m -> m.messageKey())
                .containsExactly(LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION,
                        LegalMessageCatalog.WATER_QUALITY_BLOCK);
        assertThat(file.manifest().recordCount()).isEqualTo(2);
        assertThat(file.manifest().fileSha256()).hasSize(64);
    }

    @Test
    @DisplayName("modelo sem um campo obrigatorio do perfil recusa o lote inteiro, nomeando o campo")
    void incompleteTemplateRejectsWholeBatch() {
        Set<String> missing = new java.util.HashSet<>(PrintingFixtures.completeTemplateFields());
        missing.remove(RequiredBillContent.CUT_NOTICE_DISCLOSURE);
        PrintBillSource incomplete = new PrintBillSource(BILL_1, 1, PrintingFixtures.TEMPLATE_VERSION_ID,
                missing, PrintingFixtures.lockedFields(), UNIT_1, "000001-1", "NOME EXEMPLO",
                "Endereco exemplo", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20),
                new BigDecimal("10.00"), PrintingFixtures.contextWithAnnualAndWater(),
                AccessiblePrintFormat.NONE, new PortalLinkingCode("VINC-EXEMPLO-1"),
                new VerificationCode("CODIGO-EXEMPLO-1"));
        assertThatThrownBy(() -> service.generate(PrintingFixtures.request(true, incomplete)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RequiredBillContent.CUT_NOTICE_DISCLOSURE);
    }

    @Test
    @DisplayName("falha na segunda conta do lote recusa o lote inteiro (sem arquivo parcial da primeira)")
    void failureOnSecondBillRejectsWholeBatch() {
        // [Teste acrescentado na revisao cruzada] Os demais testes de recusa
        // usam lote de uma conta; aqui a primeira conta e valida e mesmo
        // assim nenhum arquivo sai: a falha e do lote, nao da conta.
        PrintBillSource valid = PrintingFixtures.bill(BILL_1, UNIT_1, "000001-1",
                AccessiblePrintFormat.NONE, "VINC-EXEMPLO-1");
        PrintBillSource brailleSecond = PrintingFixtures.bill(BILL_2, UNIT_2, "000002-2",
                AccessiblePrintFormat.BRAILLE, "VINC-EXEMPLO-2");
        assertThatThrownBy(() -> service.generate(PrintingFixtures.request(false, valid, brailleSecond)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.braille_enabled")
                .hasMessageContaining(BILL_2.toString());
    }

    @Test
    @DisplayName("conta sem codigo de vinculacao nao monta origem nem arquivo (ADR-0022)")
    void missingLinkingCodeIsExplicitError() {
        assertThatThrownBy(() -> new PrintBillSource(BILL_1, 1, PrintingFixtures.TEMPLATE_VERSION_ID,
                PrintingFixtures.completeTemplateFields(), PrintingFixtures.lockedFields(), UNIT_1,
                "000001-1", "NOME EXEMPLO", "Endereco exemplo", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 20), new BigDecimal("10.00"),
                PrintingFixtures.contextWithAnnualAndWater(), AccessiblePrintFormat.NONE, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkingCode");
        assertThatThrownBy(() -> new PortalLinkingCode(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("braile marcado com bill.braille_enabled desligado recusa o lote (sem rebaixar)")
    void brailleWithoutTenantParameterRejects() {
        PrintBillSource braille = PrintingFixtures.bill(BILL_1, UNIT_1, "000001-1",
                AccessiblePrintFormat.BRAILLE, "VINC-EXEMPLO-1");
        assertThatThrownBy(() -> service.generate(PrintingFixtures.request(false, braille)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.braille_enabled");
    }

    @Test
    @DisplayName("valor com separador do leiaute e total fora de centavos recusam a geracao")
    void layoutUnsafeValuesReject() {
        PrintBillSource badName = new PrintBillSource(BILL_1, 1, PrintingFixtures.TEMPLATE_VERSION_ID,
                PrintingFixtures.completeTemplateFields(), PrintingFixtures.lockedFields(), UNIT_1,
                "000001-1", "NOME | EXEMPLO", "Endereco exemplo", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 20), new BigDecimal("10.00"),
                PrintingFixtures.contextWithAnnualAndWater(), AccessiblePrintFormat.NONE,
                new PortalLinkingCode("VINC-EXEMPLO-1"), new VerificationCode("CODIGO-EXEMPLO-1"));
        assertThatThrownBy(() -> service.generate(PrintingFixtures.request(true, badName)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("printedHolderName");
        PrintBillSource badTotal = new PrintBillSource(BILL_1, 1, PrintingFixtures.TEMPLATE_VERSION_ID,
                PrintingFixtures.completeTemplateFields(), PrintingFixtures.lockedFields(), UNIT_1,
                "000001-1", "NOME EXEMPLO", "Endereco exemplo", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 20), new BigDecimal("10.001"),
                PrintingFixtures.contextWithAnnualAndWater(), AccessiblePrintFormat.NONE,
                new PortalLinkingCode("VINC-EXEMPLO-1"), new VerificationCode("CODIGO-EXEMPLO-1"));
        assertThatThrownBy(() -> service.generate(PrintingFixtures.request(true, badTotal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centavos");
    }

    @Test
    @DisplayName("profileKey com separador do leiaute e recusada no pedido (cabecalho protegido)")
    void profileKeyWithDelimiterRejects() {
        // [Teste acrescentado na revisao cruzada] A profileKey vai literal ao
        // campo PERFIL= do cabecalho; era o unico texto livre sem a trava de
        // separador que os demais campos tem.
        assertThatThrownBy(() -> new PrintFileRequest(PrintingFixtures.BATCH_ID,
                PrintingFixtures.GENERATED_AT, "ARESC|X=1", true,
                java.util.List.of(PrintingFixtures.bill(BILL_1, UNIT_1, "000001-1",
                        AccessiblePrintFormat.NONE, "VINC-EXEMPLO-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileKey");
    }

    @Test
    @DisplayName("lote vazio nao gera arquivo")
    void emptyBatchRejects() {
        assertThatThrownBy(() -> new PrintFileRequest(PrintingFixtures.BATCH_ID,
                PrintingFixtures.GENERATED_AT, PrintingFixtures.PROFILE, true, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
