package com.photoizer.crm.fotografo.api;

import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.fotografo.service.FotografoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parceiros")
@Tag(name = "Parceiros", description = "Usuários do sistema elegíveis a receber repasse em um ensaio")
public class ParceiroController {

    private final FotografoService fotografoService;

    public ParceiroController(FotografoService fotografoService) {
        this.fotografoService = fotografoService;
    }

    @GetMapping
    @Operation(summary = "Listar parceiros elegíveis (FOTOGRAFO, EDITOR, AGENDADOR)")
    public ResponseEntity<List<User>> listar() {
        return ResponseEntity.ok(fotografoService.listarParceiros());
    }
}
