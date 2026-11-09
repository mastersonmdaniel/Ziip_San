package br.com.ziipsan.core.certification.external;

/**
 * Parametros do produto que regem a ED-75, todos lidos do catalogo
 * real do core V3 ({@code V3__tenant_parameters_catalog.sql}) ou
 * registrados por esta tarefa no SQL pendente — nenhum mecanismo
 * paralelo e inventado:
 *
 * <ul>
 *   <li>{@code cert.evidence_pack_personal_data} — BOOLEAN, classe a
 *       TRAVADO em {@code false} (ADR-0041, item 10; limite LOCKED do
 *       core V3): o pacote de evidencia NUNCA contem dado pessoal; so
 *       leva dado agregado, mascarado ou pseudonimizado.</li>
 *   <li>{@code cert.external_auditor_access} — BOOLEAN, classe b,
 *       padrao de fabrica {@code false} (ADR-0041, item 10): papel
 *       temporario de auditor externo, somente leitura, com prazo e
 *       registro de cada leitura; desligado por padrao.</li>
 *   <li>{@code reg.external_access} — BOOLEAN, classe b, padrao de
 *       fabrica {@code false}: o aceite da ED-75 nomeia este parametro
 *       para o acesso externo GENERALIZADO (observador externo:
 *       auditor de certificacao E regulador/titular do servico,
 *       GES-22/JOR-23). Ele NAO existe no catalogo do core V3 (que so
 *       tem o {@code cert.external_auditor_access}, restrito ao
 *       auditor); esta tarefa o REGISTRA no catalogo pelo SQL
 *       pendente, no padrao do precedente da ED-06 (INSERT em
 *       {@code public.parameter_definitions}), com classe b: fora do
 *       padrao de fabrica (ligar) exige aceite formal registrado, com
 *       proponente diferente do aceitante (regra da classe b do
 *       ADR-0015 e do ciclo de parametros da V1_5).</li>
 * </ul>
 *
 * <p>Classe b "com aceite": ligar qualquer um dos dois parametros de
 * acesso exige aceite — nesta regua, a concessao
 * ({@link ExternalAccessGrant}) exige {@code grantedBy} diferente de
 * {@code acceptedBy}.</p>
 */
public final class ExternalAccessPolicy {

    /** Parametro generalizado do aceite da ED-75 (classe b, com aceite). */
    public static final String REG_EXTERNAL_ACCESS_PARAM = "reg.external_access";

    /** Parametro pre-existente do auditor externo (core V3, classe b). */
    public static final String CERT_EXTERNAL_AUDITOR_ACCESS_PARAM = "cert.external_auditor_access";

    /** Trava do pacote de evidencia (core V3, classe a, LOCKED false). */
    public static final String EVIDENCE_PACK_PERSONAL_DATA_PARAM = "cert.evidence_pack_personal_data";

    /** Padrao de fabrica dos dois parametros de acesso: DESLIGADO. */
    public static final boolean ACCESS_FACTORY_DEFAULT = false;

    /** Padrao travado do pacote de evidencia: SEM dado pessoal. */
    public static final boolean EVIDENCE_PACK_PERSONAL_DATA_LOCKED_VALUE = false;

    private ExternalAccessPolicy() {
    }
}
