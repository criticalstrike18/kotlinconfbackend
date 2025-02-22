package org.jetbrains.kotlinApp.backend

class ServiceUnavailable: Throwable()
class BadRequest: Throwable()
class Unauthorized: Throwable()
class NotFound: Throwable()
class SecretInvalidError: Throwable()
