package br.com.fiap.alertacom.e2e.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Harness para inspeção e verificação de eventos assíncronos RabbitMQ.
 */
public class RabbitMqTestHarness {

    public static final String EXCHANGE_ALERTA = "alerta.exchange";
    public static final String QUEUE_ALERTAS_CRITICOS = "alertas.criticos.queue";
    public static final String ROUTING_KEY_CRITICO = "alerta.critico.#";

    private final RabbitTemplate rabbitTemplateMock;
    private final ObjectMapper objectMapper;

    public RabbitMqTestHarness(RabbitTemplate rabbitTemplateMock, ObjectMapper objectMapper) {
        this.rabbitTemplateMock = rabbitTemplateMock;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public RabbitMqTestHarness(RabbitTemplate rabbitTemplateMock) {
        this(rabbitTemplateMock, new ObjectMapper());
    }

    /**
     * Reseta as interações registradas no mock do RabbitTemplate.
     */
    public void resetInteractions() {
        if (rabbitTemplateMock != null) {
            reset(rabbitTemplateMock);
        }
    }

    /**
     * Retorna o número de mensagens enviadas.
     */
    public int getQuantidadeMensagensPublicadas() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        try {
            verify(rabbitTemplateMock, atLeast(0)).convertAndSend(
                    any(String.class),
                    any(String.class),
                    captor.capture()
            );
            return captor.getAllValues().size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Captura o evento publicado na exchange 'alerta.exchange' e o converte para DTO de teste.
     */
    public AlertaCriticoEventDto capturarEventoPublicado() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplateMock, atLeastOnce()).convertAndSend(
                eq(EXCHANGE_ALERTA),
                any(String.class),
                captor.capture()
        );
        Object raw = captor.getValue();
        return converterParaDto(raw);
    }

    /**
     * Captura todos os eventos publicados na exchange.
     */
    public List<AlertaCriticoEventDto> capturarTodosEventosPublicados() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplateMock, atLeastOnce()).convertAndSend(
                eq(EXCHANGE_ALERTA),
                any(String.class),
                captor.capture()
        );
        return captor.getAllValues().stream()
                .map(this::converterParaDto)
                .toList();
    }

    /**
     * Assegura que nenhuma mensagem foi enviada ao broker.
     */
    public void assertNenhumaMensagemPublicada() {
        verify(rabbitTemplateMock, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    /**
     * Valida integralmente os campos do evento capturado contra a ocorrência esperada.
     */
    public void validarEventoCritico(Long ocorrenciaIdEsperada, String severidadeEsperada, String bairroEsperado) {
        AlertaCriticoEventDto evento = capturarEventoPublicado();
        assertThat(evento).isNotNull();
        if (ocorrenciaIdEsperada != null) {
            assertThat(evento.ocorrenciaId()).isEqualTo(ocorrenciaIdEsperada);
        }
        assertThat(evento.severidade()).isEqualToIgnoringCase(severidadeEsperada);
        assertThat(evento.bairro()).isEqualToIgnoringCase(bairroEsperado);
        assertThat(evento.titulo()).isNotBlank();
        assertThat(evento.tipoRisco()).isNotBlank();
    }

    private AlertaCriticoEventDto converterParaDto(Object raw) {
        if (raw instanceof AlertaCriticoEventDto dto) {
            return dto;
        }
        try {
            JsonNode node = objectMapper.valueToTree(raw);
            Long ocorrenciaId = node.has("ocorrenciaId") && !node.get("ocorrenciaId").isNull()
                    ? node.get("ocorrenciaId").asLong() : null;
            String titulo = node.has("titulo") ? node.get("titulo").asText() : "";
            String bairro = node.has("bairro") ? node.get("bairro").asText() : "";
            String severidade = node.has("severidade") ? node.get("severidade").asText() : "";
            String tipoRisco = node.has("tipoRisco") ? node.get("tipoRisco").asText() : "";
            String descricao = node.has("descricao") ? node.get("descricao").asText() : "";
            String cidadaoNome = node.has("cidadaoNome") ? node.get("cidadaoNome").asText() : "";
            Double lat = node.has("latitude") && !node.get("latitude").isNull() ? node.get("latitude").asDouble() : null;
            Double lng = node.has("longitude") && !node.get("longitude").isNull() ? node.get("longitude").asDouble() : null;

            return new AlertaCriticoEventDto(ocorrenciaId, titulo, bairro, severidade, tipoRisco, descricao, cidadaoNome, lat, lng, null);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao converter evento AMQP para AlertaCriticoEventDto: " + raw, e);
        }
    }
}
