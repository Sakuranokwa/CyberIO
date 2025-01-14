package net.liplum.holo

import arc.graphics.g2d.Draw
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.gen.Bullet
import mindustry.gen.Call
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.ui.Bar
import mindustry.world.blocks.defense.Wall
import net.liplum.*
import net.liplum.api.holo.IHoloEntity
import net.liplum.api.holo.IHoloEntity.Companion.minHealth
import net.liplum.lib.animations.Floating
import net.liplum.lib.shaders.use
import net.liplum.registries.CioShaders
import net.liplum.utils.*

open class HoloWall(name: String) : Wall(name) {
    @JvmField var restoreReload = 10 * 60f
    @ClientOnly lateinit var BaseTR: TR
    @ClientOnly lateinit var ImageTR: TR
    @ClientOnly lateinit var DyedImageTR: TR
    @JvmField var minHealthProportion = 0.05f
    @ClientOnly @JvmField var FloatingRange = 0.6f

    init {
        solid = false
        solidifes = true
        canOverdrive = true
        update = true
        hasShadow = false
        absorbLasers = true
        flashHit = false
        floating = true
        sync = true
    }

    override fun load() {
        super.load()
        BaseTR = this.sub("base")
        ImageTR = this.sub("image")
        DyedImageTR = this.sub("dyed-image") or ImageTR
    }

    override fun icons() = arrayOf(BaseTR, DyedImageTR)
    override fun setBars() {
        super.setBars()
        DebugOnly {
            bars.add<HoloBuild>(R.Bar.IsProjectingN) {
                Bar(
                    { R.Bar.IsProjecting.bundle(it.isProjecting.yesNo()) },
                    { Pal.bar },
                    { if (it.isProjecting) 1f else 0f }
                )
            }
            bars.add<HoloBuild>(R.Bar.RestRestoreN) {
                Bar(
                    { R.Bar.RestRestore.bundle(it.restRestore.toInt()) },
                    { Pal.bar },
                    { it.restRestore / it.maxHealth }
                )
            }
            bars.add<HoloBuild>(R.Bar.ChargeN) {
                Bar(
                    { R.Bar.Charge.bundle(it.restoreCharge.seconds) },
                    { Pal.power },
                    { it.restoreCharge / restoreReload }
                )
            }
            bars.add<HoloBuild>(R.Bar.LastDamagedN) {
                Bar(
                    { R.Bar.LastDamaged.bundle(it.lastDamagedTime.seconds) },
                    { Pal.power },
                    { it.lastDamagedTime / restoreReload }
                )
            }
        }
    }

    open inner class HoloBuild : WallBuild(), IHoloEntity {
        var restoreCharge = restoreReload
        open val isProjecting: Boolean
            get() = health > minHealth
        override var restRestore = 0f
            set(value) {
                field = value.coerceAtLeast(0f)
            }
        open var lastDamagedTime = restoreReload
        override val minHealthProportion: Float
            get() = this@HoloWall.minHealthProportion
        @ClientOnly @JvmField
        var floating: Floating = Floating(FloatingRange).randomXY().changeRate(1)
        open val canRestructure: Boolean
            get() = lastDamagedTime > restoreReload || !isProjecting
        open val canRestore: Boolean
            get() = !isProjecting || health < maxHealth
        open val isRecovering: Boolean
            get() = restRestore > 0.5f

        override fun damage(damage: Float) {
            if (!this.dead()) {
                val dm = Vars.state.rules.blockHealth(team)
                var d = damage
                if (dm.isZero) {
                    d = this.health + 1.0f
                } else {
                    d /= dm
                }
                d = handleDamage(d)
                val restHealth = (health - d).coerceAtLeast(maxHealth * minHealthProportion)
                Call.tileDamage(this, restHealth)
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
            Draw.rect(BaseTR, x, y)
            if (isProjecting) {
                CioShaders.Hologram.use(Layer.power) {
                    val healthPct = healthPct
                    it.alpha = healthPct / 4f * 3f
                    it.opacityNoise *= 2f - healthPct
                    it.flickering = it.DefaultFlickering + (1f - healthPct)
                    it.blendHoloColorOpacity = 0f
                    Draw.color(R.C.Holo)
                    Draw.rect(
                        ImageTR,
                        x + floating.dx,
                        y + floating.dy
                    )
                    Draw.reset()
                }
            }
            Draw.reset()
        }
        @ClientOnly
        open fun updateFloating() {
            val d = G.D(0.1f * FloatingRange * delta() * (2f - healthPct))
            floating.move(d)
        }

        override fun updateTile() {
            lastDamagedTime += delta()
            if (restoreCharge < restoreReload && !isRecovering && canRestructure) {
                restoreCharge += delta()
            }
            if (isRecovering) {
                val restored = if (restRestore <= maxHealth * minHealthProportion)
                    restRestore
                else
                    restRestore * delta() * 0.01f
                health = health.coerceAtLeast(0f)
                if (restored > 0.001f) {
                    heal(restored)
                    restRestore -= restored
                }
            }

            if (canRestore && restoreCharge >= restoreReload) {
                restoreCharge = 0f
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

        override fun checkSolid(): Boolean = isProjecting
        override fun write(write: Writes) {
            super.write(write)
            write.f(restoreCharge)
            write.f(restRestore)
            write.f(lastDamagedTime)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            restoreCharge = read.f()
            restRestore = read.f()
            lastDamagedTime = read.f()
        }
    }
}