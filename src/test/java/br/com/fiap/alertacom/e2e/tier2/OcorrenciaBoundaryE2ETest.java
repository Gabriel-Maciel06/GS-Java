package br.com.fiap.alertacom.e2e.tier2;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 2: Ocorrência Boundary & State Machine E2E Tests (T2_OCO_01 a T2_OCO_06).
 */
public class OcorrenciaBoundaryE2ETest extends BaseE2ETest {

    private Long cadastrarOcorrenciaBase(String tokenCidadao) {
        ResponseEntity<JsonNode> resp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Ocorrência para Teste de Transição",
                "descricao", "Descrição detalhada do incidente para avaliação de estados.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "MEDIA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Teste, 100",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        return resp.getBody().get("id").asLong();
    }

    @Test
    @DisplayName("T2_OCO_01: Transição Ilegal de Máquina de Estados: Reabrir Ocorrência RESOLVIDA")
    void testTransicaoIlegalReabrirResolvida() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long id = cadastrarOcorrenciaBase(tokenCidadao);

        // Mover para RESOLVIDA
        patchJson("/api/ocorrencias/" + id + "/status", Map.of(
                "novoStatus", "RESOLVIDA",
                "justificativa", "Finalizado com sucesso"
        ), tokenGestor);

        // Tentar reabrir para ABERTA
        ResponseEntity<JsonNode> response = patchJson("/api/ocorrencias/" + id + "/status", Map.of(
                "novoStatus", "ABERTA",
                "justificativa", "Tentativa de reabertura ilegal"
        ), tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.BAD_REQUEST);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isIn(422, 400);
        }
    }

    @Test
    @DisplayName("T2_OCO_02: Transição Ilegal de Máquina de Estados: Reativar Ocorrência CANCELADA")
    void testTransicaoIlegalReativarCancelada() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long id = cadastrarOcorrenciaBase(tokenCidadao);

        // Mover para CANCELADA
        patchJson("/api/ocorrencias/" + id + "/status", Map.of(
                "novoStatus", "CANCELADA",
                "justificativa", "Cancelamento justificado"
        ), tokenGestor);

        // Tentar reativar para EM_ANALISE
        ResponseEntity<JsonNode> response = patchJson("/api/ocorrencias/" + id + "/status", Map.of(
                "novoStatus", "EM_ANALISE",
                "justificativa", "Reativando chamado cancelado"
        ), tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_OCO_03: Coordenadas Geográficas Fora dos Limites Terrestres")
    void testCoordenadasForaDosLimites() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        // Latitude > 90.0
        ResponseEntity<JsonNode> respLat = postJson("/api/ocorrencias", Map.of(
                "titulo", "Coordenada Latitude Invalida",
                "descricao", "Descrição detalhada do incidente com erro de coordenada.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "BAIXA",
                "latitude", 91.0,
                "longitude", -46.590000,
                "endereco", "Rua Polar, 1",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        assertThat(respLat.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Longitude < -180.0
        ResponseEntity<JsonNode> respLng = postJson("/api/ocorrencias", Map.of(
                "titulo", "Coordenada Longitude Invalida",
                "descricao", "Descrição detalhada do incidente com erro de coordenada.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "BAIXA",
                "latitude", -23.580000,
                "longitude", -180.5,
                "endereco", "Rua Extrema, 1",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        assertThat(respLng.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_OCO_04: Fronteiras de Tamanho de Título e Descrição")
    void testFronteirasTamanhoTituloDescricao() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        // Título com 4 caracteres (mínimo 5)
        ResponseEntity<JsonNode> respTitulo = postJson("/api/ocorrencias", Map.of(
                "titulo", "Lixo",
                "descricao", "Descrição válida com mais de dez caracteres.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "BAIXA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Teste, 1",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        assertThat(respTitulo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Descrição com 9 caracteres (mínimo 10)
        ResponseEntity<JsonNode> respDesc = postJson("/api/ocorrencias", Map.of(
                "titulo", "Título Válido Aqui",
                "descricao", "Curto d+",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "BAIXA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Teste, 1",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        assertThat(respDesc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_OCO_05: Cadastro com Vínculo a Cidadão Inexistente")
    void testCadastroVinculoCidadaoInexistente() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(999999L, "fantasma@teste.com", "Fantasma", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", Map.of(
                "titulo", "Alagamento na Rua Central",
                "descricao", "Descrição detalhada com tamanho adequado para passar na validação.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "MEDIA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Central, 100",
                "bairro", "Centro",
                "cidadaoId", 999999L
        ), tokenCidadao);

        assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_OCO_06: Consulta de Ocorrência por ID Inexistente")
    void testConsultaOcorrenciaIdInexistente() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        ResponseEntity<JsonNode> response = getJson("/api/ocorrencias/999999", tokenCidadao);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
