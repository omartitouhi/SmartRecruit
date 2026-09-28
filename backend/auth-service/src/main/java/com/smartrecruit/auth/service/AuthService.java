package com.smartrecruit.auth.service;

import com.smartrecruit.auth.dto.AccountResponse;
import com.smartrecruit.auth.dto.RegisterRequest;
import com.smartrecruit.auth.exception.ApiException;
import com.smartrecruit.auth.exception.EmailAlreadyUsedException;
import com.smartrecruit.auth.model.Account;
import com.smartrecruit.auth.model.AccountStatus;
import com.smartrecruit.auth.model.Role;
import com.smartrecruit.auth.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le rôle ADMIN ne peut pas être choisi à l'inscription");
        }
        String email = normalizeEmail(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        Account account = Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(AccountStatus.ACTIVE)
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
