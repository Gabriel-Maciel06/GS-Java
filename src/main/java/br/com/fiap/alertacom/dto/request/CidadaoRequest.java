package br.com.fiap.alertacom.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CidadaoRequest(
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    String nome,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail deve possuir formato válido")
    @Size(max = 100, message = "E-mail não pode exceder 100 caracteres")
    String email,

    @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$", message = "Telefone deve seguir o padrão (XX) XXXXX-XXXX")
    String telefone,

    @NotBlank(message = "Bairro é obrigatório")
    @Size(max = 100, message = "Bairro não pode exceder 100 caracteres")
    String bairro,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, max = 50, message = "Senha deve possuir entre 6 e 50 caracteres")
    String senha,

    @Pattern(regexp = "^(ROLE_CIDADAO|ROLE_GESTOR)$", message = "O papel deve ser ROLE_CIDADAO ou ROLE_GESTOR")
    String role
) {
    public CidadaoRequest {
        if (role == null || role.isBlank()) {
            role = "ROLE_CIDADAO";
        }
    }
}
