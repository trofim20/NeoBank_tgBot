package org.example.bot.service;

import org.example.bot.entity.UserEntity;
import org.example.bot.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Взаимодействие с сущностью пользователя
 */
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}