package ru.mephi.hotelbooking.booking.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** Аутентифицированный вызывающий, каким его описывает проверенный JWT. */
public record CurrentUser(Long id, String username, boolean admin) {

    public static CurrentUser from(Jwt jwt) {
        Number userId = jwt.getClaim(JwtService.USER_ID_CLAIM);
        List<String> roles = jwt.getClaimAsStringList(JwtService.ROLES_CLAIM);
        boolean admin = roles != null && roles.contains("ADMIN");
        return new CurrentUser(userId == null ? null : userId.longValue(), jwt.getSubject(), admin);
    }

    /** Доступ к бронированию есть у владельца и у администратора. */
    public boolean canAccessBookingOf(Long ownerId) {
        return admin || (id != null && id.equals(ownerId));
    }
}
