package br.com.fiap.alertacom.e2e.base;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

/**
 * Utilitário de forja e emissão de tokens JWT para testes E2E.
 * Suporta tokens válidos, expirados, adulterados, com chave falsa e anônimos.
 * Persiste usuários reais no banco relacional para validação estrita no SecurityFilter.
 */
@Component
public class JwtTestHelper {

    @Value("${jwt.secret:AlertaComSuperChaveSecretaGlobalSolutionFIAP2026}")
    private String secret = "AlertaComSuperChaveSecretaGlobalSolutionFIAP2026";

    private static final String ISSUER = "alertacom-api";

    @Autowired(required = false)
    private CidadaoRepository cidadaoRepository;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    public JwtTestHelper() {
    }

    public JwtTestHelper(String secret) {
        this.secret = secret;
    }

    public void setCidadaoRepository(CidadaoRepository cidadaoRepository) {
        this.cidadaoRepository = cidadaoRepository;
    }

    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Gera token JWT válido com perfil ROLE_CIDADAO, assegurando persistência no banco.
     */
    public String gerarTokenCidadao(Long id, String email, String nome, String bairro) {
        Cidadao cidadao = assegurarUsuarioPersistido(email, nome, "ROLE_CIDADAO", bairro);
        Long idFinal = (cidadao != null && cidadao.getId() != null) ? cidadao.getId() : id;
        return emitirToken(idFinal, email, nome, "ROLE_CIDADAO", bairro, 24, secret);
    }

    /**
     * Gera token JWT válido com perfil ROLE_GESTOR, assegurando persistência no banco.
     */
    public String gerarTokenGestor(Long id, String email, String nome, String bairro) {
        Cidadao gestor = assegurarUsuarioPersistido(email, nome, "ROLE_GESTOR", bairro);
        Long idFinal = (gestor != null && gestor.getId() != null) ? gestor.getId() : id;
        return emitirToken(idFinal, email, nome, "ROLE_GESTOR", bairro, 24, secret);
    }

    /**
     * Gera token JWT válido para usuário deliberadamente NÃO persistido no banco
     * para testes de segurança (rejeição de usuário inexistente com 401).
     */
    public String gerarTokenUsuarioInexistente(String email, String role) {
        return emitirToken(999999L, email, "Fantasma Teste", role, "Centro", 24, secret);
    }

    /**
     * Gera token JWT expirado (emitido com expiração no passado).
     */
    public String gerarTokenExpirado(Long id, String email, String role) {
        assegurarUsuarioPersistido(email, "Usuario Expirado", role, "Centro");
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim("id", id != null ? id : 1L)
                .withClaim("nome", "Usuario Expirado")
                .withClaim("role", role)
                .withClaim("bairro", "Centro")
                .withExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    /**
     * Gera token assinado com chave secreta incorreta (simulando ataque de falsificação).
     */
    public String gerarTokenComChaveInvalida(Long id, String email, String role) {
        Algorithm algorithm = Algorithm.HMAC256("ChaveFalsaHackersNaoAutorizados12345");
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim("id", id != null ? id : 99L)
                .withClaim("nome", "Atacante Externo")
                .withClaim("role", role)
                .withClaim("bairro", "Desconhecido")
                .withExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    /**
     * Gera token JWT válido sem a claim 'role'.
     */
    public String gerarTokenSemRole(Long id, String email) {
        assegurarUsuarioPersistido(email, "Usuario Sem Role", "ROLE_CIDADAO", "Centro");
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim("id", id != null ? id : 1L)
                .withClaim("nome", "Usuario Sem Role")
                .withClaim("bairro", "Centro")
                .withExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    /**
     * Adultera a assinatura de um token válido existente.
     */
    public String gerarTokenAdulterado(String tokenValido) {
        if (tokenValido == null || !tokenValido.contains(".")) {
            return "token.invalido.malformado";
        }
        int lastDotIndex = tokenValido.lastIndexOf('.');
        String signature = tokenValido.substring(lastDotIndex + 1);
        String tamperedSignature = signature.substring(0, Math.max(0, signature.length() - 4)) + "XXXX";
        return tokenValido.substring(0, lastDotIndex + 1) + tamperedSignature;
    }

    /**
     * Cria HttpHeaders com Bearer JWT e Content-Type JSON.
     */
    public HttpHeaders criarHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    /**
     * Cria HttpHeaders anônimos (sem cabeçalho Authorization).
     */
    public HttpHeaders criarHeadersAnonimo() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Cidadao assegurarUsuarioPersistido(String email, String nome, String role, String bairro) {
        if (cidadaoRepository == null || email == null || email.isBlank()) {
            return null;
        }
        return cidadaoRepository.findByEmail(email).map(existente -> {
            boolean modificado = false;
            if (role != null && !role.equals(existente.getRole())) {
                existente.setRole(role);
                modificado = true;
            }
            if (!Boolean.TRUE.equals(existente.getAtivo())) {
                existente.setAtivo(true);
                modificado = true;
            }
            return modificado ? cidadaoRepository.save(existente) : existente;
        }).orElseGet(() -> {
            String hashSenha = passwordEncoder != null
                    ? passwordEncoder.encode("SenhaForte@123")
                    : "$2a$10$7EqJtq98hPqEX7fNZaFWoOhiMv4L8.wG8fQ2XUfB9U5Z6QvE0P.iW";
            Cidadao novo = Cidadao.builder()
                    .nome(nome != null ? nome : "Usuario Teste")
                    .email(email)
                    .telefone("(11) 98765-4321")
                    .bairro(bairro != null ? bairro : "Vila Prudente")
                    .senha(hashSenha)
                    .role(role != null ? role : "ROLE_CIDADAO")
                    .ativo(true)
                    .build();
            return cidadaoRepository.save(novo);
        });
    }

    private String emitirToken(Long id, String email, String nome, String role, String bairro, int horasValidade, String segredo) {
        Algorithm algorithm = Algorithm.HMAC256(segredo);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(email)
                .withClaim("id", id != null ? id : 1L)
                .withClaim("nome", nome != null ? nome : "Usuario Teste")
                .withClaim("role", role)
                .withClaim("bairro", bairro != null ? bairro : "Vila Prudente")
                .withExpiresAt(Instant.now().plus(horasValidade, ChronoUnit.HOURS))
                .sign(algorithm);
    }
}
