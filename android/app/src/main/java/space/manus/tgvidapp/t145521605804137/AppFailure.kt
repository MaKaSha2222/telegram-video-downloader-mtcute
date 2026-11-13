package space.manus.tgvidapp.t145521605804137

/** A user-visible failure that preserves the upstream type, code, full message, and response details. */
class AppFailure(
    val errorType: String,
    val errorCode: String? = null,
    val errorMessage: String,
    val details: String? = null,
    cause: Throwable? = null,
) : Exception(errorMessage, cause)

object ErrorDisplay {
    fun format(error: Throwable, credentials: TelegramCredentials? = null): String {
        val primary = error as? AppFailure
        val type = primary?.errorType?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName.ifBlank { error.javaClass.name }
        val message = primary?.errorMessage?.takeIf { it.isNotBlank() }
            ?: error.localizedMessage?.takeIf { it.isNotBlank() }
            ?: error.toString()
        val secrets = listOf(credentials?.apiHash, credentials?.botToken)
            .filterNotNull()
            .filter { it.length >= 6 }
        fun safe(value: String): String = secrets.fold(value) { result, secret -> result.replace(secret, "[hidden]") }

        return buildString {
            append("အမှားအမျိုးအစား (Type): ").append(safe(type))
            primary?.errorCode?.let {
                if (it.isNotBlank()) append("\nအမှားကုဒ် (Code): ").append(safe(it))
            }
            append("\nMessage: ").append(safe(message))
            primary?.details?.takeIf { it.isNotBlank() }?.let {
                append("\nအသေးစိတ် (Details):\n").append(safe(it))
            }

            var cause = error.cause
            var index = 1
            val seenCauses = mutableSetOf<Throwable>()
            while (cause != null && seenCauses.add(cause)) {
                val causeType = cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
                val causeMessage = cause.localizedMessage ?: cause.toString()
                if (index == 1 && primary != null && causeType == primary.errorType && causeMessage == primary.errorMessage) {
                    cause = cause.cause
                    continue
                }
                append("\nCause ").append(index).append(" Type: ").append(safe(causeType))
                append("\nCause ").append(index).append(" Message: ").append(safe(causeMessage))
                cause = cause.cause
                index++
            }
        }
    }
}
