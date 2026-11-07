package br.com.ziipsan.operations.metering.rules;

/**
 * Uma ocorrência do <b>catálogo de ocorrências de leitura</b> (JOR-10, P2.5) com o efeito
 * <b>completo</b> dela — não só o efeito sobre a leitura faturada, que é o que a cascata já
 * consumia via {@link ReadingAnomalyRule}.
 *
 * <p>O catálogo continua sendo do tenant e <b>sem semente</b> (V5_1): o código da ocorrência é o
 * código do prestador, sem significado para a regra, e nenhum código é criado aqui. O que esta
 * classe acrescenta ao que já existia na entidade {@code ReadingAnomaly} é:
 * <ul>
 *   <li>o <b>tipo de OS gerado</b>, como referência por código ao catálogo de tipos de OS da
 *       P1.31 ({@code serviceOrderTypeCode}) — hoje a entidade só tem o booleano
 *       {@code generates_service_order}, que diz <i>que</i> gera OS mas não <i>qual</i>;</li>
 *   <li>a <b>mudança de situação</b> derivada dos campos de suspensão já existentes
 *       ({@link UnitSituationChange});</li>
 *   <li>o modo de faturar exposto na linguagem do catálogo ({@link OccurrenceBillingMode}),
 *       derivado dos efeitos de consumo que a cascata já aplica.</li>
 * </ul>
 * A perda de benefício é a que a entidade já guarda ({@code loses_social_tariff} — a tarifa
 * social é o benefício representado neste módulo); ela é exposta aqui como
 * {@code losesBenefit} sem renomear o fato.
 *
 * <p>O papel de sistema da entidade ({@code ReadingAnomaly.SystemRole}: hidrômetro parado)
 * <b>não</b> é repetido aqui: ele é a ligação da linha com a cascata, já carregada pela
 * {@link ConsumptionPolicy}, e este pacote não depende do pacote de domínio (a dependência é
 * a inversa). Quem monta a ocorrência a partir da entidade copia os campos de efeito; o papel
 * continua sendo lido da entidade por quem monta a política.
 *
 * <p><b>Coerência exigida na construção</b> (linha de catálogo incompleta não passa por padrão,
 * o mesmo princípio dos quatro efeitos NOT NULL da V5_1):
 * <ul>
 *   <li>gera OS ⇒ tipo de OS informado; tipo de OS sem gerar OS é recusado;</li>
 *   <li>suspensão (meses ou ocorrências) é o que liga {@link UnitSituationChange#SUSPEND_READING}:
 *       os dois fatos são gravados juntos e conferidos um contra o outro.</li>
 * </ul>
 *
 * @param code                 código do prestador (identificação, sem significado para a regra)
 * @param description          descrição da ocorrência no catálogo do tenant
 * @param rule                 a parte que a cascata usa (efeitos sobre leitura e consumo, fatores)
 * @param generatesServiceOrder a ocorrência gera uma ordem de serviço
 * @param serviceOrderTypeCode código do tipo de OS no catálogo da P1.31; obrigatório se, e somente
 *                             se, {@code generatesServiceOrder}
 * @param losesBenefit         a ocorrência faz a unidade perder o benefício (hoje: a tarifa
 *                             social, {@code loses_social_tariff} da entidade)
 * @param suspendReadingMonths meses de suspensão da leitura; nulo = não suspende por meses
 * @param suspendAfterOccurrences ocorrências a partir das quais a leitura é suspensa; nulo = sem gatilho
 */
public record ReadingOccurrence(
        String code,
        String description,
        ReadingAnomalyRule rule,
        boolean generatesServiceOrder,
        String serviceOrderTypeCode,
        boolean losesBenefit,
        Integer suspendReadingMonths,
        Integer suspendAfterOccurrences) {

    public ReadingOccurrence {
        MeteringInputException.require(code != null && !code.isBlank(), MeteringErrorCode.INVALID_INPUT,
                "code da ocorrência de leitura é obrigatório");
        MeteringInputException.require(description != null && !description.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "description da ocorrência de leitura é obrigatória");
        MeteringInputException.notNull(rule, "rule");
        MeteringInputException.require(code.equals(rule.code()), MeteringErrorCode.INVALID_INPUT,
                "code da ocorrência (" + code + ") difere do code da regra da cascata (" + rule.code() + ")");
        MeteringInputException.require(!generatesServiceOrder
                        || (serviceOrderTypeCode != null && !serviceOrderTypeCode.isBlank()),
                MeteringErrorCode.INVALID_INPUT,
                code + ": ocorrência gera OS mas não informa o tipo de OS (catálogo da P1.31)");
        MeteringInputException.require(generatesServiceOrder || serviceOrderTypeCode == null,
                MeteringErrorCode.INVALID_INPUT,
                code + ": tipo de OS informado para ocorrência que não gera OS");
        MeteringInputException.require(suspendReadingMonths == null || suspendReadingMonths >= 0,
                MeteringErrorCode.INVALID_INPUT, code + ": suspendReadingMonths negativo");
        MeteringInputException.require(suspendAfterOccurrences == null || suspendAfterOccurrences >= 1,
                MeteringErrorCode.INVALID_INPUT, code + ": suspendAfterOccurrences deve ser >= 1");
    }

    /** Ocorrência sem OS, sem perda de benefício e sem suspensão: só os efeitos da cascata. */
    public static ReadingOccurrence ofEffects(String description, ReadingAnomalyRule rule) {
        return new ReadingOccurrence(rule == null ? null : rule.code(), description, rule,
                false, null, false, null, null);
    }

    /** A mudança de situação que a ocorrência provoca, derivada dos campos de suspensão. */
    public UnitSituationChange situationChange() {
        return suspendReadingMonths != null || suspendAfterOccurrences != null
                ? UnitSituationChange.SUSPEND_READING
                : UnitSituationChange.NONE;
    }

    /** A ocorrência é a de "imóvel sem hidrômetro" (a que a validação do retorno reconhece). */
    public boolean forUnitWithoutMeter() {
        return rule.forUnitWithoutMeter();
    }

    /**
     * O efeito completo da ocorrência para um dos dois casos da cascata.
     *
     * @param hasReading houve leitura atual informada neste ciclo
     */
    public Effect effect(boolean hasReading) {
        ConsumptionEffect consumption = hasReading
                ? rule.consumptionEffectWithReading()
                : rule.consumptionEffectWithoutReading();
        ReadingEffect reading = hasReading
                ? rule.readingEffectWithReading()
                : rule.readingEffectWithoutReading();
        return new Effect(OccurrenceBillingMode.from(consumption), consumption, reading,
                generatesServiceOrder, serviceOrderTypeCode, losesBenefit, situationChange());
    }

    /**
     * O efeito completo de uma ocorrência num caso concreto (JOR-10): como fatura o consumo,
     * o que acontece com a leitura faturada, a OS gerada, a perda de benefício e a mudança de
     * situação — tudo o que quem lê o catálogo precisa decidir sem abrir a cascata.
     *
     * @param billingMode       consumo a faturar: por média, por mínimo, pelo medido ou por estimativa
     * @param consumptionEffect o efeito de consumo da cascata que produz {@code billingMode}
     * @param readingEffect     efeito sobre a leitura faturada (a "leitura anterior" do mês seguinte)
     * @param generatesServiceOrder a ocorrência gera OS neste caso
     * @param serviceOrderTypeCode  código do tipo de OS (catálogo da P1.31); nulo se não gera
     * @param losesBenefit      a ocorrência faz a unidade perder o benefício (tarifa social)
     * @param situationChange   mudança de situação da unidade provocada pela ocorrência
     */
    public record Effect(
            OccurrenceBillingMode billingMode,
            ConsumptionEffect consumptionEffect,
            ReadingEffect readingEffect,
            boolean generatesServiceOrder,
            String serviceOrderTypeCode,
            boolean losesBenefit,
            UnitSituationChange situationChange) {
    }
}
