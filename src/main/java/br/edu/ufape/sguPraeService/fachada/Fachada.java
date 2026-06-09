package br.edu.ufape.sguPraeService.fachada;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


import br.edu.ufape.sguPraeService.auth.AuthServiceClient;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.PageResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoUpdateRequest;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.exceptions.*;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import com.querydsl.core.BooleanBuilder;
import jakarta.ws.rs.NotAllowedException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.auth.RabbitAuthServiceClient;
import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.AlunoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.FuncionarioResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.AgendamentoNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CancelamentoNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CronogramaNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.PagamentoNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.ProfissionalNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.TipoAtendimentoNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.VagaNotFoundException;
import br.edu.ufape.sguPraeService.servicos.interfaces.AgendamentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.ArmazenamentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.AuthServiceHandler;
import br.edu.ufape.sguPraeService.servicos.interfaces.BeneficioService;
import br.edu.ufape.sguPraeService.servicos.interfaces.CancelamentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.CronogramaService;
import br.edu.ufape.sguPraeService.servicos.interfaces.DadosBancariosService;
//import br.edu.ufape.sguPraeService.servicos.interfaces.DocumentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.EnderecoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.EstudanteService;
import br.edu.ufape.sguPraeService.servicos.interfaces.PagamentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.ProfissionalService;
import br.edu.ufape.sguPraeService.servicos.interfaces.TipoAtendimentoService;
import br.edu.ufape.sguPraeService.servicos.interfaces.TipoBeneficioService;
import br.edu.ufape.sguPraeService.servicos.interfaces.VagaService;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.DocumentoNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.querydsl.core.types.Predicate;

@Log4j2
@Component
@RequiredArgsConstructor
public class Fachada {
    private final ProfissionalService profissionalService;
    private final TipoAtendimentoService tipoAtendimentoService;
    private final CronogramaService cronogramaService;
    private final VagaService vagaService;
    private final EnderecoService enderecoService;
    private final DadosBancariosService dadosBancariosService;
    private final EstudanteService estudanteService;
    private final AuthServiceHandler authServiceHandler;
    private final ModelMapper modelMapper;
    private final RabbitAuthServiceClient rabbitAuthServiceClient;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AgendamentoService agendamentoService;
    private final CancelamentoService cancelamentoService;
    private final TipoBeneficioService tipoBeneficioService;
//    private final DocumentoService documentoService;
    private final BeneficioService beneficioService;
    private final PagamentoService pagamentoService;
    private final ArmazenamentoService armazenamentoService;
    private final AuthServiceClient authServiceClient;
    private final NotificacaoPublisher notificacaoPublisher;

    @Value("${authClient.client-id}")
    private String clientId;

    // ------------------- Profissional ------------------- //
    public List<ProfissionalResponse> listarProfissionais() {
        List<ProfissionalResponse> profissionalResponse = new ArrayList<>();
        List<Profissional> profissionais = profissionalService.listar();
        if (profissionais.isEmpty()) {
            return profissionalResponse;
        }
        List<UUID> userIds = profissionais.stream().map(Profissional::getUserId).toList();
        log.info("Profissionals encontrados: {}", userIds);
        List<FuncionarioResponse> usuarios = authServiceHandler.buscarTecnicos(userIds);
        for (int i = 0; i < profissionais.size(); i++) {
            Profissional profissional = profissionais.get(i);
            FuncionarioResponse usuario = usuarios.get(i);
            ProfissionalResponse response = new ProfissionalResponse(profissional, modelMapper);
            response.setTecnico(usuario);
            profissionalResponse.add(response);
        }
        return profissionalResponse;
    }

    public ProfissionalResponse buscarProfissional(Long id) throws ProfissionalNotFoundException {
        Profissional profissional = profissionalService.buscar(id);
        FuncionarioResponse userInfo = authServiceHandler.buscarTecnicoPorId(profissional.getUserId());
        ProfissionalResponse response = new ProfissionalResponse(profissional, modelMapper);
        response.setTecnico(userInfo);
        return response;
    }
    
    public ProfissionalResponse buscarProfissionalAtual() throws ProfissionalNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Profissional profissional = profissionalService.buscarPorUserId(userId);
        FuncionarioResponse userInfo = authServiceHandler.buscarTecnicoPorId(profissional.getUserId());
        ProfissionalResponse response = new ProfissionalResponse(profissional, modelMapper);
        response.setTecnico(userInfo);
        return response;
    }

    public ProfissionalResponse salvarProfissional(Profissional profissional) {
        UUID userId = authenticatedUserProvider.getUserId();
        profissional.setUserId(userId);
        Profissional novoProfissional = profissionalService.salvar(profissional);
        FuncionarioResponse response = authServiceHandler.getTecnicoInfo();
        rabbitAuthServiceClient.assignRoleToUser(userId.toString(), clientId, "profissional");
        ProfissionalResponse profissionalResponse = new ProfissionalResponse(novoProfissional, modelMapper);
        profissionalResponse.setTecnico(response);
        return profissionalResponse;
    }

    public ProfissionalResponse editarProfissional(Profissional profissional) throws ProfissionalNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Profissional novoProfissional = profissionalService.editar(userId, profissional);
        FuncionarioResponse response = authServiceHandler.getTecnicoInfo();
        ProfissionalResponse profissionalResponse = new ProfissionalResponse(novoProfissional, modelMapper);
        profissionalResponse.setTecnico(response);
        return profissionalResponse;
    }

    public void deletarProfissional(Long id) throws ProfissionalNotFoundException {
        profissionalService.deletar(id);
    }

    // ================== Estudante ================== //

    @CircuitBreaker(name = "authServiceClient", fallbackMethod = "fallbackSalvarEstudante")
    public EstudanteResponse salvarEstudante(Estudante estudante, List<MultipartFile> arquivos) {
        UUID userId = authenticatedUserProvider.getUserId();
        estudante.setUserId(userId);

        if (arquivos != null && !arquivos.isEmpty()) {
            MultipartFile[] arrayArquivos = arquivos.toArray(new MultipartFile[0]);

            try {
                List<Documento> documentosSalvos = armazenamentoService.salvarArquivo(arrayArquivos);

                estudante.adicionarDocumentos(documentosSalvos);

            } catch (Exception e) {
                throw new RuntimeException("Erro ao processar documentos do estudante", e);
            }
        }

        Estudante novoEstudante = estudanteService.salvarEstudante(estudante);
        AlunoResponse userInfo = authServiceHandler.getAlunoInfo();
        rabbitAuthServiceClient.assignRoleToUser(userId.toString(), clientId, "estudante");
        EstudanteResponse response = new EstudanteResponse(novoEstudante, modelMapper);
        response.setAluno(userInfo);
        return response;
    }

    // Sobrecarga para manter compatibilidade caso algum lugar chame sem arquivos
    public EstudanteResponse salvarEstudante(Estudante estudante) {
        return salvarEstudante(estudante, null);
    }

    @Transactional
    public void adicionarDocumentosEstudante(List<MultipartFile> arquivos) throws EstudanteNotFoundException {
        // 1. Pega o ID do token
        UUID userId = authenticatedUserProvider.getUserId();

        // 2. Busca o estudante existente
        Estudante estudante = estudanteService.buscarPorUserId(userId);

        // 3. Salva os arquivos e vincula
        if (arquivos != null && !arquivos.isEmpty()) {
            MultipartFile[] arquivosArray = arquivos.toArray(new MultipartFile[0]);
            try {
                List<Documento> documentosSalvos = armazenamentoService.salvarArquivo(arquivosArray);
                // Apenas adicionamos à lista do estudante existente
                estudante.adicionarDocumentos(documentosSalvos);

                // Salva a atualização
                estudanteService.salvarEstudante(estudante);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao processar novos documentos do estudante", e);
            }
        }
    }

    public List<DocumentoResponse> buscarDocumentosPorEstudante(Long estudanteId) throws EstudanteNotFoundException, IOException {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        List<Documento> documentos = estudante.getDocumentos();

        if (documentos == null || documentos.isEmpty()) {
            return List.of();
        }

        // Reaproveita o conversor do ArmazenamentoService
        return armazenamentoService.converterDocumentosParaBase64(documentos);
    }

    public EstudanteResponse buscarEstudante(Long id) throws EstudanteNotFoundException {
        Estudante estudante = estudanteService.buscarEstudante(id);
        AlunoResponse userInfo = authServiceHandler.buscarAlunoPorId(estudante.getUserId());
        EstudanteResponse response = new EstudanteResponse(estudante, modelMapper);
        response.setAluno(userInfo);
        return response;
    }

    public Page<EstudanteResponse> listarEstudantes(Predicate predicate, Pageable pageable) throws EstudanteNotFoundException {
        Page<Estudante> estudantes = estudanteService.listarEstudantes(predicate, pageable);

        if (estudantes.isEmpty()) {
            return Page.empty();
        }

        List<UUID> userIds = estudantes.stream()
                .map(Estudante::getUserId)
                .toList();

        List<AlunoResponse> usuarios = authServiceHandler.buscarAlunos(userIds);
        if (usuarios.isEmpty()) {
            return Page.empty();
        }

        Map<UUID, AlunoResponse> mapaAlunos = usuarios.stream()
                .collect(Collectors.toMap(AlunoResponse::getId, Function.identity()));

        List<EstudanteResponse> listaEstudantes = estudantes.getContent().stream()
                .map(estudante -> {
                    EstudanteResponse resp = new EstudanteResponse(estudante, modelMapper);
                    // "lookup" no mapa para anexar o AlunoResponse correto
                    AlunoResponse ar = mapaAlunos.get(estudante.getUserId());
                    resp.setAluno(ar);
                    return resp;
                })
                .toList();
        return new PageImpl<>(
                listaEstudantes,
                pageable,
                estudantes.getTotalElements()
        );
    }

    @CircuitBreaker(name = "authServiceClient", fallbackMethod = "fallbackAtualizarEstudante")
    public EstudanteResponse atualizarEstudante(EstudanteUpdateRequest estudanteUpdateRequest)
            throws EstudanteNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Estudante estudanteParcial = new Estudante();
        Estudante estudante = estudanteService.buscarPorUserId(userId);

        if(estudanteUpdateRequest.getRendaPercapta() != null){
            estudanteParcial.setRendaPercapta(estudanteUpdateRequest.getRendaPercapta());
        }

        if(estudanteUpdateRequest.getContatoFamilia() != null ){
            estudanteParcial.setContatoFamilia(estudanteUpdateRequest.getContatoFamilia());
        }

        if(estudanteUpdateRequest.getDeficiente() != null){
            estudanteParcial.setDeficiente(estudanteUpdateRequest.getDeficiente());
        }

        if(estudanteUpdateRequest.getTipoDeficiencia() != null){
            estudanteParcial.setTipoDeficiencia(estudanteUpdateRequest.getTipoDeficiencia());
        }


        if(estudanteUpdateRequest.getEndereco() != null){
            EnderecoRequest enderecoDTO = estudanteUpdateRequest.getEndereco();
            Endereco enderecoAtualizado = enderecoDTO.convertToEntity(enderecoDTO, this.modelMapper);
            estudanteParcial.setEndereco(enderecoService.editarEndereco( estudante.getEndereco().getId(), enderecoAtualizado));
        }

        Estudante estudanteAtualizado = estudanteService.atualizarEstudante(estudanteParcial, estudante);
        return new EstudanteResponse(estudanteAtualizado, modelMapper);
    }

    public void deletarEstudante(Long id) throws EstudanteNotFoundException {
        estudanteService.deletarEstudante(id);
    }

    public Page<EstudanteResponse> listarEstudantesPorCurso(Long idCurso, Pageable pageable) {
        List<AlunoResponse> alunosNoCurso = authServiceHandler.buscarAlunosPorCurso(idCurso);

        List<UUID> userIds = alunosNoCurso.stream()
                .map(AlunoResponse::getId)
                .collect(Collectors.toList());

        Map<UUID, AlunoResponse> mapaAlunos = alunosNoCurso.stream()
                .collect(Collectors.toMap(AlunoResponse::getId, Function.identity()));

        Page<Estudante> pageEntidades = estudanteService.buscarPorUserIds(userIds, pageable);

        return pageEntidades.map(estudante -> {
            EstudanteResponse resp = new EstudanteResponse(estudante, modelMapper);
            resp.setAluno(mapaAlunos.get(estudante.getUserId()));
            return resp;
        });
    }

    public Page<CredorResponse> listarCredoresPorCurso(Long id, Pageable pageable) {
        Page<Estudante> estudantes = beneficioService.listarEstudantesComBeneficioAtivo(pageable);
        if (estudantes.isEmpty()) {
            return Page.empty(pageable);
        }
        List<AlunoResponse> alunosNoCurso = authServiceHandler.buscarAlunosPorCurso(id);

        return getCredorResponses(pageable, estudantes, alunosNoCurso);
    }

    public Page<AlunoResponse> listarCredoresParaPublicacao(Pageable pageable) {
        Page<Estudante> estudantes = beneficioService.listarEstudantesComBeneficioAtivo(pageable);
        if (estudantes.isEmpty()) {
            return Page.empty(pageable);
        }
        List<UUID> userIds = estudantes.stream().map(Estudante::getUserId).toList();

        List<AlunoResponse> alunos  = authServiceHandler.buscarAlunos(userIds);
        Map<UUID, AlunoResponse> mapaAlunos = alunos.stream()
                .collect(Collectors.toMap(AlunoResponse::getId, Function.identity()));

        List<AlunoResponse> conteudo = estudantes.getContent().stream()
                .map(est -> mapaAlunos.get(est.getUserId()))
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(conteudo, pageable, conteudo.size());
    }

    public Page<CredorResponse> listarCredoresComBeneficiosAtivos(Pageable pageable) {
        return gerarCredores(pageable, beneficioService::listarEstudantesComBeneficioAtivo);
    }

    public Page<CredorResponse> listarCredoresPorBeneficio(Long beneficioId, Pageable pageable) {
        return gerarCredores(pageable, pg ->
                beneficioService.listarEstudantesPorAuxilio(beneficioId, pg)
        );
    }
    public RelatorioEstudanteAssistidoResponse gerarRelatorioEstudanteAssistido(Long estudanteId)
            throws EstudanteNotFoundException {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        List<Beneficio> beneficiosAtivos = beneficioService.listarPorEstudante(estudanteId);

        if (beneficiosAtivos.isEmpty()) {
            throw new EstudanteSemAuxilioAtivoException();
        }

        List<RelatorioBeneficioResponse> beneficios = beneficiosAtivos.stream()
                .map(beneficio -> new RelatorioBeneficioResponse(
                        beneficio.getTipoBeneficio().getTipo(),
                        beneficio.getValorPagamento(),
                        beneficio.getInicioBeneficio().atEndOfMonth(),
                        beneficio.getFimBeneficio().atEndOfMonth()))
                .collect(Collectors.toList());

        var aluno = authServiceHandler.buscarAlunoPorId(estudante.getUserId());

        return new RelatorioEstudanteAssistidoResponse(
                aluno.getNome(),
                estudante.getRendaPercapta(),
                estudante.getContatoFamilia(),
                estudante.isDeficiente(),
                estudante.getTipoDeficiencia(),
                beneficios);
    }

    private Page<CredorResponse> getCredorResponses(Pageable pageable, Page<Estudante> estudantes, List<AlunoResponse> alunos) {
        Map<UUID, AlunoResponse> mapaAlunos = alunos.stream()
                .collect(Collectors.toMap(AlunoResponse::getId, Function.identity()));


        List<CredorResponse> listaCredores = estudantes.getContent().stream()
                .map(estudante -> {
                    AlunoResponse aluno = mapaAlunos.get(estudante.getUserId());

                    EstudanteResponse er = new EstudanteResponse(estudante, modelMapper);
                    er.setAluno(aluno);

                    List<Beneficio> beneficiosAtivos = beneficioService.listarPorEstudante(estudante.getId()).stream()
                            .filter(Beneficio::isAtivo).toList();


                    return new CredorResponse(
                            er,
                            estudante.getDadosBancarios(),
                            beneficiosAtivos
                    );
                })
                .toList();


        return new PageImpl<>(
                listaCredores,
                pageable,
                estudantes.getTotalElements()
        );
    }

    private Page<CredorResponse> gerarCredores(
            Pageable pageable,
            Function<Pageable, Page<Estudante>> fetchEstudantes
    ) {
        Page<Estudante> pageEstudantes = fetchEstudantes.apply(pageable);
        if (pageEstudantes.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> userIds = pageEstudantes.getContent().stream()
                .map(Estudante::getUserId)
                .toList();
        List<AlunoResponse> alunos = authServiceHandler.buscarAlunos(userIds);
        return getCredorResponses(pageable, pageEstudantes, alunos);
    }



    public EstudanteResponse buscarEstudanteAtual() throws EstudanteNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Estudante estudante = estudanteService.buscarPorUserId(userId);
        AlunoResponse userInfo = authServiceHandler.getAlunoInfo();
        EstudanteResponse response = new EstudanteResponse(estudante, modelMapper);
        response.setAluno(userInfo);
        return response;
    }

    public EstudanteResponse buscarEstudantePorUserId(UUID userId) throws EstudanteNotFoundException {
        Estudante estudante = estudanteService.buscarPorUserId(userId);
        AlunoResponse userInfo = authServiceHandler.buscarAlunoPorId(userId);
        EstudanteResponse response = new EstudanteResponse(estudante, modelMapper);
        response.setAluno(userInfo);
        return response;
    }

    // ================== Dados Bancarios ================== //

    public DadosBancarios salvarDadosBancarios(Long idEstudante, DadosBancarios dadosBancarios) {
        Estudante estudante = estudanteService.buscarEstudante(idEstudante);
        DadosBancarios salvo = dadosBancariosService.salvarDadosBancarios(dadosBancarios);
        estudante.setDadosBancarios(salvo);
        estudanteService.salvarEstudante(estudante);

        // Notificar o Estudante sobre o cadastro
        String msg = String.format("Seus dados bancários foram cadastrados pelo gestor. Por favor, verifique se estão corretos para o recebimento de benefícios.");
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(estudante.getUserId(), "Dados Bancários Cadastrados", msg, "SISTEMA"));

        return salvo;
    }

    public Page<DadosBancarios> listarDadosBancarios(Pageable pageable) {
        return dadosBancariosService.listarDadosBancarios(pageable);
    }

    public DadosBancarios buscarDadosBancarios(Long id) {
        return dadosBancariosService.buscarDadosBancarios(id);
    }

    public void deletarDadosBancarios(Long id) {
        dadosBancariosService.deletarDadosBancarios(id);
    }

    public DadosBancarios atualizarDadosBancarios(Long id, DadosBancarios novosDadosBancarios) {
        DadosBancarios dadosBancariosAtualizados = dadosBancariosService.atualizarDadosBancarios(id, novosDadosBancarios);

        // Notificar o Estudante sobre a atualização
        if (dadosBancariosAtualizados != null) {
            Estudante estudante = estudanteService.buscarPorDadosBancariosId(id);
            if (estudante != null) {
                String msg = "Seus dados bancários foram atualizados pelo gestor. Por favor, acesse o sistema e verifique as novas informações.";
                notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(estudante.getUserId(), "Dados Bancários Atualizados", msg, "SISTEMA"));
            }
        }

        return dadosBancariosAtualizados;
    }

    // ------------------- TipoAtendimento ------------------- //
    public Page<TipoAtendimento> listarTipoAtendimentos(Predicate predicate, Pageable pageable) {
        return tipoAtendimentoService.listar(predicate, pageable);
    }

    public TipoAtendimento buscarTipoAtendimento(Long id) throws TipoAtendimentoNotFoundException {
        return tipoAtendimentoService.buscar(id);
    }

    public TipoAtendimento salvarTipoAtendimento(TipoAtendimento tipoAtendimento) {
        return tipoAtendimentoService.salvar(tipoAtendimento);
    }

    public TipoAtendimento editarTipoAtendimento(Long id, TipoAtendimentoUpdateRequest dto)
            throws TipoAtendimentoNotFoundException {
        TipoAtendimento tipoAtendimento = tipoAtendimentoService.buscar(id);
        if (dto.getNome() != null && !dto.getNome().isBlank()) {
            tipoAtendimento.setNome(dto.getNome());
        }

        if (dto.getTempoAtendimento() != null) {
            tipoAtendimento.setTempoAtendimento(dto.getTempoAtendimento());
        }

        if(dto.getHorarios() != null) {
            tipoAtendimento.setHorarios(dto.getHorarios());
        }

        return tipoAtendimentoService.editar(id, tipoAtendimento);
    }

    public void deletarTipoAtendimento(Long id) throws TipoAtendimentoNotFoundException {
        if (cronogramaService.existeCronogramaPorTipoAtendimento(id)) {
            throw new TipoAtendimentoComCronogramaException();
        }

        tipoAtendimentoService.deletar(id);
    }

    public TipoAtendimento deletarHorarioTipoAtendimento(Long id, int index) throws TipoAtendimentoNotFoundException {
        return tipoAtendimentoService.deletarHorario(id, index);
    }

    // ------------------- Cronograma ------------------- //
    public Page<Cronograma> listarCronogramasPorProfissional(Pageable pageable) {

        return cronogramaService.listarPorProfissional(authenticatedUserProvider.getUserId(), pageable);
    }

    public Page<Cronograma> listarCronogramas(Predicate predicate, Pageable pageable) {
        return cronogramaService.listar(predicate, pageable);
    }

    public Page<Cronograma> listarCronogramasPorTipoAtendimento(Long tipoAtendimentoId, Pageable pageable) {
        return cronogramaService.listarPorTipoAtendimento(tipoAtendimentoId, pageable);
    }

    public Cronograma buscarCronograma(Long id) throws CronogramaNotFoundException {
        return cronogramaService.buscar(id);
    }

    @Transactional
    public Cronograma salvarCronograma(Cronograma cronograma, Long tipoAtendimentoId)
            throws TipoAtendimentoNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        TipoAtendimento tipoAtendimento = buscarTipoAtendimento(tipoAtendimentoId);
        Profissional profissional = profissionalService.buscarPorUserId(userId);
        cronograma.setProfissional(profissional);
        cronograma.setTipoAtendimento(tipoAtendimento);
        List<Vaga> vagas = vagaService.gerarVagas(tipoAtendimento.getHorarios(), tipoAtendimento.getTempoAtendimento());
        vagas.forEach(vaga -> vaga.setCronograma(cronograma));
        cronograma.setVagas(vagas);
        return cronogramaService.salvar(cronograma);
    }

    @Transactional
    public Cronograma editarCronograma(Long cronogramaId, Cronograma cronograma, Long tipoAtendimentoId)
            throws TipoAtendimentoNotFoundException, CronogramaNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        TipoAtendimento tipoAtendimento = buscarTipoAtendimento(tipoAtendimentoId);
        Cronograma cronogramaExistente = cronogramaService.buscar(cronogramaId);
        if (!cronogramaExistente.getProfissional().getUserId().equals(userId)) {
            throw new NotAllowedException("Você não tem permissão para editar este cronograma.");
        }
        cronogramaExistente.setData(cronograma.getData());
        cronogramaExistente.setTipoAtendimento(tipoAtendimento);
        List<Vaga> novas = vagaService
                .gerarVagas(tipoAtendimento.getHorarios(), tipoAtendimento.getTempoAtendimento());
        cronogramaExistente.trocarVagas(novas);
        return cronogramaService.salvar(cronogramaExistente);
    }

    public void deletarCronograma(Long id) throws CronogramaNotFoundException {
        cronogramaService.deletar(id);
    }

    // ------------------- Agendamento ------------------- //

    @Transactional
    public AgendamentoResponse agendarVaga(AgendamentoRequest request) throws VagaNotFoundException, UnavailableVagaException {
        try {
            // 1. Busca a vaga a partir do ID contido na requisição
            Vaga vaga = vagaService.buscar(request.getVagaId());

            // 2. Busca o estudante logado que está fazendo a requisição
            Estudante estudante = estudanteService.buscarPorUserId(authenticatedUserProvider.getUserId());

            if (vaga.isDisponivel()) {
                vaga.setDisponivel(false);
                vagaService.salvar(vaga); // Tranca a vaga para evitar concorrência

                // 3. O serviço agora recebe a Vaga, o Estudante e a Modalidade escolhida
                Agendamento agendamento = agendamentoService.agendar(vaga, estudante, request.getModalidade());

                return mapToAgendamentoResponse(agendamento);
            }

            throw new UnavailableVagaException();

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new UnavailableVagaException();
        }
    }

    @Transactional
    public CancelamentoAgendamento cancelarAgendamento(Long id, CancelamentoAgendamento cancelamento)
            throws AgendamentoNotFoundException {
        Agendamento agendamento = agendamentoService.buscar(id);
        if (!Objects.equals(agendamento.getEstudante().getUserId(), authenticatedUserProvider.getUserId())
                && !Objects.equals(agendamento.getVaga().getCronograma().getProfissional().getUserId(),
                authenticatedUserProvider.getUserId())) {
            throw new GlobalAccessDeniedException("Você não tem permissão para acessar este recurso");
        }

        Vaga vaga = agendamento.getVaga();
        vaga.setDisponivel(true);
        vagaService.salvar(vaga);
        agendamento.setAtivo(false);
        agendamentoService.salvar(agendamento);
        cancelamento.setAgendamento(agendamento);
        return cancelamentoService.salvar(cancelamento);
    }

    public AgendamentoResponse buscarAgendamento(Long id) throws AgendamentoNotFoundException {
        Agendamento agendamento = agendamentoService.buscar(id);
        return mapToAgendamentoResponse(agendamento);
    }

    public Page<AgendamentoResponse> listarAgendamentosPorEstudante(Long estudanteId, Pageable pageable) {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        return agendamentoService.listarAgendamentosPorEstudante(estudante, pageable)
                .map(this::mapToAgendamentoResponse);
    }

    public Page<AgendamentoResponse> listarAgendamentosPorProfissional(Long userId, Pageable pageable) {
        Profissional profissional = profissionalService.buscar(userId);
        return agendamentoService.listarPorProfissional(profissional, pageable)
                .map(this::mapToAgendamentoResponse);
    }

    public Page<AgendamentoResponse> listarAgendamentoPorEstudanteAtual(Pageable pageable) {
        return agendamentoService.listarAgendamentosEstudanteAtual(pageable)
                .map(this::mapToAgendamentoResponse);
    }

    public Page<AgendamentoResponse> listarAgendamentoPorProfissionalAtual(Pageable pageable) {
        return agendamentoService.listarPorProfissionalAtual(pageable)
                .map(this::mapToAgendamentoResponse);
    }

    public Page<CancelamentoAgendamento> listarCancelamentosPorEstudanteAtual(Pageable pageable) {
        return cancelamentoService.ListarPorEstudanteAtual(pageable);
    }

    public Page<CancelamentoAgendamento> listarCancelamentosPorProfissionalAtual(Pageable pageable) {
        return cancelamentoService.ListarPorProfissionalAtual(pageable);
    }

    public CancelamentoAgendamento buscarCancelamento(Long id) throws CancelamentoNotFoundException {
        return cancelamentoService.buscar(id);
    }

    @Transactional
    public Agendamento alterarModalidadeAgendamento(Long id, ModalidadeAgendamento novaModalidade) throws AgendamentoNotFoundException {
        // A validação de tempo limite (2h antes) e a edição ocorrem no Service.
        return agendamentoService.alterarModalidade(id, novaModalidade);
    }

    private AgendamentoResponse mapToAgendamentoResponse(Agendamento agendamento) {
        AgendamentoResponse response = new AgendamentoResponse(agendamento, modelMapper);
        if (agendamento.getEstudante() != null && agendamento.getEstudante().getUserId() != null) {
            AlunoResponse aluno = authServiceHandler.buscarAlunoPorId(agendamento.getEstudante().getUserId());
            if (response.getEstudante() != null) {
                response.getEstudante().setAluno(aluno);
            }
        }
        return response;
    }


    // ------------------- TipoBeneficio ------------------- //
    public Page<TipoBeneficio> listarTipoBeneficios(Predicate predicate, Pageable pageable) {
        return tipoBeneficioService.listar(predicate, pageable);
    }

    public Page<TipoBeneficio> listarTipoBeneficiosInativos(Predicate predicate, Pageable pageable) {
        return tipoBeneficioService.listarInativos(predicate, pageable);
    }

    public TipoBeneficio buscarTipoBeneficio(Long id) throws TipoBeneficioNotFoundException {
        return tipoBeneficioService.buscar(id);
    }

    public TipoBeneficio salvarTipoBeneficio(TipoBeneficio tipoBeneficio) {
        return tipoBeneficioService.salvar(tipoBeneficio);
    }

    public TipoBeneficio editarTipoBeneficio(Long id, TipoBeneficio tipoBeneficio) throws TipoBeneficioNotFoundException {
        return tipoBeneficioService.editar(id, tipoBeneficio);
    }

    public void deletarTipoBeneficio(Long id) throws TipoBeneficioNotFoundException {
        tipoBeneficioService.deletar(id);
    }

    public void desativarTipoBeneficio(Long id) throws TipoBeneficioNotFoundException {
        tipoBeneficioService.desativar(id);
    }

    public Long contarTiposBeneficio() {
        return tipoBeneficioService.contarTiposAtivos();
    }

    // ------------------- Beneficio ------------------- //
    public Page<Beneficio> listarBeneficios(Predicate predicate, Pageable pageable) {
        return beneficioService.listar(predicate, pageable);
    }

    public Page<BeneficioResponse> listarBeneficiosInativos(Predicate predicate, Pageable pageable) {
        return beneficioService.listarInativos(predicate, pageable)
                .map(this::mapToBeneficioResponse);
    }

    public Page<Beneficio> listarBeneficiosPorTipo(Long tipoId, Pageable pageable) throws BeneficioNotFoundException {
        return beneficioService.listarPorTipo(tipoId, pageable);
    }

    public Page<Beneficio> listarBeneficiosPorEstudanteId(Long estudanteId, Pageable pageable) throws EstudanteNotFoundException {
        return beneficioService.listarPorEstudante(estudanteId, pageable);
    }

    public Beneficio buscarBeneficios(Long id) throws BeneficioNotFoundException {
        return beneficioService.buscar(id);
    }

    public Beneficio salvarBeneficios(Long estudanteId, Beneficio beneficio, MultipartFile termo, Long tipoBeneficioId)
            throws TipoBeneficioNotFoundException {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        TipoBeneficio tipoBeneficio = tipoBeneficioService.buscar(tipoBeneficioId);
        beneficio.setEstudantes(estudante);
        beneficio.setTipoBeneficio(tipoBeneficio);

        MultipartFile[] arquivos = { termo };
        List<Documento> documentos = armazenamentoService.salvarArquivo(arquivos);
        beneficio.setTermo(documentos.getFirst());


        return beneficioService.salvar(beneficio);

    }

    public Beneficio editarBeneficios(Long id, Long estudanteId, Beneficio beneficio, MultipartFile termo, Long tipoBeneficioId)
            throws BeneficioNotFoundException, TipoBeneficioNotFoundException {
        Beneficio aux = buscarBeneficios(id);
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        TipoBeneficio tipoBeneficio = tipoBeneficioService.buscar(tipoBeneficioId);
        beneficio.setEstudantes(estudante);
        beneficio.setTipoBeneficio(tipoBeneficio);

        if (termo != null) {
            MultipartFile[] arquivos = { termo };
            List<Documento> documentos = armazenamentoService.salvarArquivo(arquivos);
            beneficio.setTermo(documentos.getFirst());
            beneficio.getTermo().setId(aux.getTermo().getId());
        }

        return beneficioService.editar(aux, beneficio);
    }

    @Transactional
    public void cancelarBeneficio(Long id, BeneficioCancelamentoRequest request) throws BeneficioNotFoundException {

        beneficioService.cancelar(
                id,
                request.getMotivoEncerramento(),
                request.getParecerTermino()
        );
    }

    @Transactional
    public BeneficioResponse prorrogarBeneficio(Long id, BeneficioProrrogacaoRequest request) throws BeneficioNotFoundException {

        Beneficio beneficioAtualizado = beneficioService.prorrogar(
                id,
                request.getNovoPrazo(),
                request.getObservacoes()
        );

        return mapToBeneficioResponse(beneficioAtualizado);
    }

    public void deletarBeneficio(Long id) throws BeneficioNotFoundException {
        beneficioService.deletar(id);
    }

    public Page<BeneficioResponse> listarBeneficiosPendentesMesAtual(Predicate predicate, Pageable pageable) {
        Page<Beneficio> pageBeneficios = beneficioService.listarBeneficiosPendentesMesAtual(predicate, pageable);
        return pageBeneficios.map(this::mapToBeneficioResponse);
    }

    public List<Beneficio> buscarBeneficioPorPagamentoId(Long pagamentoId) {
        return beneficioService.buscarPorPagamento(pagamentoId);
    }

//    public RelatorioFinanceiroResponse gerarRelatorioFinanceiro(Predicate predicate) {
//        // 1. Coleta os dados financeiros globais filtrados pelo PagamentoService
//        BigDecimal totalGeralBD = pagamentoService.obterValorTotalPagamentosAtivos(predicate);
//        Double totalGeral = (totalGeralBD != null) ? totalGeralBD.doubleValue() : 0.0;
//
//        // 2. Coleta a divisão de valores por Tipo de Benefício
//        List<Object[]> dadosPorTipo = pagamentoService.obterValorTotalPorTipoBeneficio(predicate);
//        List<RelatorioFinanceiroResponse.ValorPorTipoDTO> valorPorTipo = dadosPorTipo.stream()
//                .map(obj -> new RelatorioFinanceiroResponse.ValorPorTipoDTO(
//                        (Long) obj[0],
//                        (String) obj[1],
//                        ((BigDecimal) obj[2]).doubleValue()
//                )).toList();
//
//        // 3. Obter os estudantes ÚNICOS atrelados aos pagamentos que passaram no filtro
//        List<java.util.UUID> userIdsAtivos = pagamentoService.obterUserIdsEstudantesComPagamento(predicate);
//
//        // 4. Delegar ao BeneficioService o agrupamento de Cursos usando a lista de alunos exata
//        List<java.util.Map<String, Object>> dadosCursos = beneficioService.obterQuantidadeBeneficiadosPorCurso(userIdsAtivos);
//
//        List<RelatorioFinanceiroResponse.BeneficiadosPorCursoDTO> beneficiadosPorCurso = dadosCursos.stream()
//                .map(map -> new RelatorioFinanceiroResponse.BeneficiadosPorCursoDTO(
//                        (Long) map.get("cursoId"),
//                        (String) map.get("cursoNome"),
//                        (Long) map.get("quantidadeBeneficiados")
//                )).toList();
//
//        // 5. Constrói o objeto final
//        return RelatorioFinanceiroResponse.builder()
//                .totalGeral(totalGeral)
//                .quantidadePessoasAtendidas(userIdsAtivos.size()) // Usamos o tamanho da lista filtrada
//                .quantidadeTiposBeneficio(valorPorTipo.size())
//                .quantidadeCursosDistintos(beneficiadosPorCurso.size())
//                .valorTotalPorTipoBeneficio(valorPorTipo)
//                .quantidadeBeneficiadosPorCurso(beneficiadosPorCurso)
//                .build();
//    }

    public RelatorioFinanceiroResponse gerarRelatorioFinanceiro(Predicate predicate, Long cursoId) {
        QPagamento qPagamento = QPagamento.pagamento;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(qPagamento.ativo.isTrue());

        if (predicate != null) {
            builder.and(predicate);
        }

        // 1. Pegamos TODOS os UUIDs de estudantes que têm pagamento para o filtro atual
        List<java.util.UUID> userIdsGerais = pagamentoService.obterUserIdsEstudantesComPagamento(builder);

        if (userIdsGerais.isEmpty()) {
            return construirRelatorioVazio();
        }

        // 2. FILTRO POR CURSO MÁGICO (Usando a rota pública que funciona)
        if (cursoId != null) {
            // Buscamos os dados públicos (com os cursos) apenas dos alunos que têm pagamentos
            // Importante: use o método novo que chama o /public/batch
            List<br.edu.ufape.sguPraeService.comunicacao.dto.usuario.AlunoPublicResponse> alunosBatch =
                    authServiceHandler.buscarAlunosPublicos(userIdsGerais);

            // Filtramos na memória para reter apenas os UUIDs de quem é do curso pesquisado
            List<java.util.UUID> userIdsDoCurso = alunosBatch.stream()
                    .filter(a -> a.getCurso() != null && a.getCurso().getId().equals(cursoId))
                    .map(br.edu.ufape.sguPraeService.comunicacao.dto.usuario.AlunoPublicResponse::getId)
                    .toList();

            // Se, após o filtro, não sobrar ninguém, retornamos zerado
            if (userIdsDoCurso.isEmpty()) {
                return construirRelatorioVazio();
            }

            // Injetamos a trava no banco: some apenas os pagamentos DESTES alunos
            builder.and(qPagamento.beneficio.estudantes.userId.in(userIdsDoCurso));

            // Atualizamos a lista de ativos para a contagem final
            userIdsGerais = userIdsDoCurso;
        }

        // 3. Calcula Totais Financeiros com o builder já restringido (se tiver curso)
        BigDecimal totalGeralBD = pagamentoService.obterValorTotalPagamentosAtivos(builder);
        Double totalGeral = (totalGeralBD != null) ? totalGeralBD.doubleValue() : 0.0;

        List<Object[]> dadosPorTipo = pagamentoService.obterValorTotalPorTipoBeneficio(builder);
        List<RelatorioFinanceiroResponse.ValorPorTipoDTO> valorPorTipo = dadosPorTipo.stream()
                .map(obj -> new RelatorioFinanceiroResponse.ValorPorTipoDTO(
                        (Long) obj[0],
                        (String) obj[1],
                        ((BigDecimal) obj[2]).doubleValue()
                )).toList();

        // 4. Distribuição por Cursos
        // Já temos a lista de UUIDs certinha, passamos pro serviço agrupar
        List<java.util.Map<String, Object>> dadosCursos = beneficioService.obterQuantidadeBeneficiadosPorCurso(userIdsGerais);

        List<RelatorioFinanceiroResponse.BeneficiadosPorCursoDTO> beneficiadosPorCurso = dadosCursos.stream()
                .map(map -> new RelatorioFinanceiroResponse.BeneficiadosPorCursoDTO(
                        (Long) map.get("cursoId"),
                        (String) map.get("cursoNome"),
                        (Long) map.get("quantidadeBeneficiados")
                )).toList();

        return RelatorioFinanceiroResponse.builder()
                .totalGeral(totalGeral)
                .quantidadePessoasAtendidas(userIdsGerais.size())
                .quantidadeTiposBeneficio(valorPorTipo.size())
                .quantidadeCursosDistintos(beneficiadosPorCurso.size())
                .valorTotalPorTipoBeneficio(valorPorTipo)
                .quantidadeBeneficiadosPorCurso(beneficiadosPorCurso)
                .build();
    }

    // Método auxiliar apenas para deixar o código limpo e evitar repetição
    private RelatorioFinanceiroResponse construirRelatorioVazio() {
        return RelatorioFinanceiroResponse.builder()
                .totalGeral(0.0)
                .quantidadePessoasAtendidas(0)
                .quantidadeTiposBeneficio(0)
                .quantidadeCursosDistintos(0)
                .valorTotalPorTipoBeneficio(new java.util.ArrayList<>())
                .quantidadeBeneficiadosPorCurso(new java.util.ArrayList<>())
                .build();
    }

    public BeneficioResponse mapToBeneficioResponse(Beneficio beneficio) {
        BeneficioResponse response = new BeneficioResponse(beneficio, modelMapper);

        if (beneficio.getEstudantes() != null && beneficio.getEstudantes().getUserId() != null) {
            AlunoResponse aluno = authServiceHandler.buscarAlunoPorId(beneficio.getEstudantes().getUserId());
            if (response.getEstudantes() != null) {
                response.getEstudantes().setAluno(aluno);
            }
        }

        return response;
    }

    public Long contarEstudantesBeneficiados() {
        return beneficioService.contarEstudantesBeneficiados();
    }

    public Long contarCursosDistintosComBeneficioAtivo() {
        return beneficioService.contarCursosDistintosComBeneficioAtivo();
    }

    // ------------------- Pagamento ------------------- //

    public Page<Pagamento> listarPagamentos(Predicate predicate, Pageable pageable) {
        return pagamentoService.listar(predicate, pageable);
    }

    public List<Pagamento> listarPagamentosPorBeneficioId(Long beneficioId) throws BeneficioNotFoundException {
        Beneficio beneficio = beneficioService.buscar(beneficioId);
        return new ArrayList<>(beneficio.getPagamentos());
    }

    public Page<Beneficio> listarPagosPorMes(Pageable pageable) {
        return beneficioService.listarPagosPorMes(pageable);
    }

    public Pagamento buscarPagamento(Long id) throws PagamentoNotFoundException {
        return pagamentoService.buscar(id);
    }

    public List<Pagamento> salvarPagamentos(List<Pagamento> pagamentos) throws BeneficioNotFoundException {
        for (Pagamento p : pagamentos) {
            Long id = p.getBeneficio().getId();
            Beneficio b = buscarBeneficios(id);
            p.setBeneficio(b);
        }
        return pagamentoService.salvar(pagamentos);
    }

    public Pagamento editarPagamento(Long id, PagamentoPatchRequest dto) throws PagamentoNotFoundException {
        Pagamento pagamento = pagamentoService.buscar(id);

        if (dto.getValor() != null) {
            pagamento.setValor(dto.getValor());
        }
        if (dto.getData() != null) {
            pagamento.setData(dto.getData());
        }
        return pagamentoService.salvar(List.of(pagamento)).getFirst();
    }

    public void deletarPagamento(Long id) throws PagamentoNotFoundException {
        pagamentoService.deletar(id);
    }

    public void desativarPagamento(Long id) throws PagamentoNotFoundException {
        pagamentoService.desativar(id);
    }

    public Page<Pagamento> listarPagamentosPorValor(BigDecimal min, BigDecimal max, Pageable pageable) {
        return pagamentoService.listarPorValor(min, max, pageable);
    }

    public List<Pagamento> listarPagamentosPorEstudante(Long estudanteId) {
        return pagamentoService.listarPorEstudanteId(estudanteId);
    }public Page<PagamentoResponse> listarPagamentosPorEstudante(Long estudanteId, Predicate predicate, Pageable pageable) {
        QPagamento qPagamento = QPagamento.pagamento;

        // BooleanBuilder é usado para mesclar a trava de segurança com os filtros da URL
        BooleanBuilder construtorFiltros = new BooleanBuilder();

        // TRAVA OBRIGATÓRIA: Só traz pagamentos do estudante informado na URL
        construtorFiltros.and(qPagamento.beneficio.estudantes.id.eq(estudanteId));

        // Adiciona os filtros dinâmicos vindos do frontend (se houverem)
        if (predicate != null) {
            construtorFiltros.and(predicate);
        }

        // Utiliza o método listar genérico do PagamentoService que já aceita Predicate e Pageable
        Page<Pagamento> pagamentos = pagamentoService.listar(construtorFiltros.getValue(), pageable);

        // Retorna já mapeado para o DTO de resposta
        return pagamentos.map(this::mapToPagamentoResponse);
    }

    @Transactional
    public Pagamento salvarPagamentoPorCpf(PagamentoCPFRequest request) {
        log.info("Buscando aluno no Auth Service com CPF: {}", request.getCpf());

        // Prepara as duas versões do CPF: com e sem formatação
        String cpfOriginal = request.getCpf();
        String cpfSemFormatacao = cpfOriginal.replaceAll("[^0-9]", "");
        String cpfFormatado = formatarCpf(cpfSemFormatacao);

        // Tenta buscar o aluno no Auth Service
        PageResponse<AlunoResponse> page = null;

        // Primeira tentativa: com o CPF como veio na requisição
        try {
            page = authServiceClient.buscarAlunoPorCpf(cpfOriginal);
            if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                log.info("Aluno encontrado com CPF original: {}", cpfOriginal);
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar com CPF original: {}", cpfOriginal, e);
        }

        // Segunda tentativa: se não encontrou e o CPF original não estava formatado, tenta com formatação
        if ((page == null || page.getContent() == null || page.getContent().isEmpty()) && !cpfOriginal.equals(cpfFormatado)) {
            try {
                log.info("Tentando buscar com CPF formatado: {}", cpfFormatado);
                page = authServiceClient.buscarAlunoPorCpf(cpfFormatado);
                if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                    log.info("Aluno encontrado com CPF formatado: {}", cpfFormatado);
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar com CPF formatado: {}", cpfFormatado, e);
            }
        }

        // Terceira tentativa: se não encontrou e o CPF original estava formatado, tenta sem formatação
        if ((page == null || page.getContent() == null || page.getContent().isEmpty()) && !cpfOriginal.equals(cpfSemFormatacao)) {
            try {
                log.info("Tentando buscar com CPF sem formatação: {}", cpfSemFormatacao);
                page = authServiceClient.buscarAlunoPorCpf(cpfSemFormatacao);
                if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                    log.info("Aluno encontrado com CPF sem formatação: {}", cpfSemFormatacao);
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar com CPF sem formatação: {}", cpfSemFormatacao, e);
            }
        }

        // Verifica se conseguiu encontrar o aluno em alguma das tentativas
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            log.error("Nenhum aluno encontrado após tentar com CPF original ({}), formatado ({}) e sem formatação ({})",
                cpfOriginal, cpfFormatado, cpfSemFormatacao);
            throw new EstudanteNotFoundException("Aluno com CPF " + request.getCpf() + " não encontrado no sistema de autenticação");
        }

        // Pega o primeiro aluno da lista
        AlunoResponse aluno = page.getContent().getFirst();

        if (aluno.getId() == null) {
            log.error("Aluno encontrado mas sem ID para CPF: {}", request.getCpf());
            throw new EstudanteNotFoundException("Aluno encontrado mas com dados inválidos");
        }

        log.info("Aluno encontrado: {} (userId: {})", aluno.getNome(), aluno.getId());

        UUID userId = aluno.getId();
        Estudante estudante;

        try {
            estudante = estudanteService.buscarPorUserId(userId);
            log.info("Estudante encontrado no banco local: ID {}, Nome: {}", estudante.getId(), aluno.getNome());
        } catch (Exception e) {
            log.error("Estudante com userId {} não encontrado no banco local do PRAE", userId);
            throw new EstudanteNotFoundException("Aluno existe no sistema mas não está cadastrado como estudante no PRAE");
        }

        List<Beneficio> beneficiosAtivos = beneficioService.listarPorEstudante(estudante.getId());

        if (beneficiosAtivos.isEmpty()) {
            log.warn("Estudante {} não possui benefícios ativos", estudante.getId());
            throw new EstudanteSemAuxilioAtivoException();
        }

        Beneficio beneficioSelecionado;
        if (request.getTipoBeneficioId() != null) {
            beneficioSelecionado = beneficiosAtivos.stream()
                .filter(b -> b.getTipoBeneficio().getId().equals(request.getTipoBeneficioId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("O estudante não possui o benefício informado ativo"));
        } else {
            if (beneficiosAtivos.size() > 1) {
                throw new IllegalArgumentException(
                    "O estudante possui múltiplos benefícios ativos ("+ beneficiosAtivos.size() +"). É obrigatório informar o Tipo de benefício."
                );
            }
            beneficioSelecionado = beneficiosAtivos.getFirst();
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setValor(request.getValor());
        pagamento.setData(request.getData());
        pagamento.setMesReferencia(request.getMesReferencia());
        pagamento.setAnoReferencia(request.getAnoReferencia());
        pagamento.setNumeroLote(request.getNumeroLote());
        pagamento.setBeneficio(beneficioSelecionado);

        log.info("Salvando pagamento para estudante {} no benefício {}", estudante.getId(), beneficioSelecionado.getId());

        return pagamentoService.salvarIndividual(pagamento);
    }

    public FolhaPagamentoResponse gerarFolhaPagamento(Integer ano, Integer mes, String numeroLote) {
        // 1. Gera a folha com dados locais (bancários e valores)
        FolhaPagamentoResponse folha = pagamentoService.gerarFolhaPagamento(ano, mes, numeroLote);

        if (folha.getItens().isEmpty()) {
            return folha;
        }

        // 2. Extrai a lista de UUIDs distintos para busca em lote
        List<UUID> userIds = folha.getItens().stream()
                .map(ItemFolhaPagamentoResponse::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 3. Busca em Lote no Auth Service (1 Requisição apenas!)
        Map<UUID, AlunoResponse> mapaAlunos;
        try {
            List<AlunoResponse> alunosEncontrados = authServiceHandler.buscarAlunos(userIds);

            // Transforma a lista em Mapa para acesso rápido O(1)
            mapaAlunos = alunosEncontrados.stream()
                    .collect(Collectors.toMap(AlunoResponse::getId, Function.identity()));

        } catch (Exception e) {
            // Em caso de falha crítica (fallback retornou lista vazia), criamos mapa vazio
            mapaAlunos = new HashMap<>();
        }

        // 4. Preenche os dados na folha
        for (ItemFolhaPagamentoResponse item : folha.getItens()) {
            AlunoResponse aluno = mapaAlunos.get(item.getUserId());

            if (aluno != null) {
                item.setNomeEstudante(aluno.getNome());
                item.setCpf(aluno.getCpf());
                item.setMatricula(aluno.getMatricula());
            } else {
                // Caso o aluno não venha do Auth (ex: deletado ou erro parcial)
                item.setNomeEstudante("Nome não disponível");
                item.setCpf("---");
            }
        }

        return folha;
    }

    public BigDecimal obterValorTotalPagamentosAtivos(Predicate predicate) {
        return pagamentoService.obterValorTotalPagamentosAtivos(predicate);
    }

    public PagamentoResponse mapToPagamentoResponse(Pagamento pagamento) {
        PagamentoResponse response = new PagamentoResponse(pagamento, modelMapper);

        if (pagamento.getBeneficio() != null) {
            Beneficio beneficio = pagamento.getBeneficio();
            BeneficioResponse beneficioResponse = new BeneficioResponse(beneficio, modelMapper);

            if (beneficio.getEstudantes() != null && beneficio.getEstudantes().getUserId() != null) {
                AlunoResponse aluno = authServiceHandler.buscarAlunoPorId(beneficio.getEstudantes().getUserId());
                if (beneficioResponse.getEstudantes() != null) {
                    beneficioResponse.getEstudantes().setAluno(aluno);
                }
            }

            response.setBeneficio(beneficioResponse);
        }

        return response;
    }

    public List<Map<String, Object>> obterQuantidadeBeneficiadosPorCurso(Predicate predicate) {
        List<UUID> userIds = pagamentoService.obterUserIdsEstudantesComPagamento(predicate);
        return beneficioService.obterQuantidadeBeneficiadosPorCurso(userIds);
    }

    public List<Object[]> obterValorTotalPorTipoBeneficio(Predicate predicate) {
        return pagamentoService.obterValorTotalPorTipoBeneficio(predicate);
    }

    // ------------------- Armazenamento ------------------- //

//    // ------------------- Documentos (Upload Genérico) ------------------- //
//
//    /**
//     * Realiza o upload de um documento
//     * @param arquivo O arquivo a ser salvo
//     * @return Documento salvo com metadados persistidos
//     */
//    public Documento uploadDocumento(MultipartFile arquivo) {
//        return documentoService.salvar(arquivo);
//    }
//
//    /**
//     * Busca um documento pelo ID (apenas documentos do usuário logado)
//     * @param id ID do documento
//     * @return O documento encontrado
//     * @throws DocumentoNotFoundException se o documento não for encontrado ou não pertencer ao usuário
//     */
//    public Documento buscarDocumento(Long id) throws DocumentoNotFoundException {
//        return documentoService.buscar(id);
//    }
//
//    /**
//     * Lista todos os documentos do usuário logado
//     * @return Lista de documentos do usuário
//     */
//    public List<Documento> listarDocumentosDoUsuario() {
//        return documentoService.listarPorUsuario();
//    }
//
//    /**
//     * Lista todos os documentos do sistema
//     * @return Lista de todos os documentos
//     */
//    public List<Documento> listarTodosDocumentos() {
//        return documentoService.listarTodos();
//    }
//
//    /**
//     * Remove um documento do sistema (apenas se pertencer ao usuário logado)
//     * @param id ID do documento
//     * @throws DocumentoNotFoundException se o documento não for encontrado ou não pertencer ao usuário
//     */
//    public void deletarDocumento(Long id) throws DocumentoNotFoundException {
//        documentoService.deletar(id);
//    }

    public List<DocumentoResponse> converterDocumentosParaBase64(List<Documento> documentos) throws IOException {
        return armazenamentoService.converterDocumentosParaBase64(documentos);
    }

    // ------------------- Métodos Auxiliares ------------------- //

    /**
     * Formata um CPF para o padrão XXX.YYY.ZZZ-MM
     * @param cpf CPF apenas com números (11 dígitos)
     * @return CPF formatado ou o CPF original se não tiver 11 dígitos
     */
    private String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf;
        }
        return String.format("%s.%s.%s-%s",
            cpf.substring(0, 3),
            cpf.substring(3, 6),
            cpf.substring(6, 9),
            cpf.substring(9, 11)
        );
    }

}
