package br.com.ziipsan.reporting.bi;

/**
 * ED-47: porta da auditoria de acesso ao BI (SG-1252).
 *
 * <p>A implementação de produção grava na trilha de auditoria do core
 * ({@code audit_log}, particionado na P1.7) a partir do JSON do evento —
 * fiação da integração, porque o escritor da trilha vive no aplicativo.
 * Sem implementação ligada, a política de acesso recusa (ver
 * {@code BiAccessPolicy}): BI sem auditoria não é um modo válido, é um
 * incidente de configuração.
 */
public interface BiAccessAuditPort {

    void record(BiAccessEvent event);
}
