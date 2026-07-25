package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.api.CriarUserRequest;
import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + id));
        return UserResponse.of(user);
    }

    public UserResponse criar(CriarUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email já cadastrado: " + request.email());
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
}
