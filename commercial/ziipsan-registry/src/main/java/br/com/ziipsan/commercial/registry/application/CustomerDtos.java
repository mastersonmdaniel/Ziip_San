package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.Customer.PersonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cliente na API. O documento entra em claro só no corpo do POST/PUT ({@code document}) e nunca volta:
 * as respostas trazem apenas {@code documentMasked}. O valor em claro só sai por
 * {@code POST /customers/{id}/reveal-document}, com papel próprio e registro do acesso.
 *
 * <p>P0.16 opção 1: os campos que o frontend já enviava e eram descartados em silêncio passam a
 * ser aceitos e devolvidos (customerTypeId, activityBranchId, parentCustomerId,
 * documentExemptionReason, addresses[], phones[].extension/contactName e os três flags de
 * billing). Nada do que hoje é opcional virou obrigatório: payloads antigos, sem os campos
 * novos, continuam válidos e leem os novos campos como null/false/vazio.
 */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record Billing(@Min(value = 1, message = "Dia de vencimento de 1 a 31.")
                          @Max(value = 31, message = "Dia de vencimento de 1 a 31.") Integer dueDay,
                          Boolean nextMonthDue, Boolean advanceInvoice, Boolean blockNegativation) {
        /** Forma antiga (só dueDay), para não quebrar construção existente. */
        public Billing(Integer dueDay) {
            this(dueDay, null, null, null);
        }
    }

    public record EmailView(String address, boolean primary, UUID consentId) {
    }

    public record PhoneView(String number, String type, boolean primary, String extension, String contactName) {
        /** Forma antiga (sem ramal nem contato), para não quebrar construção existente. */
        public PhoneView(String number, String type, boolean primary) {
            this(number, type, primary, null, null);
        }
    }

    /** Coordenada SIRGAS 2000 (EPSG:4674), como o frontend a envia ({@code lat}/{@code lng}). */
    public record CoordinatesView(Double lat, Double lng) {
    }

    /** Forma aninhada, igual à que o frontend lê em customerToForm ({@code a.address}). */
    public record AddressView(AddressDetailView address, boolean correspondence) {
    }

    public record AddressDetailView(String cep, String streetName, String number, String complement,
                                    String neighborhood, String city, String state,
                                    CoordinatesView coordinates) {
    }

    public record CustomerView(UUID id, Long version, PersonType personType, String name, String tradeName,
                               Ref customerType, String documentMasked, String documentExemptionReason,
                               LocalDate birthDate, Ref activityBranch, Ref parentCustomer, Billing billing,
                               List<EmailView> emails, List<PhoneView> phones, List<AddressView> addresses,
                               long unitCount, OffsetDateTime deletedAt, String deletedReason) {
        /** Forma antiga (sem os campos P0.16), para não quebrar construção existente. */
        public CustomerView(UUID id, Long version, PersonType personType, String name, String tradeName,
                            String documentMasked, LocalDate birthDate, Billing billing, List<EmailView> emails,
                            List<PhoneView> phones, long unitCount, OffsetDateTime deletedAt, String deletedReason) {
            this(id, version, personType, name, tradeName, null, documentMasked, null, birthDate, null, null, billing,
                    emails, phones, List.of(), unitCount, deletedAt, deletedReason);
        }
    }

    public record CustomerSummary(UUID id, String name, PersonType personType, String documentMasked, long unitCount,
                                  boolean deleted) {
    }

    public record EmailInput(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 255) String address,
            UUID consentId) {
    }

    public record PhoneInput(
            @NotBlank(message = "Informe o telefone.")
            @Pattern(regexp = "\\+?[0-9]{8,15}", message = "Telefone em formato E.164 (+5511987654321).") String number,
            @Size(max = 50) String type,
            Boolean primary,
            @Size(max = 10) String extension,
            @Size(max = 100) String contactName) {
        /** Forma antiga (sem ramal nem contato), para não quebrar construção existente. */
        public PhoneInput(String number, String type, Boolean primary) {
            this(number, type, primary, null, null);
        }
    }

    public record CoordinatesInput(
            @DecimalMin(value = "-90", message = "Latitude de -90 a 90.")
            @DecimalMax(value = "90", message = "Latitude de -90 a 90.") Double lat,
            @DecimalMin(value = "-180", message = "Longitude de -180 a 180.")
            @DecimalMax(value = "180", message = "Longitude de -180 a 180.") Double lng) {
    }

    /**
     * Endereço do cliente. Mesmos limites do formulário (e do AddressInput da unidade).
     * Forma aninhada ({@code {address: {...}, correspondence}}) igual à que o frontend
     * envia em formToCustomerInput — não os campos no nível superior.
     */
    public record CustomerAddressInput(@Valid AddressInput address, Boolean correspondence) {
    }

    public record AddressInput(
            @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP com 8 dígitos.") String cep,
            @NotBlank(message = "Informe o logradouro.") @Size(max = 200) String streetName,
            @NotBlank(message = "Informe o número.") @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @Size(max = 100) String neighborhood,
            @Size(max = 100) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "UF com 2 letras.") String state,
            @Valid CoordinatesInput coordinates) {
    }

    public record CustomerInput(
            @NotNull(message = "Informe o tipo de pessoa (PF ou PJ).") PersonType personType,
            @NotBlank(message = "Informe o nome.") @Size(max = 200) String name,
            @Size(max = 200) String tradeName,
            /* Tipo de cliente e ramo de atividade: UUIDs sem FK (tabelas de catálogo ainda
               não existem no backend). Referências pendentes, não tabelas novas fora do escopo. */
            UUID customerTypeId,
            /* CPF ou CNPJ, com ou sem pontuação. Na alteração, ausente ou vazio mantém o atual. */
            @Size(max = 40) String document,
            /* Motivo para cadastrar sem documento (só quando document vem vazio). */
            @Size(max = 300) String documentExemptionReason,
            @Past(message = "A data de nascimento deve estar no passado.") LocalDate birthDate,
            UUID activityBranchId,
            /* Cliente responsável superior: auto-referência (FK em V3_4). */
            UUID parentCustomerId,
            @Valid Billing billing,
            List<@Valid EmailInput> emails,
            List<@Valid PhoneInput> phones,
            List<@Valid CustomerAddressInput> addresses,
            Long version) {
        /** Forma antiga (sem os campos P0.16), para não quebrar construção existente. */
        public CustomerInput(PersonType personType, String name, String tradeName, String document,
                             LocalDate birthDate, Billing billing, List<EmailInput> emails,
                             List<PhoneInput> phones, Long version) {
            this(personType, name, tradeName, null, document, null, birthDate, null, null, billing, emails, phones,
                    null, version);
        }
    }

    /** Corpo de {@code POST /customers/search}. {@code q}: nome ou CPF/CNPJ (com ou sem pontuação). */
    public record CustomerSearch(
            @Size(max = 200, message = "A busca aceita no máximo 200 caracteres.") String q,
            /* contains (padrão) ou startsWith */
            String nameMatch,
            PersonType personType,
            UUID customerTypeId,
            String city,
            Boolean includeDeleted,
            Integer page,
            Integer size,
            String sort) {
    }

    public record RevealRequest(
            @NotBlank(message = "Informe a finalidade do acesso ao documento.") @Size(max = 200) String purpose) {
    }

    /** {@code expiresInSeconds}: por quanto tempo a tela deve mostrar o valor antes de escondê-lo. */
    public record RevealResult(String document, int expiresInSeconds) {
    }
}
