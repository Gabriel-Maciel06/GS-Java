package br.com.fiap.alertacom.e2e.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * DTO Opaque-Box para captura e inspeção de eventos assíncronos RabbitMQ.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertaCriticoEventDto(
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
) {
}
