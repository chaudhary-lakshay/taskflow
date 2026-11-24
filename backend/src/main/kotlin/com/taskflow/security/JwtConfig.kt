package com.taskflow.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/** HS256 only, in both directions. Library code does all signing and parsing; nothing is hand-rolled. */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {
    @Bean
    fun jwtSecretKey(props: JwtProperties): SecretKey {
        check(props.secret.isNotBlank()) { "JWT_SECRET is not set. Generate one with: openssl rand -base64 32" }
        val bytes = try {
            Base64.getDecoder().decode(props.secret.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("JWT_SECRET must be base64 (openssl rand -base64 32)")
        }
        check(bytes.size >= 32) { "JWT_SECRET must decode to at least 32 bytes (256 bits)" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun jwtDecoder(key: SecretKey, props: JwtProperties, clock: Clock): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(Duration.ofSeconds(30)).also { it.setClock(clock) }, // 30 s clock skew
                JwtIssuerValidator(props.issuer),
                RequiredClaimsValidator,
            ),
        )
        return decoder
    }
}

/** Library validators accept a missing exp; we do not. The subject must be a positive user id. */
private object RequiredClaimsValidator : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val problem = when {
            jwt.expiresAt == null -> "exp claim is required"
            jwt.subject?.toLongOrNull()?.let { it > 0 } != true -> "sub must be a positive user id"
            else -> return OAuth2TokenValidatorResult.success()
        }
        return OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", problem, null))
    }
}
