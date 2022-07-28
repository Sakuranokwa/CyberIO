package net.liplum.ui

import arc.Core
import arc.graphics.Color
import arc.math.Interp
import arc.scene.ui.ImageButton
import arc.scene.ui.ImageButton.ImageButtonStyle
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Sounds
import mindustry.gen.Tex
import net.liplum.*
import net.liplum.common.ui.UIToast
import net.liplum.common.util.IBundlable
import net.liplum.common.util.bundle
import net.liplum.mdt.ClientOnly
import net.liplum.ui.animation.WrapAnimationSpec
import net.liplum.ui.animation.animatedVisibility
import net.liplum.ui.template.NewIconTextButton

@ClientOnly
object ContentSpecFrag : IBundlable {
    override val bundlePrefix = "setting.${R.Setting.ContentSpecific}"
    var toastUI = UIToast().apply {
        background = Tex.button
    }
    val title: String
        get() = bundle("title")
    var fadeDuration = 0.8f
    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    @JvmStatic
    fun build(cont: Table) {
        // Main
        var curSpec = Var.ContentSpecific
        val unsavedWarning = Label(R.Bundle.UnsavedChange.bundle).apply {
            setColor(R.C.RedAlert)
        }
        var warningVisible by unsavedWarning.animatedVisibility(
            isVisible = false,
            duration = 60f,
            spec = WrapAnimationSpec(Interp.pow2In)
        )
        val desc = Label(curSpec.i18nDesc)
        var descVisible by desc.animatedVisibility(
            isVisible = false,
            duration = 30f,
            spec = WrapAnimationSpec(Interp.smooth)
        )
        val savesWarning = Label(bundle("saves-warning")).apply {
            setColor(R.C.RedAlert)
        }
        var savesWarningVisible by savesWarning.animatedVisibility(
            isVisible = false,
            duration = 60f,
            spec = WrapAnimationSpec(Interp.pow2In)
        )

        fun changeCurSpec(new: ContentSpec) {
            if (curSpec != new) {
                descVisible = true
                savesWarningVisible = true
                curSpec = new
                desc.apply {
                    setColor(new.color)
                    if (new != Var.ContentSpecific)
                        setText("${new.i18nDesc} ${bundle("require-restart")}")
                    else setText(new.i18nDesc)
                }
                Sounds.message.play()
                val tipKey = if (curSpec != Var.ContentSpecific) {
                    warningVisible = true
                    "switch-to"
                } else {
                    warningVisible = false
                    "switch-back"
                }
                toastUI.postToastOnUI(Table().apply {
                    add(bundle(tipKey, curSpec.i18nName))
                })
            }
        }

        fun hasUnsavedChange() =
            curSpec != Var.ContentSpecific
        // Tip
        cont.add(Table().apply {
            add(Table().apply {
                add(unsavedWarning)
            }).row()
            // Options
            add(bundle("introduction")).row()
            addTable {
                val default = Core.scene.getStyle(ImageButtonStyle::class.java)
                val style = ImageButtonStyle(default).apply {
                    checked = default.over
                }
                addTable {
                    ContentSpec.values().forEach {
                        this.add(ImageButton(it.icon, style).apply {
                            clicked {
                                changeCurSpec(it)
                            }
                            update {
                                isChecked = it == curSpec
                            }
                            row()
                            add(Label(it.i18nName).apply {
                                update {
                                    if (it == curSpec) setColor(it.color)
                                    else setColor(Color.white)
                                }
                            })
                        }).pad(5f)
                    }
                }.pad(50f).row()
                add(desc).row()
                add(savesWarning)
            }
        }).grow()
        cont.row()
        // Buttons
        cont.add(Table().apply {
            add(NewIconTextButton("@save", Icon.save) {
                setSpec(curSpec)
            }).then {
                update {
                    isDisabled = !hasUnsavedChange()
                }
                align(Align.bottom)
            }.width(200f).row()
        })
    }
    @JvmStatic
    fun setSpec(spec: ContentSpec) {
        if (Var.ContentSpecific == spec) return
        Settings.ContentSpecific = spec.id
        Vars.ui.showInfoOnHidden(bundle("restart", spec.i18nName)) {
            Core.app.exit()
        }
    }
}