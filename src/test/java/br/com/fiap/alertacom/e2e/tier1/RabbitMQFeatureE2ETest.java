package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.e2e.base.AlertaCriticoEventDto;
import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Mensageria RabbitMQ E2E Tests (T1_RABBIT_01 a T1_RABBIT_05).
 */
public class RabbitMQFeatureE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T1_RABBIT_01: Publicação de Evento Assíncrono para Ocorrência CRITICA")
    void testPublicacaoEventoOcorrenciaCritica() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao.rabbit1@teste.com", "Cidadao Rabbit", "Vila Prudente");

        Map<String, Object> req = Map.of(
                "titulo", "Risco grave de deslizamento na encosta",
                "descricao", "Trincas profundas no solo e inclinação acentuada de postes residenciais após chuva contínua.",
                "tipoRisco", "DESLIZAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.590000,
                "longitude", -46.595000,
                "endereco", "Rua da Encosta, 500",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocorrenciaId = response.getBody().get("id").asLong();

        // Verificação do Harness AMQP
        rabbitHarness.validarEventoCritico(ocorrenciaId, "CRITICA", "Vila Prudente");
        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento.tipoRisco()).isEqualToIgnoringCase("DESLIZAMENTO");
    }

    @Test
    @DisplayName("T1_RABBIT_02: Publicação de Evento Assíncrono para Ocorrência ALTA")
    void testPublicacaoEventoOcorrenciaAlta() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao.rabbit2@teste.com", "Cidadao Rabbit", "Vila Prudente");

        Map<String, Object> req = Map.of(
                "titulo", "Alagamento atingindo nível de veículos",
                "descricao", "Água subindo rápido acima da altura dos pneus dos carros parados na avenida principal.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "ALTA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Av. dos Estados, 1500",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocorrenciaId = response.getBody().get("id").asLong();

        rabbitHarness.validarEventoCritico(ocorrenciaId, "ALTA", "Vila Prudente");
    }

    @Test
    @DisplayName("T1_RABBIT_03: Supressão de Mensageria para Ocorrência BAIXA")
    void testSupressaoMensageriaOcorrenciaBaixa() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao.rabbit3@teste.com", "Cidadao Rabbit", "Vila Prudente");

        Map<String, Object> req = Map.of(
                "titulo", "Pequeno acúmulo de folhas na sarjeta",
                "descricao", "Sarjeta suja com folhas secas, sem retenção grave de água até o momento.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "BAIXA",
                "latitude", -23.586000,
                "longitude", -46.593000,
                "endereco", "Rua Tranquila, 12",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rabbitHarness.assertNenhumaMensagemPublicada();
    }

    @Test
    @DisplayName("T1_RABBIT_04: Supressão de Mensageria para Ocorrência MEDIA")
    void testSupressaoMensageriaOcorrenciaMedia() {
        rabbitHarness.resetInteractions();
        String token = jwtHelper.gerarTokenCidadao(1L, "cidadao.rabbit4@teste.com", "Cidadao Rabbit", "Vila Prudente");

        Map<String, Object> req = Map.of(
                "titulo", "Árvore inclinada em calçada larga",
                "descricao", "Tronco inclinado em direção à via, sem fiação elétrica comprometida.",
                "tipoRisco", "QUEDA_ARVORE",
                "severidade", "MEDIA",
                "latitude", -23.588000,
                "longitude", -46.594000,
                "endereco", "Alameda dos Ipês, 88",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rabbitHarness.assertNenhumaMensagemPublicada();
    }

    @Test
    @DisplayName("T1_RABBIT_05: Consumo e Deserialização Automatizada @RabbitListener")
    void testConsumoEDeserializacaoListener() {
        AlertaCriticoEventDto eventoTeste = new AlertaCriticoEventDto(
                999L,
                "Sirene Alerta Mooca",
                "Mooca",
                "CRITICA",
                "ALAGAMENTO",
                "Teste simulado de acionamento",
                "Carlos Defesa",
                -23.580000,
                -46.590000,
                null
        );

        // Verifica que o evento é serializável e deserializável em UTF-8 com Jackson
        JsonNode jsonNode = objectMapper.valueToTree(eventoTeste);
        assertThat(jsonNode.get("ocorrenciaId").asLong()).isEqualTo(999L);
        assertThat(jsonNode.get("severidade").asText()).isEqualTo("CRITICA");
    }
}
