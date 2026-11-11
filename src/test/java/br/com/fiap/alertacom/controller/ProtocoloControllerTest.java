package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import br.com.fiap.alertacom.service.ProtocoloService;
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

@WebMvcTest(controllers = ProtocoloController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "gestor@alertacom.com.br", roles = {"GESTOR", "CIDADAO"})
@Import(GlobalExceptionHandler.class)
class ProtocoloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProtocoloService protocoloService;

    @Test
    @DisplayName("POST /api/protocolos - Deve emitir protocolo e retornar 201 Created com header Location")
    void deveEmitirProtocoloComSucesso() throws Exception {
        ProtocoloRequest request = new ProtocoloRequest(
                "Defesa Civil - Equipe Leste",
                "Equipe deslocada para contenção da encosta",
                LocalDateTime.now().plusDays(2),
                10L
        );

        ProtocoloResponse response = new ProtocoloResponse(
                1L,
                "DEF-2026-00001",
                "Defesa Civil - Equipe Leste",
                "Equipe deslocada para contenção da encosta",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(2),
                null,
                10L,
                "ENCAMINHADA_DEFESA_CIVIL"
        );

        when(protocoloService.emitir(any(ProtocoloRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/protocolos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/protocolos/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.numeroProtocolo").value("DEF-2026-00001"))
                .andExpect(jsonPath("$.equipeResponsavel").value("Defesa Civil - Equipe Leste"))
                .andExpect(jsonPath("$.ocorrenciaId").value(10))
                .andExpect(jsonPath("$.ocorrenciaStatus").value("ENCAMINHADA_DEFESA_CIVIL"));
    }

    @Test
    @DisplayName("POST /api/protocolos - Deve retornar 422 Unprocessable Entity quando ocorrência já possui protocolo")
    void deveRetornar422QuandoProtocoloDuplicado() throws Exception {
        ProtocoloRequest request = new ProtocoloRequest(
                "Equipe A",
                "Parecer",
                LocalDateTime.now().plusDays(1),
                10L
        );

        when(protocoloService.emitir(any(ProtocoloRequest.class)))
                .thenThrow(new RegraNegocioException("A ocorrência ID 10 já possui protocolo oficial da Defesa Civil emitido."));

        mockMvc.perform(post("/api/protocolos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Violação de Regra de Negócio"))
                .andExpect(jsonPath("$.detail").value(containsString("já possui protocolo oficial")));
    }

    @Test
    @DisplayName("POST /api/protocolos - Deve retornar 404 Not Found quando ocorrência não existe")
    void deveRetornar404QuandoOcorrenciaInexistente() throws Exception {
        ProtocoloRequest request = new ProtocoloRequest(
                "Equipe B",
                "Parecer",
                LocalDateTime.now().plusDays(1),
                999L
        );

        when(protocoloService.emitir(any(ProtocoloRequest.class)))
                .thenThrow(new ResourceNotFoundException("Ocorrência não encontrada com o ID: 999"));

        mockMvc.perform(post("/api/protocolos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    @DisplayName("POST /api/protocolos - Deve retornar 400 Bad Request ao violar validação de dataPrevisaoAtendimento no passado")
    void deveRetornar400QuandoPrevisaoNoPassado() throws Exception {
        ProtocoloRequest requestInvalido = new ProtocoloRequest(
                "", // Equipe em branco
                "Parecer válido",
                LocalDateTime.now().minusDays(1), // Data no passado (viola @Future)
                null // OcorrenciaId nulo
        );

        mockMvc.perform(post("/api/protocolos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItems("equipeResponsavel", "dataPrevisaoAtendimento", "ocorrenciaId")));
    }

    @Test
    @DisplayName("GET /api/protocolos - Deve listar protocolos paginados")
    void deveListarProtocolosPaginados() throws Exception {
        ProtocoloResponse p1 = new ProtocoloResponse(1L, "DEF-2026-00001", "Equipe Leste", "Parecer", LocalDateTime.now(), null, null, 10L, "ABERTA");
        when(protocoloService.listar(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(p1)));

        mockMvc.perform(get("/api/protocolos?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].numeroProtocolo").value("DEF-2026-00001"));
    }

    @Test
    @DisplayName("GET /api/protocolos/{id} - Deve retornar protocolo por ID")
    void deveBuscarPorIdExistente() throws Exception {
        ProtocoloResponse p = new ProtocoloResponse(2L, "DEF-2026-00002", "Equipe Sul", "Parecer", LocalDateTime.now(), null, null, 15L, "EM_ATENDIMENTO");
        when(protocoloService.buscarPorId(2L)).thenReturn(p);

        mockMvc.perform(get("/api/protocolos/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.numeroProtocolo").value("DEF-2026-00002"));
    }

    @Test
    @DisplayName("GET e POST /api/protocolos/numero/{numeroProtocolo} - Deve retornar protocolo por número oficial")
    void deveBuscarPorNumeroProtocolo() throws Exception {
        ProtocoloResponse p = new ProtocoloResponse(3L, "DEF-2026-00003", "Equipe Norte", "Parecer", LocalDateTime.now(), null, null, 20L, "EM_ATENDIMENTO");
        when(protocoloService.buscarPorNumero("DEF-2026-00003")).thenReturn(p);

        // Teste GET
        mockMvc.perform(get("/api/protocolos/numero/{numeroProtocolo}", "DEF-2026-00003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroProtocolo").value("DEF-2026-00003"));

        // Teste POST
        mockMvc.perform(post("/api/protocolos/numero/{numeroProtocolo}", "DEF-2026-00003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroProtocolo").value("DEF-2026-00003"));
    }

    @Test
    @DisplayName("PATCH /api/protocolos/{id}/concluir - Deve concluir atendimento e retornar ocorrência como RESOLVIDA")
    void deveConcluirProtocoloComSucesso() throws Exception {
        ConcluirProtocoloRequest request = new ConcluirProtocoloRequest("Atendimento finalizado com vistoria e liberação da via pública.");
        ProtocoloResponse response = new ProtocoloResponse(
                1L,
                "DEF-2026-00001",
                "Equipe Leste",
                "Atendimento finalizado com vistoria e liberação da via pública.",
                LocalDateTime.now().minusDays(1),
                null,
                LocalDateTime.now(),
                10L,
                "RESOLVIDA"
        );

        when(protocoloService.concluir(eq(1L), any(ConcluirProtocoloRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/protocolos/{id}/concluir", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ocorrenciaStatus").value("RESOLVIDA"))
                .andExpect(jsonPath("$.dataConclusao").exists());
    }

    @Test
    @DisplayName("PATCH /api/protocolos/{id}/concluir - Deve aceitar alias 'parecerTecnico' no payload JSON")
    void deveAceitarAliasParecerTecnicoNaConclusao() throws Exception {
        String jsonPayload = """
            {
                "parecerTecnico": "Reparo executado pelas equipes e via devidamente liberada."
            }
            """;

        ProtocoloResponse response = new ProtocoloResponse(
                1L,
                "DEF-2026-00001",
                "Equipe Leste",
                "Reparo executado pelas equipes e via devidamente liberada.",
                LocalDateTime.now().minusDays(1),
                null,
                LocalDateTime.now(),
                10L,
                "RESOLVIDA"
        );

        when(protocoloService.concluir(eq(1L), any(ConcluirProtocoloRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/protocolos/{id}/concluir", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocorrenciaStatus").value("RESOLVIDA"));
    }

    @Test
    @DisplayName("PATCH /api/protocolos/{id}/concluir - Deve retornar 400 Bad Request se parecer técnico for menor que 10 caracteres")
    void deveRetornar400QuandoParecerCurto() throws Exception {
        String jsonPayload = """
            {
                "parecerFinal": "Feito"
            }
            """;

        mockMvc.perform(patch("/api/protocolos/{id}/concluir", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.invalidParams[0].field").value("parecerFinal"));
    }
}
