package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.dto.request.AtualizarCidadaoRequest;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import br.com.fiap.alertacom.service.CidadaoService;
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

@WebMvcTest(controllers = CidadaoController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "gestor@alertacom.com.br", roles = {"GESTOR", "CIDADAO"})
@Import(GlobalExceptionHandler.class)
class CidadaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CidadaoService cidadaoService;

    @Test
    @DisplayName("POST /api/cidadaos - Deve cadastrar cidadão e retornar 201 Created com header Location")
    void deveCadastrarCidadaoComSucesso() throws Exception {
        CidadaoRequest request = new CidadaoRequest(
                "Carlos Silva",
                "carlos.silva@email.com",
                "(11) 98765-4321",
                "Mooca",
                "senha123",
                "ROLE_CIDADAO"
        );

        CidadaoResponse response = new CidadaoResponse(
                1L,
                "Carlos Silva",
                "carlos.silva@email.com",
                "(11) 98765-4321",
                "Mooca",
                "ROLE_CIDADAO",
                LocalDateTime.now(),
                true
        );

        when(cidadaoService.cadastrar(any(CidadaoRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cidadaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/cidadaos/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Carlos Silva"))
                .andExpect(jsonPath("$.email").value("carlos.silva@email.com"))
                .andExpect(jsonPath("$.role").value("ROLE_CIDADAO"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("POST /api/cidadaos - Deve retornar 400 Bad Request com RFC 7807 ProblemDetail e invalidParams ao enviar campos inválidos")
    void deveRetornar400QuandoDadosInvalidos() throws Exception {
        CidadaoRequest requestInvalido = new CidadaoRequest(
                "", // Nome em branco
                "email-invalido", // Email com formato inválido
                "123", // Telefone fora do padrão
                "", // Bairro em branco
                "123", // Senha curta (< 6 caracteres)
                "ROLE_INVALIDA" // Role fora do padrão
        );

        mockMvc.perform(post("/api/cidadaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Requisição Inválida"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.invalidParams", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItems("nome", "email", "bairro", "senha")));
    }

    @Test
    @DisplayName("POST /api/cidadaos - Deve retornar 409 Conflict ao tentar cadastrar e-mail duplicado")
    void deveRetornar409QuandoEmailDuplicado() throws Exception {
        CidadaoRequest request = new CidadaoRequest(
                "Carlos Silva",
                "duplicado@email.com",
                "(11) 98765-4321",
                "Mooca",
                "senha123",
                "ROLE_CIDADAO"
        );

        when(cidadaoService.cadastrar(any(CidadaoRequest.class)))
                .thenThrow(new DuplicateResourceException("E-mail já cadastrado na plataforma: duplicado@email.com"));

        mockMvc.perform(post("/api/cidadaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflito de Recurso"))
                .andExpect(jsonPath("$.detail").value(containsString("duplicado@email.com")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/cidadaos - Deve retornar 200 OK com página de cidadãos")
    void deveListarCidadaosPaginados() throws Exception {
        CidadaoResponse r1 = new CidadaoResponse(1L, "Cidadao 1", "c1@email.com", "(11) 91111-1111", "Mooca", "ROLE_CIDADAO", LocalDateTime.now(), true);
        when(cidadaoService.listar(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(r1)));

        mockMvc.perform(get("/api/cidadaos?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value("Cidadao 1"));
    }

    @Test
    @DisplayName("GET /api/cidadaos/{id} - Deve retornar 200 OK quando cidadão encontrado")
    void deveBuscarPorIdExistente() throws Exception {
        CidadaoResponse response = new CidadaoResponse(5L, "Ana Paula", "ana@email.com", "(11) 92222-2222", "Ipiranga", "ROLE_CIDADAO", LocalDateTime.now(), true);
        when(cidadaoService.buscarPorId(5L)).thenReturn(response);

        mockMvc.perform(get("/api/cidadaos/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.nome").value("Ana Paula"));
    }

    @Test
    @DisplayName("GET /api/cidadaos/{id} - Deve retornar 404 Not Found quando ID não existe")
    void deveRetornar404QuandoCidadaoInexistente() throws Exception {
        when(cidadaoService.buscarPorId(999L)).thenThrow(new ResourceNotFoundException("Cidadão não encontrado com o ID: 999"));

        mockMvc.perform(get("/api/cidadaos/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso Não Encontrado"))
                .andExpect(jsonPath("$.detail").value(containsString("999")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("PUT /api/cidadaos/{id} - Deve atualizar cidadão e retornar 200 OK")
    void deveAtualizarCidadaoComSucesso() throws Exception {
        AtualizarCidadaoRequest request = new AtualizarCidadaoRequest("Ana Paula Nova", "(11) 93333-3333", "Tatuapé");
        CidadaoResponse response = new CidadaoResponse(5L, "Ana Paula Nova", "ana@email.com", "(11) 93333-3333", "Tatuapé", "ROLE_CIDADAO", LocalDateTime.now(), true);

        when(cidadaoService.atualizar(eq(5L), any(AtualizarCidadaoRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/cidadaos/{id}", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Paula Nova"))
                .andExpect(jsonPath("$.bairro").value("Tatuapé"));
    }

    @Test
    @DisplayName("DELETE /api/cidadaos/{id} - Deve desativar cidadão e retornar 204 No Content")
    void deveDesativarCidadao() throws Exception {
        doNothing().when(cidadaoService).desativar(5L);

        mockMvc.perform(delete("/api/cidadaos/{id}", 5L))
                .andExpect(status().isNoContent());

        verify(cidadaoService, times(1)).desativar(5L);
    }
}
