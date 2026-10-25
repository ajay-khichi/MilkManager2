package `in`.ignishers.milkmanager2.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.models.Customer
import `in`.ignishers.milkmanager2.models.RouteGroup
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentRouteId: Long? = null,
    onNavigateToRoute: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPos: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val allRouteGroups by viewModel.routeGroups.collectAsState()
    val totalRevenue by viewModel.totalRevenue.collectAsState()
    val totalMilk by viewModel.totalMilk.collectAsState()
    val isAutoEntryMasterEnabled by viewModel.isAutoEntryMasterEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkPendingAutoEntries(context)
    }
    
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    
    var showAddRouteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMilkPriceDialog by remember { mutableStateOf(false) }
    
    // Action Menu States
    var selectedCustomerForAction by remember { mutableStateOf<Customer?>(null) }
    var selectedRouteForAction by remember { mutableStateOf<RouteGroup?>(null) }
    var customerToMove by remember { mutableStateOf<Customer?>(null) }
    var routeToMove by remember { mutableStateOf<RouteGroup?>(null) }
    
    var isFabExpanded by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(targetValue = if (isFabExpanded) 45f else 0f)

    val listState = rememberLazyListState()
    var uiItems by remember { mutableStateOf(emptyList<Any>()) }

    LaunchedEffect(allRouteGroups, customers, currentRouteId) {
        val routes = allRouteGroups.filter { it.parentId == currentRouteId }.sortedBy { it.sortOrder ?: 0 }
        val custs = customers.filter { it.routeGroupId == currentRouteId }.sortedBy { it.sortOrder ?: 0 }
        uiItems = routes + custs
    }
    val defaultAppName = stringResource(R.string.app_name)
    val currentRouteName = allRouteGroups.find { it.id == currentRouteId }?.name ?: defaultAppName

    val currentMilkRate by viewModel.currentMilkRate.collectAsState()

    if (showAddCustomerDialog || customerToEdit != null) {
        AddCustomerDialog(
            initialCustomer = customerToEdit,
            milkRate = currentMilkRate?.price ?: BigDecimal("60.0"),
            onDismiss = { 
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { customer ->
                if (customerToEdit == null) {
                    customer.routeGroupId = currentRouteId
                    viewModel.addCustomer(customer)
                } else {
                    viewModel.updateCustomer(customer)
                }
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    if (showAddRouteDialog) {
        AddRouteDialog(
            parentId = currentRouteId,
            onDismiss = { showAddRouteDialog = false },
            onSave = { routeGroup ->
                viewModel.addRouteGroup(routeGroup)
                showAddRouteDialog = false
            }
        )
    }

    if (showMilkPriceDialog) {
        val currentPrice = viewModel.currentMilkRate.collectAsState().value
        UpdateMilkPriceDialog(
            currentPrice = currentPrice?.price,
            onDismiss = { showMilkPriceDialog = false },
            onSave = { newRate ->
                viewModel.saveMilkRate(newRate)
                showMilkPriceDialog = false
                Toast.makeText(context, context.getString(R.string.milk_price_updated), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (selectedCustomerForAction != null) {
        ModalBottomSheet(onDismissRequest = { selectedCustomerForAction = null }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.manage_item, selectedCustomerForAction?.name ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.edit_customer)) },
                    leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        customerToEdit = selectedCustomerForAction
                        selectedCustomerForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.move_to_route)) },
                    leadingContent = { Icon(Icons.Filled.ArrowForward, contentDescription = null) },
                    modifier = Modifier.clickable {
                        customerToMove = selectedCustomerForAction
                        selectedCustomerForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.delete_customer), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        val c = selectedCustomerForAction!!
                        if (c.currentDue != null && c.currentDue!! > BigDecimal.ZERO) {
                            Toast.makeText(context, context.getString(R.string.cannot_delete_customer_due), Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.deleteCustomer(c)
                        }
                        selectedCustomerForAction = null
                    }
                )
            }
        }
    }

    if (selectedRouteForAction != null) {
        ModalBottomSheet(onDismissRequest = { selectedRouteForAction = null }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.manage_item, selectedRouteForAction?.name ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.move_route)) },
                    leadingContent = { Icon(Icons.Filled.ArrowForward, contentDescription = null) },
                    modifier = Modifier.clickable {
                        routeToMove = selectedRouteForAction
                        selectedRouteForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.delete_route), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        val route = selectedRouteForAction!!
                        val hasSubRoutes = allRouteGroups.any { it.parentId == route.id }
                        val hasCustomers = customers.any { it.routeGroupId == route.id }
                        if (hasSubRoutes || hasCustomers) {
                            Toast.makeText(context, context.getString(R.string.cannot_delete_route_not_empty), Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.deleteRouteGroup(route)
                        }
                        selectedRouteForAction = null
                    }
                )
            }
        }
    }

    if (customerToMove != null) {
        val customer = customerToMove!!
        val customerLabel = customer.name ?: stringResource(R.string.customer)
        MoveItemDialog(
            title = stringResource(R.string.move_title, customerLabel),
            currentParentId = customer.routeGroupId,
            availableRoutes = allRouteGroups,
            onDismiss = { customerToMove = null },
            onSelectDestination = { targetRouteId ->
                val maxSort = customers.filter { it.routeGroupId == targetRouteId }.maxOfOrNull { it.sortOrder ?: 0 } ?: -1
                customer.sortOrder = maxSort + 1
                customer.routeGroupId = targetRouteId
                viewModel.updateCustomer(customer)
                val targetName = allRouteGroups.find { it.id == targetRouteId }?.name ?: context.getString(R.string.main_dashboard)
                Toast.makeText(context, context.getString(R.string.moved_item_to, customer.name ?: "", targetName), Toast.LENGTH_SHORT).show()
                customerToMove = null
            }
        )
    }

    if (routeToMove != null) {
        val route = routeToMove!!
        val descendantIds = remember(route.id, allRouteGroups) {
            val descendants = mutableSetOf<Long>()
            fun findChildren(parentId: Long) {
                allRouteGroups.filter { it.parentId == parentId }.forEach { child ->
                    child.id?.let { cid ->
                        if (descendants.add(cid)) {
                            findChildren(cid)
                        }
                    }
                }
            }
            route.id?.let { findChildren(it) }
            descendants
        }
        val availableRoutes = allRouteGroups.filter { it.id != route.id && it.id !in descendantIds }
        val routeLabel = route.name ?: stringResource(R.string.route)
        MoveItemDialog(
            title = stringResource(R.string.move_title, routeLabel),
            currentParentId = route.parentId,
            availableRoutes = availableRoutes,
            onDismiss = { routeToMove = null },
            onSelectDestination = { targetParentId ->
                val maxSort = allRouteGroups.filter { it.parentId == targetParentId }.maxOfOrNull { it.sortOrder ?: 0 } ?: -1
                route.sortOrder = maxSort + 1
                route.parentId = targetParentId
                viewModel.updateRouteGroup(route)
                val targetName = allRouteGroups.find { it.id == targetParentId }?.name ?: context.getString(R.string.main_dashboard)
                Toast.makeText(context, context.getString(R.string.moved_item_to, route.name ?: "", targetName), Toast.LENGTH_SHORT).show()
                routeToMove = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentRouteName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (currentRouteId != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Auto",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isAutoEntryMasterEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setAutoEntryMasterEnabled(context, enabled)
                                Toast.makeText(
                                    context,
                                    if (enabled) "Master Auto Entry ON (5:00 AM & 4:00 PM)" else "Master Auto Entry OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.onPrimary,
                                checkedBorderColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                uncheckedTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                uncheckedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.update_milk_price)) },
                                onClick = { 
                                    showMilkPriceDialog = true
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sales_records)) },
                                onClick = { 
                                    Toast.makeText(context, context.getString(R.string.sales_records_coming_soon), Toast.LENGTH_SHORT).show()
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Filled.List, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = { 
                                    onNavigateToSettings()
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = isFabExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.new_route), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        FloatingActionButton(
                            onClick = { 
                                isFabExpanded = false
                                showAddRouteDialog = true 
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.add_route))
                        }
                    }
                }

                AnimatedVisibility(visible = isFabExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.new_customer), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        FloatingActionButton(
                            onClick = { 
                                isFabExpanded = false
                                showAddCustomerDialog = true 
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.add_customer))
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.expand),
                        modifier = Modifier.rotate(fabRotation)
                    )
                }
            }
        }
    ) { paddingValues ->
        var swipeOffsetX by remember { mutableStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(currentRouteId) {
                    if (currentRouteId != null) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffsetX > 150f) {
                                    onNavigateBack()
                                }
                                swipeOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (dragAmount > 0) { // Only track rightward swipe
                                    swipeOffsetX += dragAmount
                                }
                            }
                        )
                    }
                }
        ) {
            val configuration = LocalConfiguration.current
            val isCompact = configuration.screenWidthDp < 360
            
            if (currentRouteId == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.total_milk), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                                Text("${totalMilk.toPlainString()} L", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = if (isCompact) 18.sp else 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.revenue), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                                Text("₹${totalRevenue.toPlainString()}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = if (isCompact) 18.sp else 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAutoEntryMasterEnabled) stringResource(R.string.auto_daily_entry_active) else stringResource(R.string.auto_daily_entry_inactive),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (isAutoEntryMasterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isAutoEntryMasterEnabled) stringResource(R.string.active) else stringResource(R.string.disabled),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoEntryMasterEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (uiItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_entries_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val saveSortOrder: () -> Unit = {
                    var routeOrder = 0
                    var custOrder = 0
                    uiItems.forEach { item ->
                        if (item is RouteGroup) {
                            if (item.sortOrder != routeOrder) viewModel.updateRouteSortOrder(item, routeOrder)
                            routeOrder++
                        } else if (item is Customer) {
                            if (item.sortOrder != custOrder) viewModel.updateCustomerSortOrder(item, custOrder)
                            custOrder++
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiItems.size, key = { index -> 
                        val item = uiItems[index]
                        if (item is RouteGroup) "r_${item.id}" else "c_${(item as Customer).id}" 
                    }) { index ->
                        val item = uiItems[index]
                        val isFirstOfType = index == 0 || uiItems[index - 1]::class != item::class
                        val isLastOfType = index == uiItems.lastIndex || uiItems[index + 1]::class != item::class

                        if (item is RouteGroup) {
                            RouteCard(
                                routeGroup = item,
                                canMoveUp = !isFirstOfType,
                                canMoveDown = !isLastOfType,
                                onMoveUp = {
                                    val mutableItems = uiItems.toMutableList()
                                    mutableItems.add(index - 1, mutableItems.removeAt(index))
                                    uiItems = mutableItems
                                    saveSortOrder()
                                },
                                onMoveDown = {
                                    val mutableItems = uiItems.toMutableList()
                                    mutableItems.add(index + 1, mutableItems.removeAt(index))
                                    uiItems = mutableItems
                                    saveSortOrder()
                                },
                                onClick = { onNavigateToRoute(item.id!!) },
                                onLongClick = { selectedRouteForAction = item }
                            )
                        } else if (item is Customer) {
                            CustomerCard(
                                customer = item,
                                canMoveUp = !isFirstOfType,
                                canMoveDown = !isLastOfType,
                                onMoveUp = {
                                    val mutableItems = uiItems.toMutableList()
                                    mutableItems.add(index - 1, mutableItems.removeAt(index))
                                    uiItems = mutableItems
                                    saveSortOrder()
                                },
                                onMoveDown = {
                                    val mutableItems = uiItems.toMutableList()
                                    mutableItems.add(index + 1, mutableItems.removeAt(index))
                                    uiItems = mutableItems
                                    saveSortOrder()
                                },
                                onClick = { onNavigateToPos(item.id!!) },
                                onLongClick = { selectedCustomerForAction = item }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouteCard(
    routeGroup: RouteGroup, 
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit, 
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.route), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = routeGroup.name ?: stringResource(R.string.unnamed_route),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp, 
                        contentDescription = stringResource(R.string.move_up),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, 
                        contentDescription = stringResource(R.string.move_down),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomerCard(
    customer: Customer,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit, 
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = customer.name ?: stringResource(R.string.unknown_customer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                val due = customer.currentDue ?: BigDecimal.ZERO
                Text(
                    text = stringResource(R.string.due_format, due.toPlainString()),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (due > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp, 
                        contentDescription = stringResource(R.string.move_up),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, 
                        contentDescription = stringResource(R.string.move_down),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun MoveItemDialog(
    title: String,
    currentParentId: Long?,
    availableRoutes: List<RouteGroup>,
    onDismiss: () -> Unit,
    onSelectDestination: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                item {
                    val isCurrent = currentParentId == null
                    ListItem(
                        headlineContent = { 
                            Text(
                                stringResource(R.string.main_dashboard_root), 
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        leadingContent = { 
                            Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
                        },
                        supportingContent = if (isCurrent) { { Text(stringResource(R.string.current_location)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent) {
                                onSelectDestination(null)
                            }
                    )
                    HorizontalDivider()
                }

                items(availableRoutes) { route ->
                    val isCurrent = currentParentId == route.id
                    ListItem(
                        headlineContent = { 
                            Text(
                                route.name ?: stringResource(R.string.unnamed_route),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        leadingContent = { 
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) 
                        },
                        supportingContent = if (isCurrent) { { Text(stringResource(R.string.current_location)) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent) {
                                onSelectDestination(route.id)
                            }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
