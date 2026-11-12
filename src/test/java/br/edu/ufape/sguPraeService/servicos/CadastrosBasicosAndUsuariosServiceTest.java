package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.dados.*;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.exceptions.TipoEtniaDuplicadoException;
import br.edu.ufape.sguPraeService.exceptions.UniqueConstraintViolationException;
import br.edu.ufape.sguPraeService.models.*;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CadastrosBasicosAndUsuariosServiceTest {

    private ModelMapper modelMapper;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapper();
    }

    @Test
    @DisplayName("CursoService: Deve reativar curso desativado com mesmo nome ao salvar e converter erro de integridade")
    void deveReativarCursoInativoComMesmoNome() {
        CursoRepository repo = mock(CursoRepository.class);
        CursoService service = new CursoService(repo, modelMapper);

        Curso desativado = new Curso(1L, "Engenharia de Software", 10, false);
        when(repo.existsByNome("Engenharia de Software")).thenReturn(true);
        when(repo.findByNomeAndAtivoFalse("Engenharia de Software")).thenReturn(desativado);
        when(repo.save(any(Curso.class))).thenAnswer(inv -> inv.getArgument(0));

        Curso novo = new Curso(null, "Engenharia de Software", 10, true);
        Curso resultado = service.salvar(novo);

        assertEquals(1L, resultado.getId());
        assertTrue(resultado.isAtivo());

        // Violação de unicidade em curso já ativo -> IllegalArgumentException / UniqueConstraintViolationException via ExceptionUtil
        ConstraintViolationException cve = new ConstraintViolationException("unique_nome", new SQLException(), "curso_nome_key");
        when(repo.existsByNome("Agronomia")).thenReturn(false);
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("erro", cve));

        assertThrows(IllegalArgumentException.class, () -> service.salvar(new Curso(null, "Agronomia", 10, true)));

        ConstraintViolationException cveEstudante = new ConstraintViolationException("estudante_user_id_key", new SQLException(), "estudante_user_id_key");
        doThrow(new DataIntegrityViolationException("erro", cveEstudante)).when(repo).save(any());
        assertThrows(UniqueConstraintViolationException.class, () -> service.salvar(new Curso(null, "Agronomia", 10, true)));
    }

    @Test
    @DisplayName("TipoEtniaService: Deve impedir cadastro ou atualização com nome de etnia duplicado")
    void deveImpedirTipoEtniaDuplicado() {
        TipoEtniaRepository repo = mock(TipoEtniaRepository.class);
        TipoEtniaService service = new TipoEtniaService(repo);

        TipoEtnia existente = new TipoEtnia(1L, "Parda");
        when(repo.findByTipoIgnoreCase("Parda")).thenReturn(Optional.of(existente));

        assertThrows(TipoEtniaDuplicadoException.class,
                () -> service.salvarTipoEtnia(new TipoEtnia(null, "Parda")));

        // Ao atualizar o próprio registro (mesmo ID), deve permitir
        when(repo.findById(1L)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> service.atualizarTipoEtnia(1L, new TipoEtnia(1L, "Parda")));

        // Ao tentar atualizar o ID 2 para o nome já usado pelo ID 1, deve bloquear
        when(repo.findById(2L)).thenReturn(Optional.of(new TipoEtnia(2L, "Indígena")));
        assertThrows(TipoEtniaDuplicadoException.class,
                () -> service.atualizarTipoEtnia(2L, new TipoEtnia(2L, "Parda")));
    }

    @Test
    @DisplayName("UsuarioService e GestorService: Devem bloquear acesso de usuários comuns a perfis de terceiros")
    void deveValidarAcessoEmUsuarioServiceEGestorService() {
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        GestorRepository gestorRepo = mock(GestorRepository.class);
        AuthenticatedUserProvider authProvider = mock(AuthenticatedUserProvider.class);

        UsuarioService usuarioService = new UsuarioService(usuarioRepo, modelMapper, authProvider);
        GestorService gestorService = new GestorService(gestorRepo);

        UUID idAlvo = UUID.randomUUID();
        UUID idSessaoOutrem = UUID.randomUUID();

        Estudante alvo = new Estudante();
        alvo.setId(idAlvo);
        when(usuarioRepo.findById(idAlvo)).thenReturn(Optional.of(alvo));

        // Não-admin tentando acessar outro usuário -> GlobalAccessDeniedException
        assertThrows(GlobalAccessDeniedException.class,
                () -> usuarioService.buscarUsuario(idAlvo, false, idSessaoOutrem));
        // Admin acessando outro usuário -> Permitido
        assertEquals(alvo, usuarioService.buscarUsuario(idAlvo, true, idSessaoOutrem));

        Gestor gestorAlvo = new Gestor();
        gestorAlvo.setId(idAlvo);
        when(gestorRepo.findById(idAlvo)).thenReturn(Optional.of(gestorAlvo));

        assertThrows(GlobalAccessDeniedException.class,
                () -> gestorService.buscarGestor(idAlvo, false, idSessaoOutrem));
        assertEquals(gestorAlvo, gestorService.buscarGestor(idAlvo, false, idAlvo));
    }

    @Test
    @DisplayName("TipoAtendimentoService, TipoBeneficioService e DadosBancariosService: Operações de CRUD e regras")
    void deveExecutarOperacoesDeTipoAtendimentoTipoBeneficioEDadosBancarios() throws Exception {
        TipoAtendimentoRepository taRepo = mock(TipoAtendimentoRepository.class);
        TipoAtendimentoService taService = new TipoAtendimentoService(taRepo, modelMapper);

        TipoAtendimento ta = new TipoAtendimento(1L, "Psicologia", LocalTime.of(0, 45),
                new ArrayList<>(List.of(LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(taRepo.findById(1L)).thenReturn(Optional.of(ta));
        when(taRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TipoAtendimento semPrimeiroHorario = taService.deletarHorario(1L, 0);
        assertEquals(1, semPrimeiroHorario.getHorarios().size());
        assertEquals(LocalTime.of(9, 0), semPrimeiroHorario.getHorarios().getFirst());

        TipoBeneficioRepository tbRepo = mock(TipoBeneficioRepository.class);
        TipoBeneficioService tbService = new TipoBeneficioService(tbRepo, modelMapper);
        TipoBeneficio tb = new TipoBeneficio();
        tb.setId(5L);
        tb.setAtivo(true);
        when(tbRepo.findById(5L)).thenReturn(Optional.of(tb));
        tbService.desativar(5L);
        assertFalse(tb.isAtivo());

        DadosBancariosRepository dbRepo = mock(DadosBancariosRepository.class);
        DadosBancariosService dbService = new DadosBancariosService(dbRepo, modelMapper);
        DadosBancarios dbExistente = new DadosBancarios();
        dbExistente.setId(1L);
        dbExistente.setBanco("001");
        dbExistente.setAgencia("222");
        dbExistente.setConta("111");
        dbExistente.setTipoConta("CORRENTE");
        when(dbRepo.findById(1L)).thenReturn(Optional.of(dbExistente));
        when(dbRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DadosBancarios dbNovo = new DadosBancarios();
        dbNovo.setId(1L);
        dbNovo.setBanco("237");
        dbNovo.setAgencia("888");
        dbNovo.setConta("999");
        dbNovo.setTipoConta("POUPANCA");
        DadosBancarios atualizado = dbService.atualizarDadosBancarios(1L, dbNovo);
        assertEquals("237", atualizado.getBanco());
        assertEquals("999", atualizado.getConta());
    }
}
