package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConviteResponse {
    private Long id;
    private String token;
    private String email;
    private TipoUsuario tipoUsuario;
    private StatusConvite status;
    private String linkConvite;
    private String comarca;
    private String departamento;
    private LocalDateTime criadoEm;
    private LocalDateTime expiraEm;
    private String criadoPorNome;
    private Long criadoPorId;
}