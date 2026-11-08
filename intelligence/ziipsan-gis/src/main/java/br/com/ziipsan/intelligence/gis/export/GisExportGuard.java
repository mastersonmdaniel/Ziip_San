package br.com.ziipsan.intelligence.gis.export;

import java.util.List;

/**
 * Guardas de publicação da ED-67 (§12 do GIS-CADASTRO-TECNICO),
 * aplicadas IGUAIS na exportação (GIS-16) e no OGC (GIS-17):
 * sem acesso anônimo (a autenticação é do controller/contexto);
 * camada pessoal no backoffice só com {@code gis.view-personal} e
 * com o registro no {@code personal_data_access_log} (item 5);
 * fora do backoffice (credencial de terceiro ou público), imóvel e
 * ligação NUNCA saem individuais — só agregados por quadra/setor com
 * pelo menos {@code gis.public_aggregation_min_units} imóveis por
 * célula, piso 5 da classe c que "só amplia" (item 3); marcador
 * sensível nunca sai em exportação aberta (item 4); infraestrutura
 * crítica não sai para audiência externa sem controle de acesso
 * (item 7).
 */
public final class GisExportGuard {

    public enum Audience { BACKOFFICE, THIRD_PARTY, PUBLIC }

    /** Agregado publicável de camada pessoal: célula (quadra/setor) e contagem. */
    public record PersonalAggregate(String cellKey, long unitCount) {}

    public static final int AGGREGATION_FLOOR = 5;

    private GisExportGuard(){}

    /** Piso efetivo: o parâmetro do tenant só pode AMPLIAR o piso 5. */
    public static int effectiveFloor(int tenantParam){
        return Math.max(AGGREGATION_FLOOR, tenantParam);
    }

    public static void checkIndividualExport(Audience audience, List<GisExportFeature> features,
                                             boolean canViewPersonal, boolean personalAccessRegistered){
        for (GisExportFeature f : features) {
            if (f.sensitiveMarker())
                throw new IllegalArgumentException(
                    "feição com marcador sensível nunca sai em exportação (§12 item 4): " + f.id());
            if (audience != Audience.BACKOFFICE && f.criticalInfrastructure())
                throw new IllegalArgumentException(
                    "infraestrutura crítica não sai para audiência externa sem controle de acesso (§12 item 7): " + f.id());
            if (f.personal()) {
                if (audience != Audience.BACKOFFICE)
                    throw new IllegalArgumentException(
                        "camada pessoal fora do backoffice só sai agregada por quadra/setor (§12 item 3): " + f.id());
                if (!canViewPersonal)
                    throw new IllegalArgumentException(
                        "camada pessoal exige gis.view-personal (§12 item 2): " + f.id());
                if (!personalAccessRegistered)
                    throw new IllegalArgumentException(
                        "exportação com camada pessoal exige registro no personal_data_access_log (§12 item 5): " + f.id());
            }
        }
    }

    /** Só os agregados que atingem o piso são publicáveis; célula abaixo do piso é SUPRIMIDA, nunca arredondada. */
    public static List<PersonalAggregate> publishableAggregates(List<PersonalAggregate> aggregates, int tenantParam){
        int floor = effectiveFloor(tenantParam);
        return aggregates.stream().filter(a -> a.unitCount() >= floor).toList();
    }
}
