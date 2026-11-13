package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.dto.request.AtualizarCidadaoRequest;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.service.CidadaoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/cidadaos")
public class CidadaoController {

    private final CidadaoService cidadaoService;

    public CidadaoController(CidadaoService cidadaoService) {
        this.cidadaoService = cidadaoService;
    }

    @PostMapping
    public ResponseEntity<CidadaoResponse> cadastrar(@Valid @RequestBody CidadaoRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isGestor = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GESTOR".equals(a.getAuthority()));

        String roleFinal = isGestor && request.role() != null && !request.role().isBlank() ? request.role() : "ROLE_CIDADAO";
        CidadaoRequest safeRequest = new CidadaoRequest(
                request.nome(),
                request.email(),
                request.telefone(),
                request.bairro(),
                request.senha(),
                roleFinal
        );

        CidadaoResponse response = cidadaoService.cadastrar(safeRequest);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<CidadaoResponse>> listar(
            @PageableDefault(size = 10, sort = "nome") Pageable pageable,
            @RequestParam(name = "page", required = false) Integer rawPage,
            @RequestParam(name = "size", required = false) Integer rawSize) {
        if ((rawPage != null && rawPage < 0) || (rawSize != null && rawSize <= 0)
                || pageable.getPageNumber() < 0 || pageable.getPageSize() <= 0) {
            throw new IllegalArgumentException("Parâmetros de paginação inválidos: page deve ser >= 0 e size > 0");
        }
        return ResponseEntity.ok(cidadaoService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CidadaoResponse> buscarPorId(@PathVariable Long id) {
        validarAcessoProprioOuGestor(id);
        return ResponseEntity.ok(cidadaoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CidadaoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarCidadaoRequest request) {
        validarAcessoProprioOuGestor(id);
        return ResponseEntity.ok(cidadaoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        cidadaoService.desativar(id);
        return ResponseEntity.noContent().build();
    }

    private void validarAcessoProprioOuGestor(Long idAlvo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Autenticação necessária para acessar este recurso.");
        }

        boolean isGestor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GESTOR".equals(a.getAuthority()));
        if (isGestor) {
            return;
        }

        Object principal = auth.getPrincipal();
        Long principalId = null;

        if (principal instanceof Cidadao c) {
            principalId = c.getId();
        } else if (principal instanceof UserDetails u) {
            principalId = cidadaoService.buscarIdPorEmail(u.getUsername());
        } else if (principal instanceof String email) {
            principalId = cidadaoService.buscarIdPorEmail(email);
        }

        if (principalId == null || !principalId.equals(idAlvo)) {
            throw new AccessDeniedException("Acesso negado: munícipe só pode consultar ou alterar seu próprio cadastro.");
        }
    }
}
