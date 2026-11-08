package br.com.ziipsan.core.backoffice;

import java.util.List;
import java.util.UUID;

/**
 * Portao da sugestao com custo de deslocamento sobre o grafo viario
 * (pgRouting) — ED-36. Estado nas fontes conferidas nesta frente:
 *
 * <ul>
 *   <li>ADR-0010 (infraestrutura congelada), ajuste do dono de 29/10/2026:
 *       "pgRouting para roteirizacao de equipes so com ADR proprio antes do
 *       uso (ED-36)";</li>
 *   <li>a excecao do pgRouting ja registrada no ADR-0010/ADR-0034 cobre
 *       validacao de componentes conexos da rede e roteirizacao da COLETA
 *       de residuos sobre logradouros — nao cobre roteirizacao de EQUIPES
 *       de OS;</li>
 *   <li>nenhum ADR de liberacao da roteirizacao de equipes foi localizado
 *       nos fontes do repo nem nos pacotes locais.</li>
 * </ul>
 *
 * Conclusao honesta: o solver de rotas NAO e implementado nesta entrega.
 * Este portao recusa qualquer pedido de sugestao com tempo de viagem ate
 * que exista (a) o ADR proprio aceito e (b) um solver de codigo aberto com
 * licenca conferida ligado por porta — nenhum servico pago de terceiro no
 * caminho critico, como manda o acceptance. A sugestao disponivel hoje e a
 * do {@link SchedulePlanner} (sem deslocamento, explicavel, confirmada por
 * pessoa).
 */
public final class RoutingGate {

    /** Porta do solver — nenhum solver e ligado nesta entrega (ver classe). */
    public interface TravelTimeSolver {
        List<Long> travelMinutes(List<UUID> orderIdsInSequence);
    }

    public record AdrStatus(boolean teamRoutingAdrAccepted, String adrIdOrNull) {}

    private RoutingGate() {}

    public static TravelTimeSolver requireSolver(AdrStatus adr, TravelTimeSolver solverOrNull) {
        if (adr == null || !adr.teamRoutingAdrAccepted())
            throw new IllegalStateException("sugestao com custo de deslocamento BLOQUEADA: falta o ADR proprio que "
                    + "libera o pgRouting para roteirizacao de equipes (ADR-0010; cartao do dono de 29/10/2026). "
                    + "A excecao vigente (ADR-0034) cobre rede e coleta de residuos, nao equipes de OS.");
        if (adr.adrIdOrNull() == null || adr.adrIdOrNull().isBlank())
            throw new IllegalStateException("ADR de liberacao aceito sem identificador registrado nao libera o solver");
        if (solverOrNull == null)
            throw new IllegalStateException("ADR " + adr.adrIdOrNull() + " aceito, mas nenhum solver de codigo aberto "
                    + "com licenca conferida esta ligado (fiacao pendente declarada)");
        return solverOrNull;
    }
}
