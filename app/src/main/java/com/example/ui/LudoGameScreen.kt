package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.R
import com.example.model.*
import com.example.viewmodel.LudoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LudoGameScreen(
    viewModel: LudoViewModel,
    modifier: Modifier = Modifier
) {
    val players by viewModel.players.collectAsState()
    val tokens by viewModel.tokens.collectAsState()
    val currentTurnPlayerIndex by viewModel.currentTurnPlayerIndex.collectAsState()
    val diceState by viewModel.diceState.collectAsState()
    val gamePhase by viewModel.gamePhase.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val movableTokens by viewModel.movableTokens.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMovingToken by viewModel.isMovingToken.collectAsState()
    val winnerList by viewModel.winnerList.collectAsState()
    val winnerCelebration by viewModel.winnerCelebration.collectAsState()
    val aiSpeedMs by viewModel.aiSpeedMs.collectAsState()
    val moveHistory by viewModel.moveHistory.collectAsState()
    val turnTimeRemaining by viewModel.turnTimeRemaining.collectAsState()

    var showRulesModal by remember { mutableStateOf(false) }
    var showMoveHistorySidebar by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp >= 600 || isLandscape

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ludo Classic",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleMute() }) {
                        Text(
                            text = if (isMuted) "Muted" else "Sound On",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (gamePhase == GamePhase.PLAYING) {
                        IconButton(onClick = { showMoveHistorySidebar = !showMoveHistorySidebar }) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Move History",
                                tint = if (showMoveHistorySidebar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { showRulesModal = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Show Rules"
                        )
                    }
                    if (gamePhase != GamePhase.SETUP) {
                        IconButton(onClick = { viewModel.setupDefaultGame() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Match"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (gamePhase) {
                GamePhase.SETUP -> SetupScreen(
                    players = players,
                    onPlayerConfigChanged = viewModel::setPlayerConfig,
                    onStartGame = viewModel::startGame,
                    modifier = Modifier.fillMaxSize()
                )
                GamePhase.PLAYING -> {
                    if (isWideScreen) {
                        // Wide Screen Grid: Left side Board, Right side dashboard
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .align(Alignment.CenterVertically)
                            ) {
                                LudoBoardContainer(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LudoBoard(
                                        tokens = tokens,
                                        movableTokens = movableTokens,
                                        onTokenSelected = viewModel::moveToken,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ActiveTurnHeader(
                                    activePlayer = viewModel.getCurrentPlayer(),
                                    diceState = diceState,
                                    isMovingToken = isMovingToken,
                                    turnTime = turnTimeRemaining,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DiceActionBox(
                                        diceState = diceState,
                                        activePlayer = viewModel.getCurrentPlayer(),
                                        isMovingToken = isMovingToken,
                                        onRoll = { viewModel.rollDice() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    AiSpeedControl(
                                        aiSpeedMs = aiSpeedMs,
                                        onSpeedChanged = viewModel::setAiSpeed,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                LogHistoryView(
                                    logs = logs,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // Portrait Stack: Header -> Board -> Action Roll -> Log
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActiveTurnHeader(
                                activePlayer = viewModel.getCurrentPlayer(),
                                diceState = diceState,
                                isMovingToken = isMovingToken,
                                turnTime = turnTimeRemaining,
                                modifier = Modifier.fillMaxWidth()
                            )

                            LudoBoardContainer(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LudoBoard(
                                    tokens = tokens,
                                    movableTokens = movableTokens,
                                    onTokenSelected = viewModel::moveToken,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            DiceActionBox(
                                diceState = diceState,
                                activePlayer = viewModel.getCurrentPlayer(),
                                isMovingToken = isMovingToken,
                                onRoll = { viewModel.rollDice() },
                                modifier = Modifier.fillMaxWidth()
                            )

                            AiSpeedControl(
                                aiSpeedMs = aiSpeedMs,
                                onSpeedChanged = viewModel::setAiSpeed,
                                modifier = Modifier.fillMaxWidth()
                            )

                            LogHistoryView(
                                logs = logs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                        }
                    }
                }
                GamePhase.FINISHED -> {
                    FinishedScreen(
                        winnerList = winnerList,
                        onPlayAgain = viewModel::setupDefaultGame,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (showRulesModal) {
                RulesSheetDialog(onDismiss = { showRulesModal = false })
            }

            ConfettiCelebration(
                winnerCelebration = winnerCelebration,
                onFinished = { /* Keep state active or dismissed via CTA click */ }
            )

            if (winnerCelebration != null) {
                WinnerAnnouncementOverlay(
                    winner = winnerCelebration!!,
                    onDismiss = { viewModel.dismissCelebration() }
                )
            }

            // Move History Sidebar Overlay Backdrop
            AnimatedVisibility(
                visible = showMoveHistorySidebar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showMoveHistorySidebar = false }
                )
            }

            // Move History Sidebar Drawer
            AnimatedVisibility(
                visible = showMoveHistorySidebar,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.85f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Move History",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { showMoveHistorySidebar = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Sidebar"
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                        Text(
                            text = "Last ${moveHistory.take(10).size} moves",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (moveHistory.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No moves recorded yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Roll the dice and make a move to begin tracking history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(moveHistory.take(10), key = { it.id }) { record ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            // Player color bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(record.playerColor.composeColor, CircleShape)
                                                    .border(1.dp, Color.White, CircleShape)
                                            )

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = record.playerName,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = record.playerColor.composeColor
                                                )
                                                Text(
                                                    text = record.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            // Dice face indicator
                                            MiniDiceView(
                                                value = record.diceValue,
                                                borderColor = record.playerColor.composeColor
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
    }
}

@Composable
fun LudoBoardContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .padding(4.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color(0xFFC7D2FE).copy(alpha = 0.2f),
                spotColor = Color(0xFF818CF8).copy(alpha = 0.15f)
            )
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
        ) {
            content()
        }
    }
}

// Player setup before launching game
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    players: List<Player>,
    onPlayerConfigChanged: (Int, String, PlayerType, Boolean) -> Unit,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Decorative Hero Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_ludo_banner_1782117635417),
                contentDescription = "Ludo Game Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = "Game Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Configure 2 to 4 players and choose if they are human or local intelligent AI bots.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Player Roster
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            players.forEachIndexed { idx, player ->
                PlayerConfigRow(
                    player = player,
                    onConfigChanged = { name, type, active ->
                        onPlayerConfigChanged(idx, name, type, active)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val activeCount = players.filter { it.isActive }.size
        Button(
            onClick = onStartGame,
            enabled = activeCount >= 2,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_game_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(27.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Ludo Match", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlayerConfigRow(
    player: Player,
    onConfigChanged: (String, PlayerType, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = player.color.composeColor.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, player.color.composeColor.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player Color indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(player.color.composeColor, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )

            // Switch to enable player
            Checkbox(
                checked = player.isActive,
                onCheckedChange = { onConfigChanged(player.name, player.type, it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = player.color.composeColor
                )
            )

            // Player configuration inputs
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = player.name,
                    onValueChange = { onConfigChanged(it, player.type, player.isActive) },
                    enabled = player.isActive,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = player.color.composeColor,
                        unfocusedBorderColor = player.color.composeColor.copy(alpha = 0.5f)
                    ),
                    placeholder = { Text("Player Name") }
                )

                // Bot toggler Segmented elements
                if (player.isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = player.type == PlayerType.HUMAN,
                            onClick = { onConfigChanged(player.name, PlayerType.HUMAN, player.isActive) },
                            label = { Text("Human", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = player.type == PlayerType.AI,
                            onClick = { onConfigChanged(player.name, PlayerType.AI, player.isActive) },
                            label = { Text("AI Bot", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// Turn active indicators
@Composable
fun ActiveTurnHeader(
    activePlayer: Player?,
    diceState: DiceState,
    isMovingToken: Boolean,
    modifier: Modifier = Modifier
) {
    activePlayer ?: return
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = activePlayer.color.composeColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, activePlayer.color.composeColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(activePlayer.color.composeColor, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activePlayer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = activePlayer.color.composeColor
                )
                
                val promptText = when {
                    diceState.isRolling -> "Rolling dice..."
                    isMovingToken -> "Moving token..."
                    diceState.rollCompleted -> "Select a highlighted token to move!"
                    activePlayer.type == PlayerType.AI -> "AI is analyzing options..."
                    else -> "Your turn! Roll the dice."
                }
                
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Segment styling for game statuses
            Surface(
                color = activePlayer.color.composeColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (activePlayer.type == PlayerType.AI) Icons.Default.Face else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (activePlayer.type == PlayerType.AI) "BOT" else "HUMAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Rolling action deck containing beautiful vectors for the dice pips
@Composable
fun DiceActionBox(
    diceState: DiceState,
    activePlayer: Player?,
    isMovingToken: Boolean,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    activePlayer ?: return

    val canRoll = !diceState.isRolling && !diceState.rollCompleted && !isMovingToken && activePlayer.type == PlayerType.HUMAN

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color(0xFFE2E8F0),
                spotColor = Color(0xFFE2E8F0)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dice Roller",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Animated Die Representing values 1-6
            LudoDie(
                value = diceState.value,
                isRolling = diceState.isRolling,
                activeColor = activePlayer.color.composeColor,
                onClick = { if (canRoll) onRoll() }
            )

            // Dynamic roll helper button
            Button(
                onClick = onRoll,
                enabled = canRoll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activePlayer.color.composeColor,
                    disabledContainerColor = Color.LightGray
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("roll_dice_button")
            ) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (diceState.rollCompleted) "Roll Completed" else if (diceState.isRolling) "Rolling..." else "Roll Dice",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Die layout drawing shaded sides and coordinates inside it procedurally
@Composable
fun LudoDie(
    value: Int,
    isRolling: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    // Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "DiceShake")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShakeAngle"
    )

    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(140, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleFactor"
    )

    val currentRotation = if (isRolling) rotationAngle else 0f
    val currentScale = if (isRolling) scaleFactor else 1.0f

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, activeColor),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(80.dp)
            .scale(currentScale)
            .rotate(currentRotation)
            .clickable(enabled = !isRolling) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val pipCol1 = w * 0.22f
                val pipCol2 = w * 0.5f
                val pipCol3 = w * 0.78f

                val pipRow1 = h * 0.22f
                val pipRow2 = h * 0.5f
                val pipRow3 = h * 0.78f

                val radius = w * 0.088f

                fun drawPip(cx: Float, cy: Float) {
                    drawCircle(
                        color = Color(0xFF222222),
                        radius = radius,
                        center = Offset(cx, cy)
                    )
                }

                when (value) {
                    1 -> {
                        drawPip(pipCol2, pipRow2)
                    }
                    2 -> {
                        drawPip(pipCol1, pipRow1)
                        drawPip(pipCol3, pipRow3)
                    }
                    3 -> {
                        drawPip(pipCol1, pipRow1)
                        drawPip(pipCol2, pipRow2)
                        drawPip(pipCol3, pipRow3)
                    }
                    4 -> {
                        drawPip(pipCol1, pipRow1)
                        drawPip(pipCol3, pipRow1)
                        drawPip(pipCol1, pipRow3)
                        drawPip(pipCol3, pipRow3)
                    }
                    5 -> {
                        drawPip(pipCol1, pipRow1)
                        drawPip(pipCol3, pipRow1)
                        drawPip(pipCol2, pipRow2)
                        drawPip(pipCol1, pipRow3)
                        drawPip(pipCol3, pipRow3)
                    }
                    6 -> {
                        drawPip(pipCol1, pipRow1)
                        drawPip(pipCol3, pipRow1)
                        drawPip(pipCol1, pipCol2)
                        drawPip(pipCol3, pipCol2)
                        drawPip(pipCol1, pipRow3)
                        drawPip(pipCol3, pipRow3)
                    }
                }
            }
        }
    }
}

// Log view to show user rolls and captures clearly
@Composable
fun LogHistoryView(
    logs: List<LudoLog>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll automatically on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Card(
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Live Match logs",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Colored tick bullet matches source color
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    log.color?.composeColor ?: Color.Gray,
                                    CircleShape
                                )
                        )
                        Text(
                            text = log.message,
                            fontSize = 12.sp,
                            color = if (log.color != null) log.color.composeColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// Adjustable sliders to accelerate robot calculations
@Composable
fun AiSpeedControl(
    aiSpeedMs: Long,
    onSpeedChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "AI Play Speed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = aiSpeedMs.toFloat(),
                    onValueChange = { onSpeedChanged(it.toLong()) },
                    valueRange = 200f..1500f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${aiSpeedMs}ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(50.dp)
                )
            }
        }
    }
}

// Celebration winner view
@Composable
fun FinishedScreen(
    winnerList: List<Player>,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Victory Star",
            tint = Color(0xFFFBC02D), // Gold
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Congratulations!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "The ultimate Ludo match has successfully finished! Here are the standings:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Standings cards
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            winnerList.forEachIndexed { idx, winner ->
                val standing = idx + 1
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (standing) {
                            1 -> Color(0xFFFFFBEB) // Gold pastel
                            2 -> Color(0xFFF8FAFC) // Silver pastel
                            else -> Color(0xFFFFF7ED) // Bronze pastel
                        }
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, winner.color.composeColor.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "#$standing",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = winner.color.composeColor
                        )

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(winner.color.composeColor, CircleShape)
                        )

                        Text(
                            text = winner.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = winner.color.composeColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onPlayAgain,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Launch New Match", fontWeight = FontWeight.Bold)
        }
    }
}

// Dialog explaining game configurations
@Composable
fun RulesSheetDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ludo Rules & Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                RuleSection(
                    title = "1. Getting Out of Yard",
                    body = "You must roll exactly a 6 to move a token from your yard into your start cell."
                )

                RuleSection(
                    title = "2. Rolling a 6",
                    body = "Rolling a 6 awards you a BONUS roll! However, rolling three consecutive 6s skips your turn immediately."
                )

                RuleSection(
                    title = "3. Capturing Opponents",
                    body = "Land on an opponent's token in a common track cell to capture them. Captured tokens return to their yard, and you get a BONUS roll!"
                )

                RuleSection(
                    title = "4. Safe Zones (Stars)",
                    body = "Safe zones are represented by Star emblems. Multiple tokens of different colors can occupy these cells simultaneously without being captured."
                )

                RuleSection(
                    title = "5. Moving Home",
                    body = "You must reach your color's central Home triangle with an exact roll. First player to score all 4 tokens home wins standing #1!"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Got It")
                }
            }
        }
    }
}

@Composable
fun RuleSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val opacity: Float,
    val shapeType: Int
)

class ConfettiState {
    var particles by mutableStateOf<List<Particle>>(emptyList())
        private set

    fun burst(width: Float, height: Float, playerColor: Color) {
        val count = 150
        val newList = mutableListOf<Particle>()
        val colors = listOf(
            playerColor,
            Color(0xFF6366F1), // Indigo
            Color(0xFF3B82F6), // Blue
            Color(0xFF10B981), // Green
            Color(0xFFF59E0B), // Amber
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6)  // Purple
        )
        for (i in 0 until count) {
            val fromLeft = i % 2 == 0
            val startX = if (fromLeft) width * 0.15f else width * 0.85f
            val startY = height * 0.85f
            val vx = if (fromLeft) {
                (2f + (kotlin.random.Random.nextFloat() * 10f))
            } else {
                (-2f - (kotlin.random.Random.nextFloat() * 10f))
            }
            val vy = -15f - (kotlin.random.Random.nextFloat() * 18f)
            newList.add(
                Particle(
                    x = startX,
                    y = startY,
                    vx = vx,
                    vy = vy,
                    color = colors[i % colors.size].copy(alpha = 0.9f),
                    size = 10f + (kotlin.random.Random.nextFloat() * 14f),
                    rotation = kotlin.random.Random.nextFloat() * 360f,
                    rotationSpeed = -6f + (kotlin.random.Random.nextFloat() * 12f),
                    opacity = 1f,
                    shapeType = (0..2).random()
                )
            )
        }
        particles = newList
    }

    fun update() {
        if (particles.isEmpty()) return
        particles = particles.mapNotNull { p ->
            if (p.opacity <= 0.02f || p.y > 2500f) {
                null
            } else {
                p.copy(
                    x = p.x + p.vx,
                    y = p.y + p.vy,
                    vx = p.vx * 0.97f, // drag
                    vy = p.vy + 0.38f,  // gravity
                    rotation = p.rotation + p.rotationSpeed,
                    opacity = (p.opacity - 0.015f).coerceAtLeast(0f)
                )
            }
        }
    }

    fun clear() {
        particles = emptyList()
    }
}

@Composable
fun ConfettiCelebration(
    winnerCelebration: Player?,
    onFinished: () -> Unit
) {
    val confettiState = remember { ConfettiState() }
    var layoutWidth by remember { mutableStateOf(0f) }
    var layoutHeight by remember { mutableStateOf(0f) }

    LaunchedEffect(winnerCelebration, layoutWidth) {
        if (winnerCelebration != null && layoutWidth > 0f) {
            confettiState.clear()
            confettiState.burst(layoutWidth, layoutHeight, winnerCelebration.color.composeColor)
        }
    }

    if (confettiState.particles.isNotEmpty()) {
        LaunchedEffect(confettiState.particles) {
            delay(16)
            confettiState.update()
            if (confettiState.particles.isEmpty()) {
                onFinished()
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                layoutWidth = coordinates.size.width.toFloat()
                layoutHeight = coordinates.size.height.toFloat()
            }
    ) {
        confettiState.particles.forEach { p ->
            val alphaColor = p.color.copy(alpha = p.color.alpha * p.opacity)
            when (p.shapeType) {
                0 -> { // rectangle
                    withTransform({
                        rotate(p.rotation, Offset(p.x, p.y))
                    }) {
                        drawRect(
                            color = alphaColor,
                            topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                            size = Size(p.size, p.size * 0.6f)
                        )
                    }
                }
                1 -> { // circle
                    drawCircle(
                        color = alphaColor,
                        radius = p.size / 2,
                        center = Offset(p.x, p.y)
                    )
                }
                2 -> { // triangle
                    withTransform({
                        rotate(p.rotation, Offset(p.x, p.y))
                    }) {
                        val path = Path().apply {
                            val half = p.size / 2
                            moveTo(p.x, p.y - half)
                            lineTo(p.x - half, p.y + half)
                            lineTo(p.x + half, p.y + half)
                            close()
                        }
                        drawPath(path = path, color = alphaColor)
                    }
                }
            }
        }
    }
}

@Composable
fun WinnerAnnouncementOverlay(
    winner: Player,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .clickable(enabled = false) { }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                    ambientColor = Color(0xFFC7D2FE).copy(alpha = 0.25f),
                    spotColor = Color(0xFF818CF8).copy(alpha = 0.2f)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = winner.color.composeColor.copy(alpha = 0.08f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Victory Medal",
                        tint = Color(0xFFFBC02D),
                        modifier = Modifier.size(52.dp)
                    )
                }

                Text(
                    text = "WINNER ANNOUNCEMENT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color(0xFF64748B)
                )

                Text(
                    text = "CONGRATULATIONS!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF0F172A)
                )

                Surface(
                    color = winner.color.composeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, winner.color.composeColor.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(winner.color.composeColor, CircleShape)
                        )
                        Text(
                            text = if (winner.type == PlayerType.AI) "🤖 ${winner.name} (BOT)" else "👤 ${winner.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = winner.color.composeColor
                        )
                    }
                }

                Text(
                    text = "Successfully escorted all 4 tokens home into the center triangles to achieve Ludo standing #${winner.finishOrder}!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = winner.color.composeColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Celebrate & Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MiniDiceView(
    value: Int,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.8f)),
        shadowElevation = 1.dp,
        modifier = modifier.size(24.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height

            val pipCol1 = w * 0.25f
            val pipCol2 = w * 0.5f
            val pipCol3 = w * 0.75f

            val pipRow1 = h * 0.25f
            val pipRow2 = h * 0.5f
            val pipRow3 = h * 0.75f

            val radius = w * 0.1f

            fun drawPip(cx: Float, cy: Float) {
                drawCircle(
                    color = Color(0xFF222222),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }

            when (value) {
                1 -> {
                    drawPip(pipCol2, pipRow2)
                }
                2 -> {
                    drawPip(pipCol1, pipRow1)
                    drawPip(pipCol3, pipRow3)
                }
                3 -> {
                    drawPip(pipCol1, pipRow1)
                    drawPip(pipCol2, pipRow2)
                    drawPip(pipCol3, pipRow3)
                }
                4 -> {
                    drawPip(pipCol1, pipRow1)
                    drawPip(pipCol3, pipRow1)
                    drawPip(pipCol1, pipRow3)
                    drawPip(pipCol3, pipRow3)
                }
                5 -> {
                    drawPip(pipCol1, pipRow1)
                    drawPip(pipCol3, pipRow1)
                    drawPip(pipCol2, pipRow2)
                    drawPip(pipCol1, pipRow3)
                    drawPip(pipCol3, pipRow3)
                }
                6 -> {
                    drawPip(pipCol1, pipRow1)
                    drawPip(pipCol3, pipRow1)
                    drawPip(pipCol1, pipRow2)
                    drawPip(pipCol3, pipRow2)
                    drawPip(pipCol1, pipRow3)
                    drawPip(pipCol3, pipRow3)
                }
            }
        }
    }
}
