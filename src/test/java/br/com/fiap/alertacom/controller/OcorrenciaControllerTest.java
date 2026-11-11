package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.dto.request.AtualizarStatusRequest;
import br.com.fiap.alertacom.dto.request.OcorrenciaRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResumoResponse;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import br.com.fiap.alertacom.service.OcorrenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OcorrenciaController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "gestor@alertacom.com.br", roles = {"GESTOR", "CIDADAO"})
@Import(GlobalExceptionHandler.class)
class OcorrenciaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcorrenciaService ocorrenciaService;

    @Test
    @DisplayName("POST /api/ocorrencias - Deve registrar ocorrência com sucesso e retornar 201 Created")
    void deveRegistrarOcorrenciaComSucesso() throws Exception {
        OcorrenciaRequest request = new OcorrenciaRequest(
                "Alagamento na Rua dos Trilhos",
                "Água da chuva invadindo as calçadas e comércios locais",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA,
                -23.55052,
                -46.63330,
                "Rua dos Trilhos, 1200",
                "Mooca",
                1L
        );

        OcorrenciaResponse response = new OcorrenciaResponse(
                10L,
                "Alagamento na Rua dos Trilhos",
                "Água da chuva invadindo as calçadas e comércios locais",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA,
                StatusOcorrencia.ABERTA,
                -23.55052,
                -46.63330,
                "Rua dos Trilhos, 1200",
                "Mooca",
                LocalDateTime.now(),
                null,
                new CidadaoResumoResponse(1L, "João Silva", "joao@email.com", "Mooca"),
                null
        );

        when(ocorrenciaService.criar(any(OcorrenciaRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/ocorrencias/10")))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.titulo").value("Alagamento na Rua dos Trilhos"))
                .andExpect(jsonPath("$.categoria").value("ALAGAMENTO"))
                .andExpect(jsonPath("$.tipoRisco").value("ALAGAMENTO"))
                .andExpect(jsonPath("$.severidade").value("ALTA"))
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.cidadao.nome").value("João Silva"));
    }

    @Test
    @DisplayName("POST /api/ocorrencias - Deve aceitar JSON com 'tipoRisco' via JsonAlias")
    void deveAceitarJsonComAliasTipoRisco() throws Exception {
        String jsonPayload = """
            {
                "titulo": "Risco de queda de árvore",
                "descricao": "Árvore centenária inclinada com risco sobre a fiação elétrica",
                "tipoRisco": "QUEDA_ARVORE",
                "severidade": "MEDIA",
                "latitude": -23.55,
                "longitude": -46.63,
                "endereco": "Rua Taquari, 500",
                "bairro": "Mooca",
                "cidadaoId": 2
            }
            """;

        OcorrenciaResponse response = new OcorrenciaResponse(
                11L,
                "Risco de queda de árvore",
                "Árvore centenária inclinada com risco sobre a fiação elétrica",
                CategoriaOcorrencia.QUEDA_ARVORE,
                SeveridadeOcorrencia.MEDIA,
                StatusOcorrencia.ABERTA,
                -23.55,
                -46.63,
                "Rua Taquari, 500",
                "Mooca",
                LocalDateTime.now(),
                null,
                null,
                null
        );

        when(ocorrenciaService.criar(any(OcorrenciaRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoRisco").value("QUEDA_ARVORE"));
    }

    @Test
    @DisplayName("POST /api/ocorrencias - Deve retornar 400 Bad Request ao violar Bean Validation (latitude fora dos limites)")
    void deveRetornar400QuandoValidacaoFalhar() throws Exception {
        OcorrenciaRequest requestInvalido = new OcorrenciaRequest(
                "ABC", // Menor que 5 caracteres (3 caracteres)
                "Pequeno", // Menor que 10 caracteres (7 caracteres)
                null, // Categoria nula
                null, // Severidade nula
                95.0, // Latitude > 90.0 (inválida)
                -190.0, // Longitude < -180.0 (inválida)
                "", // Endereço em branco
                "", // Bairro em branco
                null // CidadaoId nulo
        );

        mockMvc.perform(post("/api/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItems("titulo", "descricao", "categoria", "severidade", "latitude", "longitude", "endereco", "bairro", "cidadaoId")));
    }

    @Test
    @DisplayName("GET /api/ocorrencias - Deve listar ocorrências com filtros dinâmicos e paginação")
    void deveListarOcorrenciasComFiltros() throws Exception {
        OcorrenciaResponse response = new OcorrenciaResponse(
                1L, "Ocorrencia 1", "Desc", CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA, StatusOcorrencia.ABERTA, -23.55, -46.63,
                "Rua A", "Mooca", LocalDateTime.now(), null, null, null
        );

        when(ocorrenciaService.listar(any(Pageable.class), eq("Mooca"), eq(SeveridadeOcorrencia.ALTA), eq(StatusOcorrencia.ABERTA), eq(CategoriaOcorrencia.ALAGAMENTO)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/ocorrencias")
                        .param("bairro", "Mooca")
                        .param("severidade", "ALTA")
                        .param("status", "ABERTA")
                        .param("categoria", "ALAGAMENTO")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bairro").value("Mooca"));
    }

    @Test
    @DisplayName("GET /api/ocorrencias/{id} - Deve retornar ocorrência por ID")
    void deveBuscarPorIdExistente() throws Exception {
        OcorrenciaResponse response = new OcorrenciaResponse(
                20L, "Ocorrência 20", "Descricao completa", CategoriaOcorrencia.DESLIZAMENTO,
                SeveridadeOcorrencia.CRITICA, StatusOcorrencia.ABERTA, null, null,
                "Rua Tal", "Ipiranga", LocalDateTime.now(), null, null, null
        );

        when(ocorrenciaService.buscarPorId(20L)).thenReturn(response);

        mockMvc.perform(get("/api/ocorrencias/{id}", 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.severidade").value("CRITICA"));
    }

    @Test
    @DisplayName("GET /api/ocorrencias/{id} - Deve retornar 404 quando ocorrência não encontrada")
    void deveRetornar404QuandoInexistente() throws Exception {
        when(ocorrenciaService.buscarPorId(999L)).thenThrow(new ResourceNotFoundException("Ocorrência não encontrada com o ID: 999"));

        mockMvc.perform(get("/api/ocorrencias/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    @DisplayName("PATCH /api/ocorrencias/{id}/status - Deve transicionar status e retornar 200 OK")
    void deveTransicionarStatusComSucesso() throws Exception {
        AtualizarStatusRequest request = new AtualizarStatusRequest(StatusOcorrencia.EM_ANALISE, "Iniciando perícia no local");

        OcorrenciaResponse response = new OcorrenciaResponse(
                10L, "Titulo", "Desc", CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA, StatusOcorrencia.EM_ANALISE, null, null,
                "Rua", "Bairro", LocalDateTime.now(), LocalDateTime.now(), null, null
        );

        when(ocorrenciaService.transicionarStatus(eq(10L), any(AtualizarStatusRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/ocorrencias/{id}/status", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANALISE"));
    }

    @Test
    @DisplayName("PATCH /api/ocorrencias/{id}/status - Deve retornar 422 Unprocessable Entity quando transição é inválida")
    void deveRetornar422EmTransicaoInvalida() throws Exception {
        AtualizarStatusRequest request = new AtualizarStatusRequest(StatusOcorrencia.ABERTA, "Tentando reabrir");

        when(ocorrenciaService.transicionarStatus(eq(10L), any(AtualizarStatusRequest.class)))
                .thenThrow(new RegraNegocioException("Transição de status inválida de 'RESOLVIDA' para 'ABERTA'"));

        mockMvc.perform(patch("/api/ocorrencias/{id}/status", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Violação de Regra de Negócio"))
                .andExpect(jsonPath("$.detail").value(containsString("Transição de status inválida")));
    }

    @Test
    @DisplayName("DELETE /api/ocorrencias/{id} - Deve cancelar ocorrência e retornar 204 No Content")
    void deveCancelarOcorrencia() throws Exception {
        doNothing().when(ocorrenciaService).cancelar(eq(10L), anyString());

        mockMvc.perform(delete("/api/ocorrencias/{id}", 10L)
                        .param("justificativa", "Morador informou que problema foi resolvido"))
                .andExpect(status().isNoContent());

        verify(ocorrenciaService, times(1)).cancelar(eq(10L), anyString());
    }
}
