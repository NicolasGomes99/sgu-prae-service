package br.edu.ufape.sguPraeService.comunicacao.dto.beneficio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import br.edu.ufape.sguPraeService.models.Beneficio;
import br.edu.ufape.sguPraeService.models.enums.MotivoEncerramento;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BeneficioRequest {
    @Positive(message = "Id do tipo de beneficio inválido")
    private Long tipoBeneficioId;

    @NotNull(message = "A data de início do beneficio é obrigatória")
    @JsonFormat(pattern = "MM/yyyy")
    @DateTimeFormat(pattern = "MM/yyyy")
    private YearMonth inicioBeneficio;

    @NotNull(message = "A data de fim do beneficio é obrigatória")
    @JsonFormat(pattern = "MM/yyyy")
    @DateTimeFormat(pattern = "MM/yyyy")
    private YearMonth fimBeneficio;

    @NotNull(message = "O valor do beneficio é obrigatório")
    @Positive(message = "Valor do beneficio inválido")
    private BigDecimal valorPagamento;

    @NotNull(message = "O termo não pode ser nulo")
    private MultipartFile termo;

    @NotNull(message = "O id do estudante é obrigatória")
//    @Positive(message = "Id do estudante inválido")
    private UUID estudanteId;


    public Beneficio convertToEntity(BeneficioRequest beneficioRequest, ModelMapper modelMapper) {
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return modelMapper.map(beneficioRequest, Beneficio.class);
    }
}
