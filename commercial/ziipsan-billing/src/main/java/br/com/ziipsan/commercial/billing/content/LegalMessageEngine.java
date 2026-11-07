package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.commercial.billing.social.BenefitExpiryRule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Motor de mensagens legais da fatura (P2.32): decide, para uma conta, quais
 * mensagens do catalogo saem e em que ordem, e monta a PROVA DE EMISSAO.
 *
 * <p>ORDEM TRAVADA: toda mensagem legal sai antes de qualquer mensagem nao
 * legal (inclusive dos slots quando ativados), por menor que seja a ordem
 * declarada da nao legal — nao ha parametro nem entrada que inverta isso;
 * dentro de cada grupo vale a ordem do catalogo.
 *
 * <p>Antecedencia da tarifa social (Lei 14.898/2024, art. 3o): a perda so
 * pode ser efetivada depois de avisos por pelo menos
 * {@code social.loss_notice_months} meses (piso 3). O motor expoe a conta:
 * dado o primeiro aviso e a data prevista da perda, diz se a antecedencia
 * esta cumprida — calculo a mao: perda em 01/07/2026 com primeiro aviso em
 * 01/04/2026 = exatamente 3 meses (cumprida); primeiro aviso em 02/04/2026
 * = menos de 3 meses (nao cumprida).
 */
public final class LegalMessageEngine {

    private LegalMessageEngine() {
    }

    /**
     * Contexto de emissao de uma conta. Campos ausentes = a condicao nao se
     * aplica (a mensagem correspondente nao dispara); o chamador (P2.8/P2.24
     * e o ciclo da P2.5) monta o contexto a partir dos dados reais da conta —
     * dado necessario ausente onde a condicao se aplica e erro do chamador,
     * nunca um disparo por aproximacao.
     */
    public record EmissionContext(
            LocalDate issueDate,
            LocalDate socialLossEffectiveDate,
            LocalDate socialLossFirstNoticeDate,
            int socialLossNoticeMonths,
            boolean annualDischargeDue,
            boolean waterQualityAvailable,
            LocalDate benefitValidUntil,
            Integer benefitExpiryNoticeDays,
            Set<String> activatedSlotKeys) {

        public EmissionContext {
            if (issueDate == null) {
                throw new IllegalArgumentException("issueDate e obrigatoria");
            }
            activatedSlotKeys = activatedSlotKeys == null ? Set.of() : Set.copyOf(activatedSlotKeys);
        }
    }

    /** Uma mensagem selecionada para a conta, na ordem final de impressao. */
    public record SelectedMessage(LegalMessageDef definition, int printPosition) {
    }

    /**
     * A antecedencia do aviso de perda esta cumprida?
     * {@code primeiroAviso + meses <= dataDaPerda}, com o piso legal de 3
     * meses mesmo se o parametro vier menor (parametro de classe c em
     * PARAMETROS-DO-TENANT: piso fixo da Lei 14.898/2024, o tenant so amplia).
     * [Correcao da revisao cruzada: a versao original dizia "classe a".]
     */
    public static boolean lossNoticeLeadTimeMet(LocalDate firstNoticeDate, LocalDate lossEffectiveDate,
                                                int noticeMonths) {
        if (firstNoticeDate == null || lossEffectiveDate == null) {
            throw new IllegalArgumentException("primeiro aviso e data da perda sao obrigatorios");
        }
        int effectiveMonths = Math.max(noticeMonths, LegalMessageCatalog.SOCIAL_LOSS_NOTICE_MONTHS_FLOOR);
        return !firstNoticeDate.plusMonths(effectiveMonths).isAfter(lossEffectiveDate);
    }

    /**
     * A antecedencia a partir do contexto da conta: usa o parametro
     * {@code social.loss_notice_months} carregado no contexto. Sem perda
     * programada no contexto, nao ha antecedencia a cumprir (false nao se
     * aplica: devolve {@code true} por vacuidade — quem decide a perda usa a
     * sobrecarga com datas explicitas). [Revisao cruzada: o campo do contexto
     * existia sem nenhum consumidor; esta sobrecarga e o consumidor dele.]
     */
    public static boolean lossNoticeLeadTimeMet(EmissionContext ctx) {
        if (ctx.socialLossEffectiveDate() == null || ctx.socialLossFirstNoticeDate() == null) {
            return true;
        }
        return lossNoticeLeadTimeMet(ctx.socialLossFirstNoticeDate(), ctx.socialLossEffectiveDate(),
                ctx.socialLossNoticeMonths());
    }

    /** A mensagem dispara neste contexto? (vigencia + condicao de disparo) */
    public static boolean fires(LegalMessageDef def, EmissionContext ctx) {
        if (!def.covers(ctx.issueDate())) {
            return false;
        }
        return switch (def.trigger()) {
            case SOCIAL_TARIFF_LOSS_SCHEDULED -> ctx.socialLossEffectiveDate() != null
                    && ctx.socialLossFirstNoticeDate() != null
                    && !ctx.issueDate().isBefore(ctx.socialLossFirstNoticeDate())
                    && !ctx.issueDate().isAfter(ctx.socialLossEffectiveDate());
            case ANNUAL_DISCHARGE_DUE -> ctx.annualDischargeDue();
            case WATER_QUALITY_AVAILABLE -> ctx.waterQualityAvailable();
            case BENEFIT_EXPIRY_WINDOW -> ctx.benefitValidUntil() != null
                    && ctx.benefitExpiryNoticeDays() != null
                    && BenefitExpiryRule.noticeWindowOpen(ctx.issueDate(), ctx.benefitValidUntil(),
                            ctx.benefitExpiryNoticeDays());
            case SLOT_ACTIVATION -> def.slotOnly() && ctx.activatedSlotKeys().contains(def.messageKey());
        };
    }

    /**
     * A ORDEM TRAVADA, isolada para ser testavel com definicoes sinteticas:
     * legais primeiro (grupo), depois as demais; dentro do grupo, a ordem
     * declarada. Uma nao legal com ordem MENOR que a de uma legal continua
     * depois dela — e o comparador por grupo, nao a numeracao do catalogo,
     * que garante a trava. [Extracao da revisao cruzada: antes o comparador
     * vivia inline em {@link #select} e o teste de ordem usava so o catalogo
     * real, cujos slots ja nascem com ordem maior — nao provava a trava.]
     */
    public static List<LegalMessageDef> orderForPrint(List<LegalMessageDef> definitions) {
        List<LegalMessageDef> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing((LegalMessageDef d) -> !d.legal())
                .thenComparingInt(LegalMessageDef::order));
        return List.copyOf(sorted);
    }

    /**
     * Selecao ordenada para a conta: legais primeiro (travado), depois as
     * demais; dentro do grupo, a ordem do catalogo. Slots nao ativados nao
     * entram.
     */
    public static List<SelectedMessage> select(EmissionContext ctx) {
        List<LegalMessageDef> fired = new ArrayList<>();
        for (LegalMessageDef def : LegalMessageCatalog.all()) {
            if (fires(def, ctx)) {
                fired.add(def);
            }
        }
        List<LegalMessageDef> ordered = orderForPrint(fired);
        List<SelectedMessage> selected = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            selected.add(new SelectedMessage(ordered.get(i), i + 1));
        }
        return List.copyOf(selected);
    }

    /**
     * Prova de emissao: registra QUAL mensagem saiu em QUAL versao da conta
     * (e em qual versao do modelo e do catalogo). E o objeto que o servico
     * persiste (tabela da V4_10) e que a P2.24/P2.8 exibem na auditoria.
     */
    public record EmissionProof(
            java.util.UUID billId,
            int billVersionNo,
            java.util.UUID billTemplateVersionId,
            int contentCatalogVersion,
            int messageCatalogVersion,
            LocalDate issuedOn,
            List<EmittedMessage> messages) {

        public record EmittedMessage(String messageKey, String normKey, int printPosition) {
        }

        public EmissionProof {
            if (billId == null) {
                throw new IllegalArgumentException("billId e obrigatorio na prova de emissao");
            }
            messages = List.copyOf(messages);
        }
    }

    /** Monta a prova a partir da selecao (sem persistir: ver BillContentService). */
    public static EmissionProof proof(java.util.UUID billId, int billVersionNo,
                                      java.util.UUID billTemplateVersionId, EmissionContext ctx) {
        List<EmissionProof.EmittedMessage> emitted = new ArrayList<>();
        for (SelectedMessage s : select(ctx)) {
            emitted.add(new EmissionProof.EmittedMessage(s.definition().messageKey(),
                    s.definition().normKey(), s.printPosition()));
        }
        return new EmissionProof(billId, billVersionNo, billTemplateVersionId,
                RequiredBillContent.CATALOG_VERSION, LegalMessageCatalog.CATALOG_VERSION,
                ctx.issueDate(), emitted);
    }
}
