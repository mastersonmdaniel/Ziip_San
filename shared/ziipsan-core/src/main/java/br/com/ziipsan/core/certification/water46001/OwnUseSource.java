package br.com.ziipsan.core.certification.water46001;

import java.util.Objects;
import java.util.UUID;

/**
 * Fonte do uso proprio: o balanco hidrico da ED-85 (OPE-05), SEM
 * RECALCULO (fronteira expressa do aceite da ED-81).
 *
 * <p><b>VERDADE DA FONTE (correcao D1 da revisao da ED-81):</b> o
 * snapshot {@code loss_monthly_balances} da ED-85 NAO tem o split
 * operacional/emergencial. Ele guarda {@code exported_m3} e um
 * {@code authorized_unbilled_m3} UNICO, agregado — que INCLUI o uso
 * social (categoria de carro-pipa social da ED-85). Ler operacional/emergencial "do
 * snapshot" e, portanto, impossivel: um adapter que lesse so o
 * snapshot traria o social junto, violando o aceite. O split so
 * existe nos registros de detalhe
 * {@code loss_authorized_unbilled_volumes} da ED-85, por categoria
 * ({@code AuthorizedUnbilledVolume.Category}).
 *
 * <p><b>CONTRATO DO ADAPTER (pendencia P-ED81-01, especificado nesta
 * correcao — documental/contratual, SEM implementacao aqui):</b>
 * quem monta esta fonte, para um DMC e um mes, decompoe assim:
 * <ul>
 *   <li><b>operacional</b> = soma dos volumes de
 *       {@code loss_authorized_unbilled_volumes} nas categorias
 *       operacionais {@code NETWORK_FLUSHING} (descarga de rede) e
 *       {@code RESERVOIR_CLEANING} (limpeza de reservatorio),
 *       agrupados por DMC ({@code dmc_profile_id}) e pelo mes de
 *       {@code occurred_on};</li>
 *   <li><b>emergencial</b> = soma dos volumes de
 *       {@code loss_authorized_unbilled_volumes} na categoria
 *       {@code FIREFIGHTING} (combate a incendio), pelo mesmo
 *       agrupamento DMC/mes de {@code occurred_on};</li>
 *   <li><b>social EXCLUIDO</b>: a categoria social (carro-pipa
 *       social) nao entra em componente alguma — o aceite da ED-81
 *       nomeia operacionais, emergenciais e exportada, e so eles;</li>
 *   <li><b>exportada</b> = {@code exported_m3} do snapshot
 *       {@code loss_monthly_balances} do mesmo DMC/periodo (esta,
 *       sim, le-se do snapshot).</li>
 * </ul>
 * (Os nomes literais das categorias da ED-85, inclusive a social
 * excluida, estao grafados na especificacao da P-ED81-01 no doc
 * do pacote e no cabecalho do SQL desta entrega.)
 *
 * Essa decomposicao por categoria NAO e recalculo do balanco: nao
 * reabre entradas, perdas, NRW nem ILI da ED-85 — apenas separa,
 * por categoria declarada, registros que a ED-85 ja apurou.
 *
 * <p>NENHUMA componente e recalculada NESTA classe: os tres volumes
 * chegam prontos, ja decompostos pelo adapter conforme o contrato
 * acima (referencia {@code balanceRef} ao snapshot
 * {@code loss_monthly_balances} da ED-85) e sao carregados verbatim.
 * O {@code totalM3} e a mera soma aritmetica dos tres componentes
 * declarados — nao e um novo balanco. Componente nao declarada
 * (null) RECUSA: uso proprio desconhecido nao vira zero. Valores
 * nao finitos (infinito) tambem recusam (correcao D5).
 *
 * <p><b>Ligacao DMC x instalacao (correcao D4):</b> esta fonte sabe
 * o DMC ({@code dmcProfileRef}) e o ano/mes, mas NADA na ED-85 nem
 * neste pacote liga um DMC a instalacao do escopo de ativacao — a
 * ligacao DMC→instalacao e a pendencia nomeada <b>P-ED81-05</b>.
 * Ate ela ser resolvida na fiacao, o modo de falha e: uma fonte de
 * um DMC que nao atende a instalacao pode ser anexada a revisao
 * dela sem que este pacote detecte (a amarra periodo x fonte, essa
 * sim, e travada em {@link WaterUseReview}).
 */
public record OwnUseSource(UUID balanceRef, UUID dmcProfileRef, int periodYear, int periodMonth,
                           Double operationalM3, Double emergencyM3, Double exportedM3) {

    public OwnUseSource {
        Objects.requireNonNull(balanceRef, "referencia ao snapshot do balanco da ED-85 obrigatoria "
                + "(o uso proprio le o balanco, nao o recalcula)");
        Objects.requireNonNull(dmcProfileRef, "DMC do balanco da ED-85 obrigatorio");
        if (periodYear < 1900 || periodYear > 2100)
            throw new IllegalArgumentException("ano do periodo do balanco fora da faixa "
                    + "saneadora 1900..2100 (correcao D5): " + periodYear);
        if (periodMonth < 1 || periodMonth > 12)
            throw new IllegalArgumentException("mes do periodo do balanco invalido: " + periodMonth);
        requireDeclared(operationalM3, "usos operacionais do balanco da ED-85 (OPE-05)");
        requireDeclared(emergencyM3, "usos emergenciais do balanco da ED-85 (OPE-05)");
        requireDeclared(exportedM3, "agua exportada do balanco da ED-85 (OPE-05)");
    }

    private static void requireDeclared(Double v, String what) {
        if (v == null) throw new IllegalArgumentException(what
                + " nao declarado no balanco: componente ausente recusa, nunca vira zero");
        if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException(what + " invalido: " + v);
    }

    /** Soma dos tres componentes DECLARADOS no balanco — sem recalcular componente alguma. */
    public double totalM3() {
        return operationalM3 + emergencyM3 + exportedM3;
    }
}
