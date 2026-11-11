package br.com.fiap.alertacom.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de resposta contendo o Boletim Preventivo Oficial da Defesa Civil sintetizado
 * pelo motor Dual-Engine (Spring AI OpenAI ou Fallback Determinístico Inteligente).
 */
public record BoletimPreventivoResponse(
        String bairro,
        LocalDateTime dataEmissao,
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
