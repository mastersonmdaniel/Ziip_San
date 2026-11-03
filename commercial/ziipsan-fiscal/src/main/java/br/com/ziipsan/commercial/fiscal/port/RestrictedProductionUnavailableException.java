package br.com.ziipsan.commercial.fiscal.port;

/**
 * Falha fechada do stub da produção restrita (P1.8): o ambiente de produção restrita da
 * NFS-e Nacional é inalcançável a partir deste sandbox (sem certificado, sem rede para a
 * Sefin), então a emissão fora do simulador é recusada ANTES de consumir número, gravar
 * documento ou tocar no cofre — nunca fingida como emissão real. A prova em ambiente real
 * (produção restrita, a partir do ambiente local, com o e-CNPJ da Ziip/TGP) é pendência
 * registrada, não evidência simulada.
 */
public class RestrictedProductionUnavailableException extends IllegalStateException {

    public RestrictedProductionUnavailableException() {
        super("Producao restrita da NFS-e Nacional inalcançavel neste ambiente (stub local): "
                + "nenhuma emissao foi feita, nenhum numero foi consumido. "
                + "Prova em ambiente real pendente (P1.8).");
    }
}
