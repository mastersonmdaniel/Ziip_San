package br.com.ziipsan.commercial.fiscal.relief;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vinculo sistematico codigos fiscais x CONSUMO/CATEGORIA (PA-01 V-44 =
 * PARCIAL confirmado; insumo (c) do coordenador): o grupo IBS/CBS por item
 * (CST/cClassTrib, V107) existe e o vinculo cClass x SERVICO (ED-34) esta
 * COBERTO — nao se duplica. O que entra e o recorte que faltava: tipo de
 * consumo e categoria tarifaria.
 *
 * <p>Guarda de completude (fronteira da secao 9 do desenho): par
 * (tipo de consumo, categoria) usado numa emissao SEM vinculo completo =>
 * emissao impedida com erro explicito, no espirito do art. 909. O relatorio
 * de cobertura lista os pares em falta.
 */
public final class FiscalConsumptionLinkService {

    /** Par (tipo de consumo, categoria tarifaria) sujeito a vinculo. */
    public record ConsumptionCategory(String consumptionType, String tariffCategory) {
        public ConsumptionCategory {
            if (consumptionType == null || consumptionType.isBlank()) {
                throw new IllegalArgumentException("tipo de consumo e obrigatorio");
            }
            if (tariffCategory == null || tariffCategory.isBlank()) {
                throw new IllegalArgumentException("categoria tarifaria e obrigatoria");
            }
        }

        String key() {
            return consumptionType + "|" + tariffCategory;
        }
    }

    /** Classificacao fiscal vinculada ao par (codigos declarados no cadastro). */
    public record FiscalClassification(String cstCode, String cClassTribCode) {
        public FiscalClassification {
            if (cstCode == null || cstCode.isBlank()) {
                throw new IllegalArgumentException("CST do vinculo e obrigatorio");
            }
            if (cClassTribCode == null || cClassTribCode.isBlank()) {
                throw new IllegalArgumentException("cClassTrib do vinculo e obrigatorio");
            }
        }
    }

    private final Map<String, FiscalClassification> links = new LinkedHashMap<>();
    private final List<ConsumptionCategory> knownPairs = new ArrayList<>();

    public void registerPair(ConsumptionCategory pair) {
        if (!knownPairs.contains(pair)) {
            knownPairs.add(pair);
        }
    }

    public void link(ConsumptionCategory pair, FiscalClassification classification) {
        Objects.requireNonNull(classification, "classificacao e obrigatoria");
        registerPair(pair);
        links.put(pair.key(), classification);
    }

    /**
     * Resolucao para emissao: par sem vinculo completo => emissao impedida.
     */
    public FiscalClassification requireForEmission(ConsumptionCategory pair) {
        FiscalClassification found = links.get(pair.key());
        if (found == null) {
            throw new IllegalStateException(
                    "Emissao impedida: sem vinculo fiscal completo para consumo "
                            + pair.consumptionType() + " / categoria " + pair.tariffCategory());
        }
        return found;
    }

    /** Cobertura: pares conhecidos sem vinculo. */
    public List<ConsumptionCategory> coverageGaps() {
        return knownPairs.stream().filter(p -> !links.containsKey(p.key())).toList();
    }
}
