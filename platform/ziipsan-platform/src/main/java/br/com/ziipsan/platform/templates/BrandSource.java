package br.com.ziipsan.platform.templates;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura da marca do tenant para documentos (P2.17).
 *
 * <p>PONTO DE INTEGRAÇÃO (declarado): a implementação de produção lê a
 * marca da P1.19 — {@code TenantBrandService.currentBrand(tenantId)} para
 * nome/cor/contato/rodapé e os ids dos ativos efetivos (só {@code CLEAN})
 * para os logos — e monta o {@link TenantPdfBrand}. Tenant sem marca
 * cadastrada devolve {@link Optional#empty()}: o documento sai sem camada
 * de marca, nunca com marca de outro tenant nem com a marca do produto
 * fingindo ser a do tenant.
 */
@FunctionalInterface
public interface BrandSource {

    /** Marca do tenant para documentos; vazia quando o tenant não tem marca. */
    Optional<TenantPdfBrand> brandFor(UUID tenantId);
}
