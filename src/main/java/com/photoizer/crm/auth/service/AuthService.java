package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.api.LoginRequest;
import com.photoizer.crm.auth.api.LoginResponse;
import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        if (!user.isAtivo()) {
            throw new BadCredentialsException("Usuário inativo");
        }

        var token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getPapel().name());
        return new LoginResponse(token, user.getNome(), user.getEmail(), user.getPapel(), user.getId().toString());
    }
}
