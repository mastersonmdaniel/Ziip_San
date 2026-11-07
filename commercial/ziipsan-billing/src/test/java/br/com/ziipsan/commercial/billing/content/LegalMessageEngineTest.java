package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine.EmissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Motor de mensagens legais (P2.32): ordem travada, disparos, antecedencia
 * de 3 meses da tarifa social e prova de emissao com versao da conta.
 * Datas calculadas a mao nos comentarios de cada caso.
 */
class LegalMessageEngineTest {

    private static EmissionContext ctx(LocalDate issue, LocalDate lossEffective, LocalDate firstNotice,
                                       boolean annual, boolean water, LocalDate benefitUntil,
                                       Integer noticeDays, Set<String> slots) {
        return new EmissionContext(issue, lossEffective, firstNotice, 3, annual, water,
                benefitUntil, noticeDays, slots);
    }

    @Test
    @DisplayName("antecedencia de 3 meses: aviso em 01/04/2026 para perda em 01/07/2026 cumpre; em 02/04/2026, nao")
    void socialLossLeadTimeOfThreeMonths() {
        // 01/04/2026 + 3 meses = 01/07/2026, que nao e depois da perda: cumprida.
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1), 3)).isTrue();
        // 02/04/2026 + 3 meses = 02/07/2026, depois da perda de 01/07: nao cumprida.
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 7, 1), 3)).isFalse();
        // Parametro abaixo do piso nao reduz o piso (classe a): 2 meses nao bastam.
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 1), 2)).isFalse();
    }

    @Test
    @DisplayName("aviso de perda dispara na janela entre o primeiro aviso e a perda, e nao fora dela")
    void socialLossNoticeFiresOnlyInsideWindow() {
        LocalDate loss = LocalDate.of(2026, 7, 1);
        LocalDate first = LocalDate.of(2026, 4, 1);
        assertThat(LegalMessageEngine.select(ctx(LocalDate.of(2026, 5, 10), loss, first,
                false, false, null, null, Set.of())))
                .extracting(s -> s.definition().messageKey())
                .contains(LegalMessageCatalog.SOCIAL_TARIFF_LOSS_NOTICE);
        assertThat(LegalMessageEngine.select(ctx(LocalDate.of(2026, 3, 10), loss, first,
                false, false, null, null, Set.of())))
                .extracting(s -> s.definition().messageKey())
                .doesNotContain(LegalMessageCatalog.SOCIAL_TARIFF_LOSS_NOTICE);
    }

    @Test
    @DisplayName("antecedencia pelo contexto: o parametro do contexto e consumido e o piso de 3 meses vale nele")
    void socialLossLeadTimeFromContext() {
        // Mesmo calculo a mao do caso direto, agora via EmissionContext.
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(ctx(LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 4, 1),
                false, false, null, null, Set.of()))).isTrue();
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(ctx(LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 4, 2),
                false, false, null, null, Set.of()))).isFalse();
        // Sem perda programada, nao ha antecedencia pendente.
        assertThat(LegalMessageEngine.lossNoticeLeadTimeMet(ctx(LocalDate.of(2026, 5, 10),
                null, null, false, false, null, null, Set.of()))).isTrue();
    }

    @Test
    @DisplayName("ordem travada de verdade: nao legal com ordem MENOR que a legal continua depois dela")
    void legalFirstEvenWhenNonLegalHasSmallerOrder() {
        // Definicoes sinteticas: a trava e do comparador por grupo, nao da
        // numeracao do catalogo (onde os slots ja nascem com ordem maior).
        LegalMessageDef legalLate = new LegalMessageDef("legal.synthetic", "NORMA-SINTETICA",
                LegalMessageDef.ParameterClass.A, LegalMessageDef.Trigger.ANNUAL_DISCHARGE_DUE,
                99, true, false, null, null, null);
        LegalMessageDef nonLegalEarly = new LegalMessageDef("other.synthetic", "NORMA-SINTETICA",
                LegalMessageDef.ParameterClass.B, LegalMessageDef.Trigger.SLOT_ACTIVATION,
                1, false, false, null, null, null);
        assertThat(LegalMessageEngine.orderForPrint(List.of(nonLegalEarly, legalLate)))
                .containsExactly(legalLate, nonLegalEarly);
        assertThat(LegalMessageEngine.orderForPrint(List.of(legalLate, nonLegalEarly)))
                .containsExactly(legalLate, nonLegalEarly);
    }

    @Test
    @DisplayName("ordem no catalogo real: legais antes das demais com todos os disparos ativos")
    void legalMessagesAlwaysPrintBeforeOthers() {
        // Contexto com todas as legais ativas + os tres slots ativados.
        EmissionContext all = ctx(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 4, 1), true, true, LocalDate.of(2026, 5, 20), 30,
                Set.of(LegalMessageCatalog.SLOT_MANDATORY_CONNECTION_90D,
                        LegalMessageCatalog.SLOT_COBILLING_60D,
                        LegalMessageCatalog.SLOT_ABNORMAL_CONSUMPTION_ALERT));
        List<LegalMessageEngine.SelectedMessage> selected = LegalMessageEngine.select(all);
        assertThat(selected).hasSize(7);
        // As 4 primeiras posicoes sao legais, na ordem do catalogo (10, 20, 30, 40).
        assertThat(selected.subList(0, 4))
                .extracting(s -> s.definition().messageKey())
                .containsExactly(LegalMessageCatalog.SOCIAL_TARIFF_LOSS_NOTICE,
                        LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION,
                        LegalMessageCatalog.WATER_QUALITY_BLOCK,
                        LegalMessageCatalog.BENEFIT_EXPIRY_NOTICE);
        assertThat(selected.subList(0, 4)).allMatch(s -> s.definition().legal());
        assertThat(selected.subList(4, 7)).allMatch(s -> !s.definition().legal());
        // Posicoes de impressao sao 1..7, sem buraco.
        assertThat(selected).extracting(LegalMessageEngine.SelectedMessage::printPosition)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("slot nao ativado nao emite: os espacos de ED-88, ED-25 e P3.5 ficam inertes")
    void inactiveSlotsDoNotEmit() {
        EmissionContext none = ctx(LocalDate.of(2026, 5, 10), null, null,
                false, false, null, null, Set.of());
        assertThat(LegalMessageEngine.select(none)).isEmpty();
    }

    @Test
    @DisplayName("vencimento de beneficio (CAD-88): janela = vencimento - benefit.expiry_notice_days")
    void benefitExpiryWindowUsesP25Rule() {
        // Vencimento 20/05/2026, antecedencia de 30 dias: janela abre em 20/04/2026.
        assertThat(LegalMessageEngine.select(ctx(LocalDate.of(2026, 4, 19), null, null,
                false, false, LocalDate.of(2026, 5, 20), 30, Set.of())))
                .extracting(s -> s.definition().messageKey())
                .doesNotContain(LegalMessageCatalog.BENEFIT_EXPIRY_NOTICE);
        assertThat(LegalMessageEngine.select(ctx(LocalDate.of(2026, 4, 20), null, null,
                false, false, LocalDate.of(2026, 5, 20), 30, Set.of())))
                .extracting(s -> s.definition().messageKey())
                .contains(LegalMessageCatalog.BENEFIT_EXPIRY_NOTICE);
    }

    @Test
    @DisplayName("prova de emissao registra qual mensagem saiu em qual versao da conta e do catalogo")
    void emissionProofRecordsMessageAndBillVersion() {
        UUID billId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID templateVersionId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        EmissionContext c = ctx(LocalDate.of(2026, 5, 10), null, null,
                true, true, null, null, Set.of());
        LegalMessageEngine.EmissionProof proof = LegalMessageEngine.proof(billId, 3, templateVersionId, c);
        assertThat(proof.billId()).isEqualTo(billId);
        assertThat(proof.billVersionNo()).isEqualTo(3);
        assertThat(proof.billTemplateVersionId()).isEqualTo(templateVersionId);
        assertThat(proof.messageCatalogVersion()).isEqualTo(LegalMessageCatalog.CATALOG_VERSION);
        assertThat(proof.contentCatalogVersion()).isEqualTo(RequiredBillContent.CATALOG_VERSION);
        assertThat(proof.messages())
                .extracting(LegalMessageEngine.EmissionProof.EmittedMessage::messageKey)
                .containsExactly(LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION,
                        LegalMessageCatalog.WATER_QUALITY_BLOCK);
        assertThat(proof.messages())
                .extracting(LegalMessageEngine.EmissionProof.EmittedMessage::normKey)
                .containsExactly("Lei 12.007/2009", "Decreto 5.440/2005");
    }

    @Test
    @DisplayName("nome do titular sai pela regra bill.holder_name_display da P2.5")
    void holderNameUsesP25Rule() {
        BillContentService service = new DefaultBillContentService();
        assertThat(service.printedHolderName(null, "Maria da Silva", "Joao Souza"))
                .isEqualTo("Maria da Silva");
        assertThat(service.printedHolderName("\"USER_HOLDER_AND_OWNER\"", "Maria da Silva", "Joao Souza"))
                .isEqualTo("Maria da Silva / Joao Souza");
    }
}
