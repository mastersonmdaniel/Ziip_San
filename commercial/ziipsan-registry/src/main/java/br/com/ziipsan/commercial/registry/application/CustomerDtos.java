package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.Customer.PersonType;
import jakarta.validation.Valid;
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
 */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record Billing(@Min(value = 1, message = "Dia de vencimento de 1 a 31.")
                          @Max(value = 31, message = "Dia de vencimento de 1 a 31.") Integer dueDay) {
    }

    public record EmailView(String address, boolean primary, UUID consentId) {
    }

    public record PhoneView(String number, String type, boolean primary) {
    }

    public record CustomerView(UUID id, Long version, PersonType personType, String name, String tradeName,
                               String documentMasked, LocalDate birthDate, Billing billing, List<EmailView> emails,
                               List<PhoneView> phones, long unitCount, OffsetDateTime deletedAt, String deletedReason) {
    }

    public record CustomerSummary(UUID id, String name, PersonType personType, String documentMasked, long unitCount,
                                  boolean deleted) {
    }

    public record EmailInput(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 255) String address,
            UUID consentId) {
    }

    /** {@code type} (RESIDENTIAL, MOBILE...) vai para o rótulo do contato; ramal e nome do contato não têm coluna. */
    public record PhoneInput(
            @NotBlank(message = "Informe o telefone.")
            @Pattern(regexp = "\\+?[0-9]{8,15}", message = "Telefone em formato E.164 (+5511987654321).") String number,
            @Size(max = 50) String type,
            Boolean primary) {
    }

    public record CustomerInput(
            @NotNull(message = "Informe o tipo de pessoa (PF ou PJ).") PersonType personType,
            @NotBlank(message = "Informe o nome.") @Size(max = 200) String name,
            @Size(max = 200) String tradeName,
            /* CPF ou CNPJ, com ou sem pontuação. Na alteração, ausente ou vazio mantém o atual. */
            @Size(max = 40) String document,
            @Past(message = "A data de nascimento deve estar no passado.") LocalDate birthDate,
            @Valid Billing billing,
            List<@Valid EmailInput> emails,
            List<@Valid PhoneInput> phones,
            Long version) {
    }

    /** Corpo de {@code POST /customers/search}. {@code q}: nome ou CPF/CNPJ (com ou sem pontuação). */
    public record CustomerSearch(
            String q,
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
