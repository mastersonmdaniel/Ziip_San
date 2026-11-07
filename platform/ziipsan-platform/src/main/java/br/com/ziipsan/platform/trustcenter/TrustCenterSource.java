package br.com.ziipsan.platform.trustcenter;

import java.util.List;

/**
 * P3.38: porta de leitura dos fatos do Trust Center (suboperadores da
 * {@code V10_6}, certificações do provedor do kit da P0.44/P0.26,
 * encarregado, política de divulgação), no padrão das portas do módulo
 * (cf. {@code StatusPageSource} da P3.18).
 *
 * <p>As implementações reais (JDBC sobre {@code platform.subprocessor},
 * leitura do relatório de certificações arquivado na P0.44, do resumo do
 * pentest da LC.8 e da matriz de evidências do ASVS da P3.8) são a fiação
 * da integração e das tarefas-fonte — declaradas no relatório da P3.38,
 * não fingidas aqui: sem implementação entregue, nenhum bean é
 * registrado e o endpoint não sobe com dado falso. Retornos
 * {@code null}/vazios têm significado definido e honesto em
 * {@link TrustCenterService}: seção pendente de fonte, nunca conteúdo
 * inventado.
 */
public interface TrustCenterSource {

    /** Suboperadores cadastrados (a visão filtra os publicáveis). */
    List<Subprocessor> subprocessors();

    /** Certificações/relatórios do provedor de hospedagem (fonte: P0.26, kit da P0.44). */
    List<TrustCenterService.ProviderCertification> providerCertifications();

    /** Resumo público do pentest externo; {@code null} = LC.8 ainda não executada. */
    String pentestSummary();

    /** Nome do encarregado da plataforma; {@code null} = nomeação não registrada. */
    String dpoName();

    /** Canal público do encarregado; {@code null} = não registrado. */
    String dpoContact();

    /** Política de divulgação configurada (canal/prazos podem estar pendentes). */
    DisclosurePolicy disclosurePolicy();

    /** A matriz de evidências do ASVS da P3.8 foi entregue para publicação? */
    boolean asvsEvidenceAvailable();
}
