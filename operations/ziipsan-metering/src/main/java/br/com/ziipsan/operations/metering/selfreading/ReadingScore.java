package br.com.ziipsan.operations.metering.selfreading;

import java.math.BigDecimal;

/**
 * O score calculado de uma autoleitura (II-7): o total em [0, 100] com
 * 2 casas (HALF_UP) e os 4 componentes em [0, 1] que o compoeem, para o
 * desfecho ser explicavel componente a componente -- nenhum total opaco.
 */
public record ReadingScore(BigDecimal total, BigDecimal ocrComponent,
                           BigDecimal coherenceComponent, BigDecimal photoComponent,
                           BigDecimal geoComponent) {
}
