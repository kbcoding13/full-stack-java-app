package com.example.inventory.auth;

import com.example.inventory.auth.AuthDtos.LoginRequest;
import com.example.inventory.auth.AuthDtos.RegisterRequest;
import com.example.inventory.auth.AuthDtos.TokenResponse;
import com.example.inventory.auth.AuthDtos.UserResponse;
import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.user.Role;
import com.example.inventory.user.User;
import com.example.inventory.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** The first account to register becomes ADMIN so a fresh deployment is usable; the rest are STAFF. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with that email already exists");
        }

        Role role = userRepository.count() == 0 ? Role.ADMIN : Role.STAFF;
        User user = userRepository.save(new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                role));

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        var claims = jwtService
                .parseRefreshToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid or expired"));

        User user = userRepository
                .findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new NotFoundException("User", claims.getSubject()));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository
                .findById(userId)
                .map(AuthService::toUserResponse)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private TokenResponse issueTokens(User user) {
        return new TokenResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                toUserResponse(user));
    }

    private static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
