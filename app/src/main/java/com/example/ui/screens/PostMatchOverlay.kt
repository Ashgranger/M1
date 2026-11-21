package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.game.config.RankTier
import com.example.game.progression.PostMatchRewards
import com.example.ui.theme.BioEmerald
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlasmaMagenta
import com.example.ui.theme.SlateArmor
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

@Composable
fun PostMatchOverlay(
    rewards: PostMatchRewards,
    onPlayAgain: () -> Unit,
    onReturnLobby: () -> Unit
) {
    val r = rewards.report
    val isVictory = r.placement == 1
    val accuracy = if (r.shotsFired > 0) (r.shotsHit.toFloat() / r.shotsFired) * 100f else 0f
    val (newTier, newDiv) = RankTier.fromRp(rewards.newRp)
    val accent = if (isVictory) LegendaryAmber else PlasmaMagenta

    Box(
        modifier = Modifier.fillMaxSize().background(VoidObsidian.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SlateArmor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, accent),
            modifier = Modifier.width(560.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isVictory) "VICTORY ROYALE" else "ELIMINATED · #${r.placement}/${r.totalPlayers}",
                    style = MaterialTheme.typography.displayMedium,
                    color = accent
                )
                Text(
                    text = rewards.motivationalLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                // Match Stats Grid + Personal Best Comparison
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryStatBox("KILLS", "${r.kills}", if (rewards.isNewBestKills) "NEW BEST!" else "HS: ${r.headshots}", Modifier.weight(1f))
                    SummaryStatBox("DAMAGE", "${r.damageDealt}", if (rewards.isNewBestDamage) "NEW BEST!" else "Best: ${rewards.prevBestDamage}", Modifier.weight(1f))
                    SummaryStatBox("ACCURACY", "%.0f%%".format(accuracy), "${r.shotsHit}/${r.shotsFired}", Modifier.weight(1f))
                    SummaryStatBox("SURVIVED", "${r.survivalSeconds / 60}m ${r.survivalSeconds % 60}s", "Best #${rewards.prevBestPlacement}", Modifier.weight(1f))
                }

                // Rank RP, XP & Currency Rewards Row
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val rpSign = if (rewards.rpDelta >= 0) "+${rewards.rpDelta}" else "${rewards.rpDelta}"
                            Text(
                                "${newTier.title.uppercase()} ${RankTier.divisionRoman(newDiv)} · ${rewards.newRp} RP ($rpSign)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (rewards.rpDelta >= 0) newTier.color else CrimsonDanger
                            )
                            if (rewards.rankedUp) Text("RANK UP!", style = MaterialTheme.typography.labelSmall, color = BioEmerald)
                            if (rewards.rankedDown) Text("RANK DOWN", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                            if (rewards.leveledUp) Text("LEVEL UP -> LVL ${rewards.newLevel}!", style = MaterialTheme.typography.labelSmall, color = LegendaryAmber)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("+${rewards.xpEarned} XP", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                            Text("+${rewards.coinsEarned} COINS", style = MaterialTheme.typography.labelLarge, color = LegendaryAmber)
                            if (rewards.gemsEarned > 0) {
                                Text("+${rewards.gemsEarned} GEMS", style = MaterialTheme.typography.labelLarge, color = PlasmaMagenta)
                            }
                        }
                    }
                }

                // Primary Play Again + Lobby buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onReturnLobby,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = TextPrimary),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("post_match_lobby_button")
                    ) {
                        Text("LOBBY", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onPlayAgain,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian),
                        modifier = Modifier.weight(1.5f).height(48.dp).testTag("post_match_play_again_button")
                    ) {
                        Text("PLAY AGAIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(color = SlateElevated, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, SlateBorder), modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleLarge, color = NeonCyan)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = LegendaryAmber)
        }
    }
}

@Composable
fun PauseModal(
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onQuitLobby: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(VoidObsidian.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = SlateArmor, shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, NeonCyan), modifier = Modifier.width(320.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MATCH PAUSED", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidObsidian), modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text("RESUME", style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = TextPrimary), modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text("SETTINGS & HUD", style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = onQuitLobby, colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger.copy(alpha = 0.25f), contentColor = CrimsonDanger), border = BorderStroke(1.dp, CrimsonDanger), modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text("EXIT TO LOBBY", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
