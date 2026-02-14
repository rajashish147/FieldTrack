package com.fieldtrack.app.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fieldtrack.app.R
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.databinding.ActivityAdminHomeBinding
import com.fieldtrack.app.ui.auth.LoginActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Admin home screen with bottom navigation.
 * Navigation destinations:
 *   - EmployeeListFragment (view all employees)
 *   - ApprovalFragment (approve/deactivate employees)
 *   - LiveTrackingFragment (GPS live tracking)
 */
class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding
    private val authRepository: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    fun logout() {
        lifecycleScope.launch {
            authRepository.logout()
            val intent = Intent(this@AdminHomeActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
