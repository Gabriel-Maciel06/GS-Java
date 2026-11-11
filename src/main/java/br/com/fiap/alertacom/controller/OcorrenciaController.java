package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.dto.request.AtualizarStatusRequest;
import br.com.fiap.alertacom.dto.request.OcorrenciaRequest;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import br.com.fiap.alertacom.service.OcorrenciaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/ocorrencias")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    public OcorrenciaController(OcorrenciaService ocorrenciaService) {
        this.ocorrenciaService = ocorrenciaService;
    }

    @PostMapping
    public ResponseEntity<OcorrenciaResponse> criar(@Valid @RequestBody OcorrenciaRequest request) {
        OcorrenciaResponse response = ocorrenciaService.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<OcorrenciaResponse>> listar(
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) SeveridadeOcorrencia severidade,
            @RequestParam(required = false) StatusOcorrencia status,
            @RequestParam(required = false) CategoriaOcorrencia categoria,
            @RequestParam(value = "tipoRisco", required = false) CategoriaOcorrencia tipoRisco,
            @PageableDefault(size = 10, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        CategoriaOcorrencia categoriaEfetiva = categoria != null ? categoria : tipoRisco;
        return ResponseEntity.ok(ocorrenciaService.listar(pageable, bairro, severidade, status, categoriaEfetiva));
    }

    @GetMapping("/minhas")
    public ResponseEntity<Page<OcorrenciaResponse>> listarMinhas(
            @AuthenticationPrincipal Object principal,
            @PageableDefault(size = 10, sort = "dataCriacao", direction = Sort.Direction.DESC) Pageable pageable) {

        Long cidadaoId = null;
        String email = null;

        if (principal instanceof Cidadao c) {
            cidadaoId = c.getId();
            email = c.getEmail();
        } else if (principal instanceof UserDetails u) {
            email = u.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                if (auth.getPrincipal() instanceof Cidadao c) {
                    cidadaoId = c.getId();
                    email = c.getEmail();
                } else if (auth.getName() != null) {
                    email = auth.getName();
                }
            }
        }

        return ResponseEntity.ok(ocorrenciaService.listarMinhas(cidadaoId, email, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OcorrenciaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ocorrenciaService.buscarPorId(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OcorrenciaResponse> transicionarStatus(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarStatusRequest request) {
        return ResponseEntity.ok(ocorrenciaService.transicionarStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Cancelamento solicitado pelo munícipe ou gestor") String justificativa) {
        ocorrenciaService.cancelar(id, justificativa);
        return ResponseEntity.noContent().build();
    }
}
