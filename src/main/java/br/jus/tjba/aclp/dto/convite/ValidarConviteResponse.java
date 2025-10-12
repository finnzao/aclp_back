package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ValidarConviteResponse {
    private Boolean valido;
    private TipoUsuario tipoUsuario;
    private LocalDateTime expiraEm;
    private String mensagem;
    private String comarca;        // Pré-preenchido do admin
    private String departamento;   // Pré-preenchido do admin

    public boolean isValido() {
        return Boolean.TRUE.equals(valido);
    }
}