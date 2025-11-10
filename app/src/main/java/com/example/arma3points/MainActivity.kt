package com.example.arma3points

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arma3points.ui.theme.Arma3PointsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Arma3PointsTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Input) }
    var playerNickname by remember { mutableStateOf("") }

    when (currentScreen) {
        AppScreen.Input -> {
            NicknameInputScreen(
                nickname = playerNickname,
                onNicknameChange = { playerNickname = it },
                onContinue = {
                    if (playerNickname.isNotBlank()) {
                        currentScreen = AppScreen.Stats
                    }
                }
            )
        }
        AppScreen.Stats -> {
            PointsDisplayScreen(
                playerNickname = playerNickname,
                onBack = { currentScreen = AppScreen.Input }
            )
        }
    }
}

sealed class AppScreen {
    object Input : AppScreen()
    object Stats : AppScreen()
}

@Composable
fun NicknameInputScreen(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Add the image at the top
        Image(
            painter = painterResource(id = R.drawable.holyspirit),
            contentDescription = "Arma 3 Logo",
            modifier = Modifier
                .size(200.dp) // Adjust size as needed
                .padding(bottom = 32.dp), // Space between image and title
            contentScale = ContentScale.Fit
        )

        Text(
            text = "Arma 3 Stats Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Enter your Arma 3 nickname (with guild tag)",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("Nickname", color = Color.White.copy(alpha = 0.8f)) },
            placeholder = { Text("e.g., Corpse Decay [x]", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF121212),
                unfocusedContainerColor = Color(0xFF121212),
                focusedLabelColor = Color(0xFF4EFF05),
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                focusedIndicatorColor = Color(0xFF4EFF05),
                unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color(0xFF4EFF05)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        Button(
            onClick = onContinue,
            enabled = nickname.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4EFF05),
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF424242),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "View Stats",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Note: This works for arma.badcompanypmc.com server",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

data class PlayerStats(
    val currentSessionScore: String = "0",
    val currentMinutesPlayed: String = "0",
    val currentHoursPlayed: String = "0",
    val currentRemainingMinutes: String = "0",
    val currentScorePerMinute: String = "0",
    val allTimeScore: String = "0",
    val allTimeMinutesPlayed: String = "0",
    val allTimeHoursPlayed: String = "0",
    val allTimeRemainingMinutes: String = "0",
    val allTimeScorePerMinute: String = "0",
    val rank: String = "N/A"
)

@Composable
fun PointsDisplayScreen(
    playerNickname: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playerStats by remember { mutableStateOf(PlayerStats()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Auto-refresh every 60 seconds
    LaunchedEffect(playerNickname) {
        fixedRateTimer("refresh", daemon = true, initialDelay = 0, period = 60000) {
            coroutineScope.launch {
                fetchPlayerStats(playerNickname) { stats, error, timestamp ->
                    playerStats = stats
                    errorMessage = error
                    lastUpdate = timestamp
                    isLoading = false
                }
            }
        }
    }

    // Initial load
    LaunchedEffect(playerNickname) {
        isLoading = true
        fetchPlayerStats(playerNickname) { stats, error, timestamp ->
            playerStats = stats
            errorMessage = error
            lastUpdate = timestamp
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button and header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = "←",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }

            Text(
                text = playerNickname,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // Empty space for balance
            Spacer(modifier = Modifier.size(40.dp))
        }

        Text(
            text = "Current Session Score",
            fontSize = 20.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White
                )
                Text(
                    text = "Loading...",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFE57373),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Current Session Score
            Text(
                text = playerStats.currentSessionScore,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4EFF05),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Combined Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF121212)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Current Session Section
                    Text(
                        text = "CURRENT SESSION",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4EFF05),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CompactStatRow("Time:", "${playerStats.currentHoursPlayed}h ${playerStats.currentRemainingMinutes}m")
                    CompactStatRow("SPM:", playerStats.currentScorePerMinute)

                    // Divider
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // All Time Section
                    Text(
                        text = "ALL TIME",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4EFF05),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CompactStatRow("Score:", playerStats.allTimeScore)
                    CompactStatRow("Time:", "${playerStats.allTimeHoursPlayed}h ${playerStats.allTimeRemainingMinutes}m")
                    CompactStatRow("SPM:", playerStats.allTimeScorePerMinute)
                    CompactStatRow("Rank:", playerStats.rank)
                }
            }

            // Refresh Button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        fetchPlayerStats(playerNickname) { stats, error, timestamp ->
                            playerStats = stats
                            errorMessage = error
                            lastUpdate = timestamp
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF242424),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF424242),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Refresh",
                    fontSize = 14.sp
                )
            }

            // Update info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (lastUpdate.isNotEmpty()) {
                    Text(
                        text = "Updated: $lastUpdate",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "Auto-refresh: 60s",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun CompactStatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 20.sp
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

private suspend fun fetchPlayerStats(playerNickname: String, onResult: (PlayerStats, String?, String) -> Unit) {
    try {
        val (stats, timestamp) = withContext(Dispatchers.IO) {
            scrapeAllPlayerStats(playerNickname)
        }
        onResult(stats, null, timestamp)
    } catch (e: Exception) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val errorMessage = "Failed to load stats for '$playerNickname'. Make sure:\n" +
                "1. You're using the exact in-game nickname and clan tag\n" +
                "2. The player exists on gametracker.com\n" +
                "3. Check your internet connection\n\n" +
                "Technical details: ${e.message}"
        onResult(PlayerStats(), errorMessage, timestamp)
    }
}

private fun scrapeAllPlayerStats(playerNickname: String): Pair<PlayerStats, String> {
    // Properly encode the nickname for URL - handle spaces and special characters
    val encodedNickname = java.net.URLEncoder.encode(playerNickname, "UTF-8")
        .replace("+", "%20") // Use %20 for spaces instead of +

    val gameTrackerUrl = "https://cache.gametracker.com/components/html0/?host=arma.badcompanypmc.com:2312&bgColor=121212&fontColor=CCCCCC&titleBgColor=242424&titleColor=4EFF05&borderColor=242424&linkColor=4EFF05&borderLinkColor=9C9C9C&showMap=0&currentPlayersHeight=100&showCurrPlayers=1&topPlayersHeight=100&showTopPlayers=0&showBlogs=0&width=270"
    val playerStatsUrl = "https://www.gametracker.com/player/$encodedNickname/arma.badcompanypmc.com:2312/"

    println("DEBUG: Player Stats URL: $playerStatsUrl") // For debugging

    try {
        // Fetch current session score from online players list
        val currentSessionScore = scrapeCurrentSessionScore(gameTrackerUrl, playerNickname)

        // Fetch both current and all-time stats from player page
        val playerStats = scrapePlayerStats(playerStatsUrl)

        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

        return Pair(
            PlayerStats(
                currentSessionScore = currentSessionScore,
                currentMinutesPlayed = playerStats.currentMinutesPlayed,
                currentHoursPlayed = playerStats.currentHoursPlayed,
                currentRemainingMinutes = playerStats.currentRemainingMinutes,
                currentScorePerMinute = playerStats.currentScorePerMinute,
                allTimeScore = playerStats.allTimeScore,
                allTimeMinutesPlayed = playerStats.allTimeMinutesPlayed,
                allTimeHoursPlayed = playerStats.allTimeHoursPlayed,
                allTimeRemainingMinutes = playerStats.allTimeRemainingMinutes,
                allTimeScorePerMinute = playerStats.allTimeScorePerMinute,
                rank = playerStats.rank
            ),
            timestamp
        )

    } catch (e: Exception) {
        throw Exception("Failed to fetch player data: ${e.message}")
    }
}

private fun scrapeCurrentSessionScore(url: String, playerNickname: String): String {
    val doc = Jsoup.connect(url)
        .timeout(15000)
        .get()

    val playerDivs = doc.select("div.scrollable_on_c01, div.scrollable_on_c02, div.scrollable_on_c03")

    var currentPlayerName = ""
    var currentPlayerScore = ""
    var foundScore = "0"

    println("DEBUG: Searching for player: $playerNickname") // For debugging

    for (div in playerDivs) {
        when (div.className()) {
            "scrollable_on_c01" -> {
                // This is rank - reset when starting new player row
                if (currentPlayerName.isNotEmpty()) {
                    // We have a complete player row, check if it matches
                    if (isPlayerMatch(currentPlayerName, playerNickname)) {
                        foundScore = currentPlayerScore
                        println("DEBUG: Found player! Score: $foundScore") // For debugging
                        break
                    }
                    // Reset for next player
                    currentPlayerName = ""
                    currentPlayerScore = ""
                }
            }
            "scrollable_on_c02" -> {
                // This is player name
                val nameLink = div.select("a").first()
                currentPlayerName = nameLink?.text()?.trim() ?: ""
                println("DEBUG: Found player name: '$currentPlayerName'") // For debugging
            }
            "scrollable_on_c03" -> {
                // This is player score
                currentPlayerScore = div.text().trim()
                println("DEBUG: Found player score: '$currentPlayerScore' for '$currentPlayerName'") // For debugging

                // Check if current player matches
                if (currentPlayerName.isNotEmpty() && isPlayerMatch(currentPlayerName, playerNickname)) {
                    foundScore = currentPlayerScore
                    println("DEBUG: Found player! Score: $foundScore") // For debugging
                    break
                }

                // Reset for next player
                currentPlayerName = ""
                currentPlayerScore = ""
            }
        }
    }

    // Final check in case player was the last one
    if (currentPlayerName.isNotEmpty() && isPlayerMatch(currentPlayerName, playerNickname)) {
        foundScore = currentPlayerScore
        println("DEBUG: Found player at end! Score: $foundScore") // For debugging
    }

    if (foundScore == "0") {
        println("DEBUG: Player not found in online list") // For debugging
    }

    return foundScore
}

// Improved player matching function
private fun isPlayerMatch(foundName: String, searchName: String): Boolean {
    // Try different matching strategies
    return foundName.equals(searchName, ignoreCase = true) ||
            foundName.replace("\\s+".toRegex(), " ").trim().equals(searchName.replace("\\s+".toRegex(), " ").trim(), ignoreCase = true) ||
            normalizeName(foundName).equals(normalizeName(searchName), ignoreCase = true)
}

// Helper function to normalize names for better matching
private fun normalizeName(name: String): String {
    return name.replace("\\s+".toRegex(), " ")
        .replace("[\\[\\]]".toRegex(), "")
        .trim()
        .lowercase()
}

private data class FullPlayerStats(
    val currentMinutesPlayed: String = "0",
    val currentHoursPlayed: String = "0",
    val currentRemainingMinutes: String = "0",
    val currentScorePerMinute: String = "0",
    val allTimeScore: String = "0",
    val allTimeMinutesPlayed: String = "0",
    val allTimeHoursPlayed: String = "0",
    val allTimeRemainingMinutes: String = "0",
    val allTimeScorePerMinute: String = "0",
    val rank: String = "N/A"
)

private fun scrapePlayerStats(url: String): FullPlayerStats {
    val doc = Jsoup.connect(url)
        .timeout(15000)
        .get()

    // Initialize with default values
    var currentMinutes = "0"
    var currentSpm = "0"
    var allTimeScore = "0"
    var allTimeMinutes = "0"
    var allTimeSpm = "0"
    var rank = "N/A"

    try {
        // Find both CURRENT STATS and ALL TIME STATS sections
        val statsContainers = doc.select("div.item_float_left")

        for (container in statsContainers) {
            val sectionTitle = container.select("div.section_title").text().trim()

            when {
                sectionTitle.contains("CURRENT STATS") -> {
                    // Parse current session stats
                    val currentStatsText = container.text()

                    // Extract current minutes played
                    val currentMinutesRegex = """Minutes Played:\s*(\d+)""".toRegex()
                    val currentMinutesMatch = currentMinutesRegex.find(currentStatsText)
                    currentMinutes = currentMinutesMatch?.groupValues?.get(1) ?: "0"

                    // Extract current score per minute
                    val currentSpmRegex = """Score per Minute:\s*([\d.]+)""".toRegex()
                    val currentSpmMatch = currentSpmRegex.find(currentStatsText)
                    currentSpm = currentSpmMatch?.groupValues?.get(1) ?: "0"
                }

                sectionTitle.contains("ALL TIME STATS") -> {
                    // Parse all-time stats
                    val allTimeStatsText = container.text()

                    // Extract all-time score
                    val allTimeScoreRegex = """Score:\s*(\d+)""".toRegex()
                    val allTimeScoreMatch = allTimeScoreRegex.find(allTimeStatsText)
                    allTimeScore = allTimeScoreMatch?.groupValues?.get(1) ?: "0"

                    // Extract all-time minutes played
                    val allTimeMinutesRegex = """Minutes Played:\s*(\d+)""".toRegex()
                    val allTimeMinutesMatch = allTimeMinutesRegex.find(allTimeStatsText)
                    allTimeMinutes = allTimeMinutesMatch?.groupValues?.get(1) ?: "0"

                    // Extract all-time score per minute
                    val allTimeSpmRegex = """Score per Minute:\s*([\d.]+)""".toRegex()
                    val allTimeSpmMatch = allTimeSpmRegex.find(allTimeStatsText)
                    allTimeSpm = allTimeSpmMatch?.groupValues?.get(1) ?: "0"

                    // Extract rank
                    val rankRegex = """#(\d+ out of \d+)""".toRegex()
                    val rankMatch = rankRegex.find(allTimeStatsText)
                    rank = rankMatch?.groupValues?.get(1) ?: "N/A"
                }
            }
        }

        // Convert minutes to hours and minutes for both current and all-time
        val currentTotalMinutes = currentMinutes.toIntOrNull() ?: 0
        val currentHours = currentTotalMinutes / 60
        val currentRemainingMinutes = currentTotalMinutes % 60

        val allTimeTotalMinutes = allTimeMinutes.toIntOrNull() ?: 0
        val allTimeHours = allTimeTotalMinutes / 60
        val allTimeRemainingMinutes = allTimeTotalMinutes % 60

        return FullPlayerStats(
            currentMinutesPlayed = currentMinutes,
            currentHoursPlayed = currentHours.toString(),
            currentRemainingMinutes = currentRemainingMinutes.toString(),
            currentScorePerMinute = currentSpm,
            allTimeScore = allTimeScore,
            allTimeMinutesPlayed = allTimeMinutes,
            allTimeHoursPlayed = allTimeHours.toString(),
            allTimeRemainingMinutes = allTimeRemainingMinutes.toString(),
            allTimeScorePerMinute = allTimeSpm,
            rank = rank
        )

    } catch (e: Exception) {
        println("DEBUG - Error parsing player stats: ${e.message}")
        // Return default values if parsing fails
        return FullPlayerStats()
    }
}

@Preview(showBackground = true)
@Composable
fun PointsDisplayPreview() {
    Arma3PointsTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            PointsDisplayScreen(
                playerNickname = "Test Player",
                onBack = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NicknameInputPreview() {
    Arma3PointsTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            NicknameInputScreen(
                nickname = "",
                onNicknameChange = {},
                onContinue = {}
            )
        }
    }
}