package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.query.ReportDataSourceRouter;

/**
 * Regra da P4.3 incorporada pela ED-46 (acceptance): "nenhum relatorio
 * nem consulta de BI executado no banco primario", com teste.
 *
 * <p>Contraste declarado com a P3.22: o nucleo dela aceita o primario
 * com {@code statement_timeout} quando NAO ha replica (desenho para o
 * ambiente local do piloto). O catalogo COMPLETO da ED-46 e a superficie
 * de edital (VISAN/SAMAE): para os relatorios novos desta tarefa
 * (RPT-101..RPT-105) e para qualquer consulta de BI sobre as views
 * {@code rpt_*}, a guarda exige replica configurada e recusa o primario.
 * A fiacao aplica a guarda ANTES de abrir a consulta; o teste
 * {@code ReplicaOnlyGuardTest} prova a recusa e a passagem.
 */
public final class ReplicaOnlyGuard {

    private ReplicaOnlyGuard() {
    }

    /** Recusa quando nao ha replica de leitura configurada. */
    public static void requireReplicaConfigured(ReportDataSourceRouter router) {
        if (router == null || !router.replicaConfigured()) {
            throw new PrimaryAccessForbiddenException(
                    "Catalogo ED-46 exige replica de leitura configurada: "
                            + "nenhum relatorio do catalogo completo executa no banco primario (P4.3/ED-46).");
        }
    }

    /** Recusa uma conexao ja roteada quando a fonte sorteada foi o primario. */
    public static void assertReplica(ReportDataSourceRouter.RoutedConnection connection) {
        if (connection == null || connection.source() != ReportDataSourceRouter.Source.READ_REPLICA) {
            throw new PrimaryAccessForbiddenException(
                    "Execucao recusada: a fonte sorteada nao e a replica de leitura "
                            + "(nenhum relatorio do catalogo completo executa no banco primario - P4.3/ED-46).");
        }
    }

    /** Forma pura da mesma regra, para decisao antes de abrir conexao. */
    public static void assertSource(ReportDataSourceRouter.Source source) {
        if (source != ReportDataSourceRouter.Source.READ_REPLICA) {
            throw new PrimaryAccessForbiddenException(
                    "Fonte " + source + " (banco primario) recusada para o catalogo ED-46: somente READ_REPLICA.");
        }
    }
}
