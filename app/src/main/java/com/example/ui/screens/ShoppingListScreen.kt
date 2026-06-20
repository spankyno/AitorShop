package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PredefinedItemEntity
import com.example.data.local.ShoppingItemEntity
import com.example.ui.MainViewModel
import com.example.ui.VoiceParsedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: MainViewModel,
    innerPadding: PaddingValues
) {
    val items by viewModel.items.collectAsState()
    val predefined by viewModel.predefinedItems.collectAsState()
    val totalCost by viewModel.totalEstimatedCost.collectAsState()
    val purchasedCost by viewModel.purchasedEstimatedCost.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncSuccess by viewModel.lastSyncSuccessful.collectAsState()
    val activeListId by viewModel.activeListId.collectAsState()

    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editTargetItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var showListIdDialog by remember { mutableStateOf(false) }

    // State pre-populated from speech recognition
    var showVoiceDialogResult by remember { mutableStateOf<VoiceParsedItem?>(null) }

    // Listen to voice parsed results
    LaunchedEffect(key1 = true) {
        viewModel.voiceParsedResult.collect { parsed ->
            showVoiceDialogResult = parsed
        }
    }

    // Speech intent recognizer
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (spokenText != null) {
                viewModel.handleVoiceInput(spokenText)
            }
        }
    }

    fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el producto (ej: '3 kilos de manzanas' o 'leche a un euro')")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "El dictado por voz no está disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.padding(innerPadding),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Voice Floating Action Button
                FloatingActionButton(
                    onClick = { launchVoiceRecognition() },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.testTag("voice_fab")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Dictar producto")
                }

                // Add Item Floating Action Button
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_item_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir artículo")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // --- HEADER: Shared list ID ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lista compartida",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeListId,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // DB connection status indicator
                        val connectionColor = if (viewModel.isSupabaseConfigured) {
                            if (lastSyncSuccess == false) Color.Yellow else Color.Green
                        } else {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(connectionColor)
                        )

                        Text(
                            text = if (viewModel.isSupabaseConfigured) {
                                if (lastSyncSuccess == false) "Error Sync" else "Sincronizado"
                            } else "Modo Local",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        IconButton(onClick = { showListIdDialog = true }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Cambiar ID de lista")
                        }
                    }
                }
            }

            // --- CONTROL DE GASTOS (Expense control stats banner) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Coste Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            text = String.format("%,.2f €", totalCost),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("En Carrito", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            text = String.format("%,.2f €", purchasedCost),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // --- PREDEFINED ITEMS HORIZONTAL SCROLL LIST ---
            if (predefined.isNotEmpty()) {
                Text(
                    text = "Añadir rápido",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(predefined) { item ->
                        SuggestionChip(
                            onClick = {
                                viewModel.addItem(
                                    name = item.name,
                                    quantity = 1.0,
                                    unit = item.defaultUnit,
                                    price = item.defaultPrice,
                                    category = item.category
                                )
                                Toast.makeText(context, "${item.name} añadido", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(item.name) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // --- CATEGORIES & SHOPPING ITEM LIST ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mi Lista de la Compra",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.forceSync() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sincronizar ahora", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (items.isEmpty()) {
                // Empty state illustration
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_list_header_1781954525058),
                        contentDescription = "No hay productos",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tu lista está vacía",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Escribe un producto o pulsa el micrófono para dictarlo.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Categorize items
                val categories = items.groupBy { it.category }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.forEach { (category, categoryItems) ->
                        item {
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }

                        items(categoryItems, key = { it.id }) { item ->
                            val isPurchased = item.isPurchased
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPurchased) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isPurchased) 0.dp else 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editTargetItem = item }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isPurchased,
                                        onCheckedChange = { viewModel.toggleItemPurchased(item) },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.testTag("checkbox_${item.id}")
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPurchased) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (isPurchased) TextDecoration.LineThrough else null
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.quantity.toIntIfDecimal()} ${item.unit}",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            if (item.price > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(3.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                                )
                                                Text(
                                                    text = String.format("%.2f €", item.price),
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = { viewModel.deleteItem(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar artículo",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR DICTATION & EDITING ---

    // 1. Share/Custom List ID Dialog
    if (showListIdDialog) {
        var tempId by remember { mutableStateOf(activeListId) }
        AlertDialog(
            onDismissRequest = { showListIdDialog = false },
            title = { Text("ID de Lista Compartida") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Introduce un código de lista idéntico en otros dispositivos para sincronizar los artículos en tiempo real.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempId,
                        onValueChange = { tempId = it },
                        label = { Text("Código de lista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateActiveListId(tempId)
                        showListIdDialog = false
                    },
                    modifier = Modifier.testTag("confirm_list_id_button")
                ) {
                    Text("Unirse / Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showListIdDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 2. Add Item Dialog Sheet
    if (showAddDialog) {
        var tempName by remember { mutableStateOf("") }
        var tempQuantity by remember { mutableStateOf("1") }
        var tempUnit by remember { mutableStateOf("uds.") }
        var tempPrice by remember { mutableStateOf("") }
        var tempCategory by remember { mutableStateOf("Otros") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nuevo Artículo") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Nombre del artículo *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = tempQuantity,
                                onValueChange = { tempQuantity = it },
                                label = { Text("Cant.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            // Dropdown or text field for Units
                            OutlinedTextField(
                                value = tempUnit,
                                onValueChange = { tempUnit = it },
                                label = { Text("Unidad") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = tempPrice,
                            onValueChange = { tempPrice = it },
                            label = { Text("Precio estimado (€)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        // Category auto selection
                        Text("Categoría:", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        val categoriesList = listOf("Otros", "Despensa", "Lácteos", "Panadería", "Fruta y Verdura", "Bebidas", "Limpieza", "Carnicería", "Pescadería")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categoriesList) { cat ->
                                val isSelected = tempCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { tempCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            val qty = tempQuantity.toDoubleOrNull() ?: 1.0
                            val prc = tempPrice.toDoubleOrNull() ?: 0.0
                            viewModel.addItem(tempName, qty, tempUnit, prc, tempCategory)
                            showAddDialog = false
                        } else {
                            Toast.makeText(context, "El nombre de artículo es obligatorio", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("add_item_dialog_confirm")
                ) {
                    Text("Insertar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 3. Edit Item Dialog
    editTargetItem?.let { item ->
        var tempName by remember { mutableStateOf(item.name) }
        var tempQuantity by remember { mutableStateOf(item.quantity.toIntIfDecimal().toString()) }
        var tempUnit by remember { mutableStateOf(item.unit) }
        var tempPrice by remember { mutableStateOf(if (item.price > 0) item.price.toString() else "") }
        var tempCategory by remember { mutableStateOf(item.category) }

        AlertDialog(
            onDismissRequest = { editTargetItem = null },
            title = { Text("Editar Artículo") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Nombre del artículo *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = tempQuantity,
                                onValueChange = { tempQuantity = it },
                                label = { Text("Cant.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = tempUnit,
                                onValueChange = { tempUnit = it },
                                label = { Text("Unidad") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = tempPrice,
                            onValueChange = { tempPrice = it },
                            label = { Text("Precio estimado (€)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text("Categoría:", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        val categoriesList = listOf("Otros", "Despensa", "Lácteos", "Panadería", "Fruta y Verdura", "Bebidas", "Limpieza", "Carnicería", "Pescadería")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categoriesList) { cat ->
                                val isSelected = tempCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { tempCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            val qty = tempQuantity.toDoubleOrNull() ?: item.quantity
                            val prc = tempPrice.toDoubleOrNull() ?: 0.0
                            viewModel.updateItemDetails(item, tempName, qty, prc, tempUnit, tempCategory)
                            editTargetItem = null
                        }
                    },
                    modifier = Modifier.testTag("edit_item_dialog_confirm")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTargetItem = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 4. Voice Input Parsed Result Confirmation Dialog
    showVoiceDialogResult?.let { parsed ->
        var editedName by remember { mutableStateOf(parsed.name) }
        var editedQuantity by remember { mutableStateOf(parsed.quantity.toIntIfDecimal().toString()) }
        var editedUnit by remember { mutableStateOf(parsed.unit) }
        var editedPrice by remember { mutableStateOf(if (parsed.price > 0) parsed.price.toString() else "") }
        var editedCategory by remember { mutableStateOf("Otros") }

        AlertDialog(
            onDismissRequest = { showVoiceDialogResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Confirmar Entrada por Voz")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("He entendido esto. Revisa los detalles y pulsa Guardar.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Nombre del artículo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editedQuantity,
                            onValueChange = { editedQuantity = it },
                            label = { Text("Cantidad") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editedUnit,
                            onValueChange = { editedUnit = it },
                            label = { Text("Unidad") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = editedPrice,
                        onValueChange = { editedPrice = it },
                        label = { Text("Precio (€)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Categoría:", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    val categoriesList = listOf("Otros", "Despensa", "Lácteos", "Panadería", "Fruta y Verdura", "Bebidas", "Limpieza", "Carnicería")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoriesList) { cat ->
                            val isSelected = editedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { editedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            val qty = editedQuantity.toDoubleOrNull() ?: 1.0
                            val prc = editedPrice.toDoubleOrNull() ?: 0.0
                            viewModel.addItem(editedName, qty, editedUnit, prc, editedCategory)
                            showVoiceDialogResult = null
                        }
                    },
                    modifier = Modifier.testTag("voice_dialog_confirm")
                ) {
                    Text("Añadir a la lista")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceDialogResult = null }) {
                    Text("Descartar")
                }
            }
        )
    }
}

fun Double.toIntIfDecimal(): String {
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.2f", this)
    }
}
