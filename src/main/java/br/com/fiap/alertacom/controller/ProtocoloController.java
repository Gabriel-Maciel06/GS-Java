package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.service.ProtocoloService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/protocolos")
public class ProtocoloController {

    private final ProtocoloService protocoloService;

    public ProtocoloController(ProtocoloService protocoloService) {
        this.protocoloService = protocoloService;
    }

    @PostMapping
    public ResponseEntity<ProtocoloResponse> emitir(@Valid @RequestBody ProtocoloRequest request) {
        ProtocoloResponse response = protocoloService.emitir(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProtocoloResponse>> listar(
            @PageableDefault(size = 10, sort = "dataEmissao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(protocoloService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProtocoloResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(protocoloService.buscarPorId(id));
    }

    @RequestMapping(value = {"/numero/{numeroProtocolo}", "/consultar/{numeroProtocolo}"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<ProtocoloResponse> buscarPorNumero(@PathVariable String numeroProtocolo) {
        return ResponseEntity.ok(protocoloService.buscarPorNumero(numeroProtocolo));
    }

    @RequestMapping(value = "/{id}/concluir", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<ProtocoloResponse> concluir(
            @PathVariable Long id,
            @Valid @RequestBody ConcluirProtocoloRequest request) {
        return ResponseEntity.ok(protocoloService.concluir(id, request));
    }
}
