package br.com.fiap.alertacom.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Máquina de estados controlando o ciclo de vida da ocorrência.
 */
public enum StatusOcorrencia {

    ABERTA("Ocorrência registrada pelo cidadão, aguardando triagem municipal"),
    EM_ANALISE("Em análise e triagem técnica pela equipe de gestão de risco"),
    ENCAMINHADA_DEFESA_CIVIL("Protocolo oficial emitido e caso despachado para a Defesa Civil"),
    EM_ATENDIMENTO("Equipe técnica ou operacional em atendimento de campo"),
    RESOLVIDA("Atendimento concluído, risco mitigado e parecer final emitido"),
    CANCELADA("Ocorrência cancelada (duplicidade, trote ou improcedência)");

    private final String descricao;

    StatusOcorrencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Determina se o status representa um estado final imutável.
     */
    public boolean isTerminal() {
        return this == RESOLVIDA || this == CANCELADA;
    }

    /**
     * Valida se a transição para o novo status é permitida pela máquina de estados.
     *
     * @param novoStatus Status alvo desejado.
     * @return true se transição for legal; false caso contrário.
     */
    public boolean canTransitionTo(StatusOcorrencia novoStatus) {
        if (novoStatus == null || this == novoStatus) {
            return false;
        }
        return switch (this) {
            case ABERTA -> novoStatus == EM_ANALISE || novoStatus == ENCAMINHADA_DEFESA_CIVIL || novoStatus == CANCELADA;
            case EM_ANALISE -> novoStatus == ENCAMINHADA_DEFESA_CIVIL || novoStatus == EM_ATENDIMENTO || novoStatus == RESOLVIDA || novoStatus == CANCELADA;
            case ENCAMINHADA_DEFESA_CIVIL -> novoStatus == EM_ATENDIMENTO || novoStatus == RESOLVIDA || novoStatus == CANCELADA;
            case EM_ATENDIMENTO -> novoStatus == RESOLVIDA || novoStatus == CANCELADA;
            case RESOLVIDA, CANCELADA -> false; // Estados terminais são imutáveis
        };
    }

    @JsonValue
    public String getNome() {
        return this.name();
    }

    @JsonCreator
    public static StatusOcorrencia fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (StatusOcorrencia status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de ocorrência inválido: " + value);
    }
}
