package br.com.ziipsan.grs.mtr.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52): classificação corrigida (E5).
// A NBR 10004:2004 classifica em I (perigoso), IIA (não perigoso, não inerte) e IIB (não perigoso,
// inerte) — o enum anterior {I, II, III} aceitava valores inexistentes na norma (defeito D-MTR-6 do
// levantamento regulatório). RSS (grupos A–E, RDC ANVISA 222/2018 / CONAMA 358/2005) e RCC (classes
// A–D, CONAMA 307/2002) são esquemas DISTINTOS do de periculosidade e entram pelo campo
// classification_scheme. A série NBR 10004-1/-2:2024 tem conteúdo NÃO CONFIRMADO no levantamento
// (pendência P-ED104-03): nenhuma classe nova é inventada; nbr_edition regista a edição declarada
// e a revisão 2024 nasce marcada como não confirmada (ver WasteClassification).
@Entity
@Table(name = "waste_class", indexes = {
        @Index(name = "idx_waste_class_code", columnList = "code"),
        @Index(name = "idx_waste_class_hazard", columnList = "hazard_class")
})
@Getter
@Setter
public class WasteClass extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code; // Código da Lista Brasileira (IN IBAMA 13/2012): 6 dígitos, ex. "190902"

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_class", nullable = false, length = 3)
    private HazardClass hazardClass; // I (perigoso), IIA, IIB — NBR 10004:2004

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_scheme", nullable = false, length = 20)
    private ClassificationScheme classificationScheme = ClassificationScheme.NBR_10004;

    @Column(name = "nbr_edition", length = 10)
    private String nbrEdition; // "2004" confirmada; "2024" = revisão NÃO CONFIRMADA (P-ED104-03)

    @Column(name = "physical_state", length = 1)
    private String physicalState; // S, L, P, G

    @Column(name = "sinir_code", length = 20)
    private String sinirCode;

    @Column(name = "mtr_code", length = 20)
    private String mtrCode;

    @Column(name = "default_destination_type", length = 50)
    private String defaultDestinationType;

    @Column(name = "requires_special_handling")
    private Boolean requiresSpecialHandling = false;

    @Column(name = "un_number", length = 10)
    private String unNumber; // ONU number for transport

    @Column(name = "packaging_group", length = 10)
    private String packagingGroup; // I, II, III

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    /** Classes de periculosidade da NBR 10004:2004. "III" não existe na norma (D-MTR-6). */
    public enum HazardClass {
        I, // Perigoso
        IIA, // Não perigoso, não inerte
        IIB; // Não perigoso, inerte
    }

    /** Esquemas de classificação distintos — nunca misturar com a periculosidade NBR. */
    public enum ClassificationScheme {
        NBR_10004, // periculosidade: HazardClass
        RSS, // resíduos de serviços de saúde: grupos A–E (código guarda o grupo)
        RCC; // resíduos da construção civil: classes A–D (código guarda a classe)
    }
}
