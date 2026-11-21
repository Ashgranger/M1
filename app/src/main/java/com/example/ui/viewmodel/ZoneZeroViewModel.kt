package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.ProceduralAudioEngine
import com.example.game.config.WeaponClass
import com.example.game.engine.MatchEngine
import com.example.game.progression.AchievementDef
import com.example.game.progression.CosmeticCategory
import com.example.game.progression.CosmeticItem
import com.example.game.progression.LuckySpinSlot
import com.example.game.progression.MatchResultReport
import com.example.game.progression.PostMatchRewards
import com.example.game.progression.ProgressionCatalog
import com.example.game.progression.ProgressionManager
import com.example.game.save.GameSettings
import com.example.game.save.HudButtonLayout
import com.example.game.save.PlayerSaveData
import com.example.game.save.SaveRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    LOBBY,
    SHOP,
    BATTLE_PASS,
    CHALLENGES,
    STATS,
    SETTINGS,
    HUD_EDITOR,
    IN_MATCH
}

data class AchievementToast(
    val title: String,
    val subtitle: String
)

class ZoneZeroViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SaveRepository(app.applicationContext)
    val audio = ProceduralAudioEngine()

    private val _saveData = MutableStateFlow(PlayerSaveData())
    val saveData: StateFlow<PlayerSaveData> = _saveData.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.LOBBY)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _activeEngine = MutableStateFlow<MatchEngine?>(null)
    val activeEngine: StateFlow<MatchEngine?> = _activeEngine.asStateFlow()

    private val _postMatchRewards = MutableStateFlow<PostMatchRewards?>(null)
    val postMatchRewards: StateFlow<PostMatchRewards?> = _postMatchRewards.asStateFlow()

    private val _toast = MutableStateFlow<AchievementToast?>(null)
    val toast: StateFlow<AchievementToast?> = _toast.asStateFlow()

    private val _showLuckySpin = MutableStateFlow(false)
    val showLuckySpin: StateFlow<Boolean> = _showLuckySpin.asStateFlow()

    private val _lastSpinResult = MutableStateFlow<LuckySpinSlot?>(null)
    val lastSpinResult: StateFlow<LuckySpinSlot?> = _lastSpinResult.asStateFlow()

    private val _renderError = MutableStateFlow<String?>(null)
    val renderError: StateFlow<String?> = _renderError.asStateFlow()

    private val _matchInstanceKey = MutableStateFlow(0)
    val matchInstanceKey: StateFlow<Int> = _matchInstanceKey.asStateFlow()

    init {
        audio.start()
        viewModelScope.launch {
            val loaded = ProgressionManager.refreshTimeBasedState(repo.load())
            _saveData.value = loaded
            syncAudioSettings(loaded.settings)
            repo.save(loaded)
        }
    }

    private fun syncAudioSettings(s: GameSettings) {
        audio.sfxVolume = s.soundVolume
        audio.musicVolume = s.musicVolume
    }

    private fun updateSave(transform: (PlayerSaveData) -> PlayerSaveData) {
        val next = transform(_saveData.value)
        _saveData.value = next
        syncAudioSettings(next.settings)
        _activeEngine.value?.settings = next.settings
        viewModelScope.launch { repo.save(next) }
    }

    fun showToast(title: String, subtitle: String) {
        viewModelScope.launch {
            _toast.value = AchievementToast(title, subtitle)
            delay(3200)
            if (_toast.value?.title == title) _toast.value = null
        }
    }

    fun navigate(target: AppScreen) {
        audio.playUiClick()
        if (target != AppScreen.IN_MATCH) {
            _activeEngine.value?.paused = true
            audio.isInMatch = false
        }
        _screen.value = target
    }

    fun startMatch() {
        audio.playUiClick()
        _postMatchRewards.value = null
        _renderError.value = null
        val engine = MatchEngine(
            saveData = _saveData.value,
            audio = audio,
            onMatchFinished = { report -> handleMatchFinished(report) }
        )
        _activeEngine.value = engine
        _matchInstanceKey.value += 1
        _screen.value = AppScreen.IN_MATCH
    }

    fun playAgain() {
        startMatch()
    }

    fun returnToLobby() {
        audio.playUiClick()
        _activeEngine.value?.paused = true
        _activeEngine.value = null
        _postMatchRewards.value = null
        _renderError.value = null
        audio.isInMatch = false
        _screen.value = AppScreen.LOBBY
    }

    fun reportRenderError(t: Throwable) {
        _renderError.value = t.message ?: "OpenGL ES 3.0 rendering interrupted"
    }

    fun setMatchPaused(paused: Boolean) {
        _activeEngine.value?.paused = paused
        audio.isPaused = paused
    }

    private fun handleMatchFinished(report: MatchResultReport) {
        val (updated, rewards) = ProgressionManager.applyMatchResult(_saveData.value, report)
        _saveData.value = updated
        _postMatchRewards.value = rewards
        viewModelScope.launch {
            repo.save(updated)
            rewards.newlyUnlockedAchievements.firstOrNull()?.let { ach ->
                showToast("ACHIEVEMENT UNLOCKED", "${ach.title} (+${ach.rewardCoins}c)")
            }
        }
    }

    // --- Shop & Cosmetics ---
    fun buyOrEquipCosmetic(item: CosmeticItem) {
        val cur = _saveData.value
        if (item.id in cur.ownedCosmetics) {
            equipCosmetic(item)
            return
        }
        if (item.priceCoins > 0 && cur.coins < item.priceCoins) {
            showToast("NOT ENOUGH COINS", "Play matches or claim quests to earn Coins.")
            return
        }
        if (item.priceGems > 0 && cur.gems < item.priceGems) {
            showToast("NOT ENOUGH GEMS", "Win matches, spin the wheel, or rank up for Gems.")
            return
        }
        audio.playPickup()
        updateSave { s ->
            equipInSave(
                s.copy(
                    coins = s.coins - item.priceCoins,
                    gems = s.gems - item.priceGems,
                    ownedCosmetics = s.ownedCosmetics + item.id
                ),
                item
            )
        }
        showToast("ITEM ACQUIRED", "${item.name} equipped!")
    }

    fun equipCosmetic(item: CosmeticItem) {
        audio.playUiClick()
        updateSave { s -> equipInSave(s, item) }
    }

    private fun equipInSave(s: PlayerSaveData, item: CosmeticItem): PlayerSaveData = when (item.category) {
        CosmeticCategory.CHARACTER_SKIN -> s.copy(equippedCharacterSkin = item.id)
        CosmeticCategory.WEAPON_SKIN -> s.copy(equippedWeaponSkin = item.id)
        CosmeticCategory.BULLET_TRAIL -> s.copy(equippedBulletTrail = item.id)
        CosmeticCategory.PARACHUTE -> s.copy(equippedParachute = item.id)
        CosmeticCategory.LANDING_EFFECT -> s.copy(equippedLandingEffect = item.id)
        CosmeticCategory.KILL_EFFECT -> s.copy(equippedKillEffect = item.id)
        CosmeticCategory.EMOTE -> s.copy(equippedEmote = item.id)
    }

    // --- Battle Pass ---
    fun unlockPremiumBattlePass() {
        val cur = _saveData.value
        if (cur.battlePassPremium) return
        if (cur.gems < ProgressionCatalog.PREMIUM_PASS_GEMS) {
            showToast("NEED ${ProgressionCatalog.PREMIUM_PASS_GEMS} GEMS", "Earn more Gems from Wins, Streaks & Achievements.")
            return
        }
        audio.playKillStreak(4)
        updateSave { it.copy(gems = it.gems - ProgressionCatalog.PREMIUM_PASS_GEMS, battlePassPremium = true) }
        showToast("PREMIUM PASS UNLOCKED", "All 30 Premium rewards are now claimable!")
    }

    fun claimBattlePassTier(tier: Int, isPremium: Boolean) {
        val cur = _saveData.value
        val currentTier = ProgressionManager.battlePassTier(cur.battlePassXp)
        if (tier > currentTier) return
        if (isPremium && !cur.battlePassPremium) return
        val def = ProgressionCatalog.BATTLE_PASS_TIERS.firstOrNull { it.tier == tier } ?: return
        val claimedList = if (isPremium) cur.claimedPremiumBpTiers else cur.claimedFreeBpTiers
        if (tier in claimedList) return

        val addCoins = if (isPremium) def.premiumCoins else def.freeCoins
        val addGems = if (isPremium) def.premiumGems else def.freeGems
        val cosmId = if (isPremium) def.premiumCosmeticId else def.freeCosmeticId

        audio.playPickup()
        updateSave { s ->
            val owned = if (cosmId != null && cosmId !in s.ownedCosmetics) s.ownedCosmetics + cosmId else s.ownedCosmetics
            s.copy(
                coins = s.coins + addCoins,
                gems = s.gems + addGems,
                ownedCosmetics = owned,
                claimedFreeBpTiers = if (!isPremium) s.claimedFreeBpTiers + tier else s.claimedFreeBpTiers,
                claimedPremiumBpTiers = if (isPremium) s.claimedPremiumBpTiers + tier else s.claimedPremiumBpTiers
            )
        }
        showToast("TIER $tier CLAIMED", if (isPremium) def.premiumLabel else def.freeLabel)
    }

    // --- Challenges, Login Streak, Achievements & Mastery ---
    fun claimChallenge(challengeId: String, isWeekly: Boolean) {
        val cur = _saveData.value
        val list = if (isWeekly) cur.weeklyChallenges else cur.dailyChallenges
        val ch = list.firstOrNull { it.id == challengeId } ?: return
        if (ch.claimed || ch.current < ch.target) return
        audio.playPickup()
        updateSave { s ->
            val updatedList = (if (isWeekly) s.weeklyChallenges else s.dailyChallenges).map {
                if (it.id == challengeId) it.copy(claimed = true) else it
            }
            s.copy(
                coins = s.coins + ch.rewardCoins,
                xp = s.xp + ch.rewardXp,
                battlePassXp = s.battlePassXp + ch.rewardXp,
                dailyChallenges = if (!isWeekly) updatedList else s.dailyChallenges,
                weeklyChallenges = if (isWeekly) updatedList else s.weeklyChallenges
            )
        }
        showToast("QUEST COMPLETE", "+${ch.rewardCoins} Coins & +${ch.rewardXp} XP")
    }

    fun claimLoginDay(day: Int) {
        val cur = _saveData.value
        if (day > cur.loginStreakDay || day in cur.claimedLoginDays) return
        val reward = ProgressionCatalog.LOGIN_STREAK.firstOrNull { it.day == day } ?: return
        audio.playPickup()
        updateSave { s ->
            val owned = if (reward.cosmeticId != null && reward.cosmeticId !in s.ownedCosmetics) {
                s.ownedCosmetics + reward.cosmeticId
            } else s.ownedCosmetics
            s.copy(
                coins = s.coins + reward.coins,
                gems = s.gems + reward.gems,
                ownedCosmetics = owned,
                claimedLoginDays = s.claimedLoginDays + day
            )
        }
        showToast("DAY $day STREAK", "Claimed ${reward.label}!")
    }

    fun claimAchievement(ach: AchievementDef) {
        val cur = _saveData.value
        if (ach.id !in cur.unlockedAchievements || ach.id in cur.claimedAchievements) return
        audio.playPickup()
        updateSave { s ->
            s.copy(
                coins = s.coins + ach.rewardCoins,
                gems = s.gems + ach.rewardGems,
                claimedAchievements = s.claimedAchievements + ach.id
            )
        }
        showToast("ACHIEVEMENT CLAIMED", "+${ach.rewardCoins} Coins & +${ach.rewardGems} Gems")
    }

    fun claimMasteryReward(wc: WeaponClass, level: Int) {
        val key = "${wc.id}_$level"
        val cur = _saveData.value
        if (key in cur.claimedMasteryRewards) return
        val curLvl = ProgressionCatalog.masteryLevelForXp(cur.weaponMasteryXp[wc.id] ?: 0)
        if (curLvl < level) return
        audio.playPickup()
        val addGems = if (level >= 5) 40 else 0
        val addCoins = if (level >= 5) 0 else if (level >= 3) 300 else 150
        updateSave { s ->
            s.copy(
                coins = s.coins + addCoins,
                gems = s.gems + addGems,
                claimedMasteryRewards = s.claimedMasteryRewards + key
            )
        }
        showToast("${wc.displayName.uppercase()} MASTERY", "Unlocked Level $level reward!")
    }

    // --- 4-Hour Free Lucky Spin ---
    fun openLuckySpin() {
        audio.playUiClick()
        _lastSpinResult.value = null
        _showLuckySpin.value = true
    }

    fun closeLuckySpin() {
        audio.playUiClick()
        _showLuckySpin.value = false
    }

    fun performLuckySpin(): LuckySpinSlot? {
        val cur = _saveData.value
        if (!ProgressionManager.canFreeSpin(cur)) return null
        val won = ProgressionManager.rollLuckySpin()
        audio.playKillStreak(3)
        _lastSpinResult.value = won
        updateSave { s ->
            val owned = if (won.cosmeticId != null && won.cosmeticId !in s.ownedCosmetics) {
                s.ownedCosmetics + won.cosmeticId
            } else s.ownedCosmetics
            s.copy(
                coins = s.coins + won.coins,
                gems = s.gems + won.gems,
                ownedCosmetics = owned,
                lastLuckySpinMs = System.currentTimeMillis()
            )
        }
        return won
    }

    // --- Settings, HUD Editor & Reset ---
    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        updateSave { it.copy(settings = transform(it.settings)) }
    }

    fun saveHudLayout(layout: Map<String, HudButtonLayout>) {
        audio.playPickup()
        updateSettings { it.copy(hudLayout = layout) }
        showToast("HUD SAVED", "Custom control layout applied.")
    }

    fun resetHudLayout() {
        audio.playUiClick()
        updateSettings { it.copy(hudLayout = GameSettings.defaultHudLayout()) }
    }

    fun resetAllSaveData() {
        audio.playUiClick()
        viewModelScope.launch {
            val fresh = ProgressionManager.refreshTimeBasedState(repo.resetAll())
            repo.save(fresh)
            _saveData.value = fresh
            syncAudioSettings(fresh.settings)
            showToast("DATA RESET", "All progress & stats restored to default.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        audio.stop()
    }
}
