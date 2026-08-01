package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the skin used by manual launches and boot-time display startup. */
public final class SkinPreferences {
    private static final String PREFERENCES_NAME = "cluster_settings";
    private static final String KEY_SELECTED_SKIN = "selected_skin";
    private static final String KEY_AUTOSTART = "autostart_enabled";

    private SkinPreferences() {
    }

    public static String getSelectedSkin(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String selected = preferences.getString(
                KEY_SELECTED_SKIN,
                SkinRegistry.getDefaultId());
        return SkinRegistry.normalize(selected);
    }

    public static void setSelectedSkin(Context context, String skin) {
        String safeSkin = SkinRegistry.normalize(skin);
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_SKIN, safeSkin)
                .apply();
    }

    /** When false, boot / ACC resume never launches the overlay by themselves. */
    public static boolean isAutostartEnabled(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTOSTART, true);
    }

    public static void setAutostartEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTOSTART, enabled)
                .apply();
    }
}
