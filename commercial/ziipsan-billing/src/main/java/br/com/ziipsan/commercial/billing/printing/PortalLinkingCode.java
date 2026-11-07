package br.com.ziipsan.commercial.billing.printing;

/**
 * Codigo de vinculacao ao portal do imovel (ADR-0022): o codigo impresso na
 * conta com o qual o consumidor vincula a matricula a conta dele no portal
 * (o vinculo em si, com o hash do codigo por imovel, e da P3.20, tenant
 * {@code V8_2}; o pagamento sem conta pelo codigo e do ADR-0043/P3.3).
 *
 * <p>Esta tarefa NAO gera nem valida o codigo contra o cadastro: recebe o
 * valor vigente do chamador e o coloca SOMENTE no arquivo final da grafica.
 * Por isso o objeto e opaco e o {@link #toString()} e REDIGIDO: o codigo nao
 * aparece em log, em resumo de lote, em manifesto nem em representacao
 * textual de nenhum objeto desta tarefa fora do conteudo do arquivo gerado
 * por {@code DefaultPrintFileService} (ver {@code LinkingCodeLeakTest}). Quem
 * persiste, compara por indice cego e revoga na troca de titularidade e a
 * P3.20 — aqui nao ha copia persistida do codigo.
 */
public record PortalLinkingCode(String value) {

    public PortalLinkingCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "codigo de vinculacao ao portal e obrigatorio: conta sem codigo nao entra no arquivo da grafica (ADR-0022)");
        }
        if (value.contains(PrintLayout.DELIMITER) || value.contains("\n") || value.contains("\r")
                || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "codigo de vinculacao com caractere incompativel com o leiaute do arquivo da grafica");
        }
    }

    /** Representacao REDIGIDA: nunca devolve o codigo (ver javadoc da classe). */
    @Override
    public String toString() {
        return "PortalLinkingCode[REDACTED]";
    }
}
