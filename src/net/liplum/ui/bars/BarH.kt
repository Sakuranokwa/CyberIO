package net.liplum.ui.bars

import mindustry.world.meta.BlockBars

fun BlockBars.removeIfExist(name: String) {
    try {
        this.remove(name)
    } catch (_: RuntimeException) {
    }
}

fun BlockBars.removeItems() {
    try {
        this.remove("items")
    } catch (_: RuntimeException) {
    }
}
