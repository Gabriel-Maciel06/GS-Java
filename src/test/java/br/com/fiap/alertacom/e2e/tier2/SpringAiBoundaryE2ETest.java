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
 * Tier 2: Spring AI Boundary & Fallback E2E Tests (T2_AI_01 a T2_AI_06).
 */
public class SpringAiBoundaryE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T2_AI_01: Bairro com Caracteres Especiais, Espaços e Acentuação")
    void testBairroComCaracteresEspeciais() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai@alertacom.com", "Gestor AI", "Centro");
        String bairroEspecial = "Jardim São Luís - Área 3 & Setor Sul";

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=" + bairroEspecial, null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("bairro").asText()).isEqualToIgnoringCase(bairroEspecial);
    }

    @Test
    @DisplayName("T2_AI_02: Parâmetro Bairro Vazio ou em Branco")
    void testParametroBairroVazio() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai@alertacom.com", "Gestor AI", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=", null, tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("T2_AI_03: Bloqueio de Acesso para Usuário não Gestor")
    void testBloqueioAcessoNaoGestor() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Vila Prudente", null, tokenCidadao);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = response.getBody();
        if (body != null && body.has("status")) {
            assertThat(body.get("status").asInt()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("T2_AI_04: Bairro com Alto Volume de Ocorrências (Stress Data Aggregation)")
    void testBairroAltoVolumeOcorrencias() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai@alertacom.com", "Gestor AI", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Mooca");

        // Cria 10 ocorrências para agregação
        for (int i = 1; i <= 10; i++) {
            postJson("/api/ocorrencias", Map.of(
                    "titulo", "Alagamento Volume Alto " + i,
                    "descricao", "Descrição detalhada do lote de estresse para agregação pelo modelo de IA.",
                    "tipoRisco", "ALAGAMENTO",
                    "severidade", "ALTA",
                    "latitude", -23.570000,
                    "longitude", -46.580000,
                    "endereco", "Rua Volume, " + i,
                    "bairro", "Mooca",
                    "cidadaoId", 1L
            ), tokenCidadao);
        }

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Mooca", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("T2_AI_05: Descarte Exclusivo de Ocorrências Já Concluídas")
    void testDescarteOcorrenciasJaConcluidas() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai@alertacom.com", "Gestor AI", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Ipiranga");

        // Cria ocorrência e resolve
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Ocorrência Anteriormente Resolvida",
                "descricao", "Incidente totalmente solucionado pela equipe municipal de zeladoria.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "CRITICA",
                "latitude", -23.590000,
                "longitude", -46.600000,
                "endereco", "Rua Resolvida, 1",
                "bairro", "Ipiranga",
                "cidadaoId", 1L
        ), tokenCidadao);
        Long id = ocResp.getBody().get("id").asLong();

        patchJson("/api/ocorrencias/" + id + "/status", Map.of(
                "novoStatus", "RESOLVIDA",
                "justificativa", "Limpeza executada"
        ), tokenGestor);

        // Dispara IA para o bairro Ipiranga
        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Ipiranga", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isEqualTo(0);
        assertThat(body.get("nivelRiscoGeral").asText()).isIn("BAIXO", "NORMAL");
    }

    @Test
    @DisplayName("T2_AI_06: Simulação de Timeout / Erro da API Externa tratada com Fallback")
    void testFalhaApiExternaTratadaComFallback() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai@alertacom.com", "Gestor AI", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Vila Prudente", null, tokenGestor);

        // A aplicação não pode retornar 500 mesmo sem conectividade com OpenAI
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        if (body.has("modoExecucao")) {
            assertThat(body.get("modoExecucao").asText()).containsIgnoringCase("FALLBACK");
        }
    }
}
