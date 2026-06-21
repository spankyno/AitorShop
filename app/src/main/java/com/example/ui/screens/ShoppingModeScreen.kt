package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ShoppingItemEntity
import com.example.ui.MainViewModel
import java.util.Locale

@Composable
fun ShoppingModeScreen(
    viewModel: MainViewModel,
    innerPadding: PaddingValues
) {
    val items by viewModel.items.collectAsState()
    val totalEstimated by viewModel.totalEstimatedCost.collectAsState()
    val purchasedEstimated by viewModel.purchasedEstimatedCost.collectAsState()

    val context = LocalContext.current
    var showCheckoutConfirmation by remember { mutableStateOf(false) }

    val purchasedItems = items.filter { it.isPurchased }
    val remainingItems = items.filter { !it.isPurchased }
    val totalCount = items.size
    val purchasedCount = purchasedItems.size
    val progress = if (totalCount > 0) purchasedCount.toFloat() / totalCount else 0f

    Scaffold(
        modifier = Modifier.padding(innerPadding)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // --- PROGRESS BANNER ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modo de Compra Activo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$purchasedCount / $totalCount",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "Carrito: %.2f €", purchasedEstimated),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "Total lista: %.2f €", totalEstimated),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Añade artículos en la pestaña anterior para empezar a comprar.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (remainingItems.isNotEmpty()) {
                        item {
                            Text(
                                "Pendientes (${remainingItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(remainingItems, key = { it.id }) { item ->
                            ShoppingModeRow(item = item, onClick = { viewModel.toggleItemPurchased(item) })
                        }
                    }

                    if (purchasedItems.isNotEmpty()) {
                        item {
                            Text(
                                "En el carrito (${purchasedItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }

                        items(purchasedItems, key = { it.id }) { item ->
                            ShoppingModeRow(item = item, onClick = { viewModel.toggleItemPurchased(item) })
                        }
                    }
                }

                // Check out button
                if (purchasedCount > 0) {
                    Button(
                        onClick = { showCheckoutConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp, top = 8.dp)
                            .testTag("finish_shopping_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "Finalizar Compra (%.2f €)", purchasedEstimated),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog
    if (showCheckoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showCheckoutConfirmation = false },
            title = { Text("¿Finalizar compra actual?") },
            text = {
                Text("Se guardará el recibo en el historial por un coste de ${String.format(Locale.getDefault(), "%.2f €", purchasedEstimated)} y se vaciarán los artículos del carrito.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completePurchase()
                        showCheckoutConfirmation = false
                        Toast.makeText(context, "¡Compra finalizada y guardada en el historial!", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.testTag("checkout_confirm_btn")
                ) {
                    Text("Guardar e Historial")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutConfirmation = false }) {
                    Text("Seguir Comprando")
                }
            }
        )
    }
}

@Composable
fun ShoppingModeRow(
    item: ShoppingItemEntity,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPurchased) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isPurchased) MaterialTheme.colorScheme.secondary 
                        else Color.Transparent
                    )
                    .border(
                        width = 2.dp,
                        color = if (item.isPurchased) MaterialTheme.colorScheme.secondary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isPurchased) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isPurchased) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${item.quantity.toIntIfDecimal()} ${item.unit}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (item.price > 0) {
                        Text(
                            String.format(Locale.getDefault(), "%.2f €", item.price),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
