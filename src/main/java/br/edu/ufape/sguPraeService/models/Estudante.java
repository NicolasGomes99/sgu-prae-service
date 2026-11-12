package br.edu.ufape.sguPraeService.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estudante extends Usuario {

    @Column(unique = true)
    private String matricula;

    @ManyToOne
    @JoinColumn(name = "curso_id")
    private Curso curso;

    @ManyToOne
    @JoinColumn(name = "tipo_etnia_id")
    private TipoEtnia tipoEtnia;

    private BigDecimal rendaPercapta;
    private String contatoFamilia;
    private boolean deficiente = false;
    private String tipoDeficiencia;

    @OneToOne(cascade = CascadeType.ALL)
    private DadosBancarios dadosBancarios;

    @OneToOne(cascade = CascadeType.ALL)
    private Endereco endereco;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "estudante_id")
    private List<Documento> documentos = new ArrayList<>();

    public void adicionarDocumentos(List<Documento> docs) {
        this.documentos.addAll(docs);
    }
}