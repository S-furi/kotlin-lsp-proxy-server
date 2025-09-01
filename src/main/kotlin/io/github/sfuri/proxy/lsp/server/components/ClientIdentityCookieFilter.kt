package io.github.sfuri.proxy.lsp.server.components

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class ClientIdentityCookieFilter : OncePerRequestFilter() {

    private val cookieName = "clientId"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val hasCookie = request.cookies?.any { it.name == cookieName } ?: false
        if (!hasCookie) {
            val id = UUID.randomUUID().toString()
            val cookie = ResponseCookie.from(cookieName, id)
                .httpOnly(true)
                .secure(request.isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(60L * 60 *  24 * 30)
                .build()

            response.addHeader("Set-Cookie", cookie.toString())
        }
        filterChain.doFilter(request, response)
    }
}