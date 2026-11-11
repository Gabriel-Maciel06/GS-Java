package br.com.fiap.alertacom.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade JPA representando o relato de risco climático ou incidente de zeladoria urbana.
 */
@Entity
@Table(
    name = "TB_OCORRENCIA",
    indexes = {
        @Index(name = "IDX_OCORRENCIA_BAIRRO", columnList = "bairro"),
        @Index(name = "IDX_OCORRENCIA_STATUS", columnList = "status"),
        @Index(name = "IDX_OCORRENCIA_SEVERIDADE", columnList = "severidade")
    }
)
@Builder
@AllArgsConstructor
public class Ocorrencia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ocorrencia", nullable = false, updatable = false)
    private Long id;

    @Column(name = "titulo", length = 120, nullable = false)
    private String titulo;

    @Column(name = "descricao", length = 1000, nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 30, nullable = false)
    private CategoriaOcorrencia categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", length = 20, nullable = false)
    private SeveridadeOcorrencia severidade;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 35, nullable = false)
    private StatusOcorrencia status = StatusOcorrencia.ABERTA;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "endereco", length = 255, nullable = false)
    private String endereco;

    @Column(name = "bairro", length = 100, nullable = false)
    private String bairro;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cidadao_id", nullable = false)
    private Cidadao cidadao;

    @OneToOne(
        mappedBy = "ocorrencia",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private ProtocoloDefesaCivil protocolo;

    // =========================================================================
    // Construtores
    // =========================================================================

    public Ocorrencia() {
    }

    public Ocorrencia(String titulo, String descricao, CategoriaOcorrencia categoria,
                      SeveridadeOcorrencia severidade, Double latitude, Double longitude,
                      String endereco, String bairro, Cidadao cidadao) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.severidade = severidade;
        this.status = StatusOcorrencia.ABERTA;
        this.latitude = latitude;
        this.longitude = longitude;
        this.endereco = endereco;
        this.bairro = bairro;
        this.cidadao = cidadao;
    }

    // =========================================================================
    // Callbacks do Ciclo de Vida JPA
    // =========================================================================

    @PrePersist
    protected void prePersist() {
        if (this.dataCriacao == null) {
            this.dataCriacao = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = StatusOcorrencia.ABERTA;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    // =========================================================================
    // Métodos de Negócio e Conveniência
    // =========================================================================

    /**
     * Vincula um protocolo oficial da Defesa Civil à ocorrência,
     * sincronizando a relação bidirecional e atualizando o status para ENCAMINHADA_DEFESA_CIVIL.
     *
     * @param protocolo Instância do protocolo emitido.
     */
    public void vincularProtocolo(ProtocoloDefesaCivil protocolo) {
        this.protocolo = protocolo;
        if (protocolo != null) {
            protocolo.setOcorrencia(this);
            this.status = StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL;
            this.dataAtualizacao = LocalDateTime.now();
        }
    }

    /**
     * Desvincula o protocolo associado à ocorrência.
     */
    public void desvincularProtocolo() {
        if (this.protocolo != null) {
            this.protocolo.setOcorrencia(null);
            this.protocolo = null;
            this.dataAtualizacao = LocalDateTime.now();
        }
    }

    /**
     * Executa a transição de status validando as regras da máquina de estados.
     *
     * @param novoStatus Novo status pretendido.
     */
    public void transicionarStatus(StatusOcorrencia novoStatus) {
        if (!this.status.canTransitionTo(novoStatus)) {
            throw new IllegalStateException(String.format(
                "Transição de status inválida de '%s' para '%s' na ocorrência ID %d",
                this.status, novoStatus, this.id
            ));
        }
        this.status = novoStatus;
        this.dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Avalia se a ocorrência é de risco alto ou crítico para acionamento de mensageria.
     */
    public boolean isCriticaOuAlta() {
        return this.severidade != null && this.severidade.isCriticaOuAlta();
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

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public CategoriaOcorrencia getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaOcorrencia categoria) {
        this.categoria = categoria;
    }

    public SeveridadeOcorrencia getSeveridade() {
        return severidade;
    }

    public void setSeveridade(SeveridadeOcorrencia severidade) {
        this.severidade = severidade;
    }

    public StatusOcorrencia getStatus() {
        return status;
    }

    public void setStatus(StatusOcorrencia status) {
        this.status = status;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public Cidadao getCidadao() {
        return cidadao;
    }

    public void setCidadao(Cidadao cidadao) {
        this.cidadao = cidadao;
    }

    public ProtocoloDefesaCivil getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(ProtocoloDefesaCivil protocolo) {
        this.protocolo = protocolo;
    }

    // =========================================================================
    // Equals, HashCode e ToString
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ocorrencia)) return false;
        Ocorrencia that = (Ocorrencia) o;
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Ocorrencia{" +
                "id=" + id +
                ", titulo='" + titulo + '\'' +
                ", categoria=" + categoria +
                ", severidade=" + severidade +
                ", status=" + status +
                ", bairro='" + bairro + '\'' +
                ", dataCriacao=" + dataCriacao +
                '}';
    }
}
