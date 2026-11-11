package br.com.fiap.alertacom.dto.request;

import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarStatusRequest(
    @NotNull(message = "Novo status da ocorrência é obrigatório")
    StatusOcorrencia novoStatus,

    @Size(max = 500, message = "Justificativa não pode exceder 500 caracteres")
    String justificativa
) {
}
