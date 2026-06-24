package com.example.security

/**
 * Validación centralizada de todas las entradas de usuario.
 *
 * Principios aplicados:
 *  · Fallar rápido: cada función devuelve un Result<T> sellado que el
 *    ViewModel puede tratar sin depender de excepciones.
 *  · Ninguna entrada llega a Room ni a la API sin pasar por aquí.
 *  · Los mensajes de error son legibles en español para mostrarse en la UI.
 */
object InputValidator {

    // ─── Límites de longitud ─────────────────────────────────────────────────
    private const val NAME_MAX        = 100
    private const val CATEGORY_MAX    = 60
    private const val UNIT_MAX        = 20
    private const val LIST_ID_MAX     = 40
    private const val EMAIL_MAX       = 254   // RFC 5321
    private const val PASSWORD_MIN    = 8
    private const val PASSWORD_MAX    = 128

    // Regex seguros con longitud acotada (no vulnerable a ReDoS)
    private val EMAIL_REGEX    = Regex("""^[a-zA-Z0-9._%+\-]{1,64}@[a-zA-Z0-9.\-]{1,189}\.[a-zA-Z]{2,}$""")
    private val LIST_ID_REGEX  = Regex("""^[A-Z0-9_\-]{1,40}$""")
    private val SAFE_TEXT_REGEX = Regex("""^[^<>"'`;\\]{1,$NAME_MAX}$""")

    // ─── Resultado sellado ────────────────────────────────────────────────────

    sealed class ValidationResult<out T> {
        data class Ok<T>(val value: T) : ValidationResult<T>()
        data class Err(val message: String) : ValidationResult<Nothing>()
    }

    // ─── Nombre de artículo ───────────────────────────────────────────────────

    fun validateItemName(raw: String): ValidationResult<String> {
        val v = raw.trim()
        if (v.isEmpty())
            return ValidationResult.Err("El nombre no puede estar vacío.")
        if (v.length > NAME_MAX)
            return ValidationResult.Err("El nombre es demasiado largo (máx. $NAME_MAX caracteres).")
        if (!SAFE_TEXT_REGEX.matches(v))
            return ValidationResult.Err("El nombre contiene caracteres no permitidos.")
        return ValidationResult.Ok(v)
    }

    // ─── Cantidad ─────────────────────────────────────────────────────────────

    fun validateQuantity(value: Double): ValidationResult<Double> {
        if (value <= 0.0)
            return ValidationResult.Err("La cantidad debe ser mayor que cero.")
        if (value > 9_999.0)
            return ValidationResult.Err("La cantidad es demasiado grande.")
        return ValidationResult.Ok(value)
    }

    // ─── Precio ───────────────────────────────────────────────────────────────

    fun validatePrice(value: Double): ValidationResult<Double> {
        if (value < 0.0)
            return ValidationResult.Err("El precio no puede ser negativo.")
        if (value > 99_999.0)
            return ValidationResult.Err("El precio es demasiado alto.")
        return ValidationResult.Ok(value)
    }

    // ─── Unidad ───────────────────────────────────────────────────────────────

    fun validateUnit(raw: String): ValidationResult<String> {
        val v = raw.trim()
        if (v.isEmpty())
            return ValidationResult.Err("La unidad no puede estar vacía.")
        if (v.length > UNIT_MAX)
            return ValidationResult.Err("La unidad es demasiado larga (máx. $UNIT_MAX caracteres).")
        return ValidationResult.Ok(v)
    }

    // ─── Categoría ────────────────────────────────────────────────────────────

    fun validateCategory(raw: String): ValidationResult<String> {
        val v = raw.trim()
        if (v.isEmpty())
            return ValidationResult.Err("La categoría no puede estar vacía.")
        if (v.length > CATEGORY_MAX)
            return ValidationResult.Err("La categoría es demasiado larga (máx. $CATEGORY_MAX caracteres).")
        return ValidationResult.Ok(v)
    }

    // ─── ID de lista ──────────────────────────────────────────────────────────

    fun validateListId(raw: String): ValidationResult<String> {
        val v = raw.trim().uppercase()
        if (v.isEmpty())
            return ValidationResult.Err("El código de lista no puede estar vacío.")
        if (v.length > LIST_ID_MAX)
            return ValidationResult.Err("El código de lista es demasiado largo (máx. $LIST_ID_MAX caracteres).")
        if (!LIST_ID_REGEX.matches(v))
            return ValidationResult.Err("El código solo puede contener letras mayúsculas, números, guiones y guiones bajos.")
        return ValidationResult.Ok(v)
    }

    // ─── Email ────────────────────────────────────────────────────────────────

    fun validateEmail(raw: String): ValidationResult<String> {
        val v = raw.trim().lowercase()
        if (v.isEmpty())
            return ValidationResult.Err("El correo electrónico no puede estar vacío.")
        if (v.length > EMAIL_MAX)
            return ValidationResult.Err("El correo electrónico es demasiado largo.")
        if (!EMAIL_REGEX.matches(v))
            return ValidationResult.Err("El formato del correo electrónico no es válido.")
        return ValidationResult.Ok(v)
    }

    // ─── Contraseña ───────────────────────────────────────────────────────────

    fun validatePassword(raw: String): ValidationResult<String> {
        if (raw.length < PASSWORD_MIN)
            return ValidationResult.Err("La contraseña debe tener al menos $PASSWORD_MIN caracteres.")
        if (raw.length > PASSWORD_MAX)
            return ValidationResult.Err("La contraseña es demasiado larga (máx. $PASSWORD_MAX caracteres).")
        if (!raw.any { it.isDigit() })
            return ValidationResult.Err("La contraseña debe contener al menos un número.")
        if (!raw.any { it.isUpperCase() })
            return ValidationResult.Err("La contraseña debe contener al menos una letra mayúscula.")
        return ValidationResult.Ok(raw)
    }

    // ─── Helper: valida un artículo completo de una vez ──────────────────────

    data class ValidatedItem(
        val name: String,
        val quantity: Double,
        val unit: String,
        val price: Double,
        val category: String
    )

    fun validateItem(
        name: String,
        quantity: Double,
        unit: String,
        price: Double,
        category: String
    ): ValidationResult<ValidatedItem> {
        val vName     = validateItemName(name)     as? ValidationResult.Err ?: null
        val vQuantity = validateQuantity(quantity) as? ValidationResult.Err ?: null
        val vUnit     = validateUnit(unit)         as? ValidationResult.Err ?: null
        val vPrice    = validatePrice(price)       as? ValidationResult.Err ?: null
        val vCategory = validateCategory(category) as? ValidationResult.Err ?: null

        val firstError = listOf(
            validateItemName(name),
            validateQuantity(quantity),
            validateUnit(unit),
            validatePrice(price),
            validateCategory(category)
        ).filterIsInstance<ValidationResult.Err>().firstOrNull()

        if (firstError != null) return firstError

        return ValidationResult.Ok(
            ValidatedItem(
                name     = (validateItemName(name)     as ValidationResult.Ok).value,
                quantity = (validateQuantity(quantity) as ValidationResult.Ok).value,
                unit     = (validateUnit(unit)         as ValidationResult.Ok).value,
                price    = (validatePrice(price)       as ValidationResult.Ok).value,
                category = (validateCategory(category) as ValidationResult.Ok).value
            )
        )
    }
}
