package br.com.fiap.alertacom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizarCidadaoRequest(
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    String nome,

    @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$", message = "Telefone deve seguir o padrão (XX) XXXXX-XXXX")
    String telefone,

    @NotBlank(message = "Bairro é obrigatório")
    @Size(max = 100, message = "Bairro não pode exceder 100 caracteres")
    String bairro
) {
}
