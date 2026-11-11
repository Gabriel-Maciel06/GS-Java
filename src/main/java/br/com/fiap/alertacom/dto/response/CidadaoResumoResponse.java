package br.com.fiap.alertacom.dto.response;

import br.com.fiap.alertacom.domain.model.Cidadao;

public record CidadaoResumoResponse(
    Long id,
    String nome,
    String email,
    String bairro
) {
    public static CidadaoResumoResponse fromEntity(Cidadao c) {
        if (c == null) return null;
        return new CidadaoResumoResponse(
            c.getId(),
            c.getNome(),
            c.getEmail(),
            c.getBairro()
        );
    }
}
