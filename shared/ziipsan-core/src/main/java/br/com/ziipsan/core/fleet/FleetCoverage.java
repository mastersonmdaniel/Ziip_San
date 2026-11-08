package br.com.ziipsan.core.fleet;

import java.util.Map;

/**
 * Registro de cobertura da ED-59: codigo de achado/origem -> componente
 * desta entrega (mesmo padrao de registro da ED-56, {@code AssetCoverage}).
 * Registrar nao afirma ensaio externo: nenhum item aqui e declarado
 * ensaiado fora dos testes unitarios nomeados no relatorio.
 */
public final class FleetCoverage {

    private FleetCoverage() {
    }

    public static final Map<String, String> COVERAGE = Map.of(
            "OPE-28", "ED-59 inteira: frota sobre a P1.35, manutencao pela rotina da ED-56 estendida, "
                    + "equipes na programacao e no custo da OS (ADR-0028 item i)",
            "OPE-33", "restricao dura de equipamento especial no motor da ED-36: o FATO "
                    + "equipmentAvailable e desta tarefa; o filtro EQUIPMENT_WHEN_ED59 e da ED-36",
            "OPE-14", "FRONTEIRA: habilitacao e seguranca do trabalho do executor seguem a OS da "
                    + "P1.31 (portao da ED-36) — nao reimplementado; esta tarefa cobre a CNH do motorista",
            "ADR-0028 (i)", "origem da tarefa ED-59 (frota e equipes de campo)",
            "COE-05", "consumido da P1.35: o veiculo e registro de frota SOBRE o ativo; a tabela "
                    + "asset nao e recriada e o codigo patrimonial so existe nela (DAD-26)");

    public static Map<String, String> coverage() {
        return COVERAGE;
    }
}
