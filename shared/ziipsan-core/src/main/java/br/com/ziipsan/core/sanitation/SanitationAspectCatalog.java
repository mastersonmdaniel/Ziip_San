package br.com.ziipsan.core.sanitation;

import java.util.List;
import java.util.Optional;

/**
 * Catalogo de FABRICA de aspectos ambientais de ETA, ETE e rede
 * (aceite da ED-70: "aspectos e impactos ambientais com catalogo de
 * fabrica de ETA, ETE e rede no pacote {@code sanitation}").
 *
 * <p>O catalogo e ESTRUTURA de partida declarada pelo aceite — modelos
 * (templates) de aspecto nas tres areas do saneamento —, nao dado
 * normativo: os rotulos sao descricoes operacionais em palavras
 * proprias do processo (dosagem, geracao de lodo, lancamento de
 * efluente, perdas na rede, residuos de escavacao), sem artigo, limite
 * ou texto da ISO 14001. O catalogo NAO pre-avalia significancia: a
 * significancia e do criterio configurado pelo tenant
 * ({@code SignificanceCriteria} da ED-70) aplicado aspecto a aspecto.
 * O tenant pode usar um template como base ou declarar aspecto proprio
 * fora do catalogo (o registro guarda o codigo do template quando
 * houver, e {@code null} no aspecto livre).</p>
 *
 * <p>Os dois templates de LODO ({@code sludgeLinked=true}) nao
 * registram geracao nem destinacao: sao o ponto de leitura do aspecto
 * cuja geracao/destinacao mora na ED-105 (JOR-16, OPE-25) — ver
 * {@code SludgeAspectView} no pacote {@code env14001}. Nenhum outro
 * template e ligado a registro externo.</p>
 */
public final class SanitationAspectCatalog {

    /** Area operacional do aspecto, nas tres areas do aceite (ETA, ETE, rede) e livre. */
    public enum AspectArea {
        /** Estacao de tratamento de agua (ETA). */
        WTP,
        /** Estacao de tratamento de esgoto (ETE). */
        WWTP,
        /** Rede (distribuicao de agua / coleta de esgoto). */
        NETWORK
    }

    /**
     * Um modelo de aspecto do catalogo de fabrica: codigo estavel,
     * area, rotulo do aspecto e rotulo do impacto potencial, ambos em
     * palavras proprias, e a marca de ligacao com o lodo da ED-105.
     */
    public record AspectTemplate(String code, AspectArea area, String aspectLabel,
                                 String impactLabel, boolean sludgeLinked) {
        public AspectTemplate {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code do template do catalogo e obrigatorio");
            }
            if (area == null) {
                throw new IllegalArgumentException("area do template " + code + " e obrigatoria");
            }
            if (aspectLabel == null || aspectLabel.isBlank()) {
                throw new IllegalArgumentException("aspectLabel do template " + code + " e obrigatorio");
            }
            if (impactLabel == null || impactLabel.isBlank()) {
                throw new IllegalArgumentException("impactLabel do template " + code + " e obrigatorio");
            }
            code = code.trim();
            aspectLabel = aspectLabel.trim();
            impactLabel = impactLabel.trim();
        }
    }

    /** Templates de fabrica (aceite da ED-70). Lista fechada e imutavel. */
    public static final List<AspectTemplate> TEMPLATES = List.of(
            new AspectTemplate("WTP-CHEMICAL-HANDLING", AspectArea.WTP,
                    "Armazenamento e dosagem de produtos quimicos do tratamento de agua",
                    "Vazamento ou descarte inadequado pode contaminar solo e agua", false),
            new AspectTemplate("WTP-SLUDGE", AspectArea.WTP,
                    "Geracao de lodo da ETA",
                    "Destinacao inadequada do lodo pode contaminar solo e agua", true),
            new AspectTemplate("WWTP-EFFLUENT-DISCHARGE", AspectArea.WWTP,
                    "Lancamento do efluente tratado da ETE",
                    "Efluente fora do padrao pode degradar o corpo receptor", false),
            new AspectTemplate("WWTP-SLUDGE", AspectArea.WWTP,
                    "Geracao de lodo da ETE",
                    "Destinacao inadequada do lodo pode contaminar solo e agua", true),
            new AspectTemplate("NETWORK-WATER-LOSS", AspectArea.NETWORK,
                    "Perdas de agua na rede de distribuicao",
                    "Desperdicio de recurso hidrico e de energia de bombeamento", false),
            new AspectTemplate("NETWORK-EXCAVATION-WASTE", AspectArea.NETWORK,
                    "Residuos de escavacao e reparo da rede",
                    "Disposicao inadequada pode contaminar solo e obstruir drenagem", false));

    private SanitationAspectCatalog() {
    }

    /** Todos os templates de fabrica. */
    public static List<AspectTemplate> all() {
        return TEMPLATES;
    }

    /** Templates de uma area (ETA, ETE ou rede). */
    public static List<AspectTemplate> byArea(AspectArea area) {
        if (area == null) throw new IllegalArgumentException("area e obrigatoria");
        return TEMPLATES.stream().filter(t -> t.area() == area).toList();
    }

    /** Template pelo codigo estavel, quando existir no catalogo. */
    public static Optional<AspectTemplate> byCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return TEMPLATES.stream().filter(t -> t.code().equals(code.trim())).findFirst();
    }

    /** Templates ligados ao lodo (leitura da ED-105, sem registro proprio). */
    public static List<AspectTemplate> sludgeTemplates() {
        return TEMPLATES.stream().filter(AspectTemplate::sludgeLinked).toList();
    }
}
