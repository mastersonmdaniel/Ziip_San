package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;

/**
 * Uma linha da TABELA DE CUSTO (P0.26: decisao de hospedagem ADR-0011/
 * ADR-0033, AWS sa-east-1 gerenciada) ou de qualquer custo direto da
 * planilha da ED-10. O valor e SEMPRE dado de entrada da versao da
 * tabela vigente - nenhum custo de hospedagem e fixado neste codigo.
 */
public record CostComponent(String name, BigDecimal monthlyAmount, Allocation allocation) {

    /** Como a linha entra no custo mensal de UM tenant avaliado. */
    public enum Allocation {
        /** Custo inteiro por tenant (ex.: pilha dedicada, suporte direto). */
        FIXED_PER_TENANT,
        /** Custo por ligacao ativa: monthlyAmount e o custo UNITARIO. */
        PER_CONNECTION,
        /** Custo da plataforma dividido pela base de tenants do pool. */
        SHARED_POOL
    }

    public CostComponent {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("nome do componente de custo e obrigatorio");
        if (monthlyAmount == null || monthlyAmount.signum() < 0)
            throw new IllegalArgumentException("valor mensal do componente invalido: " + name);
        if (allocation == null) throw new IllegalArgumentException("alocacao do componente e obrigatoria: " + name);
    }
}
