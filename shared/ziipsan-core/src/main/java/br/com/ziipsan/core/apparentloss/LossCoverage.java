package br.com.ziipsan.core.apparentloss;

import java.util.List;

/**
 * Registro de cobertura da ED-95 (findings proprios CAD-103, GES-25
 * e OPE-37; ADR-0034 e ADR-0042) e das fronteiras respeitadas.
 * Declarativo: nenhum comportamento nasce daqui.
 */
public final class LossCoverage {

    private LossCoverage() {
    }

    public record Entry(String reference, String delivered, String boundary) {
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry("CAD-103",
                        "cadastro imobiliario municipal importado com inscricao, area, uso, habite-se e alvara; base legal e finalidade obrigatorias no record; sincronizacao pelo modo declarado em registry.property_registry_sync",
                        "leiaute concreto do conector municipal e dominio fechado do modo de sincronizacao: [A DEFINIR] na integracao, nenhum campo/texto legal inventado ([SEM FONTE] para texto legal especifico)"),
                new Entry("OPE-37",
                        "cruzamento edificacoes/lotes x unidades e ramal com as 4 divergencias do aceite, fila de revisao com prazo de audit.unregistered_building_review_days e acoes sugeridas vistoria/fiscalizacao/recadastramento",
                        "restrito ao backoffice (ADR-0034 §12) com recusa provada dos demais canais; tolerancia de area sem chave nas fontes chega explicita do chamador, nunca presumida"),
                new Entry("GES-25",
                        "biblioteca com as 8 regras do aceite e limiares por tenant em fraud.rules sem valor de fabrica; telemetria da ED-17 consumida quando presente; relatorio de acerto com taxa de confirmacao por regra",
                        "saida e recomendacao a humano (ADR-0042): nenhum debito, corte ou infracao nasce desta entrega; auto de infracao e da ED-24 com infraction.charge_timing"),
                new Entry("ADR-0042 / ML",
                        "portao MlGate: modelo so depois das regras medidas e com decisao ADR-0042 referenciada",
                        "nenhum modelo de ML implementado nem habilitado nesta entrega"),
                new Entry("ED-24",
                        "suspeita/achado decidido por humano abre OS de fiscalizacao pelo WorkOrderPort",
                        "auto de infracao, defesa, decisao e debito sao da ED-24 — nao reimplementados"),
                new Entry("ED-14",
                        "fatos de troca de hidrometro e de dimensionamento consumidos no retrato UnitFacts",
                        "hidrometria (troca, dimensionamento, bancada) e da ED-14 — nao reimplementada"),
                new Entry("ED-17",
                        "fluxo de telemetria do periodo consumido quando disponivel (regra do medidor parado)",
                        "ingestao/VEE/alarmes de telemetria sao da ED-17 — nao reimplementados"),
                new Entry("ED-62",
                        "fonte do cadastro municipal como porta sobre o hub de integracoes",
                        "conectores ERP da ED-62 intocados; conector municipal concreto e fiacao da integracao"));
    }
}
