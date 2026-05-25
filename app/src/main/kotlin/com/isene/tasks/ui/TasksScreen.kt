package com.isene.tasks.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.isene.tasks.data.Category
import com.isene.tasks.data.Item
import com.isene.tasks.viewmodel.TasksViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TasksViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onFilePicked) }

    // Reload-on-resume — picks up changes Syncthing made while the
    // app was backgrounded without forcing a manual refresh.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.reloadIfChanged()
        }
        lifecycle.addObserver(obs)
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                vm.clearToast()
            }
        }
    }

    var showAddCategory by remember { mutableStateOf(false) }
    var renameCategoryOf by remember { mutableStateOf<Category?>(null) }
    var addItemUnder by remember { mutableStateOf<Category?>(null) }
    var editItem by remember { mutableStateOf<Pair<Category, Item>?>(null) }
    var deleteCategoryOf by remember { mutableStateOf<Category?>(null) }
    var menuFor by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.displayName ?: "tasks",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { vm.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Pick file")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        floatingActionButton = {
            if (state.pickedUri != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddCategory = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Category") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        if (state.pickedUri == null) {
            EmptyState(onPick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (cat in state.hyperlist.categories) {
                    item(key = "cat:${cat.name}") {
                        CategoryHeader(
                            category = cat,
                            collapsed = state.collapsed.contains(cat.name),
                            onToggle = { vm.toggleCollapse(cat.name) },
                            onAddItem = { addItemUnder = cat },
                            onMenu = { menuFor = cat },
                        )
                    }
                    if (!state.collapsed.contains(cat.name)) {
                        items(cat.items, key = { "it:${cat.name}:${it.text}:${cat.items.indexOf(it)}" }) { item ->
                            ItemRow(
                                item = item,
                                onEdit = { editItem = cat to item },
                                onDelete = { vm.deleteItem(cat, item) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.size(72.dp)) }  // FAB clearance
            }
        }

        menuFor?.let { cat ->
            DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { renameCategoryOf = cat; menuFor = null },
                )
                DropdownMenuItem(
                    text = { Text("Delete (and items)") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { deleteCategoryOf = cat; menuFor = null },
                )
            }
        }
    }

    if (showAddCategory) {
        TextDialog(
            title = "New category",
            onDismiss = { showAddCategory = false },
            onConfirm = { name ->
                if (name.isNotBlank()) vm.addCategory(name.trim())
                showAddCategory = false
            },
        )
    }
    renameCategoryOf?.let { cat ->
        TextDialog(
            title = "Rename category",
            initial = cat.name,
            onDismiss = { renameCategoryOf = null },
            onConfirm = { name ->
                if (name.isNotBlank() && name != cat.name) vm.renameCategory(cat, name.trim())
                renameCategoryOf = null
            },
        )
    }
    addItemUnder?.let { cat ->
        TextDialog(
            title = "New item in “${cat.name}”",
            onDismiss = { addItemUnder = null },
            onConfirm = { text ->
                if (text.isNotBlank()) vm.addItem(cat, text.trim())
                addItemUnder = null
            },
        )
    }
    editItem?.let { (cat, item) ->
        TextDialog(
            title = "Edit item",
            initial = item.text,
            onDismiss = { editItem = null },
            onConfirm = { text ->
                if (text.isNotBlank() && text != item.text) vm.renameItem(cat, item, text.trim())
                editItem = null
            },
        )
    }
    deleteCategoryOf?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleteCategoryOf = null },
            title = { Text("Delete “${cat.name}”?") },
            text = { Text("Removes the category and ${cat.items.size} item(s).") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(cat); deleteCategoryOf = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCategoryOf = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(16.dp))
        Text("Pick your todo.hl", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text(
            "Choose the file Syncthing dropped on your phone " +
                "(e.g. Documents/tasks/todo.hl).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onPick) { Text("Pick file") }
    }
}

@Composable
private fun CategoryHeader(
    category: Category,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAddItem: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${category.items.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Category menu")
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: Item,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp)
                .clickable { onEdit() },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Item menu")
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
