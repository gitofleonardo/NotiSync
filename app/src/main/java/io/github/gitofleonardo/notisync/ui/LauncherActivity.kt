package io.github.gitofleonardo.notisync.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import io.github.gitofleonardo.coreservice.util.allBlePermissionsGranted
import io.github.gitofleonardo.coreservice.util.checkAndRequestPermissions
import io.github.gitofleonardo.coreservice.util.checkNotificationListenerPermission
import io.github.gitofleonardo.coreservice.util.requestNotificationListenerPermission
import io.github.gitofleonardo.notisync.R
import io.github.gitofleonardo.notisync.databinding.ActivityLauncherBinding

private const val REQUEST_CODE_PERMISSIONS = 0

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNav()

        checkAndRequestPermissions(REQUEST_CODE_PERMISSIONS)
        if (!checkNotificationListenerPermission()) {
            requestNotificationListenerPermission()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.apply {
                setPadding(paddingLeft, statusInsets.top, paddingRight, paddingBottom)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupNav() {
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_home)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { view, windowInsets ->
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.apply {
                layoutParams.height =
                    navInsets.bottom + resources.getDimensionPixelSize(R.dimen.navigation_height)
                setPadding(paddingLeft, paddingTop, paddingRight, navInsets.bottom)
            }
            windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (!allBlePermissionsGranted()) {
                    Toast.makeText(this, R.string.permission_rejected_msg, Toast.LENGTH_SHORT)
                        .show()
                    finish()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                findNavController(R.id.nav_host_fragment_activity_home)
                    .popBackStack()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}