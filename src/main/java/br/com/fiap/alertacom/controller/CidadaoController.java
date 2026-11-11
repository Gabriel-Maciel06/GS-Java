package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.dto.request.AtualizarCidadaoRequest;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.service.CidadaoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
        CidadaoResponse response = cidadaoService.cadastrar(request);
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
        return ResponseEntity.ok(cidadaoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CidadaoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarCidadaoRequest request) {
        return ResponseEntity.ok(cidadaoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        cidadaoService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
