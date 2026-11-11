package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Cidadão CRUD & Paginação E2E Tests (T1_CID_01 a T1_CID_05).
 */
public class CidadaoFeatureE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T1_CID_01: Criação Completa de Cidadão via Endpoint CRUD")
    void testCriacaoCompletaCidadao() {
        Map<String, Object> request = Map.of(
                "nome", "Ana Paula Costa",
                "email", "ana.costa.t1@exemplo.com",
                "telefone", "(11) 91234-5678",
                "bairro", "Ipiranga",
                "senha", "SenhaSegura@123",
                "role", "ROLE_CIDADAO"
        );

        ResponseEntity<JsonNode> response = postAnonimoJson("/api/cidadaos", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("nome").asText()).isEqualTo("Ana Paula Costa");
        assertThat(body.get("email").asText()).isEqualTo("ana.costa.t1@exemplo.com");
        if (body.has("ativo")) {
            assertThat(body.get("ativo").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("T1_CID_02: Listagem Paginada de Cidadãos com Parâmetros Spring Data")
    void testListagemPaginadaCidadaos() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        // Criar 3 cidadãos
        for (int i = 1; i <= 3; i++) {
            postAnonimoJson("/api/cidadaos", Map.of(
                    "nome", "Cidadao " + (char) ('A' + i),
                    "email", "cidadao" + i + "@teste.com",
                    "telefone", "(11) 98765-432" + i,
                    "bairro", "Bairro " + i,
                    "senha", "Senha123@"
            ));
        }

        ResponseEntity<JsonNode> response = getJson("/api/cidadaos?page=0&size=2&sort=nome,asc", tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.size()).isEqualTo(2);
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isGreaterThanOrEqualTo(2);

        String nome0 = content.get(0).get("nome").asText();
        String nome1 = content.get(1).get("nome").asText();
        assertThat(nome0.compareToIgnoreCase(nome1)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("T1_CID_03: Consulta de Detalhes de Cidadão por ID")
    void testConsultaCidadaoPorId() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> postResp = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", "Carlos Ferreira",
                "email", "carlos.id@exemplo.com",
                "telefone", "(11) 97777-6666",
                "bairro", "Tatuapé",
                "senha", "SenhaForte@1"
        ));
        Long id = postResp.getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = getJson("/api/cidadaos/" + id, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(id);
        assertThat(body.get("email").asText()).isEqualTo("carlos.id@exemplo.com");
        assertThat(body.get("bairro").asText()).isEqualTo("Tatuapé");
    }

    @Test
    @DisplayName("T1_CID_04: Atualização Cadastral de Cidadão")
    void testAtualizacaoCadastralCidadao() {
        ResponseEntity<JsonNode> postResp = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", "Ana Paula Original",
                "email", "ana.update@exemplo.com",
                "telefone", "(11) 91111-2222",
                "bairro", "Ipiranga",
                "senha", "Senha123@"
        ));
        Long id = postResp.getBody().get("id").asLong();
        String tokenCidadao = jwtHelper.gerarTokenCidadao(id, "ana.update@exemplo.com", "Ana Paula Original", "Ipiranga");

        Map<String, Object> updateReq = Map.of(
                "nome", "Ana Paula Costa Modificada",
                "telefone", "(11) 99999-8888",
                "bairro", "Sacomã"
        );

        ResponseEntity<JsonNode> response = putJson("/api/cidadaos/" + id, updateReq, tokenCidadao);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(id);
        assertThat(body.get("nome").asText()).isEqualTo("Ana Paula Costa Modificada");
        assertThat(body.get("telefone").asText()).isEqualTo("(11) 99999-8888");
        assertThat(body.get("bairro").asText()).isEqualTo("Sacomã");
    }

    @Test
    @DisplayName("T1_CID_05: Desativação Lógica de Cidadão por Gestor")
    void testDesativacaoLogicaCidadao() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> postResp = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", "Usuario Para Desativar",
                "email", "desativar@exemplo.com",
                "telefone", "(11) 93333-4444",
                "bairro", "Belém",
                "senha", "Senha123@"
        ));
        Long id = postResp.getBody().get("id").asLong();

        ResponseEntity<JsonNode> delResp = deleteJson("/api/cidadaos/" + id, tokenGestor);
        assertThat(delResp.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.OK);

        // Verificação: consultar cidadão desativado
        ResponseEntity<JsonNode> getResp = getJson("/api/cidadaos/" + id, tokenGestor);
        if (getResp.getStatusCode().is2xxSuccessful() && getResp.getBody().has("ativo")) {
            assertThat(getResp.getBody().get("ativo").asBoolean()).isFalse();
        }
    }
}
