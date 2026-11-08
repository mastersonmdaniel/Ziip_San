package br.com.ziipsan.commercial.registry.quality;

/**
 * Catalogo fechado das regras continuas de qualidade do cadastro comercial
 * (P3.32, CAD-98). Cada codigo e um item do aceite:
 *
 * <ul>
 *   <li>documento: {@link #DOCUMENT_MISSING} (ausente),
 *       {@link #DOCUMENT_INVALID} (invalido: digito verificador recusado pelo
 *       {@code DocumentNumber} do core ou validacao externa NOT_FOUND) e
 *       {@link #DOCUMENT_IRREGULAR} (situacao irregular na validacao externa
 *       da P1.41) — o aceite nomeia os tres estados numa regra so; sao tres
 *       codigos porque a fila de correcao trata cada estado com uma acao;</li>
 *   <li>{@link #CONTACT_UNVERIFIED}: contato nao verificado;</li>
 *   <li>{@link #CATEGORY_CNAE_MISMATCH}: categoria incompativel com o CNAE;</li>
 *   <li>{@link #ECONOMIES_AREA_MISMATCH}: economias incompativeis com a area
 *       construida;</li>
 *   <li>{@link #ACTIVE_UNIT_WITHOUT_METER}: unidade ativa sem hidrometro;</li>
 *   <li>{@link #UNIT_WITHOUT_READING}: unidade ativa sem leitura em N ciclos;</li>
 *   <li>{@link #METER_VERIFICATION_EXPIRED}: hidrometro vencido (intervalo de
 *       verificacao da ED-14 vencido);</li>
 *   <li>{@link #ADDRESS_WITHOUT_COORDINATES}: endereco sem coordenada;</li>
 *   <li>{@link #ADDRESS_OUT_OF_SECTOR}: endereco fora do setor (a quadra da
 *       unidade esta num setor e a rota de leitura em outro — a regra
 *       verificavel que a P1.3 tornou possivel: rota do mesmo setor da
 *       quadra);</li>
 *   <li>{@link #HOLDER_POSSIBLY_DECEASED}: titular possivelmente falecido
 *       (sinal, nunca efeito automatico — a sucessao e da P1.41, com revisao
 *       humana);</li>
 *   <li>duplicidades: {@link #DUPLICATE_DOCUMENT} (mesmo indice cego em
 *       clientes ativos distintos) e {@link #DUPLICATE_INSCRIPTION} (mesma
 *       inscricao quadra/lote/sublote em unidades ativas distintas);</li>
 *   <li>{@link #UNIT_WITHOUT_USER}: unidade ativa sem usuario vigente;</li>
 *   <li>{@link #BILL_UNDELIVERED}: conta nao entregue registrada pela P2.8
 *       (JOR-35: devolucao, endereco nao localizado).</li>
 * </ul>
 *
 * <p>Regra nova entra por nova versao deste catalogo, como nos catalogos da
 * P2.8/P2.32. A severidade padrao de cada regra e a de fabrica; o tenant a
 * sobrescreve por {@code quality.rules} (classe d).
 */
public enum QualityRuleCode {

    DOCUMENT_MISSING(QualitySeverity.HIGH),
    DOCUMENT_INVALID(QualitySeverity.HIGH),
    DOCUMENT_IRREGULAR(QualitySeverity.MEDIUM),
    CONTACT_UNVERIFIED(QualitySeverity.MEDIUM),
    CATEGORY_CNAE_MISMATCH(QualitySeverity.MEDIUM),
    ECONOMIES_AREA_MISMATCH(QualitySeverity.MEDIUM),
    ACTIVE_UNIT_WITHOUT_METER(QualitySeverity.HIGH),
    UNIT_WITHOUT_READING(QualitySeverity.HIGH),
    METER_VERIFICATION_EXPIRED(QualitySeverity.MEDIUM),
    ADDRESS_WITHOUT_COORDINATES(QualitySeverity.MEDIUM),
    ADDRESS_OUT_OF_SECTOR(QualitySeverity.MEDIUM),
    HOLDER_POSSIBLY_DECEASED(QualitySeverity.HIGH),
    DUPLICATE_DOCUMENT(QualitySeverity.CRITICAL),
    DUPLICATE_INSCRIPTION(QualitySeverity.CRITICAL),
    UNIT_WITHOUT_USER(QualitySeverity.HIGH),
    BILL_UNDELIVERED(QualitySeverity.MEDIUM);

    private final QualitySeverity defaultSeverity;

    QualityRuleCode(QualitySeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    /** Severidade de fabrica, usada quando {@code quality.rules} nao a sobrescreve. */
    public QualitySeverity defaultSeverity() {
        return defaultSeverity;
    }

    /** Chave estavel da regra (a gravada no banco e a do JSON de {@code quality.rules}). */
    public String key() {
        return name();
    }

    /** Le a chave do parametro/banco. Fora do catalogo = erro explicito. */
    public static QualityRuleCode fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("codigo de regra de qualidade e obrigatorio (catalogo da P3.32)");
        }
        for (QualityRuleCode code : values()) {
            if (code.name().equals(key.strip())) {
                return code;
            }
        }
        throw new IllegalArgumentException("Regra de qualidade fora do catalogo da P3.32: " + key);
    }
}
