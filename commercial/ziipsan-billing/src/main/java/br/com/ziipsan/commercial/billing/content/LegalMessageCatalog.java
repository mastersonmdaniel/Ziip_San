package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.commercial.billing.content.LegalMessageDef.ParameterClass;
import br.com.ziipsan.commercial.billing.content.LegalMessageDef.Trigger;

import java.util.List;

/**
 * Catalogo versionado de mensagens legais da fatura (P2.32, motor antecipado
 * da ED-79), versao {@link #CATALOG_VERSION}. Fonte unica da carga repetivel
 * {@code R__legal_message_catalog.sql} (conferida por teste de consistencia).
 *
 * <p>Mensagens de fabrica (ativas):
 * <ul>
 *   <li>aviso de perda da tarifa social — Lei 14.898/2024, arts. 2o, SS2o, e
 *       3o; antecedencia minima de {@code social.loss_notice_months} meses
 *       (padrao de fabrica 3, o piso da lei; parametro de classe c em
 *       PARAMETROS-DO-TENANT — so amplia; regra da P1.5, situacao
 *       LOSS_NOTICE);</li>
 *   <li>declaracao anual de quitacao — Lei 12.007/2009 (consumida pela
 *       P2.24);</li>
 *   <li>bloco de qualidade da agua — Decreto 5.440/2005 (consumido pela
 *       P2.24);</li>
 *   <li>aviso de vencimento de beneficio — CAD-88 da P2.5, com o marco
 *       calculado por {@code BenefitExpiryRule} e a antecedencia de
 *       {@code benefit.expiry_notice_days} (classe d).</li>
 * </ul>
 *
 * <p>Slots (inativos, so o espaco): disponibilidade de 90 dias da ligacao
 * obrigatoria (ED-88), cofaturamento de 60 dias (ED-25) e alerta de consumo
 * anormal (P3.5). Essas tarefas ativam os slots e definem conteudo e
 * disparo proprios; aqui eles NAO emitem.
 */
public final class LegalMessageCatalog {

    public static final int CATALOG_VERSION = 1;

    public static final String SOCIAL_TARIFF_LOSS_NOTICE = "legal.social_tariff_loss_notice";
    public static final String ANNUAL_DISCHARGE_DECLARATION = "legal.annual_discharge_declaration";
    public static final String WATER_QUALITY_BLOCK = "legal.water_quality_block";
    public static final String BENEFIT_EXPIRY_NOTICE = "legal.benefit_expiry_notice";

    public static final String SLOT_MANDATORY_CONNECTION_90D = "slot.mandatory_connection_90d";
    public static final String SLOT_COBILLING_60D = "slot.cobilling_60d";
    public static final String SLOT_ABNORMAL_CONSUMPTION_ALERT = "slot.abnormal_consumption_alert";

    /** Parametro da antecedencia do aviso de perda (P1.5); piso legal de 3 meses. */
    public static final String SOCIAL_LOSS_NOTICE_MONTHS_KEY = "social.loss_notice_months";
    public static final int SOCIAL_LOSS_NOTICE_MONTHS_FLOOR = 3;

    private LegalMessageCatalog() {
    }

    public static List<LegalMessageDef> all() {
        return List.of(
                new LegalMessageDef(SOCIAL_TARIFF_LOSS_NOTICE, "Lei 14.898/2024",
                        ParameterClass.C, Trigger.SOCIAL_TARIFF_LOSS_SCHEDULED, 10, true, false,
                        null, null, null),
                new LegalMessageDef(ANNUAL_DISCHARGE_DECLARATION, "Lei 12.007/2009",
                        ParameterClass.A, Trigger.ANNUAL_DISCHARGE_DUE, 20, true, false,
                        null, null, null),
                new LegalMessageDef(WATER_QUALITY_BLOCK, "Decreto 5.440/2005",
                        ParameterClass.A, Trigger.WATER_QUALITY_AVAILABLE, 30, true, false,
                        null, null, null),
                new LegalMessageDef(BENEFIT_EXPIRY_NOTICE, "CAD-88 (P2.5)",
                        ParameterClass.D, Trigger.BENEFIT_EXPIRY_WINDOW, 40, true, false,
                        null, null, null),
                new LegalMessageDef(SLOT_MANDATORY_CONNECTION_90D, "ED-88 (ligacao obrigatoria)",
                        ParameterClass.B, Trigger.SLOT_ACTIVATION, 110, false, true,
                        "ED-88", null, null),
                new LegalMessageDef(SLOT_COBILLING_60D, "ED-25 (cofaturamento)",
                        ParameterClass.B, Trigger.SLOT_ACTIVATION, 120, false, true,
                        "ED-25", null, null),
                new LegalMessageDef(SLOT_ABNORMAL_CONSUMPTION_ALERT, "P3.5 (alerta de consumo anormal)",
                        ParameterClass.B, Trigger.SLOT_ACTIVATION, 130, false, true,
                        "P3.5", null, null));
    }

    /** Definicao por chave, ou erro explicito (chave fora do catalogo nao emite). */
    public static LegalMessageDef byKey(String messageKey) {
        for (LegalMessageDef def : all()) {
            if (def.messageKey().equals(messageKey)) {
                return def;
            }
        }
        throw new IllegalArgumentException("Mensagem fora do catalogo da P2.32: " + messageKey);
    }
}
