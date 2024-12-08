package com.example.redis_jwt_cache.service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl {

    private final RedisTemplate<String, String> redisTemplate;

    // Bu secret key olmadan token extract edilemez
    @Value("${security.jwt.secret-key}")
    private String SECRET_KEY;

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    // Mevcut token'daki username ile işlem yapmak isteyen Username aynı mı?
    // Token expired olmuş mu?
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        Date expirationDate = extractExpiration(token);

        return userDetails.getUsername().equals(username) && expirationDate.after(new Date());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // JWT içindeki Payload
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private String createToken(Map<String, Object> claims, String username) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 2))
                .signWith(getSignKey(), SignatureAlgorithm.HS256).compact();
    }

    private void cacheToken(String username, String token) {
        redisTemplate.opsForValue().set(username, token);
    }

    /*
        JWT’nin imzalanması ve doğrulanması sırasında kullanılır.
        İmza, token’ın bütünlüğünü ve doğruluğunu garanti eder, yani token’ın değiştirilmediğinden emin olmanızı sağlar.
     */
    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
