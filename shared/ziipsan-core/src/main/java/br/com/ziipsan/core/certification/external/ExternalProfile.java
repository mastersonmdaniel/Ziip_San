package br.com.ziipsan.core.certification.external;

/**
 * Perfil por tipo do observador externo (ED-75; GES-22, JOR-23): a
 * generalizacao do papel de auditor externo.
 *
 * <ul>
 *   <li>{@link #CERTIFICATION_AUDITOR} — auditor de certificacao:
 *       escopo no pacote de evidencia das normas ligadas.</li>
 *   <li>{@link #REGULATOR_SERVICE_OWNER} — regulador ou titular do
 *       servico (agencia reguladora e prefeitura): escopo nos dados e
 *       relatorios regulatorios, SEM pacote de evidencia ISO e SEM
 *       dado pessoal alem do minimo. O CONTEUDO concreto desse
 *       escopo (quais dados e relatorios) e definido pela ED-97
 *       sobre este mecanismo — pendencia nomeada da ED-75; o acesso
 *       do orgao a instancia dedicada fica na LC.15.</li>
 * </ul>
 */
public enum ExternalProfile {
    CERTIFICATION_AUDITOR,
    REGULATOR_SERVICE_OWNER
}
