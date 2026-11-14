package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Fonte da tarifa media (moeda/m3) de um tenant para o ranking de
 * DMC por retorno ({@link DmcReturnRanking}) — P-II6-TARIFA, Ciclo 4
 * do programa 5x PDCA GIS.
 *
 * <p><b>Estado: [A DEFINIR] — nao existe adaptador nesta entrega,
 * deliberadamente.</b> A procura no codigo vivo (base cf5523df)
 * concluiu que NAO ha fonte real deste escalar:
 * <ul>
 *   <li>a arquitectura tarifaria do billing
 *   ({@code commercial/ziipsan-billing}, pacote {@code tariff/}) e
 *   progressiva por faixas de consumo e categorias — ex.:
 *   {@code TariffBlock(toM3, pricePerM3)} e preco por faixa, dentro
 *   de grupo/categoria/versao tarifaria — e nao produz nenhuma
 *   "tarifa media por m3 do tenant";</li>
 *   <li>nenhum parametro de tenant ({@code tariff.*} ou outro) guarda
 *   esse escalar, e nenhuma formula de reducao da estrutura
 *   progressiva a um escalar esta definida em codigo, migration ou
 *   decisao — deriva-lo exigiria inventar a formula (ponderar por
 *   que mix de consumo?), o que a regra do programa proibe;</li>
 *   <li>o modulo de perdas nao depende do billing no {@code pom.xml}
 *   (depende de core e quality): uma leitura directa criaria uma
 *   dependencia nova entre modulos fora do ambito deste ciclo.</li>
 * </ul>
 *
 * <p>Enquanto a fonte nao existir (definicao do escalar e decisao
 * de produto/dono — pendencia P-PDCAGIS4-03), quem chama usa
 * {@link DmcRankingTariff} sem porta ou com uma porta que devolve
 * vazio, e o ranking sai em {@code LOSS_VOLUME_ONLY} declarado.
 * Quando a fonte existir, o adaptador implementa esta porta e liga
 * sem tocar no motor.
 */
public interface DmcRankingTariffPort {

    /**
     * Tarifa media vigente do tenant, em moeda/m3, ou vazio quando o
     * tenant nao tem o escalar definido ({@code [A DEFINIR]}).
     * Implementacoes nao presumem valor: sem definicao, vazio.
     */
    Optional<BigDecimal> tariffPerM3(UUID tenantId);
}
