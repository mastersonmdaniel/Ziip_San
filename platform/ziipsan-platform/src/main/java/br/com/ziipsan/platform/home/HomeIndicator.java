package br.com.ziipsan.platform.home;

import java.math.BigDecimal;

/**
 * Indicador exibido na pagina inicial (P3.39): o retrato de leitura de UM
 * indicador do registro unico da P3.41, ja apurado por quem le o registro —
 * a pagina inicial NAO apura, NAO calcula e NAO inventa valor: ela exibe no
 * maximo quatro, na ordem do registro, os marcados para o perfil do usuario
 * (marcacao por perfil da P3.41: {@code listForProfile}).
 *
 * <p>{@code semaphore} usa os MESMOS nomes do {@code Semaphore} da P3.41
 * (GREEN/YELLOW/RED/NO_TARGET) carregados como texto porque o modulo
 * platform nao depende do reporting — o adaptador da integracao mapeia o
 * enum por nome e um nome fora do vocabulario e recusado aqui, nunca
 * exibido como verde por omissao.
 */
public record HomeIndicator(String code, String name, String unit, BigDecimal value, String semaphore) {

    public HomeIndicator {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("indicador da pagina inicial exige codigo e nome");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("indicador " + code + " exige unidade (registro da P3.41)");
        }
        if (value == null) {
            throw new IllegalArgumentException("indicador " + code
                    + " sem valor apurado: a pagina inicial nao exibe indicador nao apurado");
        }
        if (!"GREEN".equals(semaphore) && !"YELLOW".equals(semaphore)
                && !"RED".equals(semaphore) && !"NO_TARGET".equals(semaphore)) {
            throw new IllegalArgumentException("indicador " + code + " com semaforo fora do vocabulario"
                    + " da P3.41 (GREEN/YELLOW/RED/NO_TARGET): " + semaphore);
        }
    }
}
