package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários do SecurityFilter (Interceptador JWT)")
class SecurityFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private SecurityFilter securityFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve autenticar com sucesso quando cabeçalho Authorization contiver Bearer token válido")
    void deveAutenticarComBearerTokenValido() throws ServletException, IOException {
        String token = "jwt.valido.teste";
        String email = "cidadao@alertacom.com";

        Cidadao cidadao = Cidadao.builder()
                .id(1L)
                .email(email)
                .nome("Cidadao Teste")
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.validarToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(cidadao);

        securityFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo(email);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_CIDADAO");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deve prosseguir na cadeia mantendo contexto anônimo quando cabeçalho Authorization estiver ausente")
    void deveProsseguirSemAutenticarQuandoHeaderAusente() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenService, userDetailsService);
    }

    @Test
    @DisplayName("Deve ignorar cabeçalho sem prefixo Bearer")
    void deveIgnorarHeaderSemPrefixoBearer() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenService, userDetailsService);
    }

    @Test
    @DisplayName("Deve ignorar token inválido mantendo contexto anônimo")
    void deveIgnorarTokenInvalido() throws ServletException, IOException {
        String tokenInvalido = "token.invalido";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tokenInvalido);
        when(tokenService.validarToken(tokenInvalido)).thenReturn(null);

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Não deve autenticar quando o usuário não estiver persistido no banco de dados (eliminação do backdoor)")
    void naoDeveAutenticarQuandoUsuarioNaoPersistidoNoBanco() throws ServletException, IOException {
        String token = "jwt.sem.registro.banco";
        String email = "token.forjado@alertacom.com";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.validarToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenThrow(new UsernameNotFoundException("Não encontrado"));

        securityFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
        verify(tokenService, never()).extrairRole(anyString());
        verify(tokenService, never()).extrairId(anyString());
        verify(tokenService, never()).extrairNome(anyString());
        verify(tokenService, never()).extrairBairro(anyString());
    }

    @Test
    @DisplayName("Não deve autenticar quando o usuário estiver desativado")
    void naoDeveAutenticarQuandoUsuarioDesativado() throws ServletException, IOException {
        String token = "jwt.usuario.desativado";
        String email = "desativado@alertacom.com";

        Cidadao inativo = Cidadao.builder()
                .id(2L)
                .email(email)
                .ativo(false)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.validarToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(inativo);

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
