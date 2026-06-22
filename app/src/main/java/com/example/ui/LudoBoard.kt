package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import com.example.model.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LudoBoard(
    tokens: List<Token>,
    movableTokens: List<Token>,
    onTokenSelected: (Token) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation for movable tokens
    val infiniteTransition = rememberInfiniteTransition(label = "TokenGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
        shadowElevation = 8.dp,
        modifier = modifier
            .aspectRatio(1.0f) // perfect square layout for standard flat view
            .fillMaxWidth()
            .testTag("ludo_game_board")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tokens, movableTokens) {
                    detectTapGestures { offset ->
                        val widthPx = size.width
                        val heightPx = size.height
                        val minPx = minOf(widthPx, heightPx)
                        
                        // Translate tap to 15x15 board cell coordinates
                        val cellPx = minPx / 15f
                        val col = (offset.x / cellPx).toInt()
                        val row = (offset.y / cellPx).toInt()
                        
                        if (col in 0..14 && row in 0..14) {
                            val clickedMovable = findMovableTokenAtCell(col, row, tokens, movableTokens)
                            if (clickedMovable != null) {
                                onTokenSelected(clickedMovable)
                            }
                        }
                    }
                }
        ) {
            val cellDim = size.width / 15f

            // 1. Draw solid background
            drawRect(color = Color(0xFFF1F5F9), size = size)

            // 2. Draw Yards (base camps)
            drawYard(rowStart = 0, colStart = 0, LudoColor.GREEN, cellDim)
            drawYard(rowStart = 0, colStart = 9, LudoColor.YELLOW, cellDim)
            drawYard(rowStart = 9, colStart = 0, LudoColor.RED, cellDim)
            drawYard(rowStart = 9, colStart = 9, LudoColor.BLUE, cellDim)

            // 3. Draw Track cells
            drawTrackGrid(cellDim)

            // 4. Draw Center triangles (HOME area)
            drawCenterArea(cellDim)

            // 5. Draw Tokens (Flat 2D polished look)
            drawAllTokens(tokens, movableTokens, glowScale, cellDim)
        }
    }
}

// Draw a single 6x6 player yard
private fun DrawScope.drawYard(rowStart: Int, colStart: Int, color: LudoColor, cellDim: Float) {
    val x = colStart * cellDim
    val y = rowStart * cellDim
    val baseWidth = 6 * cellDim

    // Colored solid base card
    drawRect(
        color = color.composeColor,
        topLeft = Offset(x, y),
        size = Size(baseWidth, baseWidth)
    )

    // Inner White card with shadow/inset
    val pad = cellDim * 0.75f
    val innerX = x + pad
    val innerY = y + pad
    val innerDim = baseWidth - (2 * pad)
    drawRect(
        color = Color.White,
        topLeft = Offset(innerX, innerY),
        size = Size(innerDim, innerDim)
    )

    // Draw 4 circular target frames for tokens inside yard
    val rowOffset = if (rowStart == 0) 2 else 11
    val colOffset = if (colStart == 0) 2 else 11

    val slots = listOf(
        Pair(rowOffset, colOffset),
        Pair(rowOffset, colOffset + 1),
        Pair(rowOffset + 1, colOffset),
        Pair(rowOffset + 1, colOffset + 1)
    )

    slots.forEach { slot ->
        val cx = slot.second * cellDim + cellDim / 2f
        val cy = slot.first * cellDim + cellDim / 2f
        
        // Draw slot outer circle
        drawCircle(
            color = color.composeColor.copy(alpha = 0.3f),
            radius = cellDim * 0.38f,
            center = Offset(cx, cy)
        )
        // Draw slot inner white circle
        drawCircle(
            color = Color.White,
            radius = cellDim * 0.28f,
            center = Offset(cx, cy)
        )
        // Accent dot matches user color
        drawCircle(
            color = color.composeColor,
            radius = cellDim * 0.10f,
            center = Offset(cx, cy)
        )
    }
}

// Draw the track arms, color paths, starting boxes, and safe stars
private fun DrawScope.drawTrackGrid(cellDim: Float) {
    // Traverse rows/columns and draw standard cell outlines
    for (row in 0..14) {
        for (col in 0..14) {
            // Only draw cells that are NOT inside the corner bases (0..5, 0..5), etc.
            val isYard = (row < 6 && col < 6) ||
                         (row < 6 && col > 8) ||
                         (row > 8 && col < 6) ||
                         (row > 8 && col > 8)
            val isCenter = row in 6..8 && col in 6..8

            if (!isYard && !isCenter) {
                val cx = col * cellDim
                val cy = row * cellDim

                // Compute standard background color
                var bg = Color.White
                
                // Color home run rows
                if (row == 7 && col in 1..5) bg = LudoColor.GREEN.composeColor
                if (col == 7 && row in 1..5) bg = LudoColor.YELLOW.composeColor
                if (row == 7 && col in 9..13) bg = LudoColor.BLUE.composeColor
                if (col == 7 && row in 9..13) bg = LudoColor.RED.composeColor

                // Color starting boxes
                if (row == 6 && col == 0) bg = LudoColor.GREEN.composeColor
                if (row == 0 && col == 8) bg = LudoColor.YELLOW.composeColor
                if (row == 8 && col == 14) bg = LudoColor.BLUE.composeColor
                if (row == 14 && col == 6) bg = LudoColor.RED.composeColor

                // Fill cell background
                drawRect(
                    color = bg,
                    topLeft = Offset(cx, cy),
                    size = Size(cellDim, cellDim)
                )

                // Grid cell borders
                drawRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(cx, cy),
                    size = Size(cellDim, cellDim),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw safe indications (Stars)
                if (isSafeCoordinate(row, col)) {
                    drawStar(
                        centerX = cx + cellDim / 2f,
                        centerY = cy + cellDim / 2f,
                        radius = cellDim * 0.3f,
                        color = if (bg == Color.White) Color(0xFF94A3B8) else Color.White
                    )
                }

                // Colored arrow paths marking entrance direction
                if (row == 6 && col == 0) drawBoardArrow(cx, cy, cellDim, Direction.RIGHT, Color.White)
                if (row == 0 && col == 8) drawBoardArrow(cx, cy, cellDim, Direction.DOWN, Color.White)
                if (row == 8 && col == 14) drawBoardArrow(cx, cy, cellDim, Direction.LEFT, Color.White)
                if (row == 14 && col == 6) drawBoardArrow(cx, cy, cellDim, Direction.UP, Color.White)
            }
        }
    }
}

// Draw the iconic center hub divided into 4 triangles
private fun DrawScope.drawCenterArea(cellDim: Float) {
    val startX = 6 * cellDim
    val startY = 6 * cellDim
    val midCol = 7.5f * cellDim
    val midRow = 7.5f * cellDim
    val endX = 9 * cellDim
    val endY = 9 * cellDim

    // Triangle paths meeting at middle
    val leftPath = Path().apply {
        moveTo(startX, startY)
        lineTo(midCol, midRow)
        lineTo(startX, endY)
        close()
    }
    drawPath(path = leftPath, color = LudoColor.GREEN.composeColor)

    val topPath = Path().apply {
        moveTo(startX, startY)
        lineTo(midCol, midRow)
        lineTo(endX, startY)
        close()
    }
    drawPath(path = topPath, color = LudoColor.YELLOW.composeColor)

    val rightPath = Path().apply {
        moveTo(endX, startY)
        lineTo(midCol, midRow)
        lineTo(endX, endY)
        close()
    }
    drawPath(path = rightPath, color = LudoColor.BLUE.composeColor)

    val bottomPath = Path().apply {
        moveTo(startX, endY)
        lineTo(midCol, midRow)
        lineTo(endX, endY)
        close()
    }
    drawPath(path = bottomPath, color = LudoColor.RED.composeColor)

    // Center borders
    val borderPath = Path().apply {
        moveTo(startX, startY)
        lineTo(endX, startY)
        lineTo(endX, endY)
        lineTo(startX, endY)
        close()
        
        moveTo(startX, startY)
        lineTo(endX, endY)
        moveTo(endX, startY)
        lineTo(startX, endY)
    }
    drawPath(
        path = borderPath,
        color = Color(0xFF94A3B8),
        style = Stroke(width = 1.5.dp.toPx())
    )
}

// Draw all player tokens with dynamic clustering (anti-stack overlap offsets), and glowing pulsars
private fun DrawScope.drawAllTokens(
    tokens: List<Token>,
    movableTokens: List<Token>,
    glowScale: Float,
    cellDim: Float
) {
    // Group active tokens by their computed coordinates to handle overlaps
    val cellGroup = mutableMapOf<Pair<Int, Int>, MutableList<Token>>()

    tokens.forEach { token ->
        val coord = getTokenCoordinates(token)
        val list = cellGroup.getOrPut(coord) { mutableListOf() }
        list.add(token)
    }

    // Render tokens grouped by cell to apply offsets
    cellGroup.forEach { (coord, tokenList) ->
        val (row, col) = coord
        val count = tokenList.size

        tokenList.forEachIndexed { index, token ->
            val offset = getCellOffset(index, count, cellDim)
            val cx = col * cellDim + cellDim / 2f + offset.x
            val cy = row * cellDim + cellDim / 2f + offset.y

            // Glow trigger if piece can move
            val isMovable = movableTokens.any { it.color == token.color && it.id == token.id }
            if (isMovable) {
                // Pulsing glow border
                drawCircle(
                    color = token.color.composeColor.copy(alpha = 0.45f),
                    radius = cellDim * 0.45f * glowScale,
                    center = Offset(cx, cy)
                )
                // Pulsing halo outline
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = cellDim * 0.36f * glowScale,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Outer shadow / edge highlight
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = cellDim * 0.32f,
                center = Offset(cx + 1.2.dp.toPx(), cy + 1.2.dp.toPx())
            )

            // Token Base
            drawCircle(
                color = token.color.composeColor,
                radius = cellDim * 0.30f,
                center = Offset(cx, cy)
            )

            // Token Border
            drawCircle(
                color = Color.White,
                radius = cellDim * 0.30f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )

            // Dynamic White Core Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = cellDim * 0.16f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Inner solid center knob
            drawCircle(
                color = token.color.composeColor,
                radius = cellDim * 0.08f,
                center = Offset(cx, cy)
            )
        }
    }
}

// Multi-piece coordinate clustering maths
private fun getCellOffset(index: Int, count: Int, cellDim: Float): Offset {
    if (count <= 1) return Offset(0f, 0f)
    val spread = cellDim * 0.22f
    return when (count) {
        2 -> {
            if (index == 0) Offset(-spread, 0f) else Offset(spread, 0f)
        }
        3 -> {
            when (index) {
                0 -> Offset(-spread, spread)
                1 -> Offset(spread, spread)
                else -> Offset(0f, -spread * 1.1f)
            }
        }
        else -> { // 4+ tokens
            when (index) {
                0 -> Offset(-spread, -spread)
                1 -> Offset(spread, -spread)
                2 -> Offset(-spread, spread)
                else -> Offset(spread, spread)
            }
        }
    }
}

// Compute the layout cellular (row, col) position for a given token position index
private fun getTokenCoordinates(token: Token): Pair<Int, Int> {
    return when (token.positionCode) {
        Token.POSITION_YARD -> {
            // Find base coordinates
            token.color.yardCoords[token.id]
        }
        Token.POSITION_FINISHED -> {
            token.color.homeCenterCoord
        }
        Token.POSITION_HOME_RUN -> {
            token.color.homeRunCoords[token.homeRunStep]
        }
        else -> {
            TRACK_COORDS[token.trackIndex]
        }
    }
}

private fun findMovableTokenAtCell(col: Int, row: Int, tokens: List<Token>, movable: List<Token>): Token? {
    // Filter tokens at cell that are in the movable selection group
    val match = tokens.filter { token ->
        val coord = getTokenCoordinates(token)
        coord.first == row && coord.second == col
    }
    // Return first element present in the movable list
    return match.firstOrNull { t ->
        movable.any { it.color == t.color && it.id == t.id }
    }
}

// Draw a beautiful custom star path procedurally
private fun DrawScope.drawStar(centerX: Float, centerY: Float, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.4f
    var angle = -Math.PI / 2.0

    for (i in 0 until 2 * points) {
        val r = if (i % 2 == 0) radius else innerRadius
        val x = (centerX + r * cos(angle)).toFloat()
        val y = (centerY + r * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        angle += Math.PI / points
    }
    path.close()
    drawPath(path = path, color = color, style = Fill)
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

// Draw tiny directional arrows pointing flow inside start cells
private fun DrawScope.drawBoardArrow(x: Float, y: Float, cellDim: Float, dir: Direction, color: Color) {
    val center = Offset(x + cellDim / 2f, y + cellDim / 2f)
    val width = cellDim * 0.35f
    val path = Path()
    
    when (dir) {
        Direction.RIGHT -> {
            path.moveTo(center.x - width / 2f, center.y - width / 3f)
            path.lineTo(center.x + width / 2f, center.y)
            path.lineTo(center.x - width / 2f, center.y + width / 3f)
        }
        Direction.DOWN -> {
            path.moveTo(center.x - width / 3f, center.y - width / 2f)
            path.lineTo(center.x, center.y + width / 2f)
            path.lineTo(center.x + width / 3f, center.y - width / 2f)
        }
        Direction.LEFT -> {
            path.moveTo(center.x + width / 2f, center.y - width / 3f)
            path.lineTo(center.x - width / 2f, center.y)
            path.lineTo(center.x + width / 2f, center.y + width / 3f)
        }
        Direction.UP -> {
            path.moveTo(center.x - width / 3f, center.y + width / 2f)
            path.lineTo(center.x, center.y - width / 2f)
            path.lineTo(center.x + width / 3f, center.y + width / 2f)
        }
    }
    path.close()
    drawPath(path = path, color = color, style = Fill)
}
