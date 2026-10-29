package br.edu.ufape.sguPraeService.comunicacao.controllers;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.TipoBeneficio;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoBeneficio.TipoBeneficioResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoBeneficio.TipoBeneficioRequest;
import br.edu.ufape.sguPraeService.exceptions.TipoBeneficioNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tipo-beneficio")
public class TipoBeneficioController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;


    @GetMapping
    public Page<TipoBeneficioResponse> listar(
            @QuerydslPredicate(root = TipoBeneficio.class) Predicate predicate,
            @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarTipoBeneficios(predicate, pageable).map(tipoBeneficio -> new TipoBeneficioResponse(tipoBeneficio, modelMapper));
    }

     @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/inativos")
    public ResponseEntity<Page<TipoBeneficioResponse>> listarInativos(
            @QuerydslPredicate(root = TipoBeneficio.class) Predicate predicate,
            @PageableDefault(sort = "id") Pageable pageable) {

        Page<TipoBeneficioResponse> page = fachada.listarTipoBeneficiosInativos(predicate, pageable)
                .map(tipoBeneficio -> new TipoBeneficioResponse(tipoBeneficio, modelMapper));

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoBeneficioResponse> buscar(@PathVariable Long id) throws TipoBeneficioNotFoundException {
        TipoBeneficio response = fachada.buscarTipoBeneficio(id);
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.OK);
    }

     @PreAuthorize("hasRole('GESTOR')")
    @PostMapping
    public ResponseEntity<TipoBeneficioResponse> salvar(@Valid @RequestBody TipoBeneficioRequest entity) {
        TipoBeneficio response = fachada.salvarTipoBeneficio(entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.CREATED);
    }

     @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<TipoBeneficioResponse> editar(@PathVariable Long id, @Valid @RequestBody TipoBeneficioRequest entity) throws TipoBeneficioNotFoundException {
        TipoBeneficio response = fachada.editarTipoBeneficio(id, entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.OK);
    }

     @PreAuthorize("hasRole('GESTOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws TipoBeneficioNotFoundException {
        fachada.deletarTipoBeneficio(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

     @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping("desativar/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) throws TipoBeneficioNotFoundException {
        fachada.desativarTipoBeneficio(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/quantidade")
    public ResponseEntity<Long> getQuantidadeTiposBeneficio() {
        Long quantidade = fachada.contarTiposBeneficio();
        return ResponseEntity.ok(quantidade);
    }
}
