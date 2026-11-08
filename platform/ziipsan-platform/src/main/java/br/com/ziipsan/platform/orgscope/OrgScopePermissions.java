package br.com.ziipsan.platform.orgscope;

import java.util.Set;

/**
 * As permissões NOMEADAS pela P3.30 no catálogo {@code recurso:ação} da
 * P0.13 (DAD-31). Os nomes são literais do aceite — não há sinônimo nem
 * forma pontuada:
 * <ul>
 *   <li>{@code protocol:transfer-any-unit} — tramitar protocolo para
 *       QUALQUER unidade organizacional, sem precisar de escopo nas duas
 *       pontas (ver ProtocolTransferRule). Sem ela, o trâmite exige escopo
 *       do ator cobrindo origem E destino;</li>
 *   <li>{@code so:close-retroactive} — encerrar OS com data retroativa;</li>
 *   <li>{@code so:close-without-activities} — encerrar OS sem atividades
 *       registradas.</li>
 * </ul>
 *
 * <p>As três entram no {@code PermissionCatalog} (módulo ziipsan-security)
 * nesta mesma entrega, como CONCESSÃO DIRETA (exceções de cobertura, pelo
 * precedente de {@code registry:reveal-document}): são permissões de
 * exceção — incluí-las num perfil de fábrica daria o desvio a todos os
 * portadores do perfil. As duas de encerramento são apenas CATALOGADAS
 * aqui: quem as impõe é o motor de OS da P1.31 no fechamento (a fiação no
 * {@code ServiceOrderService} é pendência de integração declarada — esta
 * tarefa NÃO cria um segundo caminho de encerramento de OS).
 */
public final class OrgScopePermissions {

    public static final String PROTOCOL_TRANSFER_ANY_UNIT = "protocol:transfer-any-unit";
    public static final String SO_CLOSE_RETROACTIVE = "so:close-retroactive";
    public static final String SO_CLOSE_WITHOUT_ACTIVITIES = "so:close-without-activities";

    public static final Set<String> ALL = Set.of(
            PROTOCOL_TRANSFER_ANY_UNIT, SO_CLOSE_RETROACTIVE, SO_CLOSE_WITHOUT_ACTIVITIES);

    private OrgScopePermissions() {
    }
}
