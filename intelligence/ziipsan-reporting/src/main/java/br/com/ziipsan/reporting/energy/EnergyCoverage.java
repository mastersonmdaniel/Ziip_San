package br.com.ziipsan.reporting.energy;

/**
 * Declaracao de fronteiras da ED-90 (mesmo papel do FleetCoverage da
 * ED-59): o que esta tarefa NAO faz, para a integracao nao procurar
 * aqui o que pertence a outra dona.
 * <ul>
 *   <li><b>Leiaute de fatura:</b> nenhum leiaute de distribuidora e
 *       implementado — conferido na implantacao, atras de
 *       {@link EnergyPorts.EnergyBillFilePort} e de
 *       {@link DistributorLayout} verificado [SEM FONTE];</li>
 *   <li><b>Fatores de emissao:</b> o catalogo global nasce VAZIO —
 *       a carga com as fontes citadas e da integracao (P-ED90-02);</li>
 *   <li><b>Registro de indicadores:</b> e o da P3.41 (JOR-19), sem
 *       registro proprio; catalogo/painel e o da ED-48, que le o
 *       registro — a ED-90 so materializa e marca o perfil;</li>
 *   <li><b>Fichas ISO/NR 9:</b> sao da ED-77; a ED-90 substitui a
 *       FONTE (manual -> automatica) e o grau, nao a ficha;</li>
 *   <li><b>Combustivel:</b> e lido da ED-59 (registros dela), sem
 *       cadastro de abastecimento proprio; o ramo manual do aceite
 *       permanece como contingencia declarada;</li>
 *   <li><b>Volume produzido/bombeado:</b> e dado da medicao/operacao
 *       (variavel do registro); a ED-90 nao mede volume;</li>
 *   <li><b>Multa:</b> a ED-90 ALERTA o risco; nao calcula valor de
 *       multa (nenhuma tarifa consta das fontes) nem cobra nada —
 *       cobranca e do billing;</li>
 *   <li><b>Relatorio de sustentabilidade:</b> exportacao voluntaria
 *       por perfil declarado; nenhuma obrigacao foi identificada para
 *       autarquia municipal e nenhum framework de relato externo foi
 *       presumido.</li>
 * </ul>
 */
public final class EnergyCoverage {

    private EnergyCoverage() {
    }
}
