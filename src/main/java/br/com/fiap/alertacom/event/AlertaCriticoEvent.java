package br.com.fiap.alertacom.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertaCriticoEvent(
    Long ocorrenciaId,
    String titulo,
    String bairro,
    String severidade,
    String tipoRisco,
    String descricao,
    String cidadaoNome,
    Double latitude,
    Double longitude,
    LocalDateTime dataHora
) implements Serializable {
}
