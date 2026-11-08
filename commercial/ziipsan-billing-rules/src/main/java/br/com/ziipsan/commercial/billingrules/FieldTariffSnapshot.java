package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Retrato (snapshot) da tarifa e dos parametros de UMA tarefa de campo
 * (ED-19): e o que o pacote da rota carrega e o que torna o calculo offline
 * possivel e reproduzivel — o servidor recalcula com o snapshot DA TAREFA
 * (nao com a tarifa "de agora"), e qualquer diferenca de versao entre o
 * snapshot do aparelho e o esperado pelo servidor e divergencia de base.
 *
 * @param tariffVersionId    versao de tarifa da P2.5 de onde o retrato saiu
 * @param categoryId         categoria tarifaria da unidade na tarefa
 * @param minimumM3PerEconomy consumo minimo faturado por economia (m3)
 * @param fixedChargePerEconomy parcela fixa por economia (cobre ate o minimo)
 * @param brackets           faixas progressivas sobre o volume por economia
 *                           ACIMA do minimo; a ultima e aberta
 * @param sewerEnabled       esgoto faturado nesta unidade?
 * @param sewerPct           percentual do esgoto sobre o valor de agua
 *                           (agua variavel + parcela fixa), ex.: 80 = 80%
 * @param parameterVersions  versoes de parametro que afetam esta conta
 *                           (chave do parametro -> versao/valor resolvido),
 *                           carimbadas no pacote da rota
 */
public record FieldTariffSnapshot(UUID tariffVersionId, UUID categoryId,
                                  int minimumM3PerEconomy, BigDecimal fixedChargePerEconomy,
                                  List<TariffBracket> brackets,
                                  boolean sewerEnabled, BigDecimal sewerPct,
                                  Map<String, String> parameterVersions) {

    public FieldTariffSnapshot {
        Objects.requireNonNull(tariffVersionId, "versao de tarifa do snapshot e obrigatoria");
        Objects.requireNonNull(categoryId, "categoria do snapshot e obrigatoria");
        if (minimumM3PerEconomy < 0) {
            throw new IllegalArgumentException("Consumo minimo por economia negativo: " + minimumM3PerEconomy);
        }
        Objects.requireNonNull(fixedChargePerEconomy, "parcela fixa por economia e obrigatoria");
        if (fixedChargePerEconomy.signum() < 0) {
            throw new IllegalArgumentException("Parcela fixa negativa: " + fixedChargePerEconomy);
        }
        if (brackets == null || brackets.isEmpty()) {
            throw new IllegalArgumentException("Snapshot sem nenhuma faixa tarifaria");
        }
        brackets = List.copyOf(brackets);
        for (int i = 0; i < brackets.size(); i++) {
            if (brackets.get(i).isOpen() && i != brackets.size() - 1) {
                throw new IllegalArgumentException("Faixa aberta so pode ser a ultima (indice " + i + ")");
            }
        }
        if (!brackets.get(brackets.size() - 1).isOpen()) {
            throw new IllegalArgumentException("A ultima faixa do snapshot deve ser aberta (absorve o restante)");
        }
        if (sewerEnabled) {
            Objects.requireNonNull(sewerPct, "esgoto faturado exige o percentual de esgoto");
            if (sewerPct.signum() < 0) {
                throw new IllegalArgumentException("Percentual de esgoto negativo: " + sewerPct);
            }
        }
        parameterVersions = parameterVersions == null ? Map.of() : Map.copyOf(parameterVersions);
    }
}
