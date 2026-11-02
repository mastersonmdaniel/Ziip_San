package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.UnitCustomer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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

    /** Coordenadas em graus decimais, SIRGAS 2000 (EPSG:4674), como o frontend as envia (lat, lng). */
    public record CoordinatesView(Double lat, Double lng) {
    }

    public record AddressView(String cep, String streetName, String number, String complement, String neighborhood,
                              String city, String state, CoordinatesView coordinates) {
    }

    public record PhysicalView(Boolean garden, BigDecimal lowerReservoirM3, BigDecimal upperReservoirM3,
                               BigDecimal poolM3) {
    }

    public record ExternalIdsView(String iptu) {
    }

    public record UnitSubcategoryView(UUID subcategoryId, Ref subcategory, Ref category, int economies,
                                      Integer privateUnits, Integer collectiveUnits,
                                      List<UUID> activityBranchIds) {
    }

    /**
     * P0.16: os Refs de catalogos que ainda nao existem no backend (blockFace, deliveryMode,
     * sewerDischarge, profile, waterSource, wellType, pavementStreet, pavementSidewalk) vem como
     * {@code Ref(id, null, null)}: o nome so existira quando o catalogo existir, mas o id e
     * preservado para o round-trip (a edicao nao pode perder o campo). deliveryRoute e
     * altRoute resolvem em reading_routes e trazem codigo/nome como readingRoute. parentUnit
     * sai como {@code Ref(id, null, null)}: a unidade-pai nao tem nome proprio e usar a
     * matricula como codigo exigiria decidir o formato de exibicao, o que nao foi decidido.
     */
    public record UnitView(UUID id, Long version, String registration, String registrationDv,
                           InscriptionView inscription, String inscriptionText, String status,
                           OffsetDateTime deletedAt, String deletedReason,
                           Ref locality, Ref sector, Ref block, Ref blockFace, Integer lot, Integer sublot,
                           Ref readingRoute, Integer readingSequence, BigDecimal lotFrontageM,
                           AddressView address, String addressText,
                           List<UnitSubcategoryView> subcategories, Ref mainCategory, Ref mainSubcategory, int economies,
                           BigDecimal builtAreaM2, Ref waterStatus, Ref sewerStatus, Ref sewerDischarge, Ref profile,
                           Ref waterSource, Ref wellType, Ref pavementStreet, Ref pavementSidewalk,
                           PhysicalView physical,
                           Integer usagePoints, Integer residents, ExternalIdsView externalIds,
                           Ref deliveryMode, Ref deliveryRoute, Integer deliverySequence, Ref altRoute, Ref parentUnit,
                           Integer dueDay, String notes, UUID draftId) {
    }

    public record UnitSummary(UUID id, String registration, String registrationDv, InscriptionView inscription,
                              String inscriptionText, String userName, String userDocumentMasked, String addressText,
                              String mainCategoryName, String waterStatusName, String sewerStatusName, int economies,
                              String status, boolean deleted) {
    }

    /**
     * Coordenadas do endereco. Limites geograficos apenas (-90..90, -180..180): a caixa
     * do Brasil que o frontend valida e marcada la como "A CONFIRMAR com o IBGE", entao
     * o backend nao a impõe (nao inventa regra de produto). O par incompleto (so lat
     * ou so lng) e recusado pelo servico como erro de campo, como o frontend ja exige.
     */
    public record CoordinatesInput(
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record AddressInput(
            @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP com 8 dígitos.") String cep,
            @NotBlank(message = "Informe o logradouro.") @Size(max = 200) String streetName,
            @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @Size(max = 100) String neighborhood,
            @Size(max = 100) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "UF com 2 letras.") String state,
            @Valid CoordinatesInput coordinates) {
    }

    /** Caracteristicas fisicas (etapa 5 do wizard). Nulo no todo = sem caracteristicas. */
    public record PhysicalInput(
            Boolean garden,
            @DecimalMin("0") BigDecimal lowerReservoirM3,
            @DecimalMin("0") BigDecimal upperReservoirM3,
            @DecimalMin("0") BigDecimal poolM3) {
    }

    /** Identificadores externos. Hoje so IPTU (maximo 30 do formulario). */
    public record ExternalIdsInput(
            @Size(max = 30) String iptu) {
    }

    public record UnitCustomerInput(
            @NotNull(message = "Informe o cliente.") UUID customerId,
            @NotNull(message = "Informe o papel (OWNER, USER ou RESPONSIBLE).") UnitCustomer.Role relation,
            @NotNull(message = "Informe a data de início do vínculo.") LocalDate startDate,
            Boolean showOnBill) {
    }

    public record SubcategoryInput(
            @NotNull(message = "Informe a subcategoria.") UUID subcategoryId,
            @NotNull(message = "Informe as economias.") Integer economies,
            @Min(0) Integer privateUnits,
            @Min(0) Integer collectiveUnits,
            List<UUID> activityBranchIds) {
    }

    /**
     * Corpo de POST/PUT. {@code customers} é o conjunto DESEJADO de vínculos vigentes: o que sai é
     * encerrado com data de hoje, o que entra é criado. {@code localityId}/{@code sectorId} são
     * opcionais e, se vierem, precisam ser os da quadra.
     */
    /**
     * Corpo de POST/PUT. Campos P0.16 (todos opcionais, como no frontend): as referencias
     * blockFaceId, deliveryModeId, sewerDischargeId, profileId, waterSourceId, wellTypeId,
     * pavementStreetId e pavementSidewalkId apontam para catalogos que ainda nao existem
     * no backend e sao aceitas como UUID, sem checagem de existencia (ver V3_5); a
     * checagem passa a existir quando o catalogo existir. deliveryRouteId, altRouteId
     * (reading_routes) e parentUnitId (consumer_units) tem FK e existencia checada.
     */
    public record UnitInput(
            @Pattern(regexp = "ACTIVE|PROVISIONAL", message = "Situação do cadastro: ACTIVE ou PROVISIONAL.") String status,
            UUID localityId,
            UUID sectorId,
            UUID blockId,
            UUID blockFaceId,
            @Min(0) @Max(9999) Integer lot,
            @Min(0) @Max(999) Integer sublot,
            UUID readingRouteId,
            @Min(0) Integer readingSequence,
            @DecimalMin("0") BigDecimal lotFrontageM,
            @NotNull(message = "Informe o endereço do imóvel.") @Valid AddressInput address,
            @NotNull(message = "Informe os vínculos com clientes.") List<@Valid UnitCustomerInput> customers,
            UUID deliveryModeId,
            @NotNull(message = "Informe as subcategorias.") List<@Valid SubcategoryInput> subcategories,
            @DecimalMin("0") BigDecimal builtAreaM2,
            UUID waterStatusId,
            UUID sewerStatusId,
            UUID sewerDischargeId,
            UUID profileId,
            UUID waterSourceId,
            UUID wellTypeId,
            UUID pavementStreetId,
            UUID pavementSidewalkId,
            @Valid PhysicalInput physical,
            @Min(0) Integer usagePoints,
            @Min(0) Integer residents,
            @Valid ExternalIdsInput externalIds,
            UUID deliveryRouteId,
            @Min(1) Integer deliverySequence,
            UUID altRouteId,
            UUID parentUnitId,
            @Min(1) @Max(31) Integer dueDay,
            @Size(max = 750) String notes,
            UUID draftId,
            Long version,
            List<String> confirmTokens) {
    }

    /** Corpo de {@code POST /consumer-units/search}. {@code q}: inscrição formatada, matrícula, CPF/CNPJ ou nome. */
    public record UnitSearch(
            @Size(max = 200, message = "A busca aceita no máximo 200 caracteres.") String q,
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
