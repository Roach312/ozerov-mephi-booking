package ru.mephi.hotelbooking.booking.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.mephi.hotelbooking.booking.entity.Role;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || userRepository.existsByUsername(properties.getUsername())) {
            return;
        }
        userRepository.save(new User(properties.getUsername(), passwordEncoder.encode(properties.getPassword()), Role.ADMIN));
        log.info("Создан администратор '{}'", properties.getUsername());
    }
}
