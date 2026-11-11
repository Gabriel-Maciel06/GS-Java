package br.com.fiap.alertacom.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ProtocoloRequest(
    @NotBlank(message = "Equipe responsável é obrigatória")
    @Size(max = 100, message = "Equipe responsável não pode exceder 100 caracteres")
    String equipeResponsavel,

    @Size(max = 1500, message = "Parecer técnico não pode exceder 1500 caracteres")
    String parecerTecnico,

    @Future(message = "A previsão de atendimento deve ser uma data futura")
    LocalDateTime dataPrevisaoAtendimento,

    @NotNull(message = "ID da ocorrência é obrigatório")
    Long ocorrenciaId
) {
}
