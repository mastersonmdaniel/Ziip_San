package br.com.ziipsan.operations.metering.critique;

/** Metodo de estimacao VEE declarado (II-2, E4). O metodo e escolha explicita do chamador/tenant. */
public enum EstimationMethod {
    /** Media aritmetica do historico, arredondada HALF_UP a inteiro. */
    HISTORICAL_MEAN,
    /** Mediana do historico (media dos dois centrais quando n e par), arredondada HALF_UP. */
    HISTORICAL_MEDIAN,
    /** Ultimo consumo historico valido (o mais recente). */
    LAST_VALID
}
