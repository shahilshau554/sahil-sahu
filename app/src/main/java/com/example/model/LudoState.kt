package com.example.model

import androidx.compose.ui.graphics.Color

enum class LudoColor(
    val displayName: String,
    val colorHex: Long,
    val startTrackIndex: Int,
    val homeEntryTrackIndex: Int,
    val homeRunCoords: List<Pair<Int, Int>>,
    val homeCenterCoord: Pair<Int, Int>,
    val yardCoords: List<Pair<Int, Int>>
) {
    GREEN(
        displayName = "Green",
        colorHex = 0xFF2E7D32, // emerald forest green
        startTrackIndex = 0,
        homeEntryTrackIndex = 50,
        homeRunCoords = listOf(
            Pair(7, 1),
            Pair(7, 2),
            Pair(7, 3),
            Pair(7, 4),
            Pair(7, 5)
        ),
        homeCenterCoord = Pair(7, 6),
        yardCoords = listOf(
            Pair(2, 2),
            Pair(2, 3),
            Pair(3, 2),
            Pair(3, 3)
        )
    ),
    YELLOW(
        displayName = "Yellow",
        colorHex = 0xFFFBC02D, // golden amber yellow
        startTrackIndex = 13,
        homeEntryTrackIndex = 11,
        homeRunCoords = listOf(
            Pair(1, 7),
            Pair(2, 7),
            Pair(3, 7),
            Pair(4, 7),
            Pair(5, 7)
        ),
        homeCenterCoord = Pair(6, 7),
        yardCoords = listOf(
            Pair(2, 11),
            Pair(2, 12),
            Pair(3, 11),
            Pair(3, 12)
        )
    ),
    BLUE(
        displayName = "Blue",
        colorHex = 0xFF1565C0, // cobalt blue
        startTrackIndex = 26,
        homeEntryTrackIndex = 24,
        homeRunCoords = listOf(
            Pair(7, 13),
            Pair(7, 12),
            Pair(7, 11),
            Pair(7, 10),
            Pair(7, 9)
        ),
        homeCenterCoord = Pair(7, 8),
        yardCoords = listOf(
            Pair(11, 11),
            Pair(11, 12),
            Pair(12, 11),
            Pair(12, 12)
        )
    ),
    RED(
        displayName = "Red",
        colorHex = 0xFFC62828, // rich crimson red
        startTrackIndex = 39,
        homeEntryTrackIndex = 37,
        homeRunCoords = listOf(
            Pair(13, 7),
            Pair(12, 7),
            Pair(11, 7),
            Pair(10, 7),
            Pair(9, 7)
        ),
        homeCenterCoord = Pair(8, 7),
        yardCoords = listOf(
            Pair(11, 2),
            Pair(11, 3),
            Pair(12, 2),
            Pair(12, 3)
        )
    );

    val composeColor: Color get() = Color(colorHex)
}

enum class PlayerType {
    HUMAN,
    AI
}

data class Player(
    val id: Int,
    val name: String,
    val color: LudoColor,
    val type: PlayerType,
    val isActive: Boolean = true,
    val hasFinished: Boolean = false,
    val finishOrder: Int = 0
)

data class Token(
    val id: Int,       // 0..3
    val color: LudoColor,
    // positionType: YARD (-1), TRACK (value 0..51), HOME_RUN (value 0..4), FINISHED (5)
    val positionCode: Int = POSITION_YARD,
    val trackIndex: Int = -1,    // 0..51 if on common track
    val homeRunStep: Int = -1,   // 0..4 if on home run path
) {
    companion object {
        const val POSITION_YARD = -1
        const val POSITION_HOME_RUN = -2
        const val POSITION_FINISHED = -3
    }

    val isFinished: Boolean get() = positionCode == POSITION_FINISHED
    val isInYard: Boolean get() = positionCode == POSITION_YARD
    val isOnTrack: Boolean get() = positionCode >= 0
    val isInHomeRun: Boolean get() = positionCode == POSITION_HOME_RUN
}

enum class GamePhase {
    SETUP,
    PLAYING,
    FINISHED
}

data class DiceState(
    val value: Int = 1,
    val isRolling: Boolean = false,
    val rollCompleted: Boolean = false,
    val consecutiveSixes: Int = 0
)

data class LudoLog(
    val id: String,
    val timestamp: Long,
    val message: String,
    val color: LudoColor? = null
)

data class LudoMoveRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val playerName: String,
    val playerColor: LudoColor,
    val diceValue: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

// List of all 52 coordinates mapping the circular path of the board
val TRACK_COORDS = listOf(
    Pair(6, 0), Pair(6, 1), Pair(6, 2), Pair(6, 3), Pair(6, 4), Pair(6, 5), // Left arm top row
    Pair(5, 6), Pair(4, 6), Pair(3, 6), Pair(2, 6), Pair(1, 6), Pair(0, 6), // Top arm left col
    Pair(0, 7),                                                             // Top arm end
    Pair(0, 8), Pair(1, 8), Pair(2, 8), Pair(3, 8), Pair(4, 8), Pair(5, 8), // Top arm right col
    Pair(6, 9), Pair(6, 10), Pair(6, 11), Pair(6, 12), Pair(6, 13), Pair(6, 14), // Right arm top row
    Pair(7, 14),                                                            // Right arm end
    Pair(8, 14), Pair(8, 13), Pair(8, 12), Pair(8, 11), Pair(8, 10), Pair(8, 9), // Right arm bottom row
    Pair(9, 8), Pair(10, 8), Pair(11, 8), Pair(12, 8), Pair(13, 8), Pair(14, 8), // Bottom arm right col
    Pair(14, 7),                                                            // Bottom arm end
    Pair(14, 6), Pair(13, 6), Pair(12, 6), Pair(11, 6), Pair(10, 6), Pair(9, 6), // Bottom arm left col
    Pair(8, 5), Pair(8, 4), Pair(8, 3), Pair(8, 2), Pair(8, 1), Pair(8, 0), // Left arm bottom row
    Pair(7, 0)                                                              // Left arm end
)

// Safe positions on the 52 outer track path
val SAFE_TRACK_INDICES = setOf(
    0,   // Green start (6, 0)
    8,   // Star (3, 6)
    13,  // Yellow start (0, 8)
    21,  // Star (6, 11)
    26,  // Blue start (8, 14)
    34,  // Star (11, 8)
    39,  // Red start (14, 6)
    47   // Star (8, 3)
)

// Helper to check if a specific cell coordinate is a safe cell
fun isSafeCoordinate(row: Int, col: Int): Boolean {
    val trackIdx = TRACK_COORDS.indexOf(Pair(row, col))
    return if (trackIdx in SAFE_TRACK_INDICES) true else false
}
