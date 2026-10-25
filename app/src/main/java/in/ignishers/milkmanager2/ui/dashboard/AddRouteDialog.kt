package `in`.ignishers.milkmanager2.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.models.RouteGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouteDialog(
    parentId: Long?,
    onDismiss: () -> Unit,
    onSave: (RouteGroup) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var routeName by remember { mutableStateOf("") }

    fun performSave() {
        if (routeName.isBlank()) return
        keyboardController?.hide()
        val routeGroup = RouteGroup().apply {
            this.name = routeName.trim()
            this.parentId = parentId
            this.sortOrder = 0
            this.syncSellerId = null
        }
        onSave(routeGroup)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_route_group)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text(stringResource(R.string.route_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performSave() })
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { performSave() },
                enabled = routeName.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
