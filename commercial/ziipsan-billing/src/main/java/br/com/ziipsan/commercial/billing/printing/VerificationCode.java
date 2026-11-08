package br.com.ziipsan.commercial.billing.printing;

/**
 * Codigo publico de verificacao da fatura no arquivo da grafica (P3.35,
 * PLT-11): o codigo curto impresso na conta (e codificado no QR) com o qual
 * qualquer pessoa confirma a autenticidade da fatura na pagina publica
 * {@code GET /api/v1/public/documents/verify}.
 *
 * <p>Mesma disciplina do {@link PortalLinkingCode} da P2.8: esta tarefa NAO
 * gera nem valida o codigo — quem gera (CSPRNG, 80 bits), grava o indice
 * cego e revoga e a verificacao publica da P3.35 no portal (V8_3). Aqui o
 * objeto e opaco, o {@link #toString()} e REDIGIDO e o valor so e lido uma
 * vez, direto para a linha do arquivo final da grafica em
 * {@code DefaultPrintFileService}: nao aparece em log, manifesto, resumo
 * de lote nem prova de emissao.
 *
 * <p>DIFERENTE do codigo de vinculacao: sao codigos distintos, com
 * contextos de indice cego distintos — o de verificacao NAO vincula
 * unidade a conta de portal e o de vinculacao NAO serve de verificacao.
 */
public record VerificationCode(String value) {

    public VerificationCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "codigo de verificacao e obrigatorio: conta sem codigo nao entra no arquivo da grafica (P3.35)");
        }
        if (value.contains(PrintLayout.DELIMITER) || value.contains("\n") || value.contains("\r")
                || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "codigo de verificacao com caractere incompativel com o leiaute do arquivo da grafica");
        }
    }

    /** Representacao REDIGIDA: nunca devolve o codigo. */
    @Override
    public String toString() {
        return "VerificationCode[REDACTED]";
    }
}
