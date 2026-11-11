package br.com.fiap.alertacom.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Categoria dos riscos climáticos e incidentes de zeladoria urbana gerenciados pelo AlertaCom.
 */
public enum CategoriaOcorrencia {

    ALAGAMENTO("Alagamento em via pública ou imóvel residencial/comercial"),
    BUEIRO_OBSTRUIDO("Bueiro obstruído, entupido ou com grade/tampa danificada"),
    DESLIZAMENTO("Deslizamento de terra, encosta ou talude"),
    QUEDA_ARVORE("Queda de árvore ou galhos de grande porte com risco à rede elétrica/via"),
    ENXURRADA("Enxurrada com correnteza de alto fluxo e força destrutiva"),
    EROSAO_SOLO("Erosão, voçoroca ou afundamento de leito viário"),
    OUTROS("Outros incidentes correlatos de risco climático ou zeladoria");

    private final String descricao;

    CategoriaOcorrencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @JsonValue
    public String getNome() {
        return this.name();
    }

    @JsonCreator
    public static CategoriaOcorrencia fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CategoriaOcorrencia categoria : values()) {
            if (categoria.name().equalsIgnoreCase(value.trim())) {
                return categoria;
            }
        }
        throw new IllegalArgumentException("Categoria de ocorrência inválida: " + value);
    }
}
