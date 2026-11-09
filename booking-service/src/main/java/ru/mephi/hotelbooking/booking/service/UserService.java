package ru.mephi.hotelbooking.booking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.dto.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.AuthResponse;
import ru.mephi.hotelbooking.booking.dto.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.UserResponse;
import ru.mephi.hotelbooking.booking.dto.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.Role;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.exception.AuthenticationFailedException;
import ru.mephi.hotelbooking.booking.exception.ConflictException;
import ru.mephi.hotelbooking.booking.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.booking.mapper.UserMapper;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.booking.security.JwtService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /** Саморегистрация: роль всегда USER. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        ensureUsernameIsFree(username, null);
        User user = userRepository.save(new User(username, passwordEncoder.encode(request.password()), Role.USER));
        log.info("Пользователь зарегистрирован: id={}, username={}", user.getId(), user.getUsername());
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new AuthenticationFailedException("Неверное имя пользователя или пароль"));
        log.info("Пользователь вошёл в систему: id={}, username={}", user.getId(), user.getUsername());
        return authResponse(user);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        ensureUsernameIsFree(username, null);
        User user = userRepository.save(new User(username, passwordEncoder.encode(request.password()), request.role()));
        log.info("Пользователь создан администратором: id={}, username={}, роль={}", user.getId(), user.getUsername(), user.getRole());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(UserUpdateRequest request) {
        User user = find(request.id());
        if (request.username() != null) {
            String username = request.username().trim();
            ensureUsernameIsFree(username, user.getId());
            user.setUsername(username);
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        log.info("Пользователь изменён администратором: id={}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = find(id);
        if (bookingRepository.existsByUserIdAndStatusIn(id, BookingStatus.ACTIVE)) {
            throw new ConflictException("У пользователя " + id + " есть активные бронирования: сначала отмените их");
        }
        bookingRepository.deleteByUserId(id);
        userRepository.delete(user);
        log.info("Пользователь удалён администратором: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("id")).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userMapper.toResponse(find(id));
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + id + " не найден"));
    }

    private void ensureUsernameIsFree(String username, Long ownId) {
        boolean taken = ownId == null
                ? userRepository.existsByUsername(username)
                : userRepository.existsByUsernameAndIdNot(username, ownId);
        if (taken) {
            throw new ConflictException("Имя пользователя '" + username + "' уже занято");
        }
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.generateUserToken(user), "Bearer", jwtService.userTokenTtlSeconds(),
                user.getId(), user.getUsername(), user.getRole());
    }
}
