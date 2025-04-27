package io.github.gitofleonardo.notisync.util

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import io.github.gitofleonardo.notisync.R

const val GITHUB_URL = "https://github.com/gitofleonardo/NotiSync"

private val navOptions: NavOptions
    get() {
        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.anim_window_open_enter)
            .setExitAnim(R.anim.anim_window_open_exit)
            .setPopEnterAnim(R.anim.anim_window_close_enter)
            .setPopExitAnim(R.anim.anim_window_close_exit)
            .build()

        return navOptions
    }

fun Fragment.navigate(directions: NavDirections) {
    findNavController().navigate(directions, navOptions)
}