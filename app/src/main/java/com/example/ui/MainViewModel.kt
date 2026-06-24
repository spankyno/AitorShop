package com.example.ui

import android.app.Application
import android.speech.RecognizerIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.remote.SupabaseClient
import com.example.data.repository.ShoppingRepository
import com.example.security.InputValidator
import com.example.security.InputValidator.ValidationResult
import com.example.security.SecureSessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceParsedItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ---- Sesión segura (token JWT cifrado en Android Keystore) ----
    private val session = SecureSessionManager(application)

    private val encryptedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SecureSessionManager.KEY_TOKEN -> {
                _userToken.value = session.accessToken
            }
            SecureSessionManager.KEY_EMAIL -> {
                _userEmail.value = session.userEmail
            }
        }
    }

    private val plainPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SecureSessionManager.KEY_LOGGED_IN -> {
                _isUserLoggedIn.value = session.isLoggedIn
            }
            SecureSessionManager.KEY_GUEST -> {
                _isGuestMode.value = session.isGuestMode
            }
            SecureSessionManager.KEY_ACTIVE_LIST -> {
                _activeListId.value = session.activeListId
            }
            SecureSessionManager.KEY_DARK_MODE -> {
                _isDarkMode.value = session.isDarkMode
            }
        }
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    // Database & Repository initialization
    private val database = AppDatabase.getDatabase(application)
    private val repository = ShoppingRepository(
        application,
        session,
        database.shoppingItemDao(),
        database.predefinedItemDao(),
        database.purchaseHistoryDao()
    )

    // Active shared list ID (persisted locally — dato no sensible)
    private val _activeListId = MutableStateFlow(session.activeListId)
    val activeListId: StateFlow<String> = _activeListId.asStateFlow()

    // Dark Mode Toggle state (persisted — dato no sensible)
    private val _isDarkMode = MutableStateFlow(session.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Sync state indicators
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncSuccessful = MutableStateFlow<Boolean?>(null)
    val lastSyncSuccessful: StateFlow<Boolean?> = _lastSyncSuccessful.asStateFlow()

    // Realtime shared alerts flow from repository
    val syncAlerts: SharedFlow<String> = repository.syncAlerts

    // --- Authentication State (lee de EncryptedSharedPreferences) ---
    private val _userEmail = MutableStateFlow(session.userEmail)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userToken = MutableStateFlow(session.accessToken)
    val userToken: StateFlow<String?> = _userToken.asStateFlow()

    private val _isUserLoggedIn = MutableStateFlow(session.isLoggedIn)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _isGuestMode = MutableStateFlow(session.isGuestMode)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    // Errores de validación para mostrar en la UI
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    fun clearValidationError() { _validationError.value = null }

    fun enableGuestMode() {
        session.setGuestMode(true)
        _isGuestMode.value = true
    }

    fun signUpWithSupabase(email: String, password: String, onDone: (Boolean, String) -> Unit) {
        // ── Validación de entrada ──────────────────────────────────────────
        val vEmail = InputValidator.validateEmail(email)
        if (vEmail is ValidationResult.Err) { onDone(false, vEmail.message); return }
        val vPass  = InputValidator.validatePassword(password)
        if (vPass  is ValidationResult.Err) { onDone(false, vPass.message);  return }

        val authApi = SupabaseClient.getAuthApi()
        val apiKey = SupabaseClient.getApiKey()
        if (authApi == null || apiKey.isEmpty()) {
            onDone(false, "Supabase no está configurado.")
            return
        }
        viewModelScope.launch {
            try {
                val response = authApi.signUp(com.example.data.remote.AuthRequest(
                    (vEmail as ValidationResult.Ok).value,
                    (vPass  as ValidationResult.Ok).value
                ))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    saveSession(body.user?.email ?: email, body.accessToken ?: "", body.refreshToken ?: "")
                    onDone(true, "¡Cuenta creada correctamente!")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error de credenciales/registro"
                    onDone(false, "Error: $errorBody")
                }
            } catch (e: Exception) {
                onDone(false, "Excepción: ${e.message}")
            }
        }
    }

    fun signInWithSupabase(email: String, password: String, onDone: (Boolean, String) -> Unit) {
        // ── Validación de entrada ──────────────────────────────────────────
        val vEmail = InputValidator.validateEmail(email)
        if (vEmail is ValidationResult.Err) { onDone(false, vEmail.message); return }
        val vPass  = InputValidator.validatePassword(password)
        if (vPass  is ValidationResult.Err) { onDone(false, vPass.message);  return }

        val authApi = SupabaseClient.getAuthApi()
        val apiKey = SupabaseClient.getApiKey()
        if (authApi == null || apiKey.isEmpty()) {
            onDone(false, "Supabase no está configurado.")
            return
        }
        viewModelScope.launch {
            try {
                val response = authApi.signInWithPassword("password", com.example.data.remote.AuthRequest(
                    (vEmail as ValidationResult.Ok).value,
                    (vPass  as ValidationResult.Ok).value
                ))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    saveSession(body.user?.email ?: email, body.accessToken ?: "", body.refreshToken ?: "")
                    onDone(true, "Sesión iniciada correctamente")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error de credenciales"
                    onDone(false, "Error: $errorBody")
                }
            } catch (e: Exception) {
                onDone(false, "Excepción: ${e.message}")
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, onDone: (Boolean, String) -> Unit) {
        val authApi = SupabaseClient.getAuthApi()
        val apiKey = SupabaseClient.getApiKey()
        if (authApi == null || apiKey.isEmpty()) {
            onDone(false, "Supabase no está configurado.")
            return
        }
        viewModelScope.launch {
            try {
                val response = authApi.signInWithIdToken("id_token", com.example.data.remote.IdTokenAuthRequest("google", idToken), apiKey)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    saveSession(body.user?.email ?: "usuario_google@gmail.com", body.accessToken ?: "", body.refreshToken ?: "")
                    onDone(true, "Sesión de Google iniciada")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Error con ID Token"
                    onDone(false, "Error Google: $errorBody")
                }
            } catch (e: Exception) {
                onDone(false, "Excepción Google: ${e.message}")
            }
        }
    }

    fun logout() {
        session.clearSession()           // borra token cifrado + flags
        _userEmail.value = null
        _userToken.value = null
        _isUserLoggedIn.value = false
        _isGuestMode.value = false
        _lastSyncSuccessful.value = null
    }

    private fun saveSession(email: String, token: String, refreshToken: String = "") {
        session.saveSession(email, token, refreshToken)   // cifrado en Keystore
        _userEmail.value = email
        _userToken.value = token
        _isUserLoggedIn.value = true
        _isGuestMode.value = false
        forceSync()
    }

    // Check if Supabase keys are configured in build variables
    val isSupabaseConfigured: Boolean
        get() = SupabaseClient.getApi() != null && SupabaseClient.getApiKey().isNotEmpty()

    // --- List Observables ---
    val items: StateFlow<List<ShoppingItemEntity>> = _activeListId
        .flatMapLatest { listId -> repository.getItems(listId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val predefinedItems: StateFlow<List<PredefinedItemEntity>> = repository.getPredefinedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseHistory: StateFlow<List<PurchaseHistoryEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Estimated Calculations ---
    val totalEstimatedCost: StateFlow<Double> = items
        .map { list -> list.sumOf { it.price * it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val purchasedEstimatedCost: StateFlow<Double> = items
        .map { list -> list.filter { it.isPurchased }.sumOf { it.price * it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Voice parsing dialog state trigger
    private val _voiceParsedResult = MutableSharedFlow<VoiceParsedItem>(extraBufferCapacity = 1)
    val voiceParsedResult: SharedFlow<VoiceParsedItem> = _voiceParsedResult

    init {
        session.registerListeners(encryptedPrefsListener, plainPrefsListener)

        val connectivityManager = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                private var wasConnected = true

                override fun onAvailable(network: android.net.Network) {
                    if (!wasConnected) {
                        forceSync()
                    }
                    wasConnected = true
                }

                override fun onLost(network: android.net.Network) {
                    wasConnected = false
                }
            }
            this.networkCallback = callback
            try {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            // Seed defaults
            repository.seedPredefinedItemsIfNeeded()
            // Initial Sync
            forceSync()
        }
        viewModelScope.launch {
            // Periodic background sync loop every 10 seconds to sync list changes continuously across devices
            while (true) {
                kotlinx.coroutines.delay(10000)
                if (isSupabaseConfigured && _isUserLoggedIn.value) {
                    val success = repository.triggerSync(_activeListId.value)
                    _lastSyncSuccessful.value = success
                }
            }
        }
    }

    // --- Actions ---
    fun updateActiveListId(newListId: String) {
        when (val v = InputValidator.validateListId(newListId)) {
            is ValidationResult.Err -> { _validationError.value = v.message; return }
            is ValidationResult.Ok  -> {
                _activeListId.value = v.value
                session.activeListId = v.value
                viewModelScope.launch { forceSync() }
            }
        }
    }

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        session.isDarkMode = next
    }

    fun forceSync() {
        if (!isSupabaseConfigured) {
            _lastSyncSuccessful.value = null
            return
        }
        if (!_isUserLoggedIn.value) {
            _lastSyncSuccessful.value = null
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.triggerSync(_activeListId.value)
            _lastSyncSuccessful.value = success
            _isSyncing.value = false
        }
    }

    fun addItem(name: String, quantity: Double, unit: String, price: Double, category: String) {
        when (val v = InputValidator.validateItem(name, quantity, unit, price, category)) {
            is ValidationResult.Err -> { _validationError.value = v.message; return }
            is ValidationResult.Ok  -> viewModelScope.launch {
                repository.addItem(
                    name     = v.value.name,
                    quantity = v.value.quantity,
                    unit     = v.value.unit,
                    price    = v.value.price,
                    category = v.value.category,
                    listId   = _activeListId.value
                )
                forceSync()
            }
        }
    }

    fun toggleItemPurchased(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isPurchased = !item.isPurchased))
            forceSync()
        }
    }

    fun updateItemDetails(item: ShoppingItemEntity, newName: String, newQuantity: Double, newPrice: Double, newUnit: String, newCategory: String) {
        when (val v = InputValidator.validateItem(newName, newQuantity, newUnit, newPrice, newCategory)) {
            is ValidationResult.Err -> { _validationError.value = v.message; return }
            is ValidationResult.Ok  -> viewModelScope.launch {
                repository.updateItem(
                    item.copy(
                        name      = v.value.name,
                        quantity  = v.value.quantity,
                        price     = v.value.price,
                        unit      = v.value.unit,
                        category  = v.value.category,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                forceSync()
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id, _activeListId.value)
            forceSync()
        }
    }

    fun completePurchase() {
        viewModelScope.launch {
            repository.completePurchaseAndClear(_activeListId.value)
            forceSync()
        }
    }

    fun deleteHistoryRecord(id: String) {
        viewModelScope.launch {
            repository.deleteHistoryRecord(id)
        }
    }

    fun addCustomPredefined(name: String, category: String, price: Double, unit: String) {
        val vName     = InputValidator.validateItemName(name)
        if (vName     is ValidationResult.Err) { _validationError.value = vName.message;     return }
        val vCategory = InputValidator.validateCategory(category)
        if (vCategory is ValidationResult.Err) { _validationError.value = vCategory.message; return }
        val vPrice    = InputValidator.validatePrice(price)
        if (vPrice    is ValidationResult.Err) { _validationError.value = vPrice.message;    return }
        val vUnit     = InputValidator.validateUnit(unit)
        if (vUnit     is ValidationResult.Err) { _validationError.value = vUnit.message;     return }

        viewModelScope.launch {
            repository.addPredefinedItem(
                (vName     as ValidationResult.Ok).value,
                (vCategory as ValidationResult.Ok).value,
                (vPrice    as ValidationResult.Ok).value,
                (vUnit     as ValidationResult.Ok).value
            )
        }
    }

    fun removePredefined(item: PredefinedItemEntity) {
        viewModelScope.launch {
            repository.deletePredefinedItem(item)
        }
    }

    // --- Voice Input Parsing ---
    fun handleVoiceInput(text: String) {
        val parsed = parseSpokenGrocery(text)
        _voiceParsedResult.tryEmit(parsed)
    }

    private fun parseSpokenGrocery(spokenText: String): VoiceParsedItem {
        val text = spokenText.lowercase(Locale.getDefault())

        // Examples:
        // "añadir 3 litros de leche entera por 1.20"
        // "cinco paquetes de pasta"
        // "dos panes a un euro con cincuenta"
        // "manzanas"

        var quantity = 1.0
        var unit = "uds."
        var price = 0.0
        var name = spokenText

        // 1. Try to find number figures for quantity at the beginning
        // Detect: un/uno/una, dos, tres, cuatro, cinco, seis, siete, ocho, nueve, diez ... or digit figures "3", "3.5"
        val words = text.split(" ")
        if (words.isNotEmpty()) {
            val firstWord = words[0]
            val numericQuantity = parseSpokenNumber(firstWord)
            if (numericQuantity != null) {
                quantity = numericQuantity
            } else {
                // Try parsing digit
                val digitMatch = "^\\d+([.,]\\d+)?".toRegex().find(firstWord)
                if (digitMatch != null) {
                    quantity = digitMatch.value.replace(",", ".").toDoubleOrNull() ?: 1.0
                }
            }
        }

        // 2. Identify common food units
        val unitsList = listOf(
            "kilo" to "kg", "kilos" to "kg", "kg" to "kg",
            "litro" to "l", "litros" to "l", "l" to "l",
            "bote" to "uds.", "botes" to "uds.", "paquete" to "uds.", "paquetes" to "uds.",
            "gramos" to "g", "gr" to "g", "g" to "g", "pack" to "uds."
        )
        for ((spokenUnit, matchedUnit) in unitsList) {
            if (text.contains(" $spokenUnit ") || text.contains(" $spokenUnit$") || text.startsWith("$spokenUnit ")) {
                unit = matchedUnit
                break
            }
        }

        // 3. Price parsing (e.g. "a 1.25", "por 1.20", "a un euro con noventa", "0.90 euros")
        val pricePatterns = listOf(
            "a\\s+(\\d+([.,]\\d+)?)\\s*euros?".toRegex(),
            "por\\s+(\\d+([.,]\\d+)?)\\s*euros?".toRegex(),
            "a\\s+(\\d+([.,]\\d+)?)".toRegex(),
            "(\\d+([.,]\\d+)?)\\s*euros?".toRegex()
        )

        var priceFound = false
        for (pattern in pricePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val valueStr = match.groupValues[1].replace(",", ".")
                val parsedPrice = valueStr.toDoubleOrNull()
                if (parsedPrice != null) {
                    price = parsedPrice
                    priceFound = true
                    break
                }
            }
        }

        // Handful textual prices like "un euro", "un euro con cincuenta"
        if (!priceFound) {
            if (text.contains("un euro con cincuenta") || text.contains("1,50") || text.contains("1.50")) {
                price = 1.50
            } else if (text.contains("dos euros con cincuenta") || text.contains("2,50")) {
                price = 2.50
            } else if (text.contains("un euro") || text.contains("1 euro")) {
                price = 1.00
            } else if (text.contains("dos euros") || text.contains("2 euros")) {
                price = 2.00
            }
        }

        // Clean up the name by removing numeric quantity words, price tails, and units if specified
        var cleanedName = spokenText
        val wordsToFilter = listOf(
            "añadir", "agrega", "agregar", "pon", "de", "del", "euros", "euro", "con", "a", "por",
            "un", "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
            "kilo", "kilos", "litro", "litros", "paquete", "paquetes", "botes", "bote", "kg", "gr", "gramos", "uds."
        )

        // Split raw input by spaces, filter noise words, and join back
        val rawCleaned = cleanedName.split(" ").filter { word ->
            val w = word.lowercase(Locale.getDefault()).replace(",", "").replace(".", "")
            !wordsToFilter.contains(w) && !w.matches("\\d+".toRegex()) && !w.matches("\\d+[.,]\\d+".toRegex())
        }.joinToString(" ").trim()

        if (rawCleaned.isNotEmpty()) {
            name = rawCleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        return VoiceParsedItem(
            name = name,
            quantity = quantity,
            unit = unit,
            price = price
        )
    }

    private fun parseSpokenNumber(word: String): Double? {
        return when (word.lowercase(Locale.getDefault())) {
            "un", "uno", "una" -> 1.0
            "dos" -> 2.0
            "tres" -> 3.0
            "cuatro" -> 4.0
            "cinco" -> 5.0
            "seis" -> 6.0
            "siete" -> 7.0
            "ocho" -> 8.0
            "nueve" -> 9.0
            "diez" -> 10.0
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        session.unregisterListeners(encryptedPrefsListener, plainPrefsListener)
        val connectivityManager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
