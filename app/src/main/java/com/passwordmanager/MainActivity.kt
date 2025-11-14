package com.passwordmanager

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.passwordmanager.data.AppDatabase
import com.passwordmanager.data.PasswordEntity
import com.passwordmanager.data.PasswordRepository
import com.passwordmanager.security.AuthManager
import com.passwordmanager.security.EncryptionHelper
import com.passwordmanager.ui.theme.HomeViewModel
import com.passwordmanager.ui.theme.HomeViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "password_manager_db"
        ).build()

        val repository = PasswordRepository(
            dao = database.passwordDao(),
            encryption = EncryptionHelper()
        )

        setContent {
            val authManager = remember { AuthManager(this) }

            PasswordManagerApp(
                repository = repository,
                authManager = authManager
            )
        }
    }
}

@Composable
fun PasswordManagerApp(
    repository: PasswordRepository,
    authManager: AuthManager
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    val authenticate = {
        if (authManager.canAuthenticate()) {
            authManager.authenticate(
                onSuccess = {
                    isAuthenticated = true
                    showAuthDialog = false
                },
                onError = { showAuthDialog = true }
            )
        } else {
            isAuthenticated = true // No biometrics, allow access
        }
    }

    LaunchedEffect(Unit) {
        authenticate()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color(0xFF2B6BE6))
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (isAuthenticated) {
                HomeScreen(
                    repository = repository,
                    authManager = authManager
                )
            } else if (showAuthDialog) {
                AuthDialog(onRetry = authenticate)
            }
        }
    }
}

@Composable
fun AuthDialog(onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissible */ },
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Lock Icon",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Authentication Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "This app is locked. Please authenticate to continue.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text("Authenticate")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: PasswordRepository,
    authManager: AuthManager,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(repository)
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passwords by viewModel.passwords.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PasswordEntity?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Password Manager",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF3F6F9))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = Color(0xFF2B6BE6)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        },
        containerColor = Color(0xFFF3F6F9)
    ) { padding ->
        if (passwords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No accounts yet\nTap + to add one",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }

                items(items = passwords, key = { it.id }) { item ->
                    PasswordRow(
                        item = item,
                        onClick = {
                            selectedItem = item
                            showDetailSheet = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            AddAccountSheet(
                onClose = { showAddSheet = false },
                onSave = { title, username, password ->
                    scope.launch {
                        try {
                            repository.addPassword(title, username, password)
                            Toast.makeText(context, "Account added successfully", Toast.LENGTH_SHORT).show()
                            showAddSheet = false
                        } catch (e: Exception) {
                            Log.d("TAG_E", "error adding account >> ${e.message}")
                            Toast.makeText(context, "Error adding account: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    if (showDetailSheet && selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showDetailSheet = false
                selectedItem = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            DetailAccountSheet(
                item = selectedItem!!,
                repository = repository,
                authManager = authManager,
                onClose = {
                    showDetailSheet = false
                    selectedItem = null
                },
                onUpdate = { entity, newTitle, newUsername, newPassword ->
                    scope.launch {
                        try {
                            repository.updatePassword(
                                entity.copy(title = newTitle, username = newUsername),
                                newPassword
                            )
                            Toast.makeText(context, "Account updated successfully", Toast.LENGTH_SHORT).show()
                            showDetailSheet = false
                            selectedItem = null
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error updating account: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = { entity ->
                    scope.launch {
                        try {
                            viewModel.delete(entity)
                            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                            showDetailSheet = false
                            selectedItem = null
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error deleting account: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun PasswordRow(
    item: PasswordEntity,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.username,
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Open",
            tint = Color(0xFFB0B6BE)
        )
    }
}

@Composable
fun AddAccountSheet(
    onClose: () -> Unit,
    onSave: (title: String, username: String, password: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Account Name") },
            placeholder = { Text("e.g., Google, Facebook") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username / Email") },
            placeholder = { Text("user@example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("Enter password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSave(title.trim(), username.trim(), password) },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text(text = "Add New Account")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PasswordDetailRow(
    label: String,
    isDecrypted: Boolean,
    decryptedPassword: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (!isDecrypted) "●●●●●●●●●●" else if (passwordVisible) decryptedPassword else "●●●●●●●●●●",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle password visibility",
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun DetailAccountSheet(
    item: PasswordEntity,
    repository: PasswordRepository,
    authManager: AuthManager,
    onClose: () -> Unit,
    onUpdate: (entity: PasswordEntity, title: String, username: String, password: String) -> Unit,
    onDelete: (entity: PasswordEntity) -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf(item.title) }
    var username by remember { mutableStateOf(item.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isPasswordDecrypted by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Account" else "Account Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isEditing) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Account Name") },
                readOnly = !isEditing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / Email") },
                readOnly = !isEditing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                readOnly = !isEditing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(
                    label = "Account Type",
                    value = title
                )
                DetailRow(
                    label = "Username/ Email",
                    value = username
                )
                PasswordDetailRow(
                    label = "Password",
                    isDecrypted = isPasswordDecrypted,
                    decryptedPassword = password,
                    passwordVisible = passwordVisible,
                    onToggleVisibility = {
                        if (!isPasswordDecrypted) {
                            authManager.authenticate(
                                onSuccess = {
                                    try {
                                        password = repository.decryptPassword(item)
                                        isPasswordDecrypted = true
                                        passwordVisible = true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error getting password", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onError = { Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            passwordVisible = !passwordVisible
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditing) {
                TextButton(
                    onClick = {
                        isEditing = false
                        title = item.title
                        username = item.username
                        password = ""
                        isPasswordDecrypted = false
                        passwordVisible = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Cancel", color = Color(0xFF2B6BE6))
                }
                Button(
                    onClick = { onUpdate(item, title.trim(), username.trim(), password) },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B6BE6)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Save Changes")
                }
            } else {
                Button(
                    onClick = {
                        authManager.authenticate(
                            onSuccess = {
                                try {
                                    password = repository.decryptPassword(item)
                                    isPasswordDecrypted = true
                                    isEditing = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error getting password", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = {
                                Toast.makeText(context, "Authentication failed, cannot edit", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete ${item.title}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
