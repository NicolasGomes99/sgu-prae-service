package br.edu.ufape.sguPraeService.models;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.querydsl.core.annotations.QueryInit;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Pagamento {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	private BigDecimal valor;
	private LocalDate data;
	private Integer mesReferencia;
	private Integer anoReferencia;
	private String numeroLote;
	private boolean ativo = true;
	@ManyToOne
	@JsonManagedReference
	@QueryInit({"estudantes.curso", "tipoBeneficio"})
	private Beneficio beneficio;
}
