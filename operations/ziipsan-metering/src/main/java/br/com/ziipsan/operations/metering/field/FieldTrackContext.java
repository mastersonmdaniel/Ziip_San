package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.Tracking;

import java.time.Duration;
import java.util.Objects;

/**
 * Contexto da trilha GPS de UM trabalhador, montado pela integracao a partir
 * das fontes vigentes do tenant (escala, registo do aviso ao empregado,
 * parametros {@code field.*} da ED-20 e limiares do resumo do painel).
 *
 * <p>NENHUM valor aqui tem origem nesta classe: a jornada, o aviso, a trava
 * {@code track_only_in_shift}, os limiares de parada (raio/duracao) e a
 * retencao sao ENTRADA EXPLICITA de quem monta o contexto — a regra nao
 * presume fonte nem valor (invariante da frente: nenhum limiar ou valor por
 * omissao inventado). A fonte viva destes dados (escala real, registo real
 * do aviso) e a pendencia P-MOB-GPS-02; ate la, quem fornece o contexto e a
 * integracao/teste que o declara.
 *
 * @param shift                        janela da jornada vigente do trabalhador
 * @param employeeNoticeAcknowledged   se o aviso de rastreamento ao empregado
 *                                     esta registado (sem ele, a trilha nao
 *                                     comeca — {@link Tracking#ingest})
 * @param trackOnlyInShiftConfigured   valor configurado de
 *                                     {@code field.track_only_in_shift}; a
 *                                     trava em si e aplicada pelo nucleo
 *                                     ({@code FieldOsParameters}), nao aqui
 * @param stopRadiusMeters             limiar de parada do resumo do painel:
 *                                     raio em metros (consumido por
 *                                     {@link Tracking#summarize})
 * @param stopMinDuration              limiar de parada do resumo do painel:
 *                                     duracao minima da parada
 * @param trackRetentionDays           retencao configurada da trilha
 *                                     ({@code field.track_retention_days});
 *                                     transportada para a integracao
 *                                     (o expurgo por {@code Tracking#purgeCutoff}
 *                                     e operacional — ver relatorio)
 */
public record FieldTrackContext(Tracking.Shift shift,
                                boolean employeeNoticeAcknowledged,
                                boolean trackOnlyInShiftConfigured,
                                double stopRadiusMeters,
                                Duration stopMinDuration,
                                int trackRetentionDays) {

    public FieldTrackContext {
        Objects.requireNonNull(shift, "jornada vigente do trabalhador e obrigatoria");
        Objects.requireNonNull(stopMinDuration, "limiar de duracao de parada e obrigatorio");
        if (stopRadiusMeters < 0) {
            throw new IllegalArgumentException("raio de parada nao pode ser negativo");
        }
        if (trackRetentionDays <= 0) {
            throw new IllegalArgumentException("retencao da trilha deve ser positiva");
        }
    }
}
