package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fieldtrack.app.databinding.FragmentUnifiedApprovalsBinding
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.tabs.TabLayoutMediator
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * UnifiedApprovalsFragment - Container for employee and expense approvals using tabs.
 * Reuses existing ApprovalFragment and AdminExpenseFragment without modification.
 */
class UnifiedApprovalsFragment : Fragment() {

    private var _binding: FragmentUnifiedApprovalsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnifiedApprovalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        observeBadgeCounts()
    }

    private fun setupViewPager() {
        val adapter = ApprovalsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Employees"
                1 -> "Expenses"
                else -> ""
            }
        }.attach()
    }

    private fun observeBadgeCounts() {
        viewModel.pendingEmployeeCount.observe(viewLifecycleOwner) { count ->
            updateBadge(0, count)
        }

        viewModel.pendingExpenseCount.observe(viewLifecycleOwner) { count ->
            updateBadge(1, count)
        }
    }

    private fun updateBadge(tabPosition: Int, count: Int) {
        val tab = binding.tabLayout.getTabAt(tabPosition) ?: return
        
        if (count > 0) {
            val badge = tab.orCreateBadge
            badge.number = count
            badge.isVisible = true
        } else {
            tab.removeBadge()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * ViewPager2 adapter that hosts:
     * - Tab 0: ApprovalFragment (employee approvals)
     * - Tab 1: AdminExpenseFragment (expense approvals, pending only)
     */
    private inner class ApprovalsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ApprovalFragment()
                1 -> AdminExpenseFragment.newInstance(showPendingOnly = true)
                else -> throw IllegalStateException("Invalid tab position: $position")
            }
        }
    }
}
