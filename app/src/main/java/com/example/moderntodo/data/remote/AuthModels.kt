package com.example.moderntodo.data.remote

data class EmailAuthRequest(val email: String, val password: String)
data class AuthResponse(val access_token: String?, val token_type: String, val expires_in: Int, val refresh_token: String, val user: User)
data class User(val id: String, val email: String)

data class IdTokenRequest(val id_token: String, val provider: String = "google")

data class RefreshTokenRequest(val refresh_token: String)
