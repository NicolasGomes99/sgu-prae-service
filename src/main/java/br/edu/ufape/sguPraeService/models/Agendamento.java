package br.edu.ufape.sguPraeService.models;


import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class Agendamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @Column(nullable = false)
    private LocalDate data;

    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModalidadeAgendamento modalidade;

    @ManyToOne
    private Vaga vaga;

    @ManyToOne
    private Estudante estudante;

    @Column(name = "confirmacao_enviada", nullable = false)
    private boolean confirmacaoEnviada = false;
}
