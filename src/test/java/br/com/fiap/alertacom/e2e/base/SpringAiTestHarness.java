package br.com.fiap.alertacom.e2e.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Harness de validação dos boletins preventivos gerados pela arquitetura Dual-Engine da Spring AI.
 */
@Component
public class SpringAiTestHarness {

    private final ObjectMapper objectMapper;

    public SpringAiTestHarness(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public SpringAiTestHarness() {
        this(new ObjectMapper());
    }

    /**
     * Valida os contratos obrigatórios de um boletim preventivo gerado no modo de testes.
     */
    public void validarEstruturaBoletim(BoletimPreventivoResponseDto boletim, String bairroEsperado) {
        assertThat(boletim).isNotNull();
        assertThat(boletim.bairro()).isEqualToIgnoringCase(bairroEsperado);
        assertThat(boletim.dataEmissao()).isNotNull();
        assertThat(boletim.nivelRiscoGeral()).isIn("BAIXO", "MODERADO", "ALTO", "CRÍTICO", "CRITICO", "NORMAL");
        assertThat(boletim.resumoSituacaoAtual()).isNotBlank();
        assertThat(boletim.recomendacoesPopulacao()).isNotEmpty();
        assertThat(boletim.pontosCriticosEvitar()).isNotEmpty();
        assertThat(boletim.planoAcaoDefesaCivil()).isNotBlank();
        if (boletim.modoExecucao() != null) {
            assertThat(boletim.modoExecucao()).contains("FALLBACK");
        }
    }

    /**
     * Valida os contratos obrigatórios de um boletim preventivo a partir de JsonNode.
     */
    public void validarEstruturaBoletim(JsonNode node, String bairroEsperado) {
        assertThat(node).isNotNull();
        assertThat(node.has("bairro")).isTrue();
        assertThat(node.get("bairro").asText()).isEqualToIgnoringCase(bairroEsperado);
        assertThat(node.has("dataEmissao")).isTrue();
        assertThat(node.get("dataEmissao").asText()).isNotBlank();
        assertThat(node.has("nivelRiscoGeral")).isTrue();
        assertThat(node.get("nivelRiscoGeral").asText()).isIn("BAIXO", "MODERADO", "ALTO", "CRÍTICO", "CRITICO", "NORMAL");
        assertThat(node.has("resumoSituacaoAtual")).isTrue();
        assertThat(node.get("resumoSituacaoAtual").asText()).isNotBlank();
        assertThat(node.has("recomendacoesPopulacao")).isTrue();
        assertThat(node.get("recomendacoesPopulacao").isArray()).isTrue();
        assertThat(node.has("pontosCriticosEvitar")).isTrue();
        assertThat(node.get("pontosCriticosEvitar").isArray()).isTrue();
        assertThat(node.has("planoAcaoDefesaCivil")).isTrue();
    }

    /**
     * Valida que o nível de risco corresponde à presença de ocorrências críticas.
     */
    public void validarClassificacaoRisco(BoletimPreventivoResponseDto boletim, String nivelEsperado, int totalEsperado) {
        assertThat(boletim.nivelRiscoGeral()).isEqualToIgnoringCase(nivelEsperado);
        assertThat(boletim.totalOcorrenciasAnalisadas()).isEqualTo(totalEsperado);
    }

    /**
     * Valida que o nível de risco corresponde à presença de ocorrências críticas a partir de JsonNode.
     */
    public void validarClassificacaoRisco(JsonNode node, String nivelEsperado, int totalEsperado) {
        assertThat(node.get("nivelRiscoGeral").asText()).isEqualToIgnoringCase(nivelEsperado);
        if (node.has("totalOcorrenciasAnalisadas")) {
            assertThat(node.get("totalOcorrenciasAnalisadas").asInt()).isEqualTo(totalEsperado);
        }
    }

    public BoletimPreventivoResponseDto converterJsonParaDto(JsonNode node) {
        return objectMapper.convertValue(node, BoletimPreventivoResponseDto.class);
    }
}
