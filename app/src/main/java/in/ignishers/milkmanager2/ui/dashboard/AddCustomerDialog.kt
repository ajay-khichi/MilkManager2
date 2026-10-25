package `in`.ignishers.milkmanager2.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.models.Customer
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun UnitSegmentedButtons(
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val units = listOf(
        "Litres" to stringResource(R.string.liters),
        "ml" to stringResource(R.string.ml),
        "Rupees" to stringResource(R.string.rupees)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            units.forEach { (unitKey, label) ->
                val isSelected = selectedUnit.equals(unitKey, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    onClick = { onUnitSelected(unitKey) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerDialog(
    initialCustomer: Customer? = null,
    milkRate: BigDecimal = BigDecimal("60.0"),
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val mobileFocusRequester = remember { FocusRequester() }
    val morningFocusRequester = remember { FocusRequester() }
    val eveningFocusRequester = remember { FocusRequester() }
    val dueFocusRequester = remember { FocusRequester() }

    var name by remember { mutableStateOf(initialCustomer?.name ?: "") }
    var mobile by remember { mutableStateOf(initialCustomer?.mobile ?: "") }
    var morningQtyStr by remember {
        mutableStateOf(
            initialCustomer?.defaultQtyMorning?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var eveningQtyStr by remember {
        mutableStateOf(
            initialCustomer?.defaultQtyEvening?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var openingDueStr by remember {
        mutableStateOf(
            initialCustomer?.currentDue?.takeIf { it > BigDecimal.ZERO }?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var entryMode by remember { mutableStateOf("Litres") } // "Litres", "ml", "Rupees"

    fun switchUnit(newUnit: String) {
        if (newUnit.equals(entryMode, ignoreCase = true)) return
        val mVal = morningQtyStr.toBigDecimalOrNull()
        val eVal = eveningQtyStr.toBigDecimalOrNull()

        if (mVal != null && mVal > BigDecimal.ZERO) {
            val baseLiters = when (entryMode) {
                "Litres" -> mVal
                "ml" -> mVal.divide(BigDecimal("1000.0"), 4, RoundingMode.HALF_UP)
                "Rupees" -> if (milkRate > BigDecimal.ZERO) mVal.divide(milkRate, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
                else -> mVal
            }
            morningQtyStr = when (newUnit) {
                "Litres" -> baseLiters.stripTrailingZeros().toPlainString()
                "ml" -> baseLiters.multiply(BigDecimal("1000.0")).stripTrailingZeros().toPlainString()
                "Rupees" -> baseLiters.multiply(milkRate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else -> baseLiters.toPlainString()
            }
        }

        if (eVal != null && eVal > BigDecimal.ZERO) {
            val baseLiters = when (entryMode) {
                "Litres" -> eVal
                "ml" -> eVal.divide(BigDecimal("1000.0"), 4, RoundingMode.HALF_UP)
                "Rupees" -> if (milkRate > BigDecimal.ZERO) eVal.divide(milkRate, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
                else -> eVal
            }
            eveningQtyStr = when (newUnit) {
                "Litres" -> baseLiters.stripTrailingZeros().toPlainString()
                "ml" -> baseLiters.multiply(BigDecimal("1000.0")).stripTrailingZeros().toPlainString()
                "Rupees" -> baseLiters.multiply(milkRate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                else -> baseLiters.toPlainString()
            }
        }

        entryMode = newUnit
    }

    val mLabel = when (entryMode) {
        "Litres" -> stringResource(R.string.morning_liters)
        "ml" -> stringResource(R.string.morning_ml)
        else -> stringResource(R.string.morning_rupees)
    }
    val eLabel = when (entryMode) {
        "Litres" -> stringResource(R.string.evening_liters)
        "ml" -> stringResource(R.string.evening_ml)
        else -> stringResource(R.string.evening_rupees)
    }
    val prefix = when (entryMode) {
        "Litres" -> "L "
        "ml" -> "ml "
        else -> "₹ "
    }

    fun saveCustomer() {
        if (name.isBlank()) return
        keyboardController?.hide()
        val morningVal = morningQtyStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val eveningVal = eveningQtyStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val openingDue = openingDueStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val morningLiters = when (entryMode) {
            "Rupees" -> if (milkRate > BigDecimal.ZERO) morningVal.divide(milkRate, 3, RoundingMode.HALF_UP) else BigDecimal.ZERO
            "ml" -> morningVal.divide(BigDecimal("1000.0"), 3, RoundingMode.HALF_UP)
            else -> morningVal
        }

        val eveningLiters = when (entryMode) {
            "Rupees" -> if (milkRate > BigDecimal.ZERO) eveningVal.divide(milkRate, 3, RoundingMode.HALF_UP) else BigDecimal.ZERO
            "ml" -> eveningVal.divide(BigDecimal("1000.0"), 3, RoundingMode.HALF_UP)
            else -> eveningVal
        }

        val customer = initialCustomer ?: Customer()
        customer.apply {
            this.name = name.trim()
            this.mobile = mobile.trim()
            this.defaultQtyMorning = morningLiters
            this.defaultQtyEvening = eveningLiters
            this.currentDue = openingDue
            if (initialCustomer == null) {
                this.sortOrder = 0
                this.syncSellerId = null
            }
        }
        onSave(customer)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialCustomer == null) stringResource(R.string.add_customer) else stringResource(R.string.edit_customer), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { mobileFocusRequester.requestFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text(stringResource(R.string.mobile_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { morningFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(mobileFocusRequester)
                )

                Text(
                    text = stringResource(R.string.entry_mode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Switchable self-selective buttons: Litres, ml, Rupees
                UnitSegmentedButtons(
                    selectedUnit = entryMode,
                    onUnitSelected = { switchUnit(it) }
                )

                OutlinedTextField(
                    value = morningQtyStr,
                    onValueChange = { morningQtyStr = it },
                    label = { Text(mLabel) },
                    prefix = { Text(prefix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { eveningFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(morningFocusRequester)
                )

                OutlinedTextField(
                    value = eveningQtyStr,
                    onValueChange = { eveningQtyStr = it },
                    label = { Text(eLabel) },
                    prefix = { Text(prefix) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { dueFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(eveningFocusRequester)
                )

                OutlinedTextField(
                    value = openingDueStr,
                    onValueChange = { openingDueStr = it },
                    label = { Text(if (initialCustomer == null) stringResource(R.string.opening_due_rupees) else stringResource(R.string.current_due_rupees)) },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveCustomer() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(dueFocusRequester)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { saveCustomer() },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
