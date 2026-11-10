package br.com.ziipsan.operations.metering.hydrometry.renewal;

/**
 * Tipo de OS da troca selectiva da II-5 no núcleo de OS da P1.31 —
 * <b>PROPOSTA declarada [A DEFINIR]</b>: o aceite da II-5 pede a geração
 * das OS selectivas por porta do núcleo, mas nenhuma fonte nomeia o
 * código do tipo; a confirmação do código (e da exigência de aprovação)
 * é da integração/tenant, no precedente das propostas marcadas do
 * projeto (ex.: tipo {@code INSPECTION} da ED-24). Até lá, este enum é o
 * contrato do payload — nenhum tipo é declarado em tenant por este
 * núcleo.
 *
 * <p><b>Alvo {@code METER}:</b> ao contrário da P3.28 (billing, que só
 * podia declarar {@code CONSUMER_UNIT}), este núcleo vive no metering,
 * onde a ED-14 já entrega o verificador de alvo
 * {@code MeterTargetVerifier} para {@code TargetType.METER} do core —
 * o payload aponta o hidrómetro como alvo tipado e carrega a unidade
 * consumidora na carga de efeito. A fiação do verificador na instância
 * do motor continua pendência de integração já nomeada na ED-14.
 *
 * <p><b>Aprovação de fábrica {@code false} (proposta):</b> a troca
 * selectiva, em si, não altera faturamento (a leitura de retirada e a de
 * instalação seguem o ciclo POC-12..14 da ED-14); como na aferição da
 * P3.28, a fábrica nasce sem aprovação e a sobreposição do tenant
 * decide na integração.
 */
public enum RenewalOrderType {

    METER_REPLACEMENT_SELECTIVE("Troca selectiva de hidrómetro (plano anual)", false,
            """
            {"required": ["meterId", "planYear", "renewalScore", "clockStatus"],
             "properties": {"meterId": "string", "unitId": "string", "meterNumber": "string",
                            "planYear": "integer", "planPosition": "integer", "renewalScore": "string",
                            "dominantCriterion": "string", "clockStatus": "string",
                            "nextVerificationOn": "string"}}""");

    private final String displayName;
    private final boolean factoryRequiresApproval;
    private final String effectSchemaJson;

    RenewalOrderType(String displayName, boolean factoryRequiresApproval, String effectSchemaJson) {
        this.displayName = displayName;
        this.factoryRequiresApproval = factoryRequiresApproval;
        this.effectSchemaJson = effectSchemaJson;
    }

    /** Código do tipo em {@code service_types.code} — PROPOSTO [A DEFINIR], a confirmar na integração. */
    public String code() { return name(); }

    /** Código de efeito do tipo (o próprio código; precedente P2.20/P3.28). */
    public String effectCode() { return name(); }

    public String displayName() { return displayName; }

    /** Aprovação de fábrica PROPOSTA, antes da sobreposição do tenant [A DEFINIR]. */
    public boolean factoryRequiresApproval() { return factoryRequiresApproval; }

    /** Esquema JSON da carga de efeito que o motor valida (ver {@link RenewalOrderPayload}). */
    public String effectSchemaJson() { return effectSchemaJson; }

    /** Classe de alvo do núcleo de OS (nome de {@code TargetType} do core): o hidrómetro. */
    public static final String TARGET_CLASS = "METER";
}
