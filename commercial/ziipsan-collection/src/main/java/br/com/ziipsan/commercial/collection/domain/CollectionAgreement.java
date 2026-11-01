package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.commercial.collection.barcode.AgreementBarcodeLayout;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Convênio do tenant com um banco para um tipo de documento. O layout do código de barras é
 * dado ({@code barcode_layout}), nunca uma subclasse por empresa.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "collection_agreements")
@Getter
@Setter
public class CollectionAgreement extends TenantAwareEntity {

    public enum DocumentKind {
        /** Conta de consumo com código de barras de arrecadação (produto 8). */
        COLLECTION,
        /** Boleto de cobrança bancária (ficha de compensação, código do banco). */
        BANK_SLIP
    }

    @Column(name = "bank_code", nullable = false, length = 3)
    private String bankCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "agreement_code", nullable = false, length = 20)
    private String agreementCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_kind", nullable = false, length = 20)
    private DocumentKind documentKind;

    /** JSON de {@code AgreementBarcodeLayout} (arrecadação) ou do layout do boleto. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "barcode_layout", nullable = false, columnDefinition = "jsonb")
    private String barcodeLayout;

    /** Versões de layout (A.09) aceitas no retorno, separadas por vírgula; nulo até o banco confirmar. */
    @Column(name = "accepted_return_versions", length = 50)
    private String acceptedReturnVersions;

    /** Último NSA de retorno aceito; nulo antes do primeiro arquivo. */
    @Column(name = "last_return_nsa")
    private Long lastReturnNsa;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    /** NSA que o próximo arquivo deve ter (o NSA evolui de 1 em 1); vazio se ainda não há arquivo. */
    public java.util.OptionalLong nextExpectedNsa() {
        return lastReturnNsa == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(lastReturnNsa + 1);
    }

    /** Versões de layout de retorno aceitas; conjunto vazio enquanto o banco não confirmar. */
    public Set<Integer> acceptedReturnVersionSet() {
        Set<Integer> out = new LinkedHashSet<>();
        if (acceptedReturnVersions != null && !acceptedReturnVersions.isBlank()) {
            for (String v : acceptedReturnVersions.split(",")) {
                out.add(Integer.valueOf(v.trim()));
            }
        }
        return out;
    }

    /** Layout de arrecadação do convênio; só vale para {@link DocumentKind#COLLECTION}. */
    public AgreementBarcodeLayout collectionLayout() {
        if (documentKind != DocumentKind.COLLECTION) {
            throw new IllegalStateException("O convênio " + agreementCode + " não é de arrecadação");
        }
        return AgreementBarcodeLayout.fromJson(barcodeLayout);
    }
}
