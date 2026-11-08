package br.com.ziipsan.commercial.billing.saaspricing;

/**
 * Faixa de PORTE da planilha da ED-10, medida em ligacoes ativas (a base
 * comprovavel da P2.12/PLT-17). As faixas sao DADO da versao da planilha
 * aprovada: nenhuma fonte fixa cortes de porte, logo NENHUM corte e
 * presumido neste codigo - faixa sem limites nao existe, e a cobertura
 * das faixas e validada pela {@link PriceSheet}.
 */
public record SizeBand(String code, long minConnections, long maxConnections) {
    public SizeBand {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo da faixa de porte e obrigatorio");
        if (minConnections < 0) throw new IllegalArgumentException("minimo da faixa nao pode ser negativo: " + minConnections);
        if (maxConnections < minConnections) throw new IllegalArgumentException("maximo da faixa menor que o minimo: " + code);
    }
    public boolean contains(long activeConnections) {
        return activeConnections >= minConnections && activeConnections <= maxConnections;
    }
}
