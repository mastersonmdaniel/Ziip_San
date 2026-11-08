package br.com.ziipsan.integration.erp;

import java.util.Objects;
import java.util.UUID;

/**
 * ED-62: os GANHOS de integracao nomeados pelo aceite. Eles NAO alteram os
 * donos dos dados: a unidade organizacional continua da P3.30, o material
 * continua da ED-58 e o contrato de empreiteira continua da ED-37 — aqui
 * vivem apenas os vinculos/campos que a exportacao ao ERP consome (a
 * ED-37 declara expressamente que empenho/ordem de compra/PNCP sao da
 * ED-62 e nao existem no record dela).
 */
public final class ErpHooks {
    private ErpHooks() {}

    /**
     * Centro de custo do ERP compativel com a unidade organizacional da
     * P3.30: o vinculo e por CODIGO da unidade (OrgUnit.code) para o
     * codigo de centro de custo DECLARADO do ERP do cliente. Sem vinculo
     * declarado, lancamento com centro de custo nao e exportado.
     */
    public record CostCenterLink(UUID tenantId, String orgUnitCode, String erpCostCenterCode) {
        public CostCenterLink {
            Objects.requireNonNull(tenantId, "tenant do vinculo de centro de custo e obrigatorio");
            if (orgUnitCode == null || orgUnitCode.isBlank()) throw new IllegalArgumentException("codigo da unidade organizacional (P3.30) e obrigatorio");
            if (erpCostCenterCode == null || erpCostCenterCode.isBlank()) throw new IllegalArgumentException("codigo de centro de custo do ERP e obrigatorio (declarado, nunca presumido)");
        }
    }

    /**
     * Natureza de despesa nos materiais da ED-58 para exportacao: o codigo
     * de natureza e DECLARADO no cadastro do vinculo (vem da tabela do
     * cliente/ERP); material sem natureza declarada nao entra na previsao
     * nem no movimento exportado — a exportacao recusa nomeando o material.
     */
    public record MaterialErpHook(UUID tenantId, String materialCode, String expenseNatureCode) {
        public MaterialErpHook {
            Objects.requireNonNull(tenantId, "tenant do gancho de material e obrigatorio");
            if (materialCode == null || materialCode.isBlank()) throw new IllegalArgumentException("codigo do material (ED-58/ED-36) e obrigatorio");
            if (expenseNatureCode == null || expenseNatureCode.isBlank()) throw new IllegalArgumentException("natureza de despesa declarada e obrigatoria para o material " + materialCode);
        }
    }

    /**
     * Campos externos do contrato de terceiros (incl. empreiteiras da
     * ED-37): numero de empenho OU ordem de compra externa (ao menos um,
     * pois sao as duas formas que o aceite nomeia) e numero no PNCP
     * quando houver. Todos sao valores DECLARADOS do contrato real —
     * nenhum e gerado.
     */
    public record ThirdPartyContractHook(UUID tenantId, UUID contractId, String commitmentNumber,
                                         String externalPurchaseOrder, String pncpNumber) {
        public ThirdPartyContractHook {
            Objects.requireNonNull(tenantId, "tenant do gancho de contrato e obrigatorio");
            Objects.requireNonNull(contractId, "contrato (ED-37 ou contrato de terceiros) e obrigatorio");
            boolean hasCommitment = commitmentNumber != null && !commitmentNumber.isBlank();
            boolean hasOrder = externalPurchaseOrder != null && !externalPurchaseOrder.isBlank();
            if (!hasCommitment && !hasOrder) {
                throw new IllegalArgumentException("gancho de contrato exige numero de empenho ou ordem de compra externa declarado");
            }
            commitmentNumber = hasCommitment ? commitmentNumber.trim() : null;
            externalPurchaseOrder = hasOrder ? externalPurchaseOrder.trim() : null;
            pncpNumber = (pncpNumber == null || pncpNumber.isBlank()) ? null : pncpNumber.trim();
        }
    }
}
