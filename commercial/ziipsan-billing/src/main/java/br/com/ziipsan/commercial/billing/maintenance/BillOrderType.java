package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.List;

/**
 * Tipos de OS interna de conta do nucleo da P1.31 nomeados no aceite da
 * P2.20 (tabela 2.9 do 20b, citada pelo plano v3.0). O {@code code} e o
 * codigo do tipo no catalogo {@code service_types} do tenant.
 *
 * <p><b>Declaracao de fabrica (correcao G3 da revisao cruzada):</b> o motor
 * da P1.31 decide a aprovacao SOMENTE pelo {@code requires_approval}
 * declarado no tipo ({@code submit} desvia por ele; ele nunca le
 * {@code service_type.internal_approval}). Por isso esta enum carrega a
 * declaracao completa do tipo -- {@link #declaration(boolean)} -- e o
 * adaptador {@link CoreServiceOrderGateway} a usa quando o tipo ainda nao
 * existe no tenant (precedente do {@code InternalOrderService} do cadastro,
 * P1.3: tipo ausente e declarado na hora, de forma idempotente por codigo,
 * e editavel pelo tenant depois). O parametro
 * {@code service_type.internal_approval} entra na declaracao (quem declara
 * resolve a exigencia efetiva por {@link MaintenanceParameters}); se o tipo
 * ja existir com {@code requires_approval} diferente da exigencia efetiva,
 * o servico recusa com erro explicito em vez de chamar approve/execute fora
 * de estado -- o motor nao redeclara tipo existente.
 *
 * <p><b>Efeito:</b> tipo nao operacional exige {@code effectCode} no motor;
 * o codigo de efeito de cada tipo e o proprio codigo do tipo (mesmo
 * precedente do cadastro) e quem o aplica e o handler que o adaptador
 * registra no {@code EffectHandlerRegistry} do motor a cada {@code execute}
 * -- sem handler, o motor recusa a execucao ({@code no-effect-handler}).
 * O esquema da carga ({@link #effectSchemaJson()}) e o contrato minimo que
 * o motor valida no alvo e na execucao.
 *
 * <p>PENDENCIA DECLARADA: a tabela 2.9 do 20b nao esta nas fontes locais;
 * os "demais tipos" dela nao foram inventados aqui. Quando o 20b for
 * conferido, tipos novos entram neste enum e no mapa de
 * {@link BillOperation} sem mudar o servico.
 */
public enum BillOrderType {

    BILL_RECTIFICATION("Retificacao de conta"),
    BILL_CANCELLATION("Cancelamento de conta"),
    BILL_REVIEW("Revisao de conta"),
    BILL_DUE_DATE_CHANGE("Alteracao de vencimento de conta");

    /** Esquema da carga de efeito (formato do EffectPayloadValidator do motor). */
    private static final String EFFECT_SCHEMA_JSON = """
            {"required": ["operationRecordId", "operation", "billId"],
             "properties": {"operationRecordId": "string", "operation": "string", "billId": "string"}}""";

    private final String displayName;

    BillOrderType(String displayName) {
        this.displayName = displayName;
    }

    /** Codigo do tipo em {@code service_types.code}. */
    public String code() {
        return name();
    }

    /** Codigo de efeito do tipo (o proprio codigo; precedente do cadastro, P1.3). */
    public String effectCode() {
        return name();
    }

    /** Nome de catalogo do tipo na declaracao. */
    public String displayName() {
        return displayName;
    }

    /** Esquema JSON da carga de efeito que o motor valida (alvo e execucao). */
    public String effectSchemaJson() {
        return EFFECT_SCHEMA_JSON;
    }

    /**
     * A declaracao de fabrica do tipo para {@code ServiceOrderService.declareType}:
     * interna (backoffice), nao operacional, com efeito, alvo CONSUMER_UNIT
     * (a unidade da conta) e a exigencia de aprovacao ja resolvida por quem
     * declara (parametro do tenant sobre o padrao de fabrica -- para o que
     * muda faturamento, o padrao do ADR-0039 item 8 e aprovacao por outra
     * pessoa; ver {@link BillMaintenanceService}).
     */
    public ServiceOrderService.ServiceTypeInput declaration(boolean requiresApproval) {
        return new ServiceOrderService.ServiceTypeInput(
                code(), displayName(), code(), effectCode(), effectSchemaJson(),
                ServiceOrderEnums.TypeIndicator.INTERNAL, requiresApproval, false, null,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }
}
