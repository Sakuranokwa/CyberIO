package net.liplum.inputs

import mindustry.graphics.Drawf
import net.liplum.utils.worldXY

fun drawCircleOnMouse(radius: Float) {
    val mx = Screen.tileXOnMouse()
    val my = Screen.tileYOnMouse()
    Drawf.circles(mx.worldXY, my.worldXY, radius)
}

inline fun drawOnMouse(howToDraw: (Float, Float) -> Unit) {
    val mx = Screen.tileXOnMouse()
    val my = Screen.tileYOnMouse()
    howToDraw(mx.worldXY, my.worldXY)
}
