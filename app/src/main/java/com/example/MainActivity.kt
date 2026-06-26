package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// Message model representing real-time chat messages
data class Message(
    val id: String = "",
    val sender: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

class MainActivity : ComponentActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var messagesRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Programmatic initialization of Firebase without google-services.json
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyD3q22qKvq-B-KCnDmC0VjAOtVUf1lEWDE")
                .setApplicationId("1:103585895934:web:6b7b40a8b29ef0496e1caf")
                .setDatabaseUrl("https://yhchat-28b00-default-rtdb.firebaseio.com")
                .setProjectId("yhchat-28b00")
                .setStorageBucket("yhchat-28b00.firebasestorage.app")
                .build()

            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this, options)
            }
            database = FirebaseDatabase.getInstance("https://yhchat-28b00-default-rtdb.firebaseio.com")
            messagesRef = database.getReference("messages")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Firebase initialization error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        setContent {
            MyApplicationTheme {
                ChatAppScreen(messagesRef)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppScreen(messagesRef: DatabaseReference) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Local Username Management & SharedPreferences Persistence
    val sharedPrefs = remember { context.getSharedPreferences("yhchat_prefs", Context.MODE_PRIVATE) }
    var currentUsername by remember {
        mutableStateOf(
            sharedPrefs.getString("username", null) ?: "User_${Random.nextInt(1000, 9999)}"
        )
    }

    // Messages and connection states
    var messagesList by remember { mutableStateOf(listOf<Message>()) }
    var isConnected by remember { mutableStateOf(false) }
    var inputMessageText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Lazy list state for scroll control
    val lazyListState = rememberLazyListState()

    // 2. Setup Realtime Listener to get database sync updates
    DisposableEffect(messagesRef) {
        // Monitor database connection status
        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        val connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isConnected = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectionListener)

        // Listen for new messages
        val messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val sender = child.child("sender").getValue(String::class.java) ?: "Anonymous"
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    val timestampVal = child.child("timestamp").value
                    val timestamp = when (timestampVal) {
                        is Long -> timestampVal
                        is Map<*, *> -> System.currentTimeMillis() // fallback
                        else -> System.currentTimeMillis()
                    }
                    list.add(Message(id, sender, text, timestamp))
                }
                // Sort by timestamp
                list.sortBy { it.timestamp }
                messagesList = list

                // Auto Scroll to last element whenever messages update
                if (list.isNotEmpty()) {
                    scope.launch {
                        delay(100)
                        lazyListState.animateScrollToItem(list.size - 1)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error reading database: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        // Limit query to last 150 messages for optimal performance
        val query = messagesRef.limitToLast(150)
        query.addValueEventListener(messagesListener)

        onDispose {
            connectedRef.removeEventListener(connectionListener)
            query.removeEventListener(messagesListener)
        }
    }

    // Method to send a message
    val sendMessage = {
        val trimmed = inputMessageText.trim()
        if (trimmed.isNotEmpty()) {
            val messageMap = mapOf(
                "sender" to currentUsername,
                "text" to trimmed,
                "timestamp" to ServerValue.TIMESTAMP
            )
            messagesRef.push().setValue(messageMap)
                .addOnSuccessListener {
                    inputMessageText = "" // Clean input field
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to send: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            // Elegant modern header with green pulsing dot and profile badge
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "YHChat",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Real-time connection status pulse
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.7f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = EaseInOutCirc),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .drawBehind {
                                        drawCircle(
                                            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            radius = size.minDimension / 2 * if (isConnected) scale else 1.0f
                                        )
                                    }
                            )
                        }
                    },
                    actions = {
                        // Profile/Username indicator chip
                        AssistChip(
                            onClick = { showRenameDialog = true },
                            label = {
                                Text(
                                    text = currentUsername,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("edit_nickname")
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        bottomBar = {
            // Beautiful Floating / docked message input bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputMessageText,
                            onValueChange = { inputMessageText = it },
                            placeholder = { Text("أكتب رسالتك هنا...") },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .testTag("message_input"),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    sendMessage()
                                    keyboardController?.hide()
                                }
                            ),
                            singleLine = false,
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = sendMessage,
                            enabled = inputMessageText.trim().isNotEmpty(),
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (inputMessageText.trim().isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                )
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = if (inputMessageText.trim().isNotEmpty()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        // Main content layout containing messages
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messagesList.isEmpty()) {
                // Polished artistic empty state when there are no messages
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "No Chat Messages",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "لا توجد رسائل بعد",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "كن أول من يرسل رسالة في هذه المحادثة الفورية!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Responsive Scrollable Chat list
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("chat_list"),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = messagesList, key = { it.id }) { message ->
                        val isCurrentUser = message.sender == currentUsername
                        MessageBubbleRow(message = message, isCurrentUser = isCurrentUser)
                    }
                }
            }
        }
    }

    // 3. Elegant customizable user Rename Dialog
    if (showRenameDialog) {
        var tempNameText by remember { mutableStateOf(currentUsername) }
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "تغيير اسم المستخدم",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "اختر اسم مستعار لكي يظهر للآخرين عند إرسال رسائل.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = tempNameText,
                        onValueChange = { tempNameText = it },
                        label = { Text("اسم المستخدم") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleanName = tempNameText.trim()
                                if (cleanName.isNotEmpty()) {
                                    currentUsername = cleanName
                                    sharedPrefs.edit().putString("username", cleanName).apply()
                                    showRenameDialog = false
                                } else {
                                    Toast.makeText(context, "الاسم لا يمكن أن يكون فارغاً", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text("حفظ")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubbleRow(message: Message, isCurrentUser: Boolean) {
    // Beautiful slide + fade entry animation per message item
    var animatedVisState by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = message.id) {
        animatedVisState = true
    }

    AnimatedVisibility(
        visible = animatedVisState,
        enter = fadeIn(animationSpec = tween(350)) + slideInVertically(
            initialOffsetY = { 30 },
            animationSpec = tween(350, easing = EaseOutBack)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
            ) {
                // Show sender name for messages from other users
                if (!isCurrentUser) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                    )
                }

                // Styled chat bubble
                Box(
                    modifier = Modifier
                        .then(
                            if (isCurrentUser) {
                                Modifier.background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 4.dp
                                    )
                                )
                            } else {
                                Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 16.dp
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 16.dp
                                        )
                                    )
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrentUser) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Timestamp formatted elegantly
                if (message.timestamp > 0L) {
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

