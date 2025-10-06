package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioInfoDTO {
    private Long id;
    private String nome;
    private String email;
    private TipoUsuario tipo;
    private String comarca;
    private String departamento;
}