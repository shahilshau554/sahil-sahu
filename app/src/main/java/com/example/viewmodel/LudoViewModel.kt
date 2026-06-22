package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// Represents the state of a Ludo game that can be persistent
data class LudoSavedState(
    val players: List<Player>,
    val tokens: List<Token>,
    val currentTurnPlayerIndex: Int,
    val diceState: DiceState,
    val gamePhase: GamePhase,
    val consecutiveSixes: Int,
    val logs: List<LudoLog>,
    val moveHistory: List<LudoMoveRecord> = emptyList()
)

class LudoViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPrefs = context.getSharedPreferences("ludo_game_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val savedStateAdapter = moshi.adapter(LudoSavedState::class.java)

    // Game states
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _tokens = MutableStateFlow<List<Token>>(emptyList())
    val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()

    private val _currentTurnPlayerIndex = MutableStateFlow(0)
    val currentTurnPlayerIndex: StateFlow<Int> = _currentTurnPlayerIndex.asStateFlow()

    private val _diceState = MutableStateFlow(DiceState())
    val diceState: StateFlow<DiceState> = _diceState.asStateFlow()

    private val _gamePhase = MutableStateFlow(GamePhase.SETUP)
    val gamePhase: StateFlow<GamePhase> = _gamePhase.asStateFlow()

    private val _logs = MutableStateFlow<List<LudoLog>>(emptyList())
    val logs: StateFlow<List<LudoLog>> = _logs.asStateFlow()

    private val _movableTokens = MutableStateFlow<List<Token>>(emptyList())
    val movableTokens: StateFlow<List<Token>> = _movableTokens.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isMovingToken = MutableStateFlow(false)
    val isMovingToken: StateFlow<Boolean> = _isMovingToken.asStateFlow()

    private val _winnerList = MutableStateFlow<List<Player>>(emptyList())
    val winnerList: StateFlow<List<Player>> = _winnerList.asStateFlow()

    private val _winnerCelebration = MutableStateFlow<Player?>(null)
    val winnerCelebration: StateFlow<Player?> = _winnerCelebration.asStateFlow()

    private val _moveHistory = MutableStateFlow<List<LudoMoveRecord>>(emptyList())
    val moveHistory: StateFlow<List<LudoMoveRecord>> = _moveHistory.asStateFlow()

    private fun addMoveRecord(playerName: String, playerColor: LudoColor, diceValue: Int, description: String) {
        val record = LudoMoveRecord(
            playerName = playerName,
            playerColor = playerColor,
            diceValue = diceValue,
            description = description
        )
        _moveHistory.update { (listOf(record) + it).take(10) }
    }

    fun dismissCelebration() {
        _winnerCelebration.value = null
    }

    companion object {
        const val TURN_TIMER_MAX_SECONDS = 15
    }

    private val _turnTimeRemaining = MutableStateFlow(TURN_TIMER_MAX_SECONDS)
    val turnTimeRemaining: StateFlow<Int> = _turnTimeRemaining.asStateFlow()

    private var timerJob: Job? = null

    fun startTurnTimer() {
        timerJob?.cancel()
        _turnTimeRemaining.value = TURN_TIMER_MAX_SECONDS
        
        if (_gamePhase.value != GamePhase.PLAYING) return

        timerJob = viewModelScope.launch {
            while (isActive && _turnTimeRemaining.value > 0) {
                delay(1000)
                if (_gamePhase.value == GamePhase.PLAYING && !_isMovingToken.value && !_diceState.value.isRolling) {
                    _turnTimeRemaining.update { prev ->
                        val next = (prev - 1).coerceAtLeast(0)
                        val activePlayer = getCurrentPlayer()
                        if (activePlayer != null && activePlayer.type == PlayerType.HUMAN && next in 1..5) {
                            if (!_isMuted.value) {
                                com.example.ui.LudoSoundSynthesizer.playTimerTick()
                            }
                        }
                        next
                    }
                }
            }
            if (_turnTimeRemaining.value == 0 && _gamePhase.value == GamePhase.PLAYING) {
                onTurnTimeExpired()
            }
        }
    }

    private fun onTurnTimeExpired() {
        val activePlayer = getCurrentPlayer() ?: return
        addLog("Time's up! ${activePlayer.name}'s turn was skipped.", activePlayer.color)
        addMoveRecord(
            playerName = activePlayer.name,
            playerColor = activePlayer.color,
            diceValue = 0,
            description = "Turn skipped (Time's up)"
        )
        if (!_isMuted.value) {
            com.example.ui.LudoSoundSynthesizer.playTimeOutSplash()
        }
        viewModelScope.launch {
            delay(1000)
            passTurn()
        }
    }

    // Configuration
    private val _aiSpeedMs = MutableStateFlow(800L)
    val aiSpeedMs: StateFlow<Long> = _aiSpeedMs.asStateFlow()

    private var activeAiJob: Job? = null

    init {
        // Load existing game or setup defaults
        if (!loadGame()) {
            setupDefaultGame()
        }
    }

    fun setupDefaultGame() {
        timerJob?.cancel()
        _turnTimeRemaining.value = TURN_TIMER_MAX_SECONDS
        _gamePhase.value = GamePhase.SETUP
        
        // Initial 4 players: 1 human, 3 AI
        val initialPlayers = listOf(
            Player(0, "Player 1", LudoColor.RED, PlayerType.HUMAN, isActive = true),
            Player(1, "Player 2", LudoColor.GREEN, PlayerType.AI, isActive = true),
            Player(2, "Player 3", LudoColor.YELLOW, PlayerType.AI, isActive = true),
            Player(3, "Player 4", LudoColor.BLUE, PlayerType.AI, isActive = true)
        )
        _players.value = initialPlayers

        // Initialize 16 tokens (4 for each color)
        val initialTokens = mutableListOf<Token>()
        LudoColor.values().forEach { color ->
            for (i in 0..3) {
                initialTokens.add(Token(id = i, color = color, positionCode = Token.POSITION_YARD))
            }
        }
        _tokens.value = initialTokens
        _currentTurnPlayerIndex.value = 0
        _diceState.value = DiceState()
        _logs.value = listOf(LudoLog(UUID.randomUUID().toString(), System.currentTimeMillis(), "Welcome to Ludo Game! Configure players and start playing."))
        _movableTokens.value = emptyList()
        _winnerList.value = emptyList()
        _moveHistory.value = emptyList()
    }

    fun setPlayerConfig(playerIndex: Int, name: String, type: PlayerType, isActive: Boolean) {
        if (_gamePhase.value != GamePhase.SETUP) return
        _players.update { list ->
            list.mapIndexed { idx, player ->
                if (idx == playerIndex) {
                    player.copy(name = name, type = type, isActive = isActive)
                } else {
                    player
                }
            }
        }
    }

    fun toggleMute() {
        _isMuted.update { !it }
    }

    fun setAiSpeed(speedMs: Long) {
        _aiSpeedMs.value = speedMs
    }

    fun startGame() {
        val activePlayers = _players.value.filter { it.isActive }
        if (activePlayers.size < 2) {
            addLog("Need at least 2 active players to start!", null)
            return
        }

        _gamePhase.value = GamePhase.PLAYING
        
        // Ensure tokens are reset
        val freshTokens = mutableListOf<Token>()
        LudoColor.values().forEach { color ->
            for (i in 0..3) {
                freshTokens.add(Token(id = i, color = color, positionCode = Token.POSITION_YARD))
            }
        }
        _tokens.value = freshTokens
        _winnerList.value = emptyList()
        _moveHistory.value = emptyList()

        // Locate first active player
        var firstPlayerIdx = 0
        while (firstPlayerIdx < 4 && !_players.value[firstPlayerIdx].isActive) {
            firstPlayerIdx++
        }
        _currentTurnPlayerIndex.value = firstPlayerIdx
        
        _diceState.value = DiceState()
        _logs.value = listOf(LudoLog(UUID.randomUUID().toString(), System.currentTimeMillis(), "The match begins! Turn of ${_players.value[firstPlayerIdx].name}.", _players.value[firstPlayerIdx].color))
        
        saveGame()

        // Trigger AI loop check
        triggerAiTurnIfNecessary()
        startTurnTimer()
    }

    fun rollDice() {
        if (_gamePhase.value != GamePhase.PLAYING) return
        if (_diceState.value.isRolling || _diceState.value.rollCompleted) return
        if (_isMovingToken.value) return

        val activePlayer = getCurrentPlayer() ?: return

        viewModelScope.launch {
            _diceState.update { it.copy(isRolling = true) }
            addLog("${activePlayer.name} is rolling...", activePlayer.color)

            // Dynamic rolling animation
            for (i in 0..5) {
                val tempValue = Random.nextInt(1, 7)
                _diceState.update { it.copy(value = tempValue) }
                if (!_isMuted.value) {
                    com.example.ui.LudoSoundSynthesizer.playDiceRollThump()
                }
                delay(100)
            }

            // Final roll result
            val finalRoll = Random.nextInt(1, 7)
            val consecutive = if (finalRoll == 6) {
                _diceState.value.consecutiveSixes + 1
            } else {
                0
            }

            _diceState.update {
                it.copy(
                    value = finalRoll,
                    isRolling = false,
                    rollCompleted = true,
                    consecutiveSixes = consecutive
                )
            }

            if (!_isMuted.value) {
                com.example.ui.LudoSoundSynthesizer.playDiceComplete()
            }

            addLog("${activePlayer.name} rolled a $finalRoll!", activePlayer.color)

            if (consecutive == 3) {
                addLog("${activePlayer.name} rolled three 6s in a row! Turn skipped.", activePlayer.color)
                addMoveRecord(
                    playerName = activePlayer.name,
                    playerColor = activePlayer.color,
                    diceValue = finalRoll,
                    description = "Three consecutive 6s (Turn skipped)"
                )
                delay(1200)
                passTurn()
            } else {
                calculateMovableTokens(finalRoll)
            }
        }
    }

    private fun calculateMovableTokens(roll: Int) {
        val player = getCurrentPlayer() ?: return
        val playerColor = player.color
        val playerTokens = _tokens.value.filter { it.color == playerColor }

        val movable = playerTokens.filter { token ->
            canTokenMove(token, roll)
        }

        _movableTokens.value = movable

        if (movable.isEmpty()) {
            viewModelScope.launch {
                addLog("No possible moves for ${player.name}!", player.color)
                addMoveRecord(
                    playerName = player.name,
                    playerColor = player.color,
                    diceValue = roll,
                    description = "No possible moves"
                )
                delay(1200)
                passTurn()
            }
        } else {
            // Reset the timer for token selection phase!
            _turnTimeRemaining.value = TURN_TIMER_MAX_SECONDS
            // If active player is AI, make AI decision
            if (player.type == PlayerType.AI) {
                viewModelScope.launch {
                    delay(_aiSpeedMs.value)
                    aiMakeMove(movable, roll)
                }
            }
        }
    }

    private fun canTokenMove(token: Token, roll: Int): Boolean {
        if (token.isFinished) return false

        if (token.isInYard) {
            // Needs exactly a 6 to enter outer track
            return roll == 6
        }

        // Token is on track
        if (token.isOnTrack) {
            val stepsWithRoll = token.trackIndex + roll // Not reliable. Let's use internal trackSteps!
            // Wait, we mapped trackSteps:
            // trackSteps range is:
            // 0: start track index
            // 50: home entry track index
            // 51..55: home run step 0..4
            // 56: finished
            val currentSteps = getStepsOfToken(token)
            val nextSteps = currentSteps + roll
            return nextSteps <= 56
        }

        // Token is in home run
        if (token.isInHomeRun) {
            val currentSteps = getStepsOfToken(token)
            val nextSteps = currentSteps + roll
            return nextSteps <= 56
        }

        return false
    }

    // Returns steps (0 to 56) from start to home target
    private fun getStepsOfToken(token: Token): Int {
        return when (token.positionCode) {
            Token.POSITION_YARD -> -1
            Token.POSITION_FINISHED -> 56
            Token.POSITION_HOME_RUN -> 51 + token.homeRunStep
            else -> {
                // To get actual steps along track, we traverse from color's start index.
                // Let's compute distance:
                val distance = (token.trackIndex - token.color.startTrackIndex + 52) % 52
                distance
            }
        }
    }

    fun moveToken(token: Token) {
        if (_gamePhase.value != GamePhase.PLAYING) return
        if (_isMovingToken.value) return
        if (!token.isOnTrack && !token.isInYard && !token.isInHomeRun) return

        val roll = _diceState.value.value
        if (!canTokenMove(token, roll)) return

        _movableTokens.value = emptyList() // Clear options immediately
        _isMovingToken.value = true

        viewModelScope.launch {
            val player = getCurrentPlayer() ?: return@launch
            val stepsNeeded = if (token.isInYard) 1 else roll
            addLog("Moving ${player.name}'s token...", token.color)

            var currentTokenState = token
            
            // Move cell-by-cell for aesthetic fluid motion!
            for (step in 1..stepsNeeded) {
                currentTokenState = getNextSingleStepTokenState(currentTokenState)
                updateTokenStateInList(currentTokenState)
                triggerHapticFeedback()
                if (!_isMuted.value) {
                    com.example.ui.LudoSoundSynthesizer.playStepPop()
                }
                delay(180L) // Subtle delay for walking effect
            }

            var captureOccurred = false
            // Check if landed cell contains any opponent tokens to capture
            var bonusTurn = false
            if (currentTokenState.isOnTrack) {
                val landingIndex = currentTokenState.trackIndex
                
                // If not safe coordinate, capture!
                if (!SAFE_TRACK_INDICES.contains(landingIndex)) {
                    val capturedTokens = _tokens.value.filter {
                        it.color != token.color && it.positionCode == landingIndex
                    }

                    if (capturedTokens.isNotEmpty()) {
                        captureOccurred = true
                        if (!_isMuted.value) {
                            com.example.ui.LudoSoundSynthesizer.playCaptureSplash()
                        }
                        capturedTokens.forEach { captured ->
                            val resetToken = captured.copy(
                                positionCode = Token.POSITION_YARD,
                                trackIndex = -1,
                                homeRunStep = -1
                            )
                            updateTokenStateInList(resetToken)
                            addLog("💥 SPLASH! ${player.name} captured ${captured.color.displayName}'s token!", token.color)
                        }
                        bonusTurn = true
                    }
                }
            }

            // Check if reached home Target
            if (currentTokenState.isFinished) {
                addLog("🎉 Player ${player.name} got a piece home!", token.color)
                bonusTurn = true

                // Check player's winning state (all 4 tokens finished)
                val allFinished = _tokens.value.filter { it.color == token.color }.all { it.isFinished }
                if (allFinished) {
                    val currentWinnersCount = _winnerList.value.size
                    val finalFinishedPlayer = player.copy(hasFinished = true, finishOrder = currentWinnersCount + 1)
                    
                    _players.update { list ->
                        list.map { if (it.id == player.id) finalFinishedPlayer else it }
                    }
                    _winnerList.update { it + finalFinishedPlayer }
                    _winnerCelebration.value = finalFinishedPlayer
                    addLog("🏆 WINNER! ${player.name} has finished Ludo in standing #${currentWinnersCount + 1}!", token.color)

                    // Check if game is over
                    val activeInGame = _players.value.filter { it.isActive && !it.hasFinished }
                    if (activeInGame.size <= 1) {
                        _gamePhase.value = GamePhase.FINISHED
                        addLog("🏁 Game Over! All available standings filled.", null)
                    }
                }
            }

            val moveDescription = when {
                token.isInYard && stepsNeeded == 1 -> "Spawned token onto track"
                currentTokenState.isFinished -> "Piece scored Home!"
                captureOccurred -> "Captured opponent token!"
                else -> "Moved piece $roll steps"
            }
            addMoveRecord(
                playerName = player.name,
                playerColor = player.color,
                diceValue = roll,
                description = moveDescription
            )

            _isMovingToken.value = false

            // Roll of 6 also gives a bonus rolling turn
            if (roll == 6 && !player.hasFinished) {
                bonusTurn = true
            }

            saveGame()

            if (bonusTurn && _gamePhase.value == GamePhase.PLAYING && !player.hasFinished) {
                // Rolled a 6 or captured or scored home -> reset dice roll but keep consecutiveSixes, let player roll again!
                addLog("${player.name} gets a BONUS roll!", token.color)
                _diceState.update {
                    it.copy(
                        rollCompleted = false,
                        isRolling = false
                    )
                }
                triggerAiTurnIfNecessary()
            } else {
                passTurn()
            }
        }
    }

    private fun getNextSingleStepTokenState(current: Token): Token {
        if (current.isInYard) {
            // First step on track is color's start index
            return current.copy(
                positionCode = current.color.startTrackIndex,
                trackIndex = current.color.startTrackIndex,
                homeRunStep = -1
            )
        }

        if (current.isOnTrack) {
            val currentSteps = getStepsOfToken(current)
            val nextSteps = currentSteps + 1
            return if (nextSteps <= 50) {
                val nextTrackIdx = (current.trackIndex + 1) % 52
                current.copy(
                    positionCode = nextTrackIdx,
                    trackIndex = nextTrackIdx,
                    homeRunStep = -1
                )
            } else {
                // Turns into home run position
                current.copy(
                    positionCode = Token.POSITION_HOME_RUN,
                    trackIndex = -1,
                    homeRunStep = 0
                )
            }
        }

        if (current.isInHomeRun) {
            val currentSteps = getStepsOfToken(current)
            val nextSteps = currentSteps + 1
            return if (nextSteps < 56) {
                current.copy(
                    positionCode = Token.POSITION_HOME_RUN,
                    trackIndex = -1,
                    homeRunStep = current.homeRunStep + 1
                )
            } else {
                current.copy(
                    positionCode = Token.POSITION_FINISHED,
                    trackIndex = -1,
                    homeRunStep = -1
                )
            }
        }

        return current
    }

    private fun updateTokenStateInList(newToken: Token) {
        _tokens.update { list ->
            list.map {
                if (it.color == newToken.color && it.id == newToken.id) newToken else it
            }
        }
    }

    private fun passTurn() {
        if (_gamePhase.value != GamePhase.PLAYING) return

        val currentIdx = _currentTurnPlayerIndex.value
        var nextIdx = (currentIdx + 1) % 4
        
        // Loop to find next active and non-finished player
        var attempts = 0
        while (attempts < 4) {
            val nextPlayer = _players.value[nextIdx]
            if (nextPlayer.isActive && !nextPlayer.hasFinished) {
                break
            }
            nextIdx = (nextIdx + 1) % 4
            attempts++
        }

        _currentTurnPlayerIndex.value = nextIdx
        _movableTokens.value = emptyList()
        _diceState.value = DiceState() // Clear dice state for next player

        val nextPlayer = _players.value[nextIdx]
        addLog("Turn passed to ${nextPlayer.name}.", nextPlayer.color)
        
        saveGame()

        // Trigger AI loop
        triggerAiTurnIfNecessary()
        startTurnTimer()
    }

    private fun triggerAiTurnIfNecessary() {
        activeAiJob?.cancel()
        val currentPlayer = getCurrentPlayer() ?: return
        if (currentPlayer.type == PlayerType.AI && _gamePhase.value == GamePhase.PLAYING) {
            activeAiJob = viewModelScope.launch {
                delay(_aiSpeedMs.value)
                rollDice()
            }
        }
    }

    private fun aiMakeMove(movable: List<Token>, roll: Int) {
        if (movable.isEmpty()) return

        // HEURISTIC AI CRITERIA:
        // 1. Can capture an opponent? VERY HIGH
        // 2. Can get a piece home (finished)? HIGH
        // 3. Can bring a piece from yard? HIGH (get more pieces active on board!)
        // 4. Can reach a Safe Square? MEDIUM
        // 5. Piece in danger (opponent is behind)? Move it! MEDIUM
        // 6. Otherwise: Move furthest piece forward.

        var bestToken = movable.first()
        var maxWeight = -100

        for (token in movable) {
            var weight = 0

            // 1. Reaching home directly
            val currentSteps = getStepsOfToken(token)
            if (currentSteps + roll == 56) {
                weight += 100
            }

            // 2. Unleashing piece from Yard
            if (token.isInYard && roll == 6) {
                weight += 80
            }

            // Calculate destination coordinate
            val dummyNext = simulateNextState(token, roll)
            if (dummyNext != null && dummyNext.isOnTrack) {
                val desIndex = dummyNext.trackIndex
                
                // 3. Capture Opponent Check
                val opponentOnCell = _tokens.value.filter {
                    it.color != token.color && it.positionCode == desIndex
                }
                if (opponentOnCell.isNotEmpty() && !SAFE_TRACK_INDICES.contains(desIndex)) {
                    weight += 120 // Capture represents highest priority!
                }

                // 4. Safe Zone Landing
                if (SAFE_TRACK_INDICES.contains(desIndex)) {
                    weight += 40
                }
            }

            // 5. Opponent Escape Priority
            if (token.isOnTrack) {
                // If there's an opponent in danger window behind this piece
                val dangerWindow = 1..6
                val enemyInDangerZone = _tokens.value.filter {
                    it.color != token.color && it.isOnTrack
                }.any { enemy ->
                    val dist = (token.trackIndex - enemy.trackIndex + 52) % 52
                    dist in dangerWindow
                }
                if (enemyInDangerZone) {
                    weight += 50 // Try to run away!
                }
            }

            // 6. Bias toward pieces that are already far along (home bound)
            weight += (getStepsOfToken(token) / 2) // subtle encouragement to move advanced pieces

            if (weight > maxWeight) {
                maxWeight = weight
                bestToken = token
            }
        }

        moveToken(bestToken)
    }

    private fun simulateNextState(token: Token, roll: Int): Token? {
        if (!canTokenMove(token, roll)) return null
        var temp = token
        val steps = if (token.isInYard) 1 else roll
        for (i in 1..steps) {
            temp = getNextSingleStepTokenState(temp)
        }
        return temp
    }

    fun getCurrentPlayer(): Player? {
        val idx = _currentTurnPlayerIndex.value
        return if (idx in 0..3) _players.value[idx] else null
    }

    private fun addLog(message: String, color: LudoColor?) {
        val entry = LudoLog(UUID.randomUUID().toString(), System.currentTimeMillis(), message, color)
        _logs.update { (listOf(entry) + it).take(50) } // Keep last 50 entries
    }

    private fun triggerHapticFeedback() {
        // Compose triggers haptics in View layer via LocalHapticFeedback.
        // We trigger simple vibrator patterns for fully real sensory telemetry.
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(35)
            }
        } catch (_: Exception) {}
    }

    // --- Persistence Setup ---
    private fun saveGame() {
        try {
            val state = LudoSavedState(
                players = _players.value,
                tokens = _tokens.value,
                currentTurnPlayerIndex = _currentTurnPlayerIndex.value,
                diceState = _diceState.value,
                gamePhase = _gamePhase.value,
                consecutiveSixes = _diceState.value.consecutiveSixes,
                logs = _logs.value
            )
            val json = savedStateAdapter.toJson(state)
            sharedPrefs.edit().putString("saved_ludo_state_json", json).apply()
        } catch (_: Exception) {}
    }

    private fun loadGame(): Boolean {
        try {
            val json = sharedPrefs.getString("saved_ludo_state_json", null) ?: return false
            val state = savedStateAdapter.fromJson(json) ?: return false
            _players.value = state.players
            _tokens.value = state.tokens
            _currentTurnPlayerIndex.value = state.currentTurnPlayerIndex
            _gamePhase.value = state.gamePhase
            _diceState.value = state.diceState
            _logs.value = state.logs
            
            // Re-populate standings/winners if finished
            val winners = state.players.filter { it.hasFinished }.sortedBy { it.finishOrder }
            _winnerList.value = winners

            triggerAiTurnIfNecessary()
            startTurnTimer()
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
