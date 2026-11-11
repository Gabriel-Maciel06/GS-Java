package br.com.fiap.alertacom.dto.request;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OcorrenciaRequest(
    @NotBlank(message = "Título é obrigatório")
    @Size(min = 5, max = 120, message = "Título deve ter entre 5 e 120 caracteres")
    String titulo,

    @NotBlank(message = "Descrição é obrigatória")
    @Size(min = 10, max = 1000, message = "Descrição deve ter entre 10 e 1000 caracteres")
    String descricao,

    @NotNull(message = "Categoria/tipo de risco da ocorrência é obrigatório")
    @JsonAlias({"categoria", "tipoRisco"})
    CategoriaOcorrencia categoria,

    @NotNull(message = "Severidade da ocorrência é obrigatória")
    SeveridadeOcorrencia severidade,

    @DecimalMin(value = "-90.0", message = "Latitude mínima permitida é -90.0")
    @DecimalMax(value = "90.0", message = "Latitude máxima permitida é 90.0")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "Longitude mínima permitida é -180.0")
    @DecimalMax(value = "180.0", message = "Longitude máxima permitida é 180.0")
    Double longitude,

    @NotBlank(message = "Endereço é obrigatório")
    @Size(max = 255, message = "Endereço não pode exceder 255 caracteres")
    String endereco,

    @NotBlank(message = "Bairro é obrigatório")
    @Size(max = 100, message = "Bairro não pode exceder 100 caracteres")
    String bairro,

    @NotNull(message = "ID do cidadão solicitante é obrigatório")
    Long cidadaoId
) {
}
