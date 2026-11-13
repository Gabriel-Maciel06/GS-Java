package br.com.fiap.alertacom.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade JPA representando o despacho formal e protocolo emitido pela Defesa Civil
 * para atendimento e resolução de uma ocorrência crítica ou prioritária.
 */
@Entity
@Table(
    name = "TB_PROTOCOLO_DEFESA_CIVIL",
    uniqueConstraints = {
        @UniqueConstraint(name = "UK_PROTOCOLO_NUMERO", columnNames = {"numero_protocolo"}),
        @UniqueConstraint(name = "UK_PROTOCOLO_OCORRENCIA", columnNames = {"ocorrencia_id"})
    }
)
@SequenceGenerator(name = "seq_protocolo_gen", sequenceName = "SEQ_PROTOCOLO_NUMERO", allocationSize = 1)
@Builder
@AllArgsConstructor
public class ProtocoloDefesaCivil implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_protocolo", nullable = false, updatable = false)
    private Long id;

    @Column(name = "numero_protocolo", length = 30, nullable = false, unique = true)
    private String numeroProtocolo;

    @Column(name = "equipe_responsavel", length = 100, nullable = false)
    private String equipeResponsavel;

    @Column(name = "parecer_tecnico", length = 1500)
    private String parecerTecnico;

    @Column(name = "data_emissao", nullable = false, updatable = false)
    private LocalDateTime dataEmissao;

    @Column(name = "data_previsao_atendimento")
    private LocalDateTime dataPrevisaoAtendimento;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false, unique = true)
    private Ocorrencia ocorrencia;

    // =========================================================================
    // Construtores
    // =========================================================================

    public ProtocoloDefesaCivil() {
    }

    public ProtocoloDefesaCivil(String numeroProtocolo, String equipeResponsavel,
                                String parecerTecnico, LocalDateTime dataPrevisaoAtendimento,
                                Ocorrencia ocorrencia) {
        this.numeroProtocolo = numeroProtocolo;
        this.equipeResponsavel = equipeResponsavel;
        this.parecerTecnico = parecerTecnico;
        this.dataPrevisaoAtendimento = dataPrevisaoAtendimento;
        this.ocorrencia = ocorrencia;
    }

    // =========================================================================
    // Callbacks do Ciclo de Vida JPA
    // =========================================================================

    @PrePersist
    protected void prePersist() {
        if (this.dataEmissao == null) {
            this.dataEmissao = LocalDateTime.now();
        }
    }

    // =========================================================================
    // Métodos de Negócio
    // =========================================================================

    /**
     * Conclui formalmente o atendimento da Defesa Civil, registrando parecer técnico,
     * data de conclusão e atualizando o status da ocorrência vinculada para RESOLVIDA.
     *
     * @param parecerFinal Descrição técnica do atendimento prestado.
     */
    public void concluirAtendimento(String parecerFinal) {
        this.parecerTecnico = parecerFinal;
        this.dataConclusao = LocalDateTime.now();
        if (this.ocorrencia != null) {
            this.ocorrencia.setStatus(StatusOcorrencia.RESOLVIDA);
            this.ocorrencia.setDataAtualizacao(LocalDateTime.now());
        }
    }

    // =========================================================================
    // Getters e Setters
    // =========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroProtocolo() {
        return numeroProtocolo;
    }

    public void setNumeroProtocolo(String numeroProtocolo) {
        this.numeroProtocolo = numeroProtocolo;
    }

    public String getEquipeResponsavel() {
        return equipeResponsavel;
    }

    public void setEquipeResponsavel(String equipeResponsavel) {
        this.equipeResponsavel = equipeResponsavel;
    }

    public String getParecerTecnico() {
        return parecerTecnico;
    }

    public void setParecerTecnico(String parecerTecnico) {
        this.parecerTecnico = parecerTecnico;
    }

    public LocalDateTime getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(LocalDateTime dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public LocalDateTime getDataPrevisaoAtendimento() {
        return dataPrevisaoAtendimento;
    }

    public void setDataPrevisaoAtendimento(LocalDateTime dataPrevisaoAtendimento) {
        this.dataPrevisaoAtendimento = dataPrevisaoAtendimento;
    }

    public LocalDateTime getDataConclusao() {
        return dataConclusao;
    }

    public void setDataConclusao(LocalDateTime dataConclusao) {
        this.dataConclusao = dataConclusao;
    }

    public Ocorrencia getOcorrencia() {
        return ocorrencia;
    }

    public void setOcorrencia(Ocorrencia ocorrencia) {
        this.ocorrencia = ocorrencia;
    }

    // =========================================================================
    // Equals, HashCode e ToString
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProtocoloDefesaCivil)) return false;
        ProtocoloDefesaCivil that = (ProtocoloDefesaCivil) o;
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProtocoloDefesaCivil{" +
                "id=" + id +
                ", numeroProtocolo='" + numeroProtocolo + '\'' +
                ", equipeResponsavel='" + equipeResponsavel + '\'' +
                ", dataEmissao=" + dataEmissao +
                ", dataConclusao=" + dataConclusao +
                '}';
    }
}
