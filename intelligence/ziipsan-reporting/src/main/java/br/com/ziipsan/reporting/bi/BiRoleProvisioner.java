package br.com.ziipsan.reporting.bi;

import java.util.List;

/**
 * ED-47: porta que executa o SQL de {@link BiRoleProvisioningSql} na
 * RÉPLICA de leitura (a implementação JDBC da fiação recebe o
 * {@code DataSource} da réplica — o mesmo {@code readReplicaDataSource}
 * da P3.22 — e roda como role administrativa do banco, fora do caminho
 * de consulta do Superset). Ligar o LOGIN da role com a senha do cofre é
 * passo separado desta porta, para a senha nunca transitar pelo SQL
 * gerado nem por esta interface em texto.
 */
public interface BiRoleProvisioner {

    void provisionReplicaObjects();

    void provision(TenantBiRole mapping, List<String> reportingViews);

    void deprovision(TenantBiRole mapping, List<String> reportingViews);
}
