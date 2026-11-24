package com.taskflow.auth

class DuplicateEmailException : RuntimeException("Email already registered")

class InvalidCredentialsException : RuntimeException("Invalid credentials")
