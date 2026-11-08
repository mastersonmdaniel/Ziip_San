package br.com.ziipsan.core.quality;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros da ED-50, lidos do tenant — nenhum prazo, leiaute ou modelo
 * e constante de negocio nesta classe, alem do teto COM FONTE declarado:
 * <pre>
 *   sac.quality_complaint_deadline_days  INTEGER prazo da reclamacao de
 *       qualidade (Decreto 5.440/2005): o aceite da ED-50 fixa 30 dias
 *       como "valor da norma, so reduz" — por isso o teto 30 esta AQUI,
 *       com a fonte nomeada; o valor vigente e do tenant e a ausencia e
 *       erro explicito, nunca fallback silencioso (padrao SacParameters
 *       da ED-35).
 *   quality.sisagua_layout               TEXT identificacao do leiaute/
 *       versao do arquivo SISAGUA vigente para o tenant.
 *       [SEM FONTE] — nenhum leiaute oficial consta das fontes do
 *       projeto; sem valor de fabrica: ausente, a exportacao recusa.
 *   quality.risk_template_key            TEXT chave do modelo de
 *       comunicacao de risco no motor de modelos da P2.30 (JOR-06).
 *       [SEM FONTE] — o modelo so e carregado depois de os artigos da
 *       Portaria GM/MS 888/2021 serem conferidos com o responsavel
 *       tecnico (JOR-22); sem chave, a comunicacao recusa.
 * </pre>
 * O chamador (fiacao) le o JSON com a classe {@code Json} do core e
 * entrega os valores tipados; esta classe valida.
 */
public final class QualityParameters {

    public static final String COMPLAINT_DEADLINE_DAYS = "sac.quality_complaint_deadline_days";
    public static final String SISAGUA_LAYOUT = "quality.sisagua_layout";
    public static final String RISK_TEMPLATE_KEY = "quality.risk_template_key";

    /** Teto do Decreto 5.440/2005 conforme o aceite da ED-50 (OPE-26): 30 dias, so reduz. */
    public static final int COMPLAINT_DEADLINE_NORM_DAYS = 30;

    private QualityParameters() {}

    public static int complaintDeadlineDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(COMPLAINT_DEADLINE_DAYS,
                "prazo da reclamacao de qualidade nao configurado para o tenant");
        if (raw <= 0) throw new IllegalArgumentException(
                COMPLAINT_DEADLINE_DAYS + " deve ser maior que zero");
        if (raw > COMPLAINT_DEADLINE_NORM_DAYS) throw new IllegalArgumentException(
                COMPLAINT_DEADLINE_DAYS + " acima do prazo do Decreto 5.440/2005 fixado no aceite"
                        + " (30 dias, so reduz): " + raw);
        return raw;
    }

    public static String sisaguaLayout(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(SISAGUA_LAYOUT,
                "leiaute do arquivo SISAGUA nao configurado para o tenant [SEM FONTE]");
        return raw.trim();
    }

    public static String riskTemplateKey(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(RISK_TEMPLATE_KEY,
                "modelo de comunicacao de risco (P2.30) nao carregado para o tenant;"
                        + " a carga exige a conferencia dos artigos com o responsavel tecnico (JOR-22)");
        return raw.trim();
    }
}
