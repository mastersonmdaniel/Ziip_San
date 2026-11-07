package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.registry.application.ConnectionCycleFactsService.CombinedFacts;
import br.com.ziipsan.commercial.registry.application.ConnectionCycleFactsService.CycleFacts;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;

/**
 * Ponte fatos -> regra do ciclo (ED-13; correção A4 da revisão): a
 * ÚNICA conversão entre {@code ConnectionCycleFactsService} (cadastro)
 * e {@link ConnectionCycleRule} (billing). Antes dela, os dois lados
 * eram incompatíveis sem conversão entregue (volume BigDecimal x
 * Integer, enum de papel duplicado, um kind por chamada x água+esgoto
 * combinados, bifurcação como Boolean anulável e sewerOutOfUse sem
 * fonte).
 *
 * <p>Conversões, todas explícitas:
 * <ul>
 *   <li>papel: {@code ConnectionStatus.SystemRole} -> {@code
 *     ConnectionCycleRule.ConnectionRole} por NOME (os dois enums têm
 *     os mesmos valores; valor sem correspondente é erro explícito);</li>
 *   <li>volumes: {@code BigDecimal} dos dois lados — nenhuma conversão
 *     numérica acontece aqui; a redução a m³ inteiro é da regra, com
 *     erro explícito para fração;</li>
 *   <li>água + esgoto: vêm do {@code CombinedFacts} do serviço de
 *     fatos (duas leituras combinadas lá, não aqui);</li>
 *   <li>{@code sewerOutOfUse}: vem da fonte entregue no serviço de
 *     fatos (bifurcação {@code sewer.out_of_use_on_water_cut} +
 *     papéis das ligações), nunca de um Boolean solto do chamador;</li>
 *   <li>a bifurcação de supressão NÃO atravessa esta ponte: ela é
 *     decisão do efeito de OS (handler), não do ciclo.</li>
 * </ul>
 *
 * <p>PENDÊNCIA DE INTEGRAÇÃO declarada: este adaptador é o ponto onde
 * o billing passa a depender do registry em compilação
 * ({@code ziipsan-registry} no pom do billing, ou a realocação do
 * adaptador para o módulo de aplicação na fiação do {@code
 * CycleService}) — o mesmo tipo de passo que a P2.5 fez ao ganhar a
 * dependência de {@code ziipsan-security}. Sem esse passo, a ponte
 * existe e é testada, mas o ciclo em produção ainda não a chama
 * (pendência da fiação, ver RELATORIO-ED-13).
 */
public final class ConnectionCycleFactsAdapter {

    private ConnectionCycleFactsAdapter() {
    }

    public static ConnectionCycleRule.ConnectionFacts toRuleFacts(CombinedFacts combined) {
        if (combined == null || combined.water() == null || combined.sewer() == null) {
            throw new IllegalArgumentException(
                    "Fatos combinados (agua + esgoto) sao obrigatorios para o ciclo.");
        }
        CycleFacts water = combined.water();
        CycleFacts sewer = combined.sewer();
        return new ConnectionCycleRule.ConnectionFacts(
                toRole(water.systemRole()),
                water.bills(),
                water.minimumConsumption(),
                water.minimumBillingConsumption(),
                toRole(sewer.systemRole()),
                sewer.bills(),
                sewer.minimumBillingVolume(),
                sewer.hasAlternativeWaterSource(),
                sewer.alternativeSourceVolumeM3(),
                combined.sewerOutOfUse());
    }

    static ConnectionCycleRule.ConnectionRole toRole(ConnectionStatus.SystemRole role) {
        if (role == null) {
            return null;
        }
        try {
            return ConnectionCycleRule.ConnectionRole.valueOf(role.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Papel da ligacao sem correspondente na regra do ciclo: " + role);
        }
    }
}
