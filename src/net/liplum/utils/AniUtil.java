package net.liplum.utils;

import arc.util.Time;
import net.liplum.CioMod;

public class AniUtil {
    public static boolean needUpdateAniStateM() {
        return Time.time % CioMod.UpdateFrequency < 1f;
    }
}