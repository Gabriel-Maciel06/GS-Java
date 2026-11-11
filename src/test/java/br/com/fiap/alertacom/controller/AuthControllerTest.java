package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.request.LoginRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.security.TokenService;
import br.com.fiap.alertacom.service.CidadaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("Testes do Slice Web MVC: AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private CidadaoService cidadaoService;

    @Test
    @DisplayName("POST /api/auth/login - Deve autenticar com sucesso e retornar TokenResponse")
    void deveRealizarLoginComSucesso() throws Exception {
        LoginRequest loginRequest = new LoginRequest("marcos.silva@exemplo.com", "SenhaForte@2026");

        Cidadao cidadao = Cidadao.builder()
                .id(1L)
                .nome("Marcos Silva")
                .email("marcos.silva@exemplo.com")
                .bairro("Vila Prudente")
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(cidadao, null, cidadao.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenService.gerarToken(cidadao)).thenReturn("jwt.token.valido.mockado");
        when(tokenService.getExpiracaoMillis()).thenReturn(86400000L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt.token.valido.mockado")))
                .andExpect(jsonPath("$.tipo", is("Bearer")))
                .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")))
                .andExpect(jsonPath("$.nome", is("Marcos Silva")))
                .andExpect(jsonPath("$.expiraEm", is(86400000)));
    }

    @Test
    @DisplayName("POST /api/auth/login - Deve retornar 401 Unauthorized com RFC 7807 quando senha estiver incorreta")
    void deveRetornar401QuandoCredenciaisInvalidas() throws Exception {
        LoginRequest loginRequest = new LoginRequest("usuario@exemplo.com", "SenhaErrada");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")))
                .andExpect(jsonPath("$.detail", containsString("Credenciais inválidas")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Deve retornar 401 Unauthorized quando e-mail não existir")
    void deveRetornar401QuandoUsuarioInexistente() throws Exception {
        LoginRequest loginRequest = new LoginRequest("fantasma@exemplo.com", "Senha123");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new UsernameNotFoundException("Usuário não encontrado"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Não Autorizado")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Deve retornar 401 Unauthorized quando usuário estiver inativo")
    void deveRetornar401QuandoUsuarioDesativado() throws Exception {
        LoginRequest loginRequest = new LoginRequest("inativo@exemplo.com", "Senha123");

        Cidadao inativo = Cidadao.builder()
                .id(2L)
                .email("inativo@exemplo.com")
                .ativo(false)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(inativo, null, inativo.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Conta Desativada")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Deve retornar 400 Bad Request quando campos forem inválidos")
    void deveRetornar400QuandoLoginComCamposInvalidos() throws Exception {
        LoginRequest loginRequest = new LoginRequest("email-invalido", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.title", is("Requisição Inválida")))
                .andExpect(jsonPath("$.invalidParams", not(empty())));
    }

    @Test
    @DisplayName("POST /api/auth/register e /cadastro - Deve realizar auto-registro forçando ROLE_CIDADAO")
    void deveRealizarAutoRegistroComSucesso() throws Exception {
        CidadaoRequest request = new CidadaoRequest(
                "Novo Cidadao",
                "novo@exemplo.com",
                "(11) 98765-4321",
                "Mooca",
                "SenhaForte@2026",
                "ROLE_GESTOR" // Tentativa de forjar papel de gestor
        );

        CidadaoResponse response = new CidadaoResponse(
                15L,
                "Novo Cidadao",
                "novo@exemplo.com",
                "(11) 98765-4321",
                "Mooca",
                "ROLE_CIDADAO", // Role forçada para cidadão
                LocalDateTime.now(),
                true
        );

        when(cidadaoService.cadastrar(any())).thenReturn(response);

        // Testa /api/auth/register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/cidadaos/15")))
                .andExpect(jsonPath("$.id", is(15)))
                .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")));

        // Testa alias /api/auth/cadastro
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(15)))
                .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Deve retornar 409 Conflict quando e-mail já estiver cadastrado")
    void deveRetornar409QuandoAutoRegistroComEmailDuplicado() throws Exception {
        CidadaoRequest request = new CidadaoRequest(
                "Duplicado",
                "duplicado@exemplo.com",
                "(11) 98765-4321",
                "Mooca",
                "SenhaForte@2026",
                "ROLE_CIDADAO"
        );

        when(cidadaoService.cadastrar(any()))
                .thenThrow(new DuplicateResourceException("E-mail já cadastrado na plataforma"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.title", is("Conflito de Recurso")));
    }
}
