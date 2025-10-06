package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.dto.ConviteDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AtivarConviteResponse {
    private Boolean success;
    private String message;
    private ConviteDTO.UsuarioInfoDTO usuario;
}
