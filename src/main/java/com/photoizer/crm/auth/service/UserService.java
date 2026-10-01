package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.api.CriarUserRequest;
import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    /**
     * Email sintético que identifica o usuário FOTOGRAFO provisionado a partir
     * do nome configurado na tela de Configuração. Fixo e único para permitir
     * upsert idempotente (renomear em vez de duplicar).
     */
    private static final String EMAIL_FOTOGRAFO_CONFIGURADO = "fotografo.configurado@photoizer.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listarTodos() {
        return userRepository.findAll().stream().map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse buscarPorId(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + id));
        return UserResponse.of(user);
    }

    public UserResponse criar(CriarUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email já cadastrado: " + request.email());
        }
        var user = new User(
            request.email(),
            passwordEncoder.encode(request.password()),
            request.nome(),
            request.papel()
        );
        user.setTelefone(request.telefone());
        user = userRepository.save(user);
        return UserResponse.of(user);
    }

    public UserResponse criarFotografo(String email, String senha, String nome, String telefone) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este email: " + email);
        }
        var user = new User(email, passwordEncoder.encode(senha), nome, Papel.FOTOGRAFO);
        if (telefone != null && !telefone.isBlank()) {
            user.setTelefone(telefone);
        }
        return UserResponse.of(userRepository.save(user));
    }

    public UserResponse atualizarFotografo(UUID id, String nome, String email, String telefone) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Fotógrafo não encontrado: " + id));
        user.setNome(nome);
        user.setEmail(email);
        if (telefone != null) {
            user.setTelefone(telefone);
        }
        return UserResponse.of(userRepository.save(user));
    }

    public void toggleStatus(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Fotógrafo não encontrado: " + id));
        user.setAtivo(!user.isAtivo());
        userRepository.save(user);
    }

    /**
     * Cria ou atualiza o usuário FOTOGRAFO provisionado a partir do nome
     * configurado na tela de Configuração. Idempotente: reutiliza o usuário de
     * email sintético e apenas renomeia, garantindo papel FOTOGRAFO e ativo.
     * A senha é aleatória (o usuário provisionado existe para ser selecionável
     * como responsável, não para login).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertFotografoConfigurado(String nome) {
        if (nome == null || nome.isBlank()) {
            return;
        }
        var nomeLimpo = nome.trim();
        var existente = userRepository.findByEmail(EMAIL_FOTOGRAFO_CONFIGURADO);
        if (existente.isPresent()) {
            var user = existente.get();
            user.setNome(nomeLimpo);
            user.setPapel(Papel.FOTOGRAFO);
            user.setAtivo(true);
            userRepository.save(user);
            return;
        }
        var user = new User(
            EMAIL_FOTOGRAFO_CONFIGURADO,
            passwordEncoder.encode(UUID.randomUUID().toString()),
            nomeLimpo,
            Papel.FOTOGRAFO
        );
        user.setAtivo(true);
        userRepository.save(user);
    }

    public void remover(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Fotógrafo não encontrado: " + id));
        userRepository.delete(user);
    }
}
