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
import org.koin.androidx.viewmodel.ext.android.viewModel

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
    
    // PHASE B - Pending Badge: Access ViewModel to observe pending counts
    private val adminViewModel: AdminViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        // PHASE B - Pending Badge: Observe pending counts and update badges
        observePendingCounts()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }
    
    // PHASE B - Pending Badge: Update badges when pending counts change
    private fun observePendingCounts() {
        // Load initial counts
        adminViewModel.loadPendingEmployees()
        adminViewModel.loadAllExpenses()
        
        adminViewModel.pendingEmployeeCount.observe(this) { count ->
            updateBadge(R.id.unifiedApprovalsFragment, count)
        }
        
        adminViewModel.pendingExpenseCount.observe(this) { count ->
            updateBadge(R.id.adminExpenseFragment, count)
        }
    }
    
    // PHASE B - Pending Badge: Show/hide badge with count
    private fun updateBadge(menuItemId: Int, count: Int) {
        val badge = binding.bottomNavigation.getOrCreateBadge(menuItemId)
        
        if (count > 0) {
            badge.isVisible = true
            badge.number = if (count > 99) 99 else count
            badge.maxCharacterCount = 2
            
            // Show "99+" for counts over 99
            if (count > 99) {
                badge.text = "99+"
            } else {
                badge.clearText()
            }
        } else {
            badge.isVisible = false
        }
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
