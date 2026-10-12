package br.com.sigsan.commercial.registry.application;

import br.com.sigsan.commercial.registry.domain.UnitCustomer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Imóvel (unidade consumidora) na API. A matrícula ({@code registration} + {@code registrationDv}) é
 * gerada pelo servidor, com dígito módulo 11 ({@code rules.Matricula}); no banco são {@code matricula}
 * e {@code dv}. Categoria principal e total de economias são derivados das subcategorias, nunca digitados.
 */
public final class ConsumerUnitDtos {

    private ConsumerUnitDtos() {
    }

    /** Partes da inscrição (códigos, sem zeros à esquerda). Nula para imóvel provisório sem quadra. */
    public record InscriptionView(int locality, int sector, int block, int lot, int sublot) {
    }

    public record AddressView(String cep, String streetName, String number, String complement, String neighborhood,
                              String city, String state) {
    }

    public record UnitSubcategoryView(UUID subcategoryId, Ref subcategory, Ref category, int economies) {
    }

    public record UnitView(UUID id, Long version, String registration, String registrationDv,
                           InscriptionView inscription, String inscriptionText, String status,
                           OffsetDateTime deletedAt, String deletedReason,
                           Ref locality, Ref sector, Ref block, Integer lot, Integer sublot,
                           Ref readingRoute, Integer readingSequence,
                           AddressView address, String addressText,
                           List<UnitSubcategoryView> subcategories, Ref mainCategory, Ref mainSubcategory, int economies,
                           BigDecimal builtAreaM2, Ref waterStatus, Ref sewerStatus, Integer dueDay, String notes) {
    }

    public record UnitSummary(UUID id, String registration, String registrationDv, InscriptionView inscription,
                              String inscriptionText, String userName, String userDocumentMasked, String addressText,
                              String mainCategoryName, String waterStatusName, String sewerStatusName, int economies,
                              String status, boolean deleted) {
    }

    public record AddressInput(
            @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP com 8 dígitos.") String cep,
            @NotBlank(message = "Informe o logradouro.") @Size(max = 200) String streetName,
            @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @Size(max = 100) String neighborhood,
            @Size(max = 100) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "UF com 2 letras.") String state) {
    }

    public record UnitCustomerInput(
            @NotNull(message = "Informe o cliente.") UUID customerId,
            @NotNull(message = "Informe o papel (OWNER, USER ou RESPONSIBLE).") UnitCustomer.Role relation,
            @NotNull(message = "Informe a data de início do vínculo.") LocalDate startDate,
            Boolean showOnBill) {
    }

    public record SubcategoryInput(
            @NotNull(message = "Informe a subcategoria.") UUID subcategoryId,
            @NotNull(message = "Informe as economias.") Integer economies) {
    }

    /**
     * Corpo de POST/PUT. {@code customers} é o conjunto DESEJADO de vínculos vigentes: o que sai é
     * encerrado com data de hoje, o que entra é criado. {@code localityId}/{@code sectorId} são
     * opcionais e, se vierem, precisam ser os da quadra.
     */
    public record UnitInput(
            @Pattern(regexp = "ACTIVE|PROVISIONAL", message = "Situação do cadastro: ACTIVE ou PROVISIONAL.") String status,
            UUID localityId,
            UUID sectorId,
            UUID blockId,
            @Min(0) @Max(9999) Integer lot,
            @Min(0) @Max(999) Integer sublot,
            UUID readingRouteId,
            @Min(0) Integer readingSequence,
            @NotNull(message = "Informe o endereço do imóvel.") @Valid AddressInput address,
            @NotNull(message = "Informe os vínculos com clientes.") List<@Valid UnitCustomerInput> customers,
            @NotNull(message = "Informe as subcategorias.") List<@Valid SubcategoryInput> subcategories,
            @DecimalMin("0") BigDecimal builtAreaM2,
            UUID waterStatusId,
            UUID sewerStatusId,
            @Min(1) @Max(31) Integer dueDay,
            @Size(max = 750) String notes,
            Long version,
            List<String> confirmTokens) {
    }

    /** Corpo de {@code POST /consumer-units/search}. {@code q}: inscrição formatada, matrícula, CPF/CNPJ ou nome. */
    public record UnitSearch(
            String q,
            UUID localityId,
            UUID sectorId,
            UUID blockId,
            UUID waterStatusId,
            UUID sewerStatusId,
            UUID categoryId,
            UUID routeId,
            String cep,
            UUID profileId,
            String numberFrom,
            String numberTo,
            Boolean includeDeleted,
            Integer page,
            Integer size,
            String sort) {
    }

    public record UnitCustomerLink(UUID id, UUID customerId, String customerName, String customerDocumentMasked,
                                   UnitCustomer.Role relation, LocalDate startDate, LocalDate endDate, String endReason,
                                   boolean showOnBill) {
    }

    public record HistoryChange(String field, String from, String to) {
    }

    /** {@code actor}: id do usuário (o nome do operador não é exposto aqui). */
    public record HistoryEvent(UUID id, OffsetDateTime occurredAt, String type, String title, String actor,
                               List<HistoryChange> changes) {
    }
}
