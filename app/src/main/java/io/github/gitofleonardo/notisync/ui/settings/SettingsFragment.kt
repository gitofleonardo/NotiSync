package io.github.gitofleonardo.notisync.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import io.github.gitofleonardo.coreservice.util.SERVICE_PREFERENCE_NAME
import io.github.gitofleonardo.notisync.R
import io.github.gitofleonardo.notisync.util.GITHUB_URL
import io.github.gitofleonardo.notisync.util.navigate

const val KEY_PROJECT_SOURCE = "key_proj_source"
const val KEY_OSS_LICENSES = "key_proj_oss"
const val KEY_FILTER_APPS = "filter_apps"

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        preferenceManager.sharedPreferencesName = SERVICE_PREFERENCE_NAME
        setPreferencesFromResource(R.xml.settings_preference_screen, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            KEY_PROJECT_SOURCE -> {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = GITHUB_URL.toUri()
                startActivity(intent)
                true
            }

            KEY_OSS_LICENSES -> {
                val intent = Intent(requireContext(), OssLicensesMenuActivity::class.java)
                startActivity(intent)
                true
            }

            KEY_FILTER_APPS -> {
                val action =
                    SettingsFragmentDirections.actionNavigationSettingsToNavigationFilteredApps()
                navigate(action)
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }
}