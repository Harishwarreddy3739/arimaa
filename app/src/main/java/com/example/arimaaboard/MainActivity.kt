package com.example.arimaaboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GameScreen()
            }
        }
    }
}

val trapSquares = setOf(
    2 to 2,
    2 to 5,
    5 to 2,
    5 to 5
)

@Composable
fun GameScreen() {
    var boardState by remember { mutableStateOf(getInitialBoardState()) }
    val boardHistory = remember { mutableSetOf(serializeBoard(boardState)) }

    var currentTurnState by remember { mutableStateOf(boardState) }
    var movesMade by remember { mutableStateOf(0) }
    var currentPlayer by remember { mutableStateOf(1) }
    var selectedPiece by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val possibleMoves = remember { mutableStateListOf<Pair<Int, Int>>() }
    val possiblePushes = remember { mutableStateListOf<Triple<Int, Int, Pair<Int, Int>>>() }
    val possiblePulls = remember { mutableStateListOf<PullAction>() }
    var errorMessage by remember { mutableStateOf("") }
    var gameOver by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Int?>(null) }
    var showPullDialog by remember { mutableStateOf(false) }
    var pendingPullAction by remember { mutableStateOf<PullAction?>(null) }

    // Update immobilePieces whenever currentTurnState changes
    val immobilePieces = calculateImmobilePieces(currentTurnState)

    // Initialize the history of actions for the current turn
    val currentTurnHistory = remember { mutableStateListOf(TurnAction(copyBoard(currentTurnState), movesMade)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Current player info with indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (gameOver) {
                Text(
                    text = "Game Over! Player $winner wins!",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = "Current Player: Player $currentPlayer",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Canvas(
                modifier = Modifier.size(24.dp)
            ) {
                drawCircle(color = if (currentPlayer == 1) Color.Blue else Color.Green)
            }
        }

        // Board
        Box(modifier = Modifier.weight(1f)) {
            ArimaaBoard(
                board = currentTurnState,
                immobilePieces = immobilePieces,
                selectedPiece = selectedPiece,
                possibleMoves = possibleMoves,
                possiblePushes = possiblePushes,
                possiblePulls = possiblePulls,
                onPieceSelected = { row, col ->
                    if (!gameOver) {
                        val piece = currentTurnState[row][col]
                        if (movesMade < 4 && piece.isNotEmpty() && isPieceBelongingToPlayer(piece, currentPlayer) &&
                            !immobilePieces.contains(row to col)
                        ) {
                            selectedPiece = row to col
                            possibleMoves.clear()
                            possiblePushes.clear()
                            possiblePulls.clear()
                            possibleMoves.addAll(getValidMoves(currentTurnState, row, col, piece, currentPlayer))
                            possiblePushes.addAll(getPossiblePushes(currentTurnState, row, col, piece, currentPlayer))
                            possiblePulls.addAll(getPossiblePulls(currentTurnState, row, col, piece, currentPlayer))
                        }
                    }
                },
                onMoveMade = { targetRow, targetCol ->
                    if (!gameOver) {
                        if (movesMade < 4) {
                            selectedPiece?.let { (sourceRow, sourceCol) ->
                                val newBoard = currentTurnState.map { it.toMutableList() }
                                newBoard[targetRow][targetCol] = newBoard[sourceRow][sourceCol]
                                newBoard[sourceRow][sourceCol] = ""
                                val boardAfterMove = applyTrapRules(newBoard) // Apply trap rules
                                currentTurnState = boardAfterMove
                                movesMade++
                                selectedPiece = null
                                possibleMoves.clear()
                                possiblePushes.clear()
                                possiblePulls.clear()
                                errorMessage = ""
                                println("Player $currentPlayer made a move! Total moves: $movesMade")

                                // Add to history
                                currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))

                                // Check for victory condition
                                val victory = checkForVictory(currentTurnState)
                                if (victory != null) {
                                    gameOver = true
                                    winner = victory
                                    println("Game Over! Player $winner wins!")
                                }
                            }
                        } else {
                            errorMessage = "No remaining moves this turn."
                        }
                    }
                },
                onPushMade = { enemyRow, enemyCol, direction ->
                    if (!gameOver) {
                        if (movesMade <= 2) { // Push counts as two moves
                            selectedPiece?.let { (sourceRow, sourceCol) ->
                                val newBoard = currentTurnState.map { it.toMutableList() }
                                val (dr, dc) = direction
                                val targetRow = enemyRow + dr
                                val targetCol = enemyCol + dc

                                if (targetRow in newBoard.indices && targetCol in newBoard[targetRow].indices &&
                                    newBoard[targetRow][targetCol].isEmpty()
                                ) {
                                    // Move the pushing piece into enemy's square
                                    newBoard[enemyRow][enemyCol] = newBoard[sourceRow][sourceCol]
                                    newBoard[sourceRow][sourceCol] = ""

                                    // Move the enemy piece into the target square
                                    newBoard[targetRow][targetCol] = currentTurnState[enemyRow][enemyCol]

                                    // Apply trap rules
                                    val boardAfterMove = applyTrapRules(newBoard)
                                    currentTurnState = boardAfterMove

                                    movesMade += 2 // Push counts as two moves
                                    selectedPiece = null
                                    possibleMoves.clear()
                                    possiblePushes.clear()
                                    possiblePulls.clear()
                                    errorMessage = ""
                                    println("Player $currentPlayer performed a push! Total moves: $movesMade")

                                    // Add to history
                                    currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))

                                    // Check for victory condition
                                    val victory = checkForVictory(currentTurnState)
                                    if (victory != null) {
                                        gameOver = true
                                        winner = victory
                                        println("Game Over! Player $winner wins!")
                                    }
                                } else {
                                    errorMessage = "Cannot push into that square."
                                }
                            }
                        } else {
                            errorMessage = "Not enough moves left to perform a push."
                        }
                    }
                },
                onPullMade = { pullAction ->
                    if (!gameOver) {
                        if (movesMade <= 2) { // Pull counts as two moves
                            // Show dialog to confirm pull action
                            pendingPullAction = pullAction
                            showPullDialog = true
                        } else {
                            errorMessage = "Not enough moves left to perform a pull."
                        }
                    }
                }
            )
        }

        // Dialog for Pull Action
        if (showPullDialog) {
            AlertDialog(
                onDismissRequest = { showPullDialog = false },
                title = { Text("Confirm Action") },
                text = { Text("Do you want to perform a pull (counts as 2 moves) or a single move?") },
                confirmButton = {
                    Button(onClick = {
                        // Perform Pull
                        pendingPullAction?.let { pullAction ->
                            val (sourceRow, sourceCol) = pullAction.piecePosition
                            val (enemyRow, enemyCol) = pullAction.enemyPosition
                            val (targetRow, targetCol) = pullAction.targetPosition

                            val newBoard = currentTurnState.map { it.toMutableList() }
                            // Move the enemy piece into the square vacated by the pulling piece
                            newBoard[targetRow][targetCol] = newBoard[enemyRow][enemyCol]
                            newBoard[enemyRow][enemyCol] = ""
                            // Move the pulling piece into the desired square
                            newBoard[sourceRow][sourceCol] = ""
                            newBoard[pullAction.pieceTargetPosition.first][pullAction.pieceTargetPosition.second] =
                                currentTurnState[sourceRow][sourceCol]

                            val boardAfterMove = applyTrapRules(newBoard)
                            currentTurnState = boardAfterMove

                            movesMade += 2 // Pull counts as two moves
                            selectedPiece = null
                            possibleMoves.clear()
                            possiblePushes.clear()
                            possiblePulls.clear()
                            errorMessage = ""
                            println("Player $currentPlayer performed a pull! Total moves: $movesMade")

                            // Add to history
                            currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))

                            // Check for victory condition
                            val victory = checkForVictory(currentTurnState)
                            if (victory != null) {
                                gameOver = true
                                winner = victory
                                println("Game Over! Player $winner wins!")
                            }
                        }
                        showPullDialog = false
                    }) {
                        Text("Pull (2 moves)")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        // Perform Single Move
                        pendingPullAction?.let { pullAction ->
                            val (sourceRow, sourceCol) = pullAction.piecePosition
                            val (targetRow, targetCol) = pullAction.pieceTargetPosition

                            val newBoard = currentTurnState.map { it.toMutableList() }
                            newBoard[targetRow][targetCol] = newBoard[sourceRow][sourceCol]
                            newBoard[sourceRow][sourceCol] = ""
                            val boardAfterMove = applyTrapRules(newBoard) // Apply trap rules
                            currentTurnState = boardAfterMove
                            movesMade++
                            selectedPiece = null
                            possibleMoves.clear()
                            possiblePushes.clear()
                            possiblePulls.clear()
                            errorMessage = ""
                            println("Player $currentPlayer made a move! Total moves: $movesMade")

                            // Add to history
                            currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))

                            // Check for victory condition
                            val victory = checkForVictory(currentTurnState)
                            if (victory != null) {
                                gameOver = true
                                winner = victory
                                println("Game Over! Player $winner wins!")
                            }
                        }
                        showPullDialog = false
                    }) {
                        Text("Single Move")
                    }
                }
            )
        }

        // Buttons
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = {
                    if (!gameOver) {
                        if (movesMade < 4) {
                            println("Player $currentPlayer can make another move.")
                        } else {
                            println("Player $currentPlayer has already made 4 moves this turn.")
                        }
                    }
                }) {
                    Text("Make Move")
                }

                Button(onClick = {
                    if (!gameOver) {
                        currentTurnState = boardState
                        movesMade = 0
                        selectedPiece = null
                        possibleMoves.clear()
                        possiblePushes.clear()
                        possiblePulls.clear()
                        errorMessage = ""
                        println("Player $currentPlayer restarted the turn.")
                        // Reset the turn history
                        currentTurnHistory.clear()
                        currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))
                    }
                }) {
                    Text("Restart Turn")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!gameOver) {
                        val serializedState = serializeBoard(currentTurnState)
                        if (serializedState == serializeBoard(boardState)) {
                            errorMessage = "No changes made to the board."
                            println(errorMessage)
                        } else if (boardHistory.contains(serializedState)) {
                            errorMessage = "This board state has already been seen."
                            println(errorMessage)
                        } else {
                            currentTurnState = applyTrapRules(currentTurnState) // Apply trap rules
                            boardState = currentTurnState
                            boardHistory.add(serializeBoard(boardState))
                            currentPlayer = if (currentPlayer == 1) 2 else 1
                            movesMade = 0
                            selectedPiece = null
                            possibleMoves.clear()
                            possiblePushes.clear()
                            possiblePulls.clear()
                            errorMessage = ""
                            println("Turn committed. It's now Player $currentPlayer's turn.")

                            // Reset the turn history
                            currentTurnHistory.clear()
                            currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))

                            // Check for victory condition at the end of turn
                            val victory = checkForVictory(boardState)
                            if (victory != null) {
                                gameOver = true
                                winner = victory
                                println("Game Over! Player $winner wins!")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish Turn and Switch Player")
            }

            Button(
                onClick = {
                    boardState = getInitialBoardState()
                    currentTurnState = boardState
                    boardHistory.clear()
                    boardHistory.add(serializeBoard(boardState))
                    movesMade = 0
                    currentPlayer = 1
                    selectedPiece = null
                    possibleMoves.clear()
                    possiblePushes.clear()
                    possiblePulls.clear()
                    errorMessage = ""
                    gameOver = false
                    winner = null
                    println("Game reset to its initial state.")
                    // Reset the turn history
                    currentTurnHistory.clear()
                    currentTurnHistory.add(TurnAction(copyBoard(currentTurnState), movesMade))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Reset Game")
            }

            // Undo Move Button
            Button(
                onClick = {
                    if (currentTurnHistory.size > 1) {
                        // Remove the last action
                        currentTurnHistory.removeLast()
                        // Restore the previous state
                        val lastAction = currentTurnHistory.last()
                        currentTurnState = copyBoard(lastAction.boardState)
                        movesMade = lastAction.movesMade
                        selectedPiece = null
                        possibleMoves.clear()
                        possiblePushes.clear()
                        possiblePulls.clear()
                        errorMessage = ""
                        println("Undo performed. Moves made: $movesMade")
                    } else {
                        errorMessage = "No moves to undo."
                        println(errorMessage)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Undo Move")
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Red),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// Data class to represent a turn action
data class TurnAction(val boardState: List<List<String>>, val movesMade: Int)

data class PullAction(
    val piecePosition: Pair<Int, Int>,
    val enemyPosition: Pair<Int, Int>,
    val targetPosition: Pair<Int, Int>,
    val pieceTargetPosition: Pair<Int, Int>
)

@Composable
fun ArimaaBoard(
    board: List<List<String>>,
    immobilePieces: Set<Pair<Int, Int>>,
    selectedPiece: Pair<Int, Int>?,
    possibleMoves: List<Pair<Int, Int>>,
    possiblePushes: List<Triple<Int, Int, Pair<Int, Int>>>,
    possiblePulls: List<PullAction>,
    onPieceSelected: (Int, Int) -> Unit,
    onMoveMade: (Int, Int) -> Unit,
    onPushMade: (Int, Int, Pair<Int, Int>) -> Unit,
    onPullMade: (PullAction) -> Unit
) {
    SquareComposable(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            board.forEachIndexed { row, pieces ->
                Row(Modifier.weight(1f)) {
                    pieces.forEachIndexed { col, piece ->
                        val isSelected = selectedPiece == row to col
                        val isPossibleMove = possibleMoves.contains(row to col)
                        val possiblePushTargets = possiblePushes.map { it.first to it.second }
                        val isPossiblePush = possiblePushTargets.contains(row to col)
                        val pushDirection = possiblePushes.find { it.first == row && it.second == col }?.third
                        val possiblePullTargets = possiblePulls.map { it.enemyPosition }
                        val isPossiblePull = possiblePullTargets.contains(row to col)
                        val pullAction = possiblePulls.find { it.enemyPosition == row to col }
                        val isImmobile = immobilePieces.contains(row to col)
                        val isTrapSquare = trapSquares.contains(row to col)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(
                                    color = when {
                                        isSelected -> Color.Cyan
                                        isPossibleMove -> Color.Green
                                        isPossiblePush -> Color.Magenta
                                        isPossiblePull -> Color.Yellow
                                        isImmobile -> Color.Red
                                        isTrapSquare -> Color(0xFFD4AF37)
                                        (row + col) % 2 == 0 -> Color.LightGray
                                        else -> Color.Gray
                                    }
                                )
                                .clickable {
                                    when {
                                        isPossibleMove -> onMoveMade(row, col)
                                        isPossiblePush && pushDirection != null -> onPushMade(row, col, pushDirection)
                                        isPossiblePull && pullAction != null -> onPullMade(pullAction)
                                        else -> onPieceSelected(row, col)
                                    }
                                }
                        ) {
                            if (piece.isNotEmpty()) {
                                GamePiece(
                                    type = piece,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculateImmobilePieces(board: List<List<String>>): Set<Pair<Int, Int>> {
    val immobile = mutableSetOf<Pair<Int, Int>>()
    for (row in board.indices) {
        for (col in board[row].indices) {
            val piece = board[row][col]
            if (piece.isNotEmpty()) {
                val isFrozen = isPieceFrozen(board, row, col)
                if (isFrozen) {
                    immobile.add(row to col)
                }
            }
        }
    }
    return immobile
}

fun isPieceFrozen(board: List<List<String>>, row: Int, col: Int): Boolean {
    val piece = board[row][col]
    val currentPlayer = if (piece.all { it.isUpperCase() }) 1 else 2
    val opponentPlayer = 3 - currentPlayer
    val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    var hasAdjacentFriendly = false
    var hasAdjacentStrongerOpponent = false

    for ((dr, dc) in directions) {
        val neighborRow = row + dr
        val neighborCol = col + dc
        if (neighborRow in board.indices && neighborCol in board[neighborRow].indices) {
            val neighborPiece = board[neighborRow][neighborCol]
            if (neighborPiece.isNotEmpty()) {
                if (isPieceBelongingToPlayer(neighborPiece, currentPlayer)) {
                    hasAdjacentFriendly = true
                } else if (isPieceBelongingToPlayer(neighborPiece, opponentPlayer)) {
                    if (isLargerPiece(neighborPiece, piece)) {
                        hasAdjacentStrongerOpponent = true
                    }
                }
            }
        }
    }
    return !hasAdjacentFriendly && hasAdjacentStrongerOpponent
}

fun isLargerPiece(piece: String, otherPiece: String): Boolean {
    val hierarchy = listOf("R", "C", "D", "H", "M", "E")
    return hierarchy.indexOf(piece.uppercase()) > hierarchy.indexOf(otherPiece.uppercase())
}

fun applyTrapRules(board: List<List<String>>): List<List<String>> {
    val newBoard = board.map { it.toMutableList() }
    val piecesToRemove = mutableListOf<Pair<Int, Int>>()

    for ((row, col) in trapSquares) {
        val piece = newBoard[row][col]
        if (piece.isNotEmpty()) {
            val currentPlayer = if (piece.all { it.isUpperCase() }) 1 else 2
            val hasAdjacentFriendly = hasAdjacentFriendlyPiece(newBoard, row, col, currentPlayer)
            if (!hasAdjacentFriendly) {
                piecesToRemove.add(row to col)
            }
        }
    }

    // Remove all pieces that need to be captured
    for ((row, col) in piecesToRemove) {
        newBoard[row][col] = ""
        println("Piece at ($row, $col) captured on trap square!")
    }

    return newBoard
}

fun hasAdjacentFriendlyPiece(board: List<List<String>>, row: Int, col: Int, currentPlayer: Int): Boolean {
    val directions = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    for ((dr, dc) in directions) {
        val neighborRow = row + dr
        val neighborCol = col + dc
        if (neighborRow in board.indices && neighborCol in board[neighborRow].indices) {
            val neighborPiece = board[neighborRow][neighborCol]
            if (neighborPiece.isNotEmpty() && isPieceBelongingToPlayer(neighborPiece, currentPlayer)) {
                return true
            }
        }
    }
    return false
}

@Composable
fun SquareComposable(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val size = min(constraints.maxWidth, constraints.maxHeight)
        val squareConstraints = constraints.copy(
            minWidth = size, maxWidth = size, minHeight = size, maxHeight = size
        )
        val placeables = measurables.map { it.measure(squareConstraints) }
        layout(size, size) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
fun GamePiece(type: String, modifier: Modifier = Modifier) {
    val color = when (type.uppercase()) {
        "R" -> Color.White
        "C" -> Color.Yellow
        "D" -> Color(139, 69, 19) // SaddleBrown
        "H" -> Color.Blue
        "M" -> Color.Green
        "E" -> Color.Gray
        else -> Color.Transparent
    }
    Canvas(modifier = modifier) {
        drawCircle(color = color)
    }
}

fun getInitialBoardState(): List<List<String>> {
    val player1BackRow = List(8) { "R" }
    val player1SecondRow = listOf("C", "D", "H", "M", "E", "H", "D", "C")
    val emptyRow = List(8) { "" }
    val player2SecondRow = player1SecondRow.map { it.lowercase() }
    val player2BackRow = player1BackRow.map { it.lowercase() }

    // Corrected board setup
    return listOf(
        player2SecondRow, // Row 0
        player2BackRow    // Row 1
    ) + List(4) { emptyRow } + listOf(
        player1BackRow,   // Row 6
        player1SecondRow  // Row 7
    )
}

fun serializeBoard(board: List<List<String>>): String {
    return board.joinToString(";") { row -> row.joinToString(",") }
}

fun getValidMoves(
    board: List<List<String>>,
    row: Int,
    col: Int,
    piece: String,
    currentPlayer: Int
): List<Pair<Int, Int>> {
    val directions = listOf(
        -1 to 0, // Up
        1 to 0,  // Down
        0 to -1, // Left
        0 to 1   // Right
    )
    return directions.mapNotNull { (dr, dc) ->
        val newRow = row + dr
        val newCol = col + dc
        if (newRow in board.indices && newCol in board[newRow].indices) {
            if (board[newRow][newCol].isEmpty()) {
                if (piece.uppercase() != "R") {
                    // Non-rabbit pieces can move in any direction
                    newRow to newCol
                } else {
                    // Rabbits have movement restrictions
                    if (currentPlayer == 1 && dr <= 0) {
                        // Player 1's rabbits cannot move backward (dr > 0)
                        newRow to newCol
                    } else if (currentPlayer == 2 && dr >= 0) {
                        // Player 2's rabbits cannot move backward (dr < 0)
                        newRow to newCol
                    } else null
                }
            } else null
        } else null
    }
}

fun getPossiblePushes(
    board: List<List<String>>,
    row: Int,
    col: Int,
    piece: String,
    currentPlayer: Int
): List<Triple<Int, Int, Pair<Int, Int>>> {
    val pushes = mutableListOf<Triple<Int, Int, Pair<Int, Int>>>()
    val directions = listOf(
        -1 to 0, // Up
        1 to 0,  // Down
        0 to -1, // Left
        0 to 1   // Right
    )
    val opponentPlayer = 3 - currentPlayer
    for ((dr, dc) in directions) {
        val enemyRow = row + dr
        val enemyCol = col + dc
        if (enemyRow in board.indices && enemyCol in board[enemyRow].indices) {
            val enemyPiece = board[enemyRow][enemyCol]
            if (enemyPiece.isNotEmpty() && isPieceBelongingToPlayer(enemyPiece, opponentPlayer)) {
                if (isLargerPiece(piece, enemyPiece)) {
                    // Check possible directions to push
                    for ((pr, pc) in directions) {
                        val pushRow = enemyRow + pr
                        val pushCol = enemyCol + pc
                        if (pushRow == row && pushCol == col) continue // Cannot push back into pushing piece's square
                        if (pushRow in board.indices && pushCol in board[pushRow].indices) {
                            if (board[pushRow][pushCol].isEmpty()) {
                                pushes.add(Triple(enemyRow, enemyCol, pr to pc))
                            }
                        }
                    }
                }
            }
        }
    }
    return pushes
}

fun getPossiblePulls(
    board: List<List<String>>,
    row: Int,
    col: Int,
    piece: String,
    currentPlayer: Int
): List<PullAction> {
    val pulls = mutableListOf<PullAction>()
    val directions = listOf(
        -1 to 0, // Up
        1 to 0,  // Down
        0 to -1, // Left
        0 to 1   // Right
    )
    val opponentPlayer = 3 - currentPlayer
    for ((dr, dc) in directions) {
        val enemyRow = row + dr
        val enemyCol = col + dc
        if (enemyRow in board.indices && enemyCol in board[enemyRow].indices) {
            val enemyPiece = board[enemyRow][enemyCol]
            if (enemyPiece.isNotEmpty() && isPieceBelongingToPlayer(enemyPiece, opponentPlayer)) {
                if (isLargerPiece(piece, enemyPiece)) {
                    // Check possible squares to move the pulling piece
                    for ((mr, mc) in directions) {
                        val moveRow = row + mr
                        val moveCol = col + mc
                        if (moveRow == enemyRow && moveCol == enemyCol) continue // Cannot move into enemy's square
                        if (moveRow in board.indices && moveCol in board[moveRow].indices) {
                            if (board[moveRow][moveCol].isEmpty()) {
                                // Move the enemy piece into the square vacated by the pulling piece
                                pulls.add(
                                    PullAction(
                                        piecePosition = row to col,
                                        enemyPosition = enemyRow to enemyCol,
                                        targetPosition = row to col, // Enemy piece moves into pulling piece's original square
                                        pieceTargetPosition = moveRow to moveCol
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    return pulls
}

fun isPieceBelongingToPlayer(piece: String, currentPlayer: Int): Boolean {
    return when (currentPlayer) {
        1 -> piece.isNotEmpty() && piece.all { it.isUpperCase() }
        2 -> piece.isNotEmpty() && piece.all { it.isLowerCase() }
        else -> false
    }
}

fun checkForVictory(board: List<List<String>>): Int? {
    // Check if Player 1's rabbit ('R') reached row 0 (top row)
    for (col in board[0].indices) {
        if (board[0][col] == "R") {
            return 1 // Player 1 wins
        }
    }
    // Check if Player 2's rabbit ('r') reached row 7 (bottom row)
    for (col in board[7].indices) {
        if (board[7][col] == "r") {
            return 2 // Player 2 wins
        }
    }
    return null // No victory yet
}

// Copy function to create a deep copy of the board
fun copyBoard(board: List<List<String>>): List<List<String>> {
    return board.map { it.toList() }
}
