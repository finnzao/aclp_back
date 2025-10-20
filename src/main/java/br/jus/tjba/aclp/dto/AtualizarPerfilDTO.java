package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para atualização de perfil do próprio usuário
 * Campos opcionais e sem permissões administrativas
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtualizarPerfilDTO {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    private String nome;

    @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
    private String departamento;

    @Size(max = 100, message = "Comarca deve ter no máximo 100 caracteres")
    private String comarca;

    @Size(max = 100, message = "Cargo deve ter no máximo 100 caracteres")
    private String cargo;

    private String avatar;
}