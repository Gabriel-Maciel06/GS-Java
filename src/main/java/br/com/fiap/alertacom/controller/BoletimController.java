package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.dto.request.BoletimRequest;
import br.com.fiap.alertacom.dto.response.BoletimPreventivoResponse;
import br.com.fiap.alertacom.service.ai.BoletimIaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller REST responsável pela emissão de Boletins Preventivos da Defesa Civil
 * utilizando a arquitetura Dual-Engine (Spring AI OpenAI + Fallback Determinístico).
 */
@RestController
@Tag(name = "Boletins Preventivos IA (Defesa Civil)", description = "Síntese inteligente de risco climático e recomendações preventivas por bairro")
public class BoletimController {

    private final BoletimIaService boletimIaService;

    public BoletimController(BoletimIaService boletimIaService) {
        this.boletimIaService = boletimIaService;
    }

    @PostMapping({
            "/api/relatorios/boletim-bairro",
            "/api/boletim/preventivo",
            "/api/boletins/preventivo"
    })
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(
            summary = "Emitir Boletim Preventivo da Defesa Civil por Bairro (POST)",
            description = "Analisa todas as ocorrências ativas do bairro informado e sintetiza um boletim oficial com nível de risco, pontos críticos e plano de ação."
    )
    public ResponseEntity<BoletimPreventivoResponse> emitirBoletimPost(
            @RequestParam(value = "bairro", required = false) String bairroParam,
            @RequestParam(value = "horasHistorico", required = false) Integer horasParam,
            @RequestBody(required = false) BoletimRequest requestBody,
            HttpServletRequest httpRequest) {

        String bairroEfetivo = resolverBairro(bairroParam, requestBody, httpRequest);
        Integer horasEfetivas = resolverHorasHistorico(horasParam, requestBody);

        BoletimPreventivoResponse response = boletimIaService.gerarBoletimPreventivo(bairroEfetivo, horasEfetivas);
        return ResponseEntity.ok(response);
    }

    @GetMapping({
            "/api/relatorios/boletim-bairro",
            "/api/boletim/preventivo",
            "/api/boletins/preventivo"
    })
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(
            summary = "Emitir Boletim Preventivo da Defesa Civil por Bairro (GET)",
            description = "Consulta e gera o boletim preventivo consolidado para o bairro informado."
    )
    public ResponseEntity<BoletimPreventivoResponse> emitirBoletimGet(
            @RequestParam(value = "bairro", required = false) String bairroParam,
            @RequestParam(value = "horasHistorico", required = false) Integer horasParam,
            HttpServletRequest httpRequest) {

        String bairroEfetivo = resolverBairro(bairroParam, null, httpRequest);
        Integer horasEfetivas = resolverHorasHistorico(horasParam, null);

        BoletimPreventivoResponse response = boletimIaService.gerarBoletimPreventivo(bairroEfetivo, horasEfetivas);
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve o nome do bairro priorizando a query string completa (para preservar caracteres
     * especiais como '&' em nomes compostos de setores/áreas), depois o parâmetro de requisição
     * e, por fim, o corpo JSON opcional.
     */
    private String resolverBairro(String bairroParam, BoletimRequest requestBody, HttpServletRequest httpRequest) {
        if (httpRequest != null && httpRequest.getQueryString() != null) {
            String rawQuery = httpRequest.getQueryString();
            int idx = rawQuery.indexOf("bairro=");
            if (idx >= 0) {
                String afterBairro = rawQuery.substring(idx + "bairro=".length());
                int idxHoras = afterBairro.indexOf("&horasHistorico=");
                if (idxHoras >= 0) {
                    afterBairro = afterBairro.substring(0, idxHoras);
                }
                return URLDecoder.decode(afterBairro, StandardCharsets.UTF_8);
            }
        }

        if (bairroParam != null) {
            return bairroParam;
        }

        if (requestBody != null && requestBody.bairro() != null) {
            return requestBody.bairro();
        }

        return null;
    }

    private Integer resolverHorasHistorico(Integer horasParam, BoletimRequest requestBody) {
        if (horasParam != null && horasParam > 0) {
            return horasParam;
        }
        if (requestBody != null) {
            return requestBody.horasHistoricoOrDefault();
        }
        return 24;
    }
}
