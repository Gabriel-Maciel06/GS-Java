package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.request.LoginRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.dto.response.TokenResponse;
import br.com.fiap.alertacom.security.TokenService;
import br.com.fiap.alertacom.service.CidadaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Controller REST para autenticação e cadastro público de usuários no AlertaCom.
 * Disponibiliza endpoints para login com emissão de JWT e auto-registro de cidadãos.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final CidadaoService cidadaoService;

    public AuthController(
            AuthenticationManager authenticationManager,
            TokenService tokenService,
            CidadaoService cidadaoService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.cidadaoService = cidadaoService;
    }

    /**
     * Realiza a autenticação de cidadãos e gestores, emitindo token Bearer JWT válido por 24 horas.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha())
        );

        Cidadao cidadao = (Cidadao) auth.getPrincipal();
        if (!Boolean.TRUE.equals(cidadao.getAtivo())) {
            throw new DisabledException("Conta de usuário desativada");
        }

        String token = tokenService.gerarToken(cidadao);
        TokenResponse response = new TokenResponse(
                token,
                "Bearer",
                tokenService.getExpiracaoMillis(),
                cidadao.getRole(),
                cidadao.getNome()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Auto-registro público de novo munícipe.
     * Força explicitamente o perfil ROLE_CIDADAO para impedir escalação de privilégios.
     */
    @PostMapping(path = {"/register", "/cadastro"})
    public ResponseEntity<CidadaoResponse> cadastrar(@Valid @RequestBody CidadaoRequest request) {
        CidadaoRequest safeRequest = new CidadaoRequest(
                request.nome(),
                request.email(),
                request.telefone(),
                request.bairro(),
                request.senha(),
                "ROLE_CIDADAO"
        );

        CidadaoResponse response = cidadaoService.cadastrar(safeRequest);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/cidadaos/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }
}
