package br.edu.ufape.sguPraeService.fachada;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.auth.TokenResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.gestor.GestorRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.notificacao.NotificacaoBroadcastRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoPatchRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalUpdateRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoUpdateRequest;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.exceptions.*;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.*;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import br.edu.ufape.sguPraeService.servicos.interfaces.*;
import com.querydsl.core.BooleanBuilder;
import jakarta.ws.rs.NotAllowedException;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CursoNotFoundException;
import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.querydsl.core.types.Predicate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final ModelMapper modelMapper;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AgendamentoService agendamentoService;
    private final CancelamentoService cancelamentoService;
    private final TipoBeneficioService tipoBeneficioService;
    private final BeneficioService beneficioService;
    private final PagamentoService pagamentoService;
    private final ArmazenamentoService armazenamentoService;
    private final NotificacaoPublisher notificacaoPublisher;
    private final KeycloakService keycloakService;
    private final UsuarioService usuarioService;
    private final TipoEtniaService tipoEtniaService;
    private final GestorService gestorService;
    private final CursoService cursoService;
    private final NotificacaoSseServiceInterface notificacaoSseService;
    private final NotificacaoRedisServiceInterface notificacaoRedisService;

    // ================== Auth ================== //

    public TokenResponse login(String username, String password) {
        return keycloakService.login(username, password);
    }

    public TokenResponse refresh(String refreshToken) {
        return keycloakService.refreshToken(refreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        keycloakService.logout(accessToken, refreshToken);
    }

    public void resetPassword(String email) {
        keycloakService.resetPassword(email);
    }

    public List<String> getUserRoles() {
        UUID sessionId = authenticatedUserProvider.getUserId();
        return keycloakService.getUserRoles(sessionId.toString());
    }

    // ================== Gestor ================== //

    public Gestor buscarGestor(UUID id) throws GestorNotFoundException, UsuarioNotFoundException {
        UUID sessionId = authenticatedUserProvider.getUserId();
        boolean isAdmin = keycloakService.getUserRoles(sessionId.toString()).contains("administrador");
        return gestorService.buscarGestor(id, isAdmin, sessionId);
    }

    public Page<Gestor> listarGestores(Predicate predicate, Pageable pageable) {
        return gestorService.listarGestores(predicate, pageable);
    }

    // ================== Usuario ================== //

    // MÉTODOS DE CRIAÇÃO (CADASTROS DISTINTOS)

    @Transactional
    public Usuario cadastrarEstudante(EstudanteRequest dto, List<MultipartFile> arquivos) throws Exception {
        // 1. Cria a conta no Keycloak e recupera o UUID gerado
        // Nota: O KeycloakService vai converter "ESTUDANTE" para "estudante" na hora de buscar a role
        UUID keycloakId = keycloakService.createUser(dto.getEmail(), dto.getSenha(), "ESTUDANTE");

        try {
            // 2. Mapeia o DTO diretamente para a Entidade Estudante (Herança resolve o resto)
            Estudante estudante = modelMapper.map(dto, Estudante.class);
            estudante.setId(keycloakId);
            estudante.setTipoEtnia(tipoEtniaService.buscarTipoEtnia(dto.getTipoEtniaId()));
//            estudante.setCurso(cursoService.buscar(dto.getCursoId()));

            // 3. Processa e anexa documentos, se existirem
            if (arquivos != null && !arquivos.isEmpty()) {
                List<Documento> documentosSalvos = armazenamentoService.salvarArquivo(arquivos.toArray(new MultipartFile[0]));
                estudante.adicionarDocumentos(documentosSalvos);
            }

            return estudanteService.salvarEstudante(estudante);

        } catch (DataIntegrityViolationException e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback no Keycloak
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        } catch (Exception e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback no Keycloak
            throw new RuntimeException("Erro inesperado ao salvar o estudante: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Usuario cadastrarProfissional(ProfissionalRequest dto) throws Exception {
        UUID keycloakId = keycloakService.createUser(dto.getEmail(), dto.getSenha(), "PROFISSIONAL");

        try {
            Profissional profissional = modelMapper.map(dto, Profissional.class);
            profissional.setId(keycloakId);
            profissional.setTipoEtnia(tipoEtniaService.buscarTipoEtnia(dto.getTipoEtniaId()));

            return profissionalService.salvar(profissional);

        } catch (DataIntegrityViolationException e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        } catch (Exception e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback
            throw new RuntimeException("Erro ao salvar o profissional: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Usuario cadastrarGestor(GestorRequest dto) throws Exception {
        UUID keycloakId = keycloakService.createUser(dto.getEmail(), dto.getSenha(), "GESTOR");

        try {
            Gestor gestor = modelMapper.map(dto, Gestor.class);
            gestor.setId(keycloakId);
            gestor.setTipoEtnia(tipoEtniaService.buscarTipoEtnia(dto.getTipoEtniaId()));

            return usuarioService.salvar(gestor);

        } catch (DataIntegrityViolationException e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        } catch (Exception e) {
            keycloakService.deleteUser(keycloakId.toString()); // Rollback
            throw new RuntimeException("Erro ao salvar o gestor: " + e.getMessage(), e);
        }
    }

    // MÉTODOS GERAIS DE USUÁRIO (BUSCA, EDIÇÃO, EXCLUSÃO)

    public Usuario buscarUsuario(UUID id) throws UsuarioNotFoundException {
        UUID sessionId = authenticatedUserProvider.getUserId();
        boolean isAdmin = keycloakService.getUserRoles(sessionId.toString()).contains("administrador");
        return usuarioService.buscarUsuario(id, isAdmin, sessionId);
    }

    public Usuario buscarUsuarioAtual() throws UsuarioNotFoundException {
        return usuarioService.buscarUsuarioAtual();
    }

    public Page<Usuario> listarUsuarios(Predicate predicate, Pageable pageable) {
        return usuarioService.listarUsuarios(predicate, pageable);
    }

    @Transactional
    public Usuario editarUsuario(UsuarioPatchRequest dto) throws UsuarioNotFoundException {
        Usuario usuario = usuarioService.buscarUsuarioAtual();

        if (dto.getNome() != null) usuario.setNome(dto.getNome());
        if (dto.getNomeSocial() != null) usuario.setNomeSocial(dto.getNomeSocial());
        if (dto.getTelefone() != null) usuario.setTelefone(dto.getTelefone());

        if (dto.getTipoEtniaId() != null) {
            usuario.setTipoEtnia(tipoEtniaService.buscarTipoEtnia(dto.getTipoEtniaId()));
        }

        return usuarioService.salvar(usuario);
    }

    @Transactional
    public void deletarUsuario() throws UsuarioNotFoundException {
        UUID idSessao = authenticatedUserProvider.getUserId();
        try {
            keycloakService.deleteUser(idSessao.toString());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar usuário no servidor de autenticação: " + e.getMessage(), e);
        }
        usuarioService.deletarUsuario(idSessao);
    }

    @Transactional
    public void deletarUsuario(UUID id) throws UsuarioNotFoundException {
        try {
            keycloakService.deleteUser(id.toString());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar usuário no servidor de autenticação: " + e.getMessage(), e);
        }
        usuarioService.deletarUsuario(id);
    }

    // ------------------- Profissional ------------------- //

    public List<ProfissionalResponse> listarProfissionais() {
        return profissionalService.listar().stream()
                .map(profissional -> new ProfissionalResponse(profissional, modelMapper))
                .toList();
    }

    // A chave agora é UUID
    public ProfissionalResponse buscarProfissional(UUID id) throws ProfissionalNotFoundException {
        Profissional profissional = profissionalService.buscar(id);
        return new ProfissionalResponse(profissional, modelMapper);
    }

    public ProfissionalResponse buscarProfissionalAtual() throws ProfissionalNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Profissional profissional = profissionalService.buscar(userId);
        return new ProfissionalResponse(profissional, modelMapper);
    }

    @Transactional
    public ProfissionalResponse editarProfissional(ProfissionalUpdateRequest dto) throws ProfissionalNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Profissional profissional = profissionalService.buscar(userId);

        if (dto.getEspecialidade() != null && !dto.getEspecialidade().isBlank()) {
            profissional.setEspecialidade(dto.getEspecialidade());
        }

        Profissional atualizado = profissionalService.salvar(profissional);
        return new ProfissionalResponse(atualizado, modelMapper);
    }

    public void deletarProfissional(UUID id) throws ProfissionalNotFoundException {
        profissionalService.deletar(id);
    }

    // ================== Estudante ================== //

    @Transactional
    public void adicionarDocumentosEstudante(List<MultipartFile> arquivos) throws EstudanteNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Estudante estudante = estudanteService.buscarEstudante(userId);

        if (arquivos != null && !arquivos.isEmpty()) {
            MultipartFile[] arquivosArray = arquivos.toArray(new MultipartFile[0]);
            try {
                List<Documento> documentosSalvos = armazenamentoService.salvarArquivo(arquivosArray);
                estudante.adicionarDocumentos(documentosSalvos);
                estudanteService.salvarEstudante(estudante);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao processar novos documentos do estudante", e);
            }
        }
    }

    public List<DocumentoResponse> buscarDocumentosPorEstudante(UUID estudanteId) throws EstudanteNotFoundException, IOException {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        List<Documento> documentos = estudante.getDocumentos();

        if (documentos == null || documentos.isEmpty()) {
            return List.of();
        }

        return armazenamentoService.converterDocumentosParaBase64(documentos);
    }

    public EstudanteResponse buscarEstudante(UUID id) throws EstudanteNotFoundException {
        Estudante estudante = estudanteService.buscarEstudante(id);
        return new EstudanteResponse(estudante, modelMapper);
    }

    public Page<EstudanteResponse> listarEstudantes(Predicate predicate, Pageable pageable) throws EstudanteNotFoundException {
        return estudanteService.listarEstudantes(predicate, pageable)
                .map(estudante -> new EstudanteResponse(estudante, modelMapper));
    }

    public Page<EstudanteResponse> listarEstudantesComFiltrosExternos(
            Predicate predicate, String nome, String cpf, Long cursoId, Pageable pageable) {

        BooleanBuilder filtrosPrae = new BooleanBuilder(predicate);
        QEstudante qEstudante = QEstudante.estudante;

        // Ao invés de bater no Auth Service, filtramos tudo nativamente no banco do PRAE
        if (nome != null && !nome.isBlank()) {
            filtrosPrae.and(qEstudante.nome.containsIgnoreCase(nome));
        }
        if (cpf != null && !cpf.isBlank()) {
            filtrosPrae.and(qEstudante.cpf.eq(cpf));
        }
        if (cursoId != null) {
            filtrosPrae.and(qEstudante.curso.id.eq(cursoId));
        }

        return estudanteService.listarEstudantes(filtrosPrae.getValue(), pageable)
                .map(estudante -> new EstudanteResponse(estudante, modelMapper));
    }

    @Transactional
    public EstudanteResponse atualizarEstudante(EstudanteUpdateRequest estudanteUpdateRequest) throws EstudanteNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Estudante estudanteParcial = new Estudante();
        Estudante estudante = estudanteService.buscarEstudante(userId);

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
            estudanteParcial.setEndereco(enderecoService.editarEndereco(estudante.getEndereco().getId(), enderecoAtualizado));
        }

        Estudante estudanteAtualizado = estudanteService.atualizarEstudante(estudanteParcial, estudante);
        return new EstudanteResponse(estudanteAtualizado, modelMapper);
    }

    public void deletarEstudante(UUID id) throws EstudanteNotFoundException {
        estudanteService.deletarEstudante(id);
    }

    public Page<EstudanteResponse> listarEstudantesPorCurso(Long idCurso, Pageable pageable) {
        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QEstudante.estudante.curso.id.eq(idCurso));

        return estudanteService.listarEstudantes(predicate.getValue(), pageable)
                .map(estudante -> new EstudanteResponse(estudante, modelMapper));
    }

    public Page<CredorResponse> listarCredoresPorCurso(Long id, Pageable pageable) {
        Page<Estudante> estudantes = beneficioService.listarEstudantesComBeneficioAtivo(pageable);
        if (estudantes.isEmpty()) {
            return Page.empty(pageable);
        }

        // Aplica o filtro de curso na memória para preservar a paginação original dos ativos
        List<CredorResponse> credores = estudantes.getContent().stream()
                .filter(e -> e.getCurso() != null && e.getCurso().getId().equals(id))
                .map(this::montarCredorResponse)
                .toList();

        return new PageImpl<>(credores, pageable, estudantes.getTotalElements());
    }

    public Page<EstudanteResponse> listarCredoresParaPublicacao(Pageable pageable) {
        return beneficioService.listarEstudantesComBeneficioAtivo(pageable)
                .map(estudante -> new EstudanteResponse(estudante, modelMapper));
    }

    public Page<CredorResponse> listarCredoresComBeneficiosAtivos(Pageable pageable) {
        return beneficioService.listarEstudantesComBeneficioAtivo(pageable)
                .map(this::montarCredorResponse);
    }

    public Page<CredorResponse> listarCredoresPorBeneficio(Long beneficioId, Pageable pageable) {
        return beneficioService.listarEstudantesPorAuxilio(beneficioId, pageable)
                .map(this::montarCredorResponse);
    }

    public RelatorioEstudanteAssistidoResponse gerarRelatorioEstudanteAssistido(UUID estudanteId) throws EstudanteNotFoundException {
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
                .toList();

        return new RelatorioEstudanteAssistidoResponse(
                estudante.getNome(),
                estudante.getRendaPercapta(),
                estudante.getContatoFamilia(),
                estudante.isDeficiente(),
                estudante.getTipoDeficiencia(),
                beneficios);
    }

    public EstudanteResponse buscarEstudanteAtual() throws EstudanteNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        Estudante estudante = estudanteService.buscarEstudante(userId);
        return new EstudanteResponse(estudante, modelMapper);
    }

    public EstudanteResponse buscarEstudantePorUserId(UUID userId) throws EstudanteNotFoundException {
        Estudante estudante = estudanteService.buscarEstudante(userId);
        return new EstudanteResponse(estudante, modelMapper);
    }

    // Auxiliar Privado para montagem repetitiva do DTO
    private CredorResponse montarCredorResponse(Estudante estudante) {
        EstudanteResponse er = new EstudanteResponse(estudante, modelMapper);
        List<Beneficio> beneficiosAtivos = beneficioService.listarPorEstudante(estudante.getId()).stream()
                .filter(Beneficio::isAtivo).toList();

        return new CredorResponse(er, estudante.getDadosBancarios(), beneficiosAtivos);
    }

    // ================== Dados Bancarios ================== //

    @Transactional
    public DadosBancarios salvarDadosBancarios(UUID idEstudante, DadosBancarios dadosBancarios) {
        Estudante estudante = estudanteService.buscarEstudante(idEstudante);
        DadosBancarios salvo = dadosBancariosService.salvarDadosBancarios(dadosBancarios);
        estudante.setDadosBancarios(salvo);
        estudanteService.salvarEstudante(estudante);

        // Notificar o Estudante sobre o cadastro (getId() ao invés de getUserId())
        String msg = "Seus dados bancários foram cadastrados pelo gestor. Por favor, verifique se estão corretos para o recebimento de benefícios.";
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(estudante.getId(), "Dados Bancários Cadastrados", msg, "SISTEMA"));

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

    @Transactional
    public DadosBancarios atualizarDadosBancarios(Long id, DadosBancarios novosDadosBancarios) {
        DadosBancarios dadosBancariosAtualizados = dadosBancariosService.atualizarDadosBancarios(id, novosDadosBancarios);

        // Notificar o Estudante sobre a atualização
        if (dadosBancariosAtualizados != null) {
            Estudante estudante = estudanteService.buscarPorDadosBancariosId(id);
            if (estudante != null) {
                String msg = "Seus dados bancários foram atualizados pelo gestor. Por favor, acesse o sistema e verifique as novas informações.";
                notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(estudante.getId(), "Dados Bancários Atualizados", msg, "SISTEMA"));
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

    @Transactional
    public TipoAtendimento editarTipoAtendimento(Long id, TipoAtendimentoUpdateRequest dto)
            throws TipoAtendimentoNotFoundException {
        TipoAtendimento tipoAtendimento = tipoAtendimentoService.buscar(id);

        if (dto.getNome() != null && !dto.getNome().isBlank()) {
            tipoAtendimento.setNome(dto.getNome());
        }

        if (dto.getTempoAtendimento() != null) {
            tipoAtendimento.setTempoAtendimento(dto.getTempoAtendimento());
        }

        if (dto.getHorarios() != null) {
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
            throws TipoAtendimentoNotFoundException, ProfissionalNotFoundException {
        UUID userId = authenticatedUserProvider.getUserId();
        TipoAtendimento tipoAtendimento = buscarTipoAtendimento(tipoAtendimentoId);

        // Agora busca o Profissional nativamente pelo ID (UUID)
        Profissional profissional = profissionalService.buscar(userId);

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

        // Alterado de getUserId() para getId()
        if (!cronogramaExistente.getProfissional().getId().equals(userId)) {
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

            // 2. Busca o estudante logado que está fazendo a requisição (usando UUID como ID nativo)
            Estudante estudante = estudanteService.buscarEstudante(authenticatedUserProvider.getUserId());

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

        // Verifica as permissões utilizando o getId() nativo
        if (!Objects.equals(agendamento.getEstudante().getId(), authenticatedUserProvider.getUserId())
                && !Objects.equals(agendamento.getVaga().getCronograma().getProfissional().getId(),
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

    public Page<AgendamentoResponse> listarAgendamentosPorEstudante(UUID estudanteId, Pageable pageable) {
        Estudante estudante = estudanteService.buscarEstudante(estudanteId);
        return agendamentoService.listarAgendamentosPorEstudante(estudante, pageable)
                .map(this::mapToAgendamentoResponse);
    }

    public Page<AgendamentoResponse> listarAgendamentosPorProfissional(UUID profissionalId, Pageable pageable) {
        Profissional profissional = profissionalService.buscar(profissionalId);
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
        return new AgendamentoResponse(agendamento, modelMapper);
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

    public Page<BeneficioResponse> listarBeneficiosInativosComFiltrosExternos(
            Predicate predicate, String nome, String cpf, Long cursoId, Pageable pageable) {

        BooleanBuilder filtrosPrae = new BooleanBuilder(predicate);
        QBeneficio qBeneficio = QBeneficio.beneficio;

        if (nome != null && !nome.isBlank()) {
            filtrosPrae.and(qBeneficio.estudantes.nome.containsIgnoreCase(nome));
        }
        if (cpf != null && !cpf.isBlank()) {
            filtrosPrae.and(qBeneficio.estudantes.cpf.eq(cpf));
        }
        if (cursoId != null) {
            filtrosPrae.and(qBeneficio.estudantes.curso.id.eq(cursoId));
        }

        Page<Beneficio> beneficios = beneficioService.listarInativos(filtrosPrae.getValue(), pageable);

        if (beneficios.isEmpty()) {
            return Page.empty(pageable);
        }

        return beneficios.map(this::mapToBeneficioResponse);
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

    @Transactional
    public Beneficio salvarBeneficios(UUID estudanteId, Beneficio beneficio, MultipartFile termo, Long tipoBeneficioId)
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

    @Transactional
    public Beneficio editarBeneficios(Long id, UUID estudanteId, Beneficio beneficio, MultipartFile termo, Long tipoBeneficioId)
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

    public RelatorioFinanceiroResponse gerarRelatorioFinanceiro(Predicate predicate, Long cursoId) {
        QPagamento qPagamento = QPagamento.pagamento;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(qPagamento.ativo.isTrue());

        if (predicate != null) {
            builder.and(predicate);
        }

        if (cursoId != null) {
            builder.and(qPagamento.beneficio.estudantes.curso.id.eq(cursoId));
        }

        List<UUID> userIdsGerais = pagamentoService.obterIdsEstudantesComPagamento(builder.getValue());
        if (userIdsGerais.isEmpty()) {
            return construirRelatorioVazio();
        }

        BigDecimal totalGeralBD = pagamentoService.obterValorTotalPagamentosAtivos(builder.getValue());
        Double totalGeral = (totalGeralBD != null) ? totalGeralBD.doubleValue() : 0.0;

        List<Object[]> dadosPorTipo = pagamentoService.obterValorTotalPorTipoBeneficio(builder.getValue());
        List<RelatorioFinanceiroResponse.ValorPorTipoDTO> valorPorTipo = dadosPorTipo.stream()
                .map(obj -> new RelatorioFinanceiroResponse.ValorPorTipoDTO(
                        (Long) obj[0],
                        (String) obj[1],
                        ((BigDecimal) obj[2]).doubleValue()
                )).toList();

        // Passa a responsabilidade de contar pelo curso diretamente ao BeneficioService (que foi ajustado na sua V3)
        List<Map<String, Object>> dadosCursos = beneficioService.obterQuantidadeBeneficiadosPorCurso(userIdsGerais);
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

    public BeneficioResponse mapToBeneficioResponse(Beneficio beneficio) {
        return new BeneficioResponse(beneficio, modelMapper);
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

    @Transactional
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

    public List<Pagamento> listarPagamentosPorEstudante(UUID estudanteId) {
        return pagamentoService.listarPorEstudanteId(estudanteId);
    }

    // Alterado de Long para UUID na assinatura
    public Page<PagamentoResponse> listarPagamentosPorEstudante(UUID estudanteId, Predicate predicate, Pageable pageable) {
        QPagamento qPagamento = QPagamento.pagamento;
        BooleanBuilder construtorFiltros = new BooleanBuilder();

        construtorFiltros.and(qPagamento.beneficio.estudantes.id.eq(estudanteId));

        if (predicate != null) {
            construtorFiltros.and(predicate);
        }

        return pagamentoService.listar(construtorFiltros.getValue(), pageable)
                .map(this::mapToPagamentoResponse);
    }

    @Transactional
    public Pagamento salvarPagamentoPorCpf(PagamentoCPFRequest request) {
        log.info("Buscando aluno localmente com CPF: {}", request.getCpf());

        // Prepara as versões do CPF
        String cpfOriginal = request.getCpf();
        String cpfSemFormatacao = cpfOriginal.replaceAll("[^0-9]", "");
        String cpfFormatado = cpfSemFormatacao.length() == 11 ?
                cpfSemFormatacao.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4") : cpfOriginal;

        QEstudante qEstudante = QEstudante.estudante;
        BooleanBuilder construtor = new BooleanBuilder();

        // Busca com 1 única query considerando as 3 possibilidades
        construtor.and(
                qEstudante.cpf.eq(cpfOriginal)
                        .or(qEstudante.cpf.eq(cpfSemFormatacao))
                        .or(qEstudante.cpf.eq(cpfFormatado))
        );

        Page<Estudante> pageEstudantes = estudanteService.listarEstudantes(construtor.getValue(), Pageable.unpaged());

        if (pageEstudantes.isEmpty()) {
            log.error("Nenhum estudante encontrado com o CPF informado: {}", cpfOriginal);
            throw new EstudanteNotFoundException("Aluno com CPF " + request.getCpf() + " não encontrado no sistema");
        }

        Estudante estudante = pageEstudantes.getContent().getFirst();
        log.info("Estudante encontrado localmente: ID {}, Nome: {}", estudante.getId(), estudante.getNome());

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
        FolhaPagamentoResponse folha = pagamentoService.gerarFolhaPagamento(ano, mes, numeroLote);

        if (folha.getItens().isEmpty()) {
            return folha;
        }

        List<UUID> userIds = folha.getItens().stream()
                .map(ItemFolhaPagamentoResponse::getUserId)
                .distinct()
                .toList();

        // Busca local, sem rede
        Page<Estudante> estudantes = estudanteService.buscarPorUserIds(userIds, Pageable.unpaged());
        Map<UUID, Estudante> mapaAlunos = estudantes.getContent().stream()
                .collect(Collectors.toMap(Estudante::getId, Function.identity()));

        for (ItemFolhaPagamentoResponse item : folha.getItens()) {
            Estudante estudante = mapaAlunos.get(item.getUserId());

            if (estudante != null) {
                item.setNomeEstudante(estudante.getNome());
                item.setCpf(estudante.getCpf());
                item.setMatricula(estudante.getMatricula());
            } else {
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
        return new PagamentoResponse(pagamento, modelMapper);
    }

    public List<Map<String, Object>> obterQuantidadeBeneficiadosPorCurso(List<UUID> userIds) {
        // Redireciona para o BeneficioService que agora resolve isso nativamente
        return beneficioService.obterQuantidadeBeneficiadosPorCurso(userIds);
    }

    public List<Object[]> obterValorTotalPorTipoBeneficio(Predicate predicate) {
        return pagamentoService.obterValorTotalPorTipoBeneficio(predicate);
    }


    // ================== TipoEtnia ================== //

    public TipoEtnia salvarTipoEtnia(TipoEtnia tipoEtnia) {
        return tipoEtniaService.salvarTipoEtnia(tipoEtnia);
    }

    public TipoEtnia buscarTipoEtnia(Long id) throws TipoEtniaNotFoundException {
        return tipoEtniaService.buscarTipoEtnia(id);
    }

    public Page<TipoEtnia> listarTiposEtnia(Predicate predicate, Pageable pageable) {
        return tipoEtniaService.listarTiposEtnia(predicate, pageable);
    }

    public TipoEtnia atualizarTipoEtnia(Long id, TipoEtnia tipoEtnia) throws TipoEtniaNotFoundException {
        return tipoEtniaService.atualizarTipoEtnia(id, tipoEtnia);
    }

    public void deletarTipoEtnia(Long id) throws TipoEtniaNotFoundException {
        tipoEtniaService.deletarTipoEtnia(id);
    }

    // ------------------- Armazenamento ------------------- //

    public List<DocumentoResponse> converterDocumentosParaBase64(List<Documento> documentos) throws IOException {
        return armazenamentoService.converterDocumentosParaBase64(documentos);
    }

    // ================== Notificações ================== //

    public Page<NotificacaoEvent> buscarNotificacoesNaoLidas(UUID userId, int page, int size) {
        return notificacaoRedisService.buscarNotificacoesNaoLidas(userId, page, size);
    }

    public void marcarNotificacaoComoLida(UUID userId, UUID notificacaoId) {
        notificacaoRedisService.marcarUnicaComoLida(userId, notificacaoId);
    }

    public void limparTodasNotificacoes(UUID userId) {
        notificacaoRedisService.marcarTodasComoLidas(userId);
    }

    public SseEmitter subscreverNotificacoes(UUID userId) {
        return notificacaoSseService.subscrever(userId);
    }

    public void limparConexoesSse(UUID userId) {
        notificacaoSseService.removerTodosEmittersDoUsuario(userId);
    }

    public void enviarNotificacaoBroadcast(NotificacaoBroadcastRequest request) {
        NotificacaoEvent evento = NotificacaoEvent.paraPerfil(
                request.getPerfilDestino().toUpperCase(),
                request.getTitulo(),
                request.getMensagem(),
                request.getTipo()
        );
        notificacaoPublisher.publicar(evento);
    }

    // ------------------- Curso ------------------- //

    public Curso salvarCurso(Curso curso){
        return cursoService.salvar(curso);
    }

    public Curso buscarCurso(Long id) throws CursoNotFoundException {
        return cursoService.buscar(id);
    }

    public Page<Curso> listarCursos(Predicate predicate, Pageable pageable) {
        return cursoService.listar(predicate, pageable);
    }

    public Curso editarCurso(Long id, CursoPatchRequest dto) throws CursoNotFoundException {
        Curso curso = cursoService.buscar(id);

        if (dto.getNome() != null) {
            String nome = dto.getNome().trim();
            if (nome.isEmpty()) {
                throw new IllegalArgumentException("Nome do curso não pode ser vazio.");
            }
            curso.setNome(nome);
        }

        if (dto.getNumeroPeriodos() != null) {
            if (dto.getNumeroPeriodos() <= 0) {
                throw new IllegalArgumentException("Número de períodos deve ser maior que zero.");
            }
            curso.setNumeroPeriodos(dto.getNumeroPeriodos());
        }

        return cursoService.salvar(curso);
    }

    public void deletarCurso(Long id) throws CursoNotFoundException {
        cursoService.deletar(id);
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
