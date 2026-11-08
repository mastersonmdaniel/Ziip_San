package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro do cadastro imobiliario municipal (CAD-103, aceite da
 * ED-95): inscricao imobiliaria, area construida, uso, habite-se e
 * alvara, importado pelo conector de cadastro (ver
 * {@code LossPorts.PropertyRegistrySource} — porta sobre o mesmo hub
 * de integracoes da ED-62/P3.37; o leiaute concreto do conector
 * municipal e fiacao da integracao, nenhum campo de leiaute e
 * inventado aqui).
 *
 * <p><b>Base legal e finalidade sao OBRIGATORIAS.</b> O aceite exige
 * "base legal e finalidade do compartilhamento dentro do mesmo ente
 * registradas": registro sem um dos dois textos e RECUSADO na
 * construcao, nunca gravado parcial. Os textos sao os DECLARADOS
 * pelo ente/tenant no ato do compartilhamento — esta entrega nao
 * cita artigo nem lei especifica: nenhuma fonte desta frente fixa o
 * texto legal do compartilhamento, logo ele chega declarado e fica
 * registrado verbatim ([SEM FONTE] para texto legal especifico).
 *
 * <p>[A DEFINIR] {@link PropertyUse} e o vocabulario de uso/categoria
 * desta entrega para o cruzamento uso x categoria (OPE-37); o
 * mapeamento dos codigos de uso do cadastro municipal de cada ente
 * para este vocabulario e declaracao da integracao, nao presuncao
 * desta classe.
 */
public record PropertyRegistryRecord(
        UUID id, UUID tenantId, String municipalRegistration,
        BigDecimal builtAreaSquareMeters, PropertyUse declaredUse,
        String habiteSeReference, String buildingPermitReference,
        String legalBasis, String sharingPurpose,
        RegistrySource source, String sourceBatchReference,
        LocalDateTime importedAt) {

    /** [A DEFINIR — vocabulario do cruzamento] Uso do imovel / categoria da unidade. */
    public enum PropertyUse {
        RESIDENTIAL, COMMERCIAL, INDUSTRIAL, PUBLIC, MIXED, RURAL, OTHER
    }

    /** Origem do registro: conector municipal ou importacao de GIS (aceite, OPE-37). */
    public enum RegistrySource {
        MUNICIPAL_CONNECTOR, GIS_IMPORT
    }

    public PropertyRegistryRecord {
        Objects.requireNonNull(id, "id do registro imobiliario e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (municipalRegistration == null || municipalRegistration.isBlank())
            throw new IllegalArgumentException("inscricao imobiliaria municipal e obrigatoria (CAD-103)");
        Objects.requireNonNull(builtAreaSquareMeters, "area construida e obrigatoria (CAD-103)");
        if (builtAreaSquareMeters.signum() < 0)
            throw new IllegalArgumentException("area construida nao pode ser negativa: " + builtAreaSquareMeters);
        Objects.requireNonNull(declaredUse, "uso declarado do imovel e obrigatorio (CAD-103)");
        if (legalBasis == null || legalBasis.isBlank())
            throw new IllegalArgumentException("base legal do compartilhamento e obrigatoria (CAD-103): "
                    + "sem ela o registro nao se importa — o texto e o declarado pelo ente, nao presumido");
        if (sharingPurpose == null || sharingPurpose.isBlank())
            throw new IllegalArgumentException("finalidade do compartilhamento e obrigatoria (CAD-103): "
                    + "sem ela o registro nao se importa — o texto e o declarado pelo ente, nao presumido");
        Objects.requireNonNull(source, "origem do registro e obrigatoria");
        if (sourceBatchReference == null || sourceBatchReference.isBlank())
            throw new IllegalArgumentException("referencia do lote/sincronizacao de origem e obrigatoria");
        Objects.requireNonNull(importedAt, "data/hora da importacao e obrigatoria");
    }

    /** Imovel edificado: area construida maior que zero. */
    public boolean built() {
        return builtAreaSquareMeters.signum() > 0;
    }
}
