package br.jus.tjba.aclp.dto.convite;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConviteStats {
    private Long totalConvites;
    private Long pendentes;
    private Long ativados;
    private Long expirados;
    private Long cancelados;
}