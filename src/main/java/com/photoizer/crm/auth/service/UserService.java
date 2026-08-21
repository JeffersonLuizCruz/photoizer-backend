package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.api.CriarUserRequest;
import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

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
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Usuário não encontrado: " + id));
        return UserResponse.of(user);
    }

    public UserResponse criar(CriarUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Email já cadastrado: " + request.email());
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
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Já existe um usuário com este email: " + email);
        }
        var user = new User(email, passwordEncoder.encode(senha), nome, Papel.FOTOGRAFO);
        if (telefone != null && !telefone.isBlank()) {
            user.setTelefone(telefone);
        }
        return UserResponse.of(userRepository.save(user));
    }

    public UserResponse atualizarFotografo(UUID id, String nome, String email, String telefone) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Fotógrafo não encontrado: " + id));
        user.setNome(nome);
        user.setEmail(email);
        if (telefone != null) {
            user.setTelefone(telefone);
        }
        return UserResponse.of(userRepository.save(user));
    }

    public void toggleStatus(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Fotógrafo não encontrado: " + id));
        user.setAtivo(!user.isAtivo());
        userRepository.save(user);
    }

    public void remover(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Fotógrafo não encontrado: " + id));
        userRepository.delete(user);
    }
}
