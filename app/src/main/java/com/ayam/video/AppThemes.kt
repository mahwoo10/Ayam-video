package com.ayam.video

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class AppTheme(
    val name: String,
    val bgTop: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val accent: Color,
    val muted: Color,
    val muted2: Color
)

object AppThemes {
    val all = listOf(
        AppTheme("Saphir & Améthyste", Color(0xFF1B0B3A), Color(0xFF090412), Color(0xFF130826), Color(0xFF2E1A47), Color(0xFF3B2859), Color(0xFFE0AAFF), Color(0xFF9A8CB9), Color(0xFF7A6B9C)),
        AppTheme("Midnight Blue", Color(0xFF0B1A3A), Color(0xFF050B18), Color(0xFF0C1630), Color(0xFF16264A), Color(0xFF23386B), Color(0xFF7FB2FF), Color(0xFF8FA3C7), Color(0xFF5F7399)),
        AppTheme("Cinema Dark", Color(0xFF1A1A1A), Color(0xFF0A0A0A), Color(0xFF161616), Color(0xFF262626), Color(0xFF333333), Color(0xFFFF4D4D), Color(0xFFA0A0A0), Color(0xFF707070)),
        AppTheme("Orange Energy", Color(0xFF2A1405), Color(0xFF120A04), Color(0xFF1E1208), Color(0xFF35200E), Color(0xFF4D3015), Color(0xFFFFA24C), Color(0xFFC4A07A), Color(0xFF8F7152)),
        AppTheme("Emerald Dark", Color(0xFF0A2A1D), Color(0xFF04120C), Color(0xFF0A1F16), Color(0xFF14382A), Color(0xFF1F5440), Color(0xFF5BE3A0), Color(0xFF8CBBA5), Color(0xFF5F8A75)),
        AppTheme("Gold Dark", Color(0xFF2A2208), Color(0xFF0F0C04), Color(0xFF1C1708), Color(0xFF332A0E), Color(0xFF4D4114), Color(0xFFFFD54A), Color(0xFFC2B27A), Color(0xFF8C7F52)),
        AppTheme("Rose Néon", Color(0xFF2E0A21), Color(0xFF14040E), Color(0xFF200817), Color(0xFF3A1229), Color(0xFF54193B), Color(0xFFFF7EC8), Color(0xFFC795B3), Color(0xFF92617F)),
        AppTheme("Ocean Teal", Color(0xFF08302F), Color(0xFF031314), Color(0xFF0A2224), Color(0xFF123B3D), Color(0xFF1B5558), Color(0xFF4DD9E6), Color(0xFF86B7BB), Color(0xFF5B8B8F)),
        AppTheme("Graphite", Color(0xFF1B1D23), Color(0xFF0E0F12), Color(0xFF181A1F), Color(0xFF272A32), Color(0xFF353945), Color(0xFFB8C4FF), Color(0xFF9AA0AE), Color(0xFF6C7280))
    )
}

/** Palette courante : toute l'interface lit ces couleurs, donc un changement de thème s'applique partout. */
object Pal {
    var theme by mutableStateOf(AppThemes.all[0])
        private set

    val bgTop: Color get() = theme.bgTop
    val bg: Color get() = theme.bg
    val surface: Color get() = theme.surface
    val surface2: Color get() = theme.surface2
    val border: Color get() = theme.border
    val accent: Color get() = theme.accent
    val muted: Color get() = theme.muted
    val muted2: Color get() = theme.muted2

    fun load(context: Context) {
        val name = context.getSharedPreferences("ayam_settings", Context.MODE_PRIVATE)
            .getString("theme_name", null)
        theme = AppThemes.all.firstOrNull { it.name == name } ?: AppThemes.all[0]
    }

    fun select(context: Context, t: AppTheme) {
        theme = t
        context.getSharedPreferences("ayam_settings", Context.MODE_PRIVATE)
            .edit().putString("theme_name", t.name).apply()
    }
}
