package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.List;

/**
 * Tipos de OS da P3.28 no nucleo de OS da P1.31: a revisao por vazamento
 * oculto e a afericao de hidrometro com laudo. O {@code code} e o codigo do
 * tipo no catalogo {@code service_types} do tenant.
 *
 * <p><b>Declaracao de fabrica (mesmo desenho corrigido na P2.20, G3):</b> o
 * motor da P1.31 decide a aprovacao SOMENTE pelo {@code requires_approval}
 * declarado no tipo; por isso esta enum carrega a declaracao completa --
 * {@link #declaration(boolean)} -- e o adaptador
 * {@link CoreLeakMeterOrderGateway} a usa quando o tipo ainda nao existe no
 * tenant. A exigencia efetiva e resolvida por {@link LeakMeterParameters}
 * (sobreposicao {@code service_type.internal_approval} sobre a fabrica
 * {@link #factoryRequiresApproval()}): divergencia entre tipo existente e
 * exigencia resolvida e erro explicito no servico, nunca fluxo quebrado.
 *
 * <p><b>Campo, agenda e fases:</b> os dois tipos sao de CAMPO
 * ({@link ServiceOrderEnums.TypeIndicator#FIELD}) porque ha vistoria
 * (vazamento) e ensaio em bancada/campo (afericao). O motor ja suporta o
 * ciclo {@code OPEN -> SCHEDULED -> IN_EXECUTION -> EXECUTED} e os portoes
 * de seguranca; as TELAS de agenda, o app de campo, a vistoria fotografica
 * e as fases do protocolo de revisao sao da ED-23 (ver ED-23 no plano) --
 * esta tarefa declara os tipos, expoe {@code schedule}/{@code
 * startExecution} na porta e NAO duplica a ED-23.
 *
 * <p><b>Alvos:</b> os dois tipos declaram apenas {@code CONSUMER_UNIT},
 * a unica classe com verificador registrado pelo proprio motor. O
 * hidrometro da afericao vai identificado na carga de efeito
 * ({@code meterId}) e na linha de {@code meter_tests}: a classe de alvo
 * {@code METER} so pode ser declarada quando o modulo metering registrar o
 * verificador dela na instancia do motor (desenho da P1.31: classe sem
 * verificador nao pode ser declarada) -- fronteira declarada, nao atalho.
 *
 * <p><b>Efeito:</b> o codigo de efeito de cada tipo e o proprio codigo do
 * tipo (precedente do cadastro/P2.20) e quem o aplica e o handler que o
 * adaptador registra no {@code EffectHandlerRegistry} a cada
 * {@code execute} -- sem handler, o motor recusa a execucao.
 */
public enum LeakMeterOrderType {

    /**
     * Revisao de conta por vazamento oculto: parte da OS {@code BILL_REVIEW}
     * da P2.20 (a OS desta enum nasce filha dela) e, deferida, produz os
     * volumes revistos que o chamador retifica pela P2.20. Muda faturamento:
     * fabrica exige aprovacao por outra pessoa (ADR-0039, item 8).
     */
    LEAK_REVIEW("Revisao por vazamento oculto", true,
            """
            {"required": ["billId", "billReviewId", "billReviewOrderId"],
             "properties": {"billId": "string", "billReviewId": "string", "billReviewOrderId": "string"}}"""),

    /**
     * Afericao de hidrometro com laudo: servico de campo tarifado pela
     * tabela de precos (ED-34, fora desta tarefa); a execucao em si nao
     * altera faturamento -- a revisao das contas de um hidrometro irregular
     * passa pela {@code BILL_REVIEW}/retificacao da P2.20, com a aprovacao
     * propria dela. Fabrica sem aprovacao.
     */
    METER_TEST("Afericao de hidrometro", false,
            """
            {"required": ["meterId", "unitId"],
             "properties": {"meterId": "string", "unitId": "string"}}""");

    private final String displayName;
    private final boolean factoryRequiresApproval;
    private final String effectSchemaJson;

    LeakMeterOrderType(String displayName, boolean factoryRequiresApproval, String effectSchemaJson) {
        this.displayName = displayName;
        this.factoryRequiresApproval = factoryRequiresApproval;
        this.effectSchemaJson = effectSchemaJson;
    }

    /** Codigo do tipo em {@code service_types.code}. */
    public String code() {
        return name();
    }

    /** Codigo de efeito do tipo (o proprio codigo; precedente do cadastro e da P2.20). */
    public String effectCode() {
        return name();
    }

    /** Nome de catalogo do tipo na declaracao. */
    public String displayName() {
        return displayName;
    }

    /** Aprovacao de fabrica do tipo, antes da sobreposicao do tenant (ADR-0039, item 8). */
    public boolean factoryRequiresApproval() {
        return factoryRequiresApproval;
    }

    /** Esquema JSON da carga de efeito que o motor valida (alvo e execucao). */
    public String effectSchemaJson() {
        return effectSchemaJson;
    }

    /**
     * A declaracao de fabrica do tipo para {@code ServiceOrderService.declareType}:
     * de campo, nao operacional, com efeito, alvo CONSUMER_UNIT e a exigencia
     * de aprovacao ja resolvida por quem declara.
     */
    public ServiceOrderService.ServiceTypeInput declaration(boolean requiresApproval) {
        return new ServiceOrderService.ServiceTypeInput(
                code(), displayName(), code(), effectCode(), effectSchemaJson(),
                ServiceOrderEnums.TypeIndicator.FIELD, requiresApproval, false, null,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }
}
