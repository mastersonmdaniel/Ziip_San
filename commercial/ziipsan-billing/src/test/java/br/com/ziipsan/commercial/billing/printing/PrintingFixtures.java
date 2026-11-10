package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.content.RequiredBillContent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da P2.8 (nenhum dado real): perfil ARESC com o
 * modelo completo da P2.32, titular e endereco de exemplo, codigos de
 * vinculacao inventados para o teste de vazamento.
 */
final class PrintingFixtures {

    static final String PROFILE = "ARESC_RES046_2016";
    static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-00000000ba7c");
    static final OffsetDateTime GENERATED_AT = OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneOffset.of("-03:00"));
    static final UUID TEMPLATE_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private PrintingFixtures() {
    }

    /** Todas as chaves exigidas pelo perfil na P2.32 (federal + ARESC). */
    static Set<String> completeTemplateFields() {
        return Set.of(
                RequiredBillContent.REGULATORY_MODEL_IDENTIFICATION,
                RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK,
                RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK,
                RequiredBillContent.ANNUAL_DISCHARGE_DECLARATION,
                RequiredBillContent.HOLDER_PRINTED_NAME,
                RequiredBillContent.CHARGES_DISCLOSURE,
                RequiredBillContent.RECONNECTION_SLA_DISCLOSURE,
                RequiredBillContent.CUT_NOTICE_DISCLOSURE);
    }

    static Set<String> lockedFields() {
        return Set.of(RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK);
    }

    /** Contexto com declaracao anual e qualidade da agua disparando (2 legais). */
    static LegalMessageEngine.EmissionContext contextWithAnnualAndWater() {
        return new LegalMessageEngine.EmissionContext(
                LocalDate.of(2026, 5, 10), null, null, 3, true, true, null, null, Set.of());
    }

    static PrintBillSource bill(UUID billId, UUID unitId, String registration,
                                AccessiblePrintFormat format, String linkingCode) {
        return new PrintBillSource(billId, 1, TEMPLATE_VERSION_ID,
                completeTemplateFields(), lockedFields(), unitId, registration,
                "NOME EXEMPLO " + registration, "Endereco exemplo, " + registration,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), new BigDecimal("123.45"),
                contextWithAnnualAndWater(), format, new PortalLinkingCode(linkingCode),
                new VerificationCode("CODIGO-EXEMPLO-1"));
    }

    static PrintFileRequest request(boolean brailleEnabled, PrintBillSource... bills) {
        return new PrintFileRequest(BATCH_ID, GENERATED_AT, PROFILE, brailleEnabled,
                java.util.List.of(bills));
    }
}
