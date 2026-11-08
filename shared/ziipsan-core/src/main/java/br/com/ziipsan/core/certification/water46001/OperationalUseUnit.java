package br.com.ziipsan.core.certification.water46001;

import java.util.Objects;
import java.util.UUID;

/**
 * Unidade de uso operacional (ED-81): a unidade consumidora do
 * cadastro da P1.3 ({@code consumer_units}, V3_1/V3_6) que o
 * prestador usa para a PROPRIA operacao — sede, ETA, ETE,
 * elevatoria, laboratorio — cadastrada SOBRE esse cadastro (nenhum
 * cadastro paralelo de unidade nasce aqui) e MEDIDA pela
 * hidrometria da ED-14: a unidade so e de uso proprio mensuravel
 * com hidrometro instalado nela (instalacao da ED-14, que liga
 * hidrometro, unidade e ligacao).
 *
 * <p>{@code purposeCode} e codigo do catalogo do tenant (finalidade
 * operacional declarada — nenhuma lista de finalidades e presumida
 * aqui). A natureza espelha a fonte do aceite: OPERATIONAL ou
 * EMERGENCY. A existencia da unidade no cadastro e a instalacao do
 * hidrometro sao conferidas pelo servico ({@link OwnUseService})
 * via portas — fail-closed.
 */
public record OperationalUseUnit(Iso46001Activation activation, UUID consumerUnitId,
                                 String installationRef, UUID meterId,
                                 UseNature nature, String purposeCode, boolean active) {

    public enum UseNature { OPERATIONAL, EMERGENCY }

    public OperationalUseUnit {
        Objects.requireNonNull(activation, "ativacao da ISO 46001 obrigatoria");
        activation.requireEnabled();
        activation.requireInstallationInScope(installationRef);
        Objects.requireNonNull(consumerUnitId,
                "unidade consumidora da P1.3 obrigatoria (cadastro sobre consumer_units)");
        Objects.requireNonNull(meterId,
                "hidrometro da ED-14 obrigatorio: unidade de uso proprio e medida pela hidrometria");
        Objects.requireNonNull(nature, "natureza do uso (OPERATIONAL/EMERGENCY) obrigatoria");
        if (purposeCode == null || purposeCode.isBlank())
            throw new IllegalArgumentException(
                    "purposeCode (finalidade operacional do catalogo do tenant) obrigatorio");
        purposeCode = purposeCode.trim();
        installationRef = installationRef.trim();
    }
}
