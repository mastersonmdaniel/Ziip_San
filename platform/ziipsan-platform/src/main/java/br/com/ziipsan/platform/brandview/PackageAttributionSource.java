package br.com.ziipsan.platform.brandview;

import java.util.UUID;

/**
 * Porta da atribuição "operado com" (P3.21): o aceite manda exibi-la
 * "conforme o pacote contratado", mas **nenhum documento do plano v3.0
 * define quais pacotes a exibem nem um parâmetro para isso** (conferido:
 * tarefas P0–P3/GL/P4/LC/ED e o PLANO-v3.0.md; a palavra "pacote" no plano é
 * o pacote de vertente {@code sanitation} e os pacotes de normas das ED,
 * sem regra de atribuição). Inventar a regra — ou um nome de pacote —
 * violaria o portão "nenhum dado inventado".
 *
 * <p>Por isso a decisão entra por esta porta: a implementação de produção
 * a lê do pacote contratado do tenant (fonte que a integração ligar), e
 * esta camada só garante o mecanismo — decisão {@code true} põe a linha
 * {@link PortalBrandView#OPERATED_WITH_LINE} no rodapé do portal e do
 * e-mail; {@code false} a omite por completo (não é texto escondido: a
 * linha simplesmente não é gerada). Os testes exercitam os dois ramos.
 *
 * <p>PENDÊNCIA DECLARADA: a regra (quais pacotes mostram a atribuição)
 * precisa de definição do dono/produto; até lá, nenhuma implementação
 * desta porta deve presumir um padrão — sem fonte ligada, a fiação não
 * deve instanciar os serviços desta tarefa.
 */
@FunctionalInterface
public interface PackageAttributionSource {

    /** Verdade quando o pacote contratado do tenant exibe a atribuição "operado com". */
    boolean showOperatedWithAttribution(UUID tenantId);
}
