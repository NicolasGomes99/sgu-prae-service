package br.edu.ufape.sguPraeService.comunicacao.validacoes;

import org.springframework.beans.factory.annotation.Autowired;

import br.edu.ufape.sguPraeService.comunicacao.annotations.PagamentoValido;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoRequest;
import br.edu.ufape.sguPraeService.exceptions.BeneficioNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Beneficio;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PagamentoValidoValidator implements ConstraintValidator<PagamentoValido, PagamentoRequest> {

    @Autowired
    private Fachada fachada;

    @Override
    public boolean isValid(PagamentoRequest pagamentoRequest, ConstraintValidatorContext context) {
        if (pagamentoRequest == null) {
            return false;
        }

        if (pagamentoRequest.getBeneficioId() != null) {
            Beneficio beneficio;
            try {
                beneficio = fachada.buscarBeneficios(pagamentoRequest.getBeneficioId());
            } catch (BeneficioNotFoundException e) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Auxílio não encontrado")
                        .addPropertyNode("auxilioId")
                        .addConstraintViolation();
                return false;
            }
            if (beneficio != null && beneficio.getFimBeneficio() != null && pagamentoRequest.getData() != null
                    && pagamentoRequest.getData().isAfter(beneficio.getFimBeneficio().atEndOfMonth())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Data do pagamento excede o termino do auxílio")
                        .addPropertyNode("data")
                        .addConstraintViolation();
                return false;
            }

        }

        return true;
    }
}