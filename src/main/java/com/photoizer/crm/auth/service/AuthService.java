package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.api.LoginRequest;
import com.photoizer.crm.auth.api.LoginResponse;
import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.auth.model.AdminUser;
import com.photoizer.crm.auth.repository.AdminUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AdminUserRepository adminUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        var user = adminUserRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        var token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new LoginResponse(token, user.getNome(), user.getEmail());
    }
}
