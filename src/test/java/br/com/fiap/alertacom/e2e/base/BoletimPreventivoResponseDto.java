package br.com.fiap.alertacom.e2e.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO Opaque-Box para validação das respostas de Boletim Preventivo geradas via Spring AI / Fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoletimPreventivoResponseDto(
        String bairro,
        String dataEmissao,
        Integer totalOcorrenciasAnalisadas,
        String nivelRiscoGeral,
        String resumoSituacaoAtual,
        List<String> recomendacoesPopulacao,
        List<String> pontosCriticosEvitar,
        String planoAcaoDefesaCivil,
        String modoExecucao,
        String modeloUtilizado
) {
}
