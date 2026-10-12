package br.com.sigsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * EPANET [OPTIONS] section: solver tolerances, time configuration and
 * reporting scope. Durations/timesteps are in seconds, matching EPANET.
 */
@Getter
@Setter
public class EpanetSimulationOptions {

    private int duration = 86400;
    private int hydraulicTimestep = 3600;
    private int qualityTimestep = 3600;
    private int patternTimestep = 3600;
    private int patternStart = 0;
    private int reportTimestep = 3600;
    private int reportStart = 0;
    private int startClockTime = 0;

    /**
     * NONE entrega a serie temporal completa. Com AVERAGE (escrito como AVERAGED) o EPANET grava so o
     * resumo estatistico do periodo e leitores de serie temporal (como o WNTR) nao conseguem ler o
     * resultado.
     */
    private EpanetSimulationService.Statistic statistic = EpanetSimulationService.Statistic.NONE;
    private EpanetSimulationService.DemandModel demandModel = EpanetSimulationService.DemandModel.DDA;
    private double emitterExponent = 0.5;
    private BigDecimal accuracy = new BigDecimal("0.001");
    private int maxTrials = 40;
    private int checkFrequency = 2;
    private BigDecimal dampingLimit = BigDecimal.ZERO;

    private int unbalancedMaxTrials = 100;
    private double unbalancedTolerance = 0.01;

    private EpanetSimulationService.LinkReport reportLinks = EpanetSimulationService.LinkReport.ALL;
    private EpanetSimulationService.NodeReport reportNodes = EpanetSimulationService.NodeReport.ALL;
}