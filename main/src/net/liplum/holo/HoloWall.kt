package net.liplum.holo

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.Texture
import arc.graphics.g2d.Draw
import arc.math.Mathf
import arc.struct.Seq
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.entities.Damage
import mindustry.gen.Building
import mindustry.gen.Bullet
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.world.blocks.defense.Wall
import mindustry.world.meta.BlockStatus
import net.liplum.DebugOnly
import net.liplum.S
import net.liplum.api.holo.IHoloEntity
import net.liplum.api.holo.IHoloEntity.Companion.addHoloChargeTimeStats
import net.liplum.api.holo.IHoloEntity.Companion.addHoloHpAtLeastStats
import net.liplum.api.holo.IHoloEntity.Companion.minHealth
import net.liplum.common.shader.use
import net.liplum.common.util.bundle
import net.liplum.common.util.toFloat
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.WhenNotPaused
import net.liplum.mdt.animation.Floating
import net.liplum.mdt.render.G
import net.liplum.mdt.ui.bars.AddBar
import net.liplum.mdt.utils.healthPct
import net.liplum.mdt.utils.seconds
import net.liplum.mdt.utils.sub
import net.liplum.registry.SD
import net.liplum.util.yesNo
import plumy.core.Serialized
import plumy.core.arc.Tick
import plumy.core.assets.TR
import plumy.core.math.isZero
import plumy.core.math.nextBoolean
import plumy.texture.*
import kotlin.math.max

open class HoloWall(name: String) : Wall(name) {
    @JvmField var restoreCharge: Tick = 10 * 60f
    @ClientOnly lateinit var ProjectorTR: TR
    @ClientOnly lateinit var ImageTR: TR
    @JvmField var minHealthProportion = 0.05f
    @ClientOnly @JvmField var floatingRange = 2f
    @JvmField var needPower = false
    /**
     * Used when [needPower] is true.
     */
    @JvmField var powerCapacity = 1000f
    /**
     * Used when [needPower] is true.
     */
    @JvmField var powerUseForChargePreUnit = 0.1f

    init {
        buildType = Prov { HoloWallBuild() }
        solid = false
        solidifes = true
        canOverdrive = true
        update = true
        hasShadow = false
        absorbLasers = true
        flashHit = false
        teamPassable = true
        floating = true
        sync = true
        generateIcons = false
    }

    override fun init() {
        if (needPower) {
            insulated = false
            hasPower = true
            outputsPower = true
            consumesPower = true
            powerCapacity = max(1f, powerCapacity)
            consumePowerBuffered(powerCapacity)
        }
        super.init()
    }

    override fun load() {
        super.load()
        ProjectorTR = this.sub("base")
        ImageTR = this.sub("image")
    }

    companion object {
        var holoTintAlpha = 0.6423f
    }

    override fun loadIcon() {
        val size = size * 32
        val maker = StackIconMaker(size, size)
        val layers = listOf(
            (PixmapRegionModelLayer(Core.atlas.getPixmap(ProjectorTR))){
                +PlainLayerProcessor()
            },
            (PixmapRegionModelLayer(Core.atlas.getPixmap(ImageTR))){
                +TintLerpLayerProcessor(S.Hologram, holoTintAlpha)
            }
        )
        val baked = maker.bake(layers).texture.toPixmap()
        val icon = TR(Texture(baked))
        fullIcon = icon
        uiIcon = icon
    }

    override fun setStats() {
        super.setStats()
        addHoloChargeTimeStats(restoreCharge)
        addHoloHpAtLeastStats(minHealthProportion)
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        super.drawPlace(x, y, rotation, valid)
    }

    override fun setBars() {
        super.setBars()
        AddBar<HoloWallBuild>("health",
            { "stat.health".bundle },
            { S.Hologram },
            { healthf() }
        ) {
            blink(Color.white)
        }

        DebugOnly {
            AddBar<HoloWallBuild>("is-projecting",
                { "Is Projecting: ${isProjecting.yesNo()}" },
                { S.HologramDark },
                { isProjecting.toFloat() }
            )
            AddBar<HoloWallBuild>("rest-restore",
                { "Rest Restore: ${restRestore.toInt()}" },
                { S.Hologram },
                { restRestore / maxHealth }
            )
            AddBar<HoloWallBuild>("charge",
                { "Charge: ${charge.seconds}s" },
                { Pal.power },
                { charge / restoreCharge }
            )
            AddBar<HoloWallBuild>("last-damaged",
                { "Last Damage: ${lastDamagedTime.seconds}s" },
                { Pal.power },
                { lastDamagedTime / restoreCharge }
            )
        }
    }

    open inner class HoloWallBuild : WallBuild(), IHoloEntity {
        @Serialized
        var charge = restoreCharge
        open val isProjecting: Boolean
            get() = health > minHealth
        @Serialized
        override var restRestore = 0f
            set(value) {
                field = value.coerceAtLeast(0f)
            }
        @Serialized
        open var lastDamagedTime = restoreCharge
        override val minHealthProportion: Float
            get() = this@HoloWall.minHealthProportion
        @ClientOnly @JvmField
        var floating: Floating = Floating(floatingRange).apply {
            clockwise = nextBoolean()
            randomPos()
            changeRate = 10
        }
        open val canRestructure: Boolean
            get() = lastDamagedTime > restoreCharge || !isProjecting
        open val canRestore: Boolean
            get() = !isProjecting || health < maxHealth
        open val isRecovering: Boolean
            get() = restRestore > 0.5f
        /**
         * Used when [needPower] is true.
         */
        var storedPower: Float
            get() = if (hasPower) (power.status * powerCapacity).coerceIn(0f, powerCapacity) else 0f
            set(value) {
                if (hasPower) power.status = (value / powerCapacity).coerceIn(0f, 1f)
            }

        override fun damage(damage: Float) {
            if (!this.dead()) {
                val dm = Vars.state.rules.blockHealth(team)
                var d = damage
                if (dm.isZero) {
                    d = this.health + 1.0f
                } else {
                    d /= Damage.applyArmor(damage, armor) / dm
                }
                d = handleDamage(d)
                val restHealth = (health - d).coerceAtLeast(maxHealth * minHealthProportion)
                if (!Vars.net.client()) {
                    health = restHealth
                }
                healthChanged()
                lastDamagedTime = 0f
            }
        }

        override fun draw() {
            WhenNotPaused {
                updateFloating()
            }
            Draw.z(Layer.blockUnder)
            Drawf.shadow(x, y, 10f)
            Draw.z(Layer.block)
            Draw.rect(ProjectorTR, x, y)
            if (isProjecting) {
                SD.Hologram.use(Layer.flyingUnit - 0.1f) {
                    val healthPct = healthPct
                    it.alpha = healthPct / 4f * 3f
                    it.opacityNoise *= 2f - healthPct
                    it.flickering = it.DefaultFlickering + (1f - healthPct)
                    it.blendHoloColorOpacity = 0f
                    Draw.color(S.Hologram)
                    Draw.rect(
                        ImageTR,
                        x + floating.x,
                        y + floating.y
                    )
                    Draw.reset()
                }
            }
            Draw.reset()
        }
        @ClientOnly
        open fun updateFloating() {
            val d = (0.1f * floatingRange * delta() * (2f - healthPct)) * G.sclx
            floating.move(d * 0.3f)
        }

        override fun updateTile() {
            val delta = delta()
            lastDamagedTime += delta
            if (charge < restoreCharge && !isRecovering && canRestructure) {
                if (needPower) {
                    val stored = storedPower
                    val curChargeDelta = delta * powerUseForChargePreUnit
                    if (stored >= curChargeDelta) {
                        storedPower -= curChargeDelta
                        charge += delta
                    }
                } else {
                    charge += delta
                }
            }
            if (isRecovering) {
                val restored = if (restRestore <= maxHealth * minHealthProportion)
                    restRestore
                else
                    restRestore * delta * 0.01f
                health = health.coerceAtLeast(0f)
                if (restored > 0.001f) {
                    heal(restored)
                    restRestore -= restored
                }
            }

            if (canRestore && charge >= restoreCharge) {
                charge = 0f
                if (health != maxHealth) {
                    dead = false
                    restRestore = maxHealth
                }
            }
        }

        override fun killThoroughly() {
            kill()
        }

        override fun collide(other: Bullet): Boolean = isProjecting
        override fun drawCracks() {
        }

        override fun overwrote(previous: Seq<Building>) {
            if (needPower) {
                for (other in previous) {
                    if (other.power != null && other.block.consPower != null && other.block.consPower.buffered) {
                        val amount = other.block.consPower.capacity * other.power.status
                        power.status = Mathf.clamp(power.status + amount / consPower.capacity)
                    }
                }
            }
        }

        override fun status(): BlockStatus {
            if (needPower) {
                if (Mathf.equal(power.status, 0f, 0.001f)) return BlockStatus.noInput
                if (Mathf.equal(power.status, 1f, 0.001f)) return BlockStatus.active
            }
            return BlockStatus.noOutput
        }

        override fun checkSolid(): Boolean = isProjecting
        override fun write(write: Writes) {
            super.write(write)
            write.f(charge)
            write.f(restRestore)
            write.f(lastDamagedTime)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            charge = read.f()
            restRestore = read.f()
            lastDamagedTime = read.f()
        }
    }
}