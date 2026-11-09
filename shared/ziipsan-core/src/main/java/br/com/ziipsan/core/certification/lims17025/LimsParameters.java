package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros proprios da ED-51, lidos do tenant — nenhum modelo e
 * constante de negocio nesta classe:
 * <pre>
 *   lims.lab_report_template_key  TEXT chave do modelo de laudo no
 *       motor de modelos da P2.30 (JOR-06).
 *       [SEM FONTE] — nenhum codigo de modelo de laudo consta das
 *       fontes do projeto (plano, ADRs, pacotes das dependencias);
 *       sem valor de fabrica: ausente, a emissao do laudo recusa com
 *       {@link MissingParameterException} nomeada, no padrao da
 *       ED-50 ({@code quality.risk_template_key}).
 * </pre>
 * O chamador (fiacao) le o valor do registro de parametros do tenant e
 * entrega o texto; esta classe valida. A carga da chave so acontece na
 * fiacao, quando o modelo de laudo existir no motor da P2.30
 * (pendencia P-ED51-02).
 */
public final class LimsParameters {

    public static final String LAB_REPORT_TEMPLATE_KEY = "lims.lab_report_template_key";

    private LimsParameters() {}

    public static String labReportTemplateKey(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(LAB_REPORT_TEMPLATE_KEY,
                "modelo de laudo (P2.30, JOR-06) nao carregado para o tenant [SEM FONTE]");
        return raw.trim();
    }
}
