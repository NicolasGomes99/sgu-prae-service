-- Adiciona a coluna para controlar se a notificação de encerramento da janela de edição já foi enviada
ALTER TABLE agendamento ADD COLUMN confirmacao_enviada BOOLEAN NOT NULL DEFAULT FALSE;