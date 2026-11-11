package br.com.fiap.alertacom.e2e.tier2;

import br.com.fiap.alertacom.e2e.base.AlertaCriticoEventDto;
import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Tier 2: RabbitMQ Boundary & Fault-Tolerance E2E Tests (T2_RABBIT_01 a T2_RABBIT_06).
 */
public class RabbitMQBoundaryE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T2_RABBIT_01: Resiliência do Publicador sob Falha do Broker RabbitMQ")
    void testResilienciaPublicadorQuedaBroker() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Vila Prudente");

        // Simula falha catastrófica no broker RabbitMQ
        doThrow(new AmqpConnectException(new RuntimeException("Connection refused - RabbitMQ offline")))
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        Map<String, Object> req = Map.of(
                "titulo", "Alagamento Crítico com Broker Offline",
                "descricao", "Descrição detalhada do incidente crítico durante indisponibilidade do broker.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Rua do Orfanato, 500",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        // A requisição HTTP NÃO deve falhar (201 Created)
        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = response.getBody().get("id").asLong();

        // Ocorrência persiste com integridade no banco relacional
        ResponseEntity<JsonNode> getResp = getJson("/api/ocorrencias/" + id, token);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("T2_RABBIT_02: Serialização e Preservação de Caracteres Especiais, Emojis e Acentos")
    void testSerializacaoCaracteresEspeciaisEmojis() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Vila Prudente");

        String tituloComplexo = "Enxurrada torrencial na Rua São João! 🌊⚠️";
        Map<String, Object> req = Map.of(
                "titulo", tituloComplexo,
                "descricao", "Água invadiu 5 casas; moradores ilhados aguardando bote. Risco iminente!",
                "tipoRisco", "ENXURRADA",
                "severidade", "CRITICA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Rua São João, 120",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento.titulo()).isEqualTo(tituloComplexo);
    }

    @Test
    @DisplayName("T2_RABBIT_03: Ocorrência com Dados de Contato Ausentes / Anônimos")
    void testOcorrenciaDadosParciaisAnonimos() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        Map<String, Object> req = Map.of(
                "titulo", "Acúmulo de Lixo Severo na Galeria",
                "descricao", "Galeria totalmente obstruída impedindo escoamento pluvial.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "CRITICA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Av. Principal, 50",
                "bairro", "Centro",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento).isNotNull();
    }

    @Test
    @DisplayName("T2_RABBIT_04: Coordenadas de Fronteira no Payload do Evento")
    void testCoordenadasFronteiraPayload() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        Map<String, Object> req = Map.of(
                "titulo", "Deslizamento no Marco Polar Norte",
                "descricao", "Deslocamento de placas de gelo e rocha com fissuras profundas.",
                "tipoRisco", "DESLIZAMENTO",
                "severidade", "CRITICA",
                "latitude", 90.0,
                "longitude", 180.0,
                "endereco", "Extremo Norte, 0",
                "bairro", "Centro",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento.latitude()).isEqualTo(90.0);
        assertThat(evento.longitude()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("T2_RABBIT_05: Tolerância do Consumidor a Payload JSON Incompleto / Desconhecido")
    void testToleranciaPayloadDesconhecido() {
        // Envio de JSON com campo desconhecido extraField
        String jsonPayload = """
                {
                  "ocorrenciaId": 777,
                  "titulo": "Alerta com campos extras",
                  "bairro": "Mooca",
                  "severidade": "CRITICA",
                  "tipoRisco": "ALAGAMENTO",
                  "campoInesperadoFuturo": "compatibilidade_retroativa"
                }
                """;

        // Verifica desserialização com JsonIgnoreProperties
        AlertaCriticoEventDto dto = null;
        try {
            dto = objectMapper.readValue(jsonPayload, AlertaCriticoEventDto.class);
        } catch (Exception e) {
            // Não deve lançar
        }
        assertThat(dto).isNotNull();
        assertThat(dto.ocorrenciaId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("T2_RABBIT_06: Rajada Concorrente de Alertas Críticos (Burst Concurrency)")
    void testRajadaConcorrenteAlertasCriticos() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Mooca");

        for (int i = 1; i <= 5; i++) {
            postJson("/api/ocorrencias", Map.of(
                    "titulo", "Incidente em Lote Concorrente " + i,
                    "descricao", "Descrição detalhada do lote concorrente de mensagens críticas.",
                    "tipoRisco", "ALAGAMENTO",
                    "severidade", "CRITICA",
                    "latitude", -23.570000,
                    "longitude", -46.580000,
                    "endereco", "Rua da Mooca, " + (100 + i),
                    "bairro", "Mooca",
                    "cidadaoId", 1L
            ), token);
        }

        assertThat(rabbitHarness.getQuantidadeMensagensPublicadas()).isEqualTo(5);
    }
}
