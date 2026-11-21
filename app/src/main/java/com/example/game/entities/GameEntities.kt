package com.example.game.entities

import com.example.game.config.AmmoType
import com.example.game.config.AttachmentType
import com.example.game.config.BotPersonality
import com.example.game.config.ConsumableType
import com.example.game.config.GrenadeType
import com.example.game.config.Rarity
import com.example.game.config.WeaponClass
import com.example.game.config.WorldConfig

// ============================================================================
// SECTION: ENTITIES & POOLS — Actors, Weapons, Loot, Vehicles, Airdrops, Pools
// ============================================================================

data class WeaponInstance(
    val weaponClass: WeaponClass,
    val rarity: Rarity,
    var magAmmo: Int = weaponClass.magSize,
    val attachments: MutableSet<AttachmentType> = mutableSetOf()
) {
    val effectiveDamage: Float get() = weaponClass.baseDamage * rarity.statMultiplier
    val effectiveMagSize: Int get() = if (AttachmentType.EXTENDED_MAG in attachments) (weaponClass.magSize * 1.45f).toInt().coerceAtLeast(weaponClass.magSize + 2) else weaponClass.magSize
    val effectiveSpread: Float get() = weaponClass.spreadDeg * (if (AttachmentType.GRIP in attachments) 0.72f else 1f) * (1.06f - rarity.ordinal * 0.04f)
    val effectiveRecoil: Float get() = weaponClass.recoilKick * (if (AttachmentType.GRIP in attachments) 0.68f else 1f)
    val effectiveReloadTime: Float get() = weaponClass.reloadTime * (1.0f - rarity.ordinal * 0.05f)
    val isSilenced: Boolean get() = AttachmentType.SILENCER in attachments
    val hasScope: Boolean get() = weaponClass.hasDefaultScope || AttachmentType.SCOPE in attachments
    val hasRedDot: Boolean get() = weaponClass.hasDefaultRedDot || AttachmentType.RED_DOT in attachments
}

enum class DropPhase {
    IN_PLANE,
    FREEFALL,
    PARACHUTE,
    LANDED
}

enum class BotAiState {
    PARACHUTE, LAND, LOOT, ROAM, FLEE_STORM, INVESTIGATE_SOUND,
    CHASE, FIGHT, TAKE_COVER, HEAL, HIDE_BUSH, THIRD_PARTY,
    GRAB_AIRDROP, DRIVE_VEHICLE
}

class Actor(
    val id: Int,
    val name: String,
    val isHuman: Boolean,
    val personality: BotPersonality,
    val skinPrimaryRgb: FloatArray,
    val skinAccentRgb: FloatArray,
    val parachuteRgb: FloatArray,
    val trailRgb: FloatArray
) {
    var alive: Boolean = true
    var dropPhase: DropPhase = DropPhase.IN_PLANE
    var planeJumpProgress: Float = 0.5f

    var x: Float = 0f
    var y: Float = WorldConfig.PLANE_ALTITUDE
    var z: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var yaw: Float = 0f   // radians (0 = +Z, PI/2 = +X)
    var pitch: Float = 0f // radians (-1.2..1.2)

    var hp: Float = WorldConfig.MAX_HP
    var shield: Float = 0f
    var stamina: Float = WorldConfig.MAX_STAMINA
    var armorLevel: Int = 0
    var helmetLevel: Int = 0
    var backpackLevel: Int = 0

    var grounded: Boolean = false
    var isCrouching: Boolean = false
    var isSprinting: Boolean = false
    var isSliding: Boolean = false
    var slideTimer: Float = 0f
    var isSwimming: Boolean = false
    var isHiddenInBush: Boolean = false
    var isAiming: Boolean = false
    var isVaulting: Boolean = false
    var vaultTimer: Float = 0f
    var flashlightOn: Boolean = false
    var speedBoostTimer: Float = 0f
    var flashBlindTimer: Float = 0f
    var vehicleId: Int = -1

    // Weapons (5 slots) & Gear
    val weaponSlots = arrayOfNulls<WeaponInstance>(5)
    var activeSlot: Int = 0
    val ammoReserves = IntArray(AmmoType.entries.size)
    val consumables = IntArray(ConsumableType.entries.size)
    val grenades = IntArray(GrenadeType.entries.size)
    var selectedGrenade: GrenadeType = GrenadeType.FRAG

    // Combat timers
    var fireCooldown: Float = 0f
    var reloadTimer: Float = 0f
    var reloading: Boolean = false
    var healingType: ConsumableType? = null
    var healingTimer: Float = 0f
    var healingDuration: Float = 0f

    // Animation & visual feedback
    var animTime: Float = 0f
    var shootAnimTimer: Float = 0f
    var deathTimer: Float = 0f
    var footstepAccum: Float = 0f
    var lastAttackerId: Int = -1
    var lastDamageTime: Float = -10f
    var kills: Int = 0
    var headshots: Int = 0
    var damageDealt: Int = 0
    var shotsFired: Int = 0
    var shotsHit: Int = 0
    var placement: Int = 0
    var streakCount: Int = 0
    var lastKillTime: Float = -20f

    // Bot AI runtime memory
    var aiState: BotAiState = BotAiState.PARACHUTE
    var aiTargetActorId: Int = -1
    var aiWaypointX: Float = 0f
    var aiWaypointZ: Float = 0f
    var aiStrafeDir: Float = 1f
    var aiStrafeTimer: Float = 0f
    var aiReactionTimer: Float = 0f
    var aiBurstRemaining: Int = 0
    var aiBurstPause: Float = 0f
    var aiStuckTimer: Float = 0f
    var aiLastCheckX: Float = 0f
    var aiLastCheckZ: Float = 0f
    var aiHeardX: Float = 0f
    var aiHeardZ: Float = 0f
    var aiHeardTimer: Float = 0f
    var aiSkillAccuracy: Float = 0.6f
    var aiReactionDelay: Float = 0.35f

    fun activeWeapon(): WeaponInstance? = weaponSlots[activeSlot]

    fun totalAmmoCount(): Int = ammoReserves.sum()
    fun maxAmmoCapacity(): Int = 240 + backpackLevel * 120

    fun bestConsumable(): ConsumableType? {
        if (hp < 60f && consumables[ConsumableType.MEDKIT.ordinal] > 0) return ConsumableType.MEDKIT
        if (hp < 75f && consumables[ConsumableType.BANDAGE.ordinal] > 0) return ConsumableType.BANDAGE
        if (shield < 50f && consumables[ConsumableType.SMALL_SHIELD.ordinal] > 0) return ConsumableType.SMALL_SHIELD
        if (shield < 95f && consumables[ConsumableType.BIG_SHIELD.ordinal] > 0) return ConsumableType.BIG_SHIELD
        if (consumables[ConsumableType.ENERGY_DRINK.ordinal] > 0) return ConsumableType.ENERGY_DRINK
        if (hp < 95f && consumables[ConsumableType.MEDKIT.ordinal] > 0) return ConsumableType.MEDKIT
        return null
    }

    fun cancelHeal() {
        healingType = null
        healingTimer = 0f
        healingDuration = 0f
    }
}

enum class LootKind {
    WEAPON, AMMO, ATTACHMENT, CONSUMABLE, ARMOR, HELMET, BACKPACK, GRENADE
}

data class WorldLootItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val kind: LootKind,
    val rarity: Rarity,
    val weaponClass: WeaponClass? = null,
    val ammoType: AmmoType? = null,
    val ammoAmount: Int = 0,
    val attachment: AttachmentType? = null,
    val consumable: ConsumableType? = null,
    val grenade: GrenadeType? = null,
    val gearLevel: Int = 1,
    var pickedUp: Boolean = false
) {
    fun displayLabel(): String = when (kind) {
        LootKind.WEAPON -> "${rarity.label} ${weaponClass?.displayName ?: "Weapon"}"
        LootKind.AMMO -> "+$ammoAmount ${ammoType?.shortName ?: "Ammo"}"
        LootKind.ATTACHMENT -> attachment?.label ?: "Attachment"
        LootKind.CONSUMABLE -> consumable?.label ?: "Med"
        LootKind.ARMOR -> "Armor Vest Lv.$gearLevel"
        LootKind.HELMET -> "Tactical Helmet Lv.$gearLevel"
        LootKind.BACKPACK -> "Backpack Lv.$gearLevel"
        LootKind.GRENADE -> grenade?.label ?: "Grenade"
    }
}

data class VehicleEntity(
    val id: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float,
    var speed: Float = 0f,
    var hp: Float = 320f,
    val maxHp: Float = 320f,
    var driverId: Int = -1,
    var exploded: Boolean = false,
    val colorRgb: FloatArray = floatArrayOf(0.18f, 0.34f, 0.48f)
)

data class BreakableCrate(
    val id: Int,
    val colliderId: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    var hp: Float = 45f,
    var broken: Boolean = false
)

data class ExplosiveBarrel(
    val id: Int,
    val colliderId: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    var hp: Float = 35f,
    var exploded: Boolean = false
)

data class BushProp(
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float = 2.3f
)

data class AirdropCrate(
    val id: Int,
    val x: Float,
    var y: Float,
    val z: Float,
    val groundY: Float,
    var landed: Boolean = false,
    var looted: Boolean = false,
    var smokeTimer: Float = 90f
)

// ============================================================================
// OBJECT POOLS — Zero-GC Projectiles, Tracers, Particles & Damage Numbers
// ============================================================================

enum class ProjectileKind { ROCKET, CROSSBOW_BOLT, FRAG, SMOKE, FLASH }

class PooledProjectile {
    var active = false
    var kind = ProjectileKind.ROCKET
    var ownerId = -1
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var damage = 0f
    var radius = 0f
    var life = 0f
    var weaponId = ""
}

class PooledTracer {
    var active = false
    var x1 = 0f; var y1 = 0f; var z1 = 0f
    var x2 = 0f; var y2 = 0f; var z2 = 0f
    var r = 0f; var g = 0.94f; var b = 1f
    var life = 0f
    var maxLife = 0.12f
}

class PooledParticle {
    var active = false
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var r = 1f; var g = 0.8f; var b = 0.2f
    var size = 0.25f
    var gravity = 12f
    var life = 0f
    var maxLife = 0.6f
    var isSmoke = false
}

class PooledDamageNumber {
    var active = false
    var worldX = 0f; var worldY = 0f; var worldZ = 0f
    var amount = 0
    var isHeadshot = false
    var isShield = false
    var life = 0f
    var maxLife = 0.85f
}

class SmokeCloud {
    var active = false
    var x = 0f; var y = 0f; var z = 0f
    var radius = 9f
    var life = 0f
}

class CombatPools {
    val projectiles = Array(48) { PooledProjectile() }
    val tracers = Array(80) { PooledTracer() }
    val particles = Array(320) { PooledParticle() }
    val damageNumbers = Array(40) { PooledDamageNumber() }
    val smokeClouds = Array(8) { SmokeCloud() }

    private var projCursor = 0
    private var tracerCursor = 0
    private var partCursor = 0
    private var dmgCursor = 0
    private var smokeCursor = 0

    fun spawnProjectile(
        kind: ProjectileKind, ownerId: Int,
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        damage: Float, radius: Float, life: Float, weaponId: String
    ) {
        val p = projectiles[projCursor++ % projectiles.size]
        p.active = true; p.kind = kind; p.ownerId = ownerId
        p.x = x; p.y = y; p.z = z
        p.vx = vx; p.vy = vy; p.vz = vz
        p.damage = damage; p.radius = radius; p.life = life; p.weaponId = weaponId
    }

    fun spawnTracer(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, rgb: FloatArray) {
        val t = tracers[tracerCursor++ % tracers.size]
        t.active = true
        t.x1 = x1; t.y1 = y1; t.z1 = z1
        t.x2 = x2; t.y2 = y2; t.z2 = z2
        t.r = rgb[0]; t.g = rgb[1]; t.b = rgb[2]
        t.life = 0.11f; t.maxLife = 0.11f
    }

    fun spawnParticle(
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        r: Float, g: Float, b: Float,
        size: Float, life: Float, gravity: Float = 14f, isSmoke: Boolean = false
    ) {
        val p = particles[partCursor++ % particles.size]
        p.active = true
        p.x = x; p.y = y; p.z = z
        p.vx = vx; p.vy = vy; p.vz = vz
        p.r = r; p.g = g; p.b = b
        p.size = size; p.life = life; p.maxLife = life; p.gravity = gravity; p.isSmoke = isSmoke
    }

    fun spawnDamageNumber(x: Float, y: Float, z: Float, amount: Int, headshot: Boolean, shield: Boolean) {
        val d = damageNumbers[dmgCursor++ % damageNumbers.size]
        d.active = true
        d.worldX = x; d.worldY = y; d.worldZ = z
        d.amount = amount; d.isHeadshot = headshot; d.isShield = shield
        d.life = 0.85f; d.maxLife = 0.85f
    }

    fun spawnSmokeCloud(x: Float, y: Float, z: Float, radius: Float = 10f, duration: Float = 11f) {
        val s = smokeClouds[smokeCursor++ % smokeClouds.size]
        s.active = true; s.x = x; s.y = y; s.z = z; s.radius = radius; s.life = duration
    }
}
