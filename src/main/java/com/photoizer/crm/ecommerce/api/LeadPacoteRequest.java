package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LeadPacoteRequest(
    @NotNull(message = "Informe o pacote")
    UUID pacoteId,

    @NotBlank(message = "Informe seu nome")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    String nome,

    @Email(message = "E-mail inválido")
    @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
    String email,

    @NotBlank(message = "Informe seu telefone")
    @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
    String telefone,

    @NotBlank(message = "Escolha a opção de entrega")
    @Pattern(regexp = "DIGITAL|FISICA|AMBAS", message = "Opção de entrega inválida")
    String opcaoEntrega,

    @NotBlank(message = "Escolha a forma de pagamento")
    @Pattern(regexp = "PIX|CARTAO_CREDITO|TRANSFERENCIA|PAYPAL", message = "Forma de pagamento inválida")
    String formaPagamento,

    @Min(value = 0, message = "A quantidade de extras não pode ser negativa")
    Integer quantidadeExtras,

    @Size(max = 2000, message = "A observação deve ter no máximo 2000 caracteres")
    String observacao
) {}
