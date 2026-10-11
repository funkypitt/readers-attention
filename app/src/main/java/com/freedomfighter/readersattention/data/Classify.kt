package com.freedomfighter.readersattention.data

/**
 * Reflex or tool. The system's category (social, game, video) and a short list of well-known
 * packages give the default; the person's own choice, kept in the preferences, wins.
 */
object Classify {
    // ApplicationInfo.CATEGORY_* values, repeated here so this file stays free of Android
    const val CATEGORY_UNDEFINED = -1
    const val CATEGORY_GAME = 0
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_SOCIAL = 4

    /** Package prefixes of apps opened by reflex: social networks, short video, games. */
    val known = listOf(
        "com.instagram.", "com.zhiliaoapp.musically", "com.ss.android.ugc.", "com.facebook.katana", "com.facebook.lite",
        "com.snapchat.", "com.twitter.", "com.reddit.", "com.google.android.youtube", "com.netflix.", "tv.twitch.",
        "com.pinterest", "com.bereal.", "com.tumblr", "com.linkedin.", "com.disney.disneyplus", "com.amazon.avod",
        "com.king.", "com.supercell.", "com.rovio.", "com.zynga.", "com.gameloft.", "com.miniclip.", "com.playrix.",
        "com.mojang.", "com.ea.game", "com.nianticlabs.", "com.dts.freefireth", "com.tencent.ig", "com.activision.",
        "com.roblox.", "com.innersloth.", "com.kiloo.subwaysurf", "com.imangi.", "com.outfit7."
    )

    fun defaultReflex(pkg: String, category: Int): Boolean {
        if (category == CATEGORY_SOCIAL || category == CATEGORY_GAME || category == CATEGORY_VIDEO) return true
        if (pkg.contains("youtube") && pkg.contains("music")) return false
        return known.any { pkg == it.trimEnd('.') || pkg.startsWith(it) }
    }

    fun isReflex(pkg: String, category: Int, reflexChoices: Set<String>, toolChoices: Set<String>): Boolean = when (pkg) {
        in reflexChoices -> true
        in toolChoices -> false
        else -> defaultReflex(pkg, category)
    }
}
