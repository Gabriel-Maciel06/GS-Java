package br.com.fiap.alertacom.dto.response;

import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;

import java.time.LocalDateTime;

public record ProtocoloResumoResponse(
    Long id,
    String numeroProtocolo,
    String equipeResponsavel,
    LocalDateTime dataEmissao,
    LocalDateTime dataConclusao
) {
    public static ProtocoloResumoResponse fromEntity(ProtocoloDefesaCivil p) {
        if (p == null) return null;
        return new ProtocoloResumoResponse(
            p.getId(),
            p.getNumeroProtocolo(),
            p.getEquipeResponsavel(),
            p.getDataEmissao(),
            p.getDataConclusao()
        );
    }
}
