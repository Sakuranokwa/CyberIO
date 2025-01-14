package net.liplum

import arc.Core
import arc.Events
import arc.util.Log
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.io.JsonIO
import mindustry.mod.Mod
import mindustry.mod.Mods
import net.liplum.api.cyber.DataCenter
import net.liplum.api.cyber.StreamCenter
import net.liplum.api.holo.IHoloEntity
import net.liplum.blocks.cloud.LiplumCloud
import net.liplum.blocks.cloud.SharedRoom
import net.liplum.inputs.UnitTap
import net.liplum.lib.animations.ganim.GlobalAnimation
import net.liplum.registries.*
import net.liplum.render.LinkDrawer
import net.liplum.scripts.NpcSystem
import net.liplum.scripts.Script
import net.liplum.ui.DebugUI
import net.liplum.ui.SettingsUI
import net.liplum.update.Updater
import net.liplum.utils.G
import net.liplum.welcome.Welcome
import net.liplum.welcome.WelcomeList

class CioMod : Mod() {
    companion object {
        @JvmField val IsClient = !Vars.headless
        @JvmField var DebugMode = false
        @JvmField var TestGlCompatibility = false
        @JvmField var ExperimentalMode = false
        @JvmField var CanGlobalAnimationPlay = false
        @JvmField var UpdateFrequency = 5f
        lateinit var Info: Mods.LoadedMod
    }
    /**
     * # Calling time node
     * 1. constructor
     * 2. FileTreeInitEvent
     * 3. loadContent()
     * 4. init()
     * 5. ClientLoadEvent
     */
    init {
        Log.info("Cyber IO mod loading started.")
        Updater.check()
        ClientOnly {
            GL.handleCompatible()
        }
        //listen for game load event
        Events.on(ClientLoadEvent::class.java) {
            //show welcome dialog upon startup
            Time.runTask(15f) { Welcome.showWelcomeDialog() }
        }
        Events.on(FileTreeInitEvent::class.java) {
            ClientOnly {
                Core.app.post {
                    CioShaders.init()
                    WelcomeList.loadList()
                    Welcome.load()
                    DebugOnly {
                        Script.init()
                        Script.initInterpreter()
                        Script.loadStory("OnTheShip.Captain.Introduction")
                    }
                }
            }
        }
        Events.on(DisposeEvent::class.java) {
            ClientOnly {
                CioShaders.dispose()
            }
        }
    }

    override fun init() {
        ClientOnly {
            Welcome.modifierModInfo()
        }
        UpdateFrequency = if (Vars.mobile || Vars.testMobile)
            10f
        else
            5f
        // Cloud is developing
        DebugOnly {
            JsonIO.json.addClassTag(SharedRoom::class.java.name, SharedRoom::class.java)
            Events.on(WorldLoadEvent::class.java) {
                LiplumCloud.reset()
                LiplumCloud.read()
            }
            Events.on(SaveWriteEvent::class.java) {
                LiplumCloud.reset()
                LiplumCloud.save()
            }
        }
        DebugOnly {
            Vars.enableConsole = true
        }
        tintedBulletsRegistryLoad()
        Events.run(Trigger.update) {
            val state = Vars.state
            if (state.isGame && !state.isPaused) {
                LiplumCloud.update()
            }
        }
        DataCenter.initData()
        StreamCenter.initAndLoad()
        ClientOnly {
            SettingsUI.appendSettings()
            DebugOnly {
                DebugUI.appendUI()
            }
            CioShaders.loadResource()
            GlobalAnimation.loadAllResources()
            Events.run(Trigger.preDraw) {
                G.init()
            }
            LinkDrawer.register()
            NpcSystem.register()
            Core.input.addProcessor(UnitTap)
        }
        Settings.updateSettings()
        Log.info("Cyber IO initialized.")
    }

    override fun loadContent() {
        Info = Vars.mods.locateMod(Meta.ModID)
        CioSounds.load()
        EntityRegistry.registerAll()
        CioCLs.load()
        ContentRegistry.loadContent()
        IHoloEntity.registerHoloEntityInitHealth()
        PrismBlackList.load()
        CanGlobalAnimationPlay = true
        Log.info("Cyber IO mod's contents loaded.")
    }
}