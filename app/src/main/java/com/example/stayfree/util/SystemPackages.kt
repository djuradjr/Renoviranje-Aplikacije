package com.example.stayfree.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Packages no block may ever cover: our own, the launcher, system UI, settings, the dialer. */
object SystemPackages {

    private val STATIC_EXEMPT = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.incallui",
        "com.android.server.telecom"
    )

    fun exempt(context: Context): Set<String> {
        val pm = context.packageManager
        val set = mutableSetOf(context.packageName)
        set.addAll(STATIC_EXEMPT)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let { set.add(it) }
        val dial = Intent(Intent.ACTION_DIAL)
        pm.resolveActivity(dial, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let { set.add(it) }
        return set
    }

    fun keyboards(context: Context): Set<String> {
        val set = mutableSetOf<String>()
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.mapTo(set) { it.packageName }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let { set.add(it) }
        return set
    }
}
