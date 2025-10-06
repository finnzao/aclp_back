package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ValidarConviteResponse {
    private Boolean valido;
    private String email;
    private TipoUsuario tipoUsuario;
    private LocalDateTime expiraEm;
    private String mensagem;
    private String comarca;        // Pré-preenchido
    private String departamento;   // Pré-preenchido
}