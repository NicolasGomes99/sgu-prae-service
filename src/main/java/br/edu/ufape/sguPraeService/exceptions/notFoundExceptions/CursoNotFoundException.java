package br.edu.ufape.sguPraeService.exceptions.notFoundExceptions;


public class CursoNotFoundException extends NotFoundException {
    public CursoNotFoundException() {
        super("Curso não encontrado");
    }
}