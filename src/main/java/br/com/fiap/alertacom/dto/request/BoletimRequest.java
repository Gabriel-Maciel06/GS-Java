package br.com.fiap.alertacom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada opcional para solicitação de emissão de Boletim Preventivo da Defesa Civil por bairro.
 */
public record BoletimRequest(

        @NotBlank(message = "O nome do bairro é obrigatório para a emissão do boletim preventivo.")
        @Size(max = 100, message = "O nome do bairro deve ter no máximo 100 caracteres.")
        String bairro,

        @Positive(message = "A janela de horas de histórico deve ser um número positivo.")
        Integer horasHistorico
) {
    public BoletimRequest(String bairro) {
        this(bairro, 24);
    }

    public int horasHistoricoOrDefault() {
        return (horasHistorico != null && horasHistorico > 0) ? horasHistorico : 24;
    }
}
