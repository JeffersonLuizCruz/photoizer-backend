package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RolesAllowed("ADMIN")
@Tag(name = "Usuários", description = "Gestão de usuários do sistema (staff).")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(operationId = "listarUsuarios", summary = "Listar usuários")
    public ResponseEntity<List<UserResponse>> listar() {
        return ResponseEntity.ok(userService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "buscarUsuarioPorId", summary = "Buscar usuário por ID")
    public ResponseEntity<UserResponse> buscarPorId(
            @PathVariable @Parameter(description = "ID do usuário") UUID id) {
        return ResponseEntity.ok(userService.buscarPorId(id));
    }

    @PostMapping
    @Operation(operationId = "criarUsuario", summary = "Criar usuário")
    @RolesAllowed("ADMIN")
    public ResponseEntity<UserResponse> criar(@Valid @RequestBody CriarUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.criar(request));
    }
}
