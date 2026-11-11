package br.com.fiap.alertacom.e2e.tier2;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 2: Protocolo Defesa Civil Boundary & Integrity E2E Tests (T2_PROT_01 a T2_PROT_06).
 */
public class ProtocoloBoundaryE2ETest extends BaseE2ETest {

    private Long cadastrarOcorrenciaBase(String tokenCidadao) {
        ResponseEntity<JsonNode> resp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Ocorrência para Protocolo Boundary",
                "descricao", "Descrição detalhada do incidente registrado para os testes de protocolo.",
                "tipoRisco", "ENXURRADA",
                "severidade", "ALTA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Teste, 100",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        return resp.getBody().get("id").asLong();
    }

    @Test
    @DisplayName("T2_PROT_01: Emissão Duplicada de Protocolo para Mesma Ocorrência")
    void testEmissaoDuplicadaProtocolo() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long ocId = cadastrarOcorrenciaBase(tokenCidadao);

        // Primeiro protocolo
        postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe 1",
                "parecerTecnico", "Vistoria preliminar em andamento.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);

        // Segundo protocolo para a mesma ocorrência
        ResponseEntity<JsonNode> response = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Segunda Equipe",
                "parecerTecnico", "Tentativa de sobrescrita ilegal de protocolo.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(2).toString()
        ), tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_PROT_02: Emissão de Protocolo para Ocorrência Inexistente")
    void testEmissaoProtocoloOcorrenciaInexistente() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", 999999L,
                "equipeResponsavel", "Equipe Fantasma",
                "parecerTecnico", "Parecer para chamado fantasma.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_PROT_03: Data de Previsão de Atendimento no Passado (@Future)")
    void testDataPrevisaoAtendimentoNoPassado() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long ocId = cadastrarOcorrenciaBase(tokenCidadao);

        ResponseEntity<JsonNode> response = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Bravo",
                "parecerTecnico", "Parecer com data retroativa proibida.",
                "dataPrevisaoAtendimento", "2020-01-01T10:00:00"
        ), tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_PROT_04: Conclusão de Protocolo Já Finalizado")
    void testConclusaoProtocoloJaFinalizado() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long ocId = cadastrarOcorrenciaBase(tokenCidadao);

        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Conclusão",
                "parecerTecnico", "Vistoria para conclusão posterior.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);
        Long protocoloId = postResp.getBody().get("id").asLong();

        // Primeira conclusão
        patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Atendimento de campo devidamente concluído e área segura."
        ), tokenGestor);

        // Segunda tentativa de conclusão
        ResponseEntity<JsonNode> response = patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Tentativa de reconcluir protocolo finalizado."
        ), tokenGestor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T2_PROT_05: Consulta Pública com Formato de Protocolo Inválido ou Inexistente")
    void testConsultaPublicaProtocoloInexistente() {
        ResponseEntity<JsonNode> response = getJson("/api/protocolos/numero/DEF-9999-99999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("T2_PROT_06: Conclusão de Protocolo com Parecer Técnico Curto (< 10 caracteres)")
    void testConclusaoParecerCurto() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao@teste.com", "Cidadao", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor@alertacom.com", "Gestor", "Centro");

        Long ocId = cadastrarOcorrenciaBase(tokenCidadao);

        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Parecer",
                "parecerTecnico", "Vistoria para teste de validação de parecer curto.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);
        Long protocoloId = postResp.getBody().get("id").asLong();

        // Parecer com 5 caracteres (mínimo 10)
        ResponseEntity<JsonNode> response = patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Feito"
        ), tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
