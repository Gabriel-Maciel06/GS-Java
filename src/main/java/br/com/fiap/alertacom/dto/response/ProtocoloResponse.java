package br.com.fiap.alertacom.dto.response;

import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;

import java.time.LocalDateTime;

public record ProtocoloResponse(
    Long id,
    String numeroProtocolo,
    String equipeResponsavel,
    String parecerTecnico,
    LocalDateTime dataEmissao,
    LocalDateTime dataPrevisaoAtendimento,
    LocalDateTime dataConclusao,
    Long ocorrenciaId,
    String ocorrenciaStatus
) {
    public static ProtocoloResponse fromEntity(ProtocoloDefesaCivil p) {
        if (p == null) return null;
        Long ocId = p.getOcorrencia() != null ? p.getOcorrencia().getId() : null;
        String ocStatus = p.getOcorrencia() != null && p.getOcorrencia().getStatus() != null
                ? p.getOcorrencia().getStatus().name() : null;
        return new ProtocoloResponse(
            p.getId(),
            p.getNumeroProtocolo(),
            p.getEquipeResponsavel(),
            p.getParecerTecnico(),
            p.getDataEmissao(),
            p.getDataPrevisaoAtendimento(),
            p.getDataConclusao(),
            ocId,
            ocStatus
        );
    }
}
