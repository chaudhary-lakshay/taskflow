package com.taskflow.support

import com.taskflow.security.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Test-only protected endpoint: proves the owner id comes from the JWT, never from the request. */
@RestController
class WhoAmIController {
    @GetMapping("/api/test/whoami")
    fun whoami(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) ownerId: Long?,
    ) = mapOf("userId" to jwt.userId())
}
