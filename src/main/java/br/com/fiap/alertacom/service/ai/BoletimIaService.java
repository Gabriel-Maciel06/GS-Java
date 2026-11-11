package br.com.fiap.alertacom.service.ai;

import br.com.fiap.alertacom.dto.response.BoletimPreventivoResponse;

/**
 * Contrato de serviço para geração de Boletins Preventivos de Inteligência Artificial
 * da Defesa Civil por bairro (ODS 11 e ODS 13).
 */
public interface BoletimIaService {

    /**
     * Emite um boletim preventivo consolidando as ocorrências ativas do bairro informado.
     *
     * @param bairro Nome do bairro a ser analisado.
     * @return {@link BoletimPreventivoResponse} estruturado com nível de risco, resumo,
     *         recomendações à população, pontos críticos a evitar e plano de ação da Defesa Civil.
     */
    BoletimPreventivoResponse gerarBoletimPreventivo(String bairro);

    /**
     * Emite um boletim preventivo considerando uma janela temporal opcional de histórico.
     *
     * @param bairro Nome do bairro a ser analisado.
     * @param horasHistorico Janela de horas para análise histórica (padrão 24h).
     * @return {@link BoletimPreventivoResponse} estruturado.
     */
    BoletimPreventivoResponse gerarBoletimPreventivo(String bairro, Integer horasHistorico);
}
