// v1.4 - Admin Session History (Live Tracking entry point)
package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentAdminSessionHistoryBinding
import com.fieldtrack.app.ui.UiState
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Displays the full attendance session history (open + closed) for a specific
 * employee, reached from the Live Tracking flow.
 *
 * Entry point:
 *   LiveTrackingFragment
 *     → tap employee card
 *     → EmployeeActionBottomSheet
 *     → "View Session History"
 *     → navigate via action_liveTracking_to_adminSessionHistory
 *
 * Arguments (SafeArgs — see nav_admin.xml adminSessionHistoryFragment):
 *   [AdminSessionHistoryFragmentArgs.userId]       Required UUID of the target employee.
 *   [AdminSessionHistoryFragmentArgs.employeeName] Optional display name for the header.
 *
 * ViewModel: [AdminViewModel] (activity-scoped).
 *   Observes [AdminViewModel.sessionHistory] : LiveData<UiState<List<AdminSession>>>
 *     UiState.Loading → spinner visible, list + empty-state hidden
 *     UiState.Success → list bound; empty-state shown when data is empty
 *     UiState.Error   → Snackbar with repository-mapped message
 *
 * Navigation out:
 *   Tap any session card → AdminSessionRouteFragment
 *   via action_adminSessionHistory_to_adminSessionRoute
 *   arg "session_id" = session.id  (matches AdminSessionRouteFragment.ARG_SESSION_ID)
 */
class AdminSessionHistoryFragment : Fragment() {

    private var _binding: FragmentAdminSessionHistoryBinding? = null
    private val binding get() = _binding!!

    // Activity-scoped so loadSessionHistory() result survives config changes
    // and is shared with other admin fragments without re-fetching.
    private val viewModel: AdminViewModel by activityViewModel()

    // SafeArgs: generated from nav_admin.xml <argument> declarations on
    // adminSessionHistoryFragment. Crashes at creation time with a clear
    // IllegalArgumentException if "userId" is missing — fail-fast is intentional.
    private val args: AdminSessionHistoryFragmentArgs by navArgs()

    private lateinit var adapter: AdminSessionHistoryAdapter

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSessionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        // Trigger load. The ViewModel guards against duplicate in-flight requests
        // implicitly: emitting Loading overwrites any prior state immediately.
        viewModel.loadSessionHistory(args.userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupHeader() {
        // employeeName has defaultValue="" in the nav graph so it is never null.
        binding.tvEmployeeName.text = args.employeeName.ifBlank { "Employee" }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = AdminSessionHistoryAdapter { session ->
            navigateToSessionRoute(session.id)
        }
        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSessionHistory(args.userId)
        }
    }

    // ── Observers ────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.sessionHistory.observe(viewLifecycleOwner) { state ->
            when (state) {

                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvSessions.visibility = View.GONE
                }

                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    adapter.submitList(state.data)

                    val isEmpty = state.data.isEmpty()
                    binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.rvSessions.visibility = if (isEmpty) View.GONE else View.VISIBLE

                    // Show count badge once data is available
                    val count = state.data.size
                    if (count > 0) {
                        binding.tvSessionCount.text = resources.getQuantityString(
                            com.fieldtrack.app.R.plurals.session_count,
                            count,
                            count
                        )
                        binding.tvSessionCount.visibility = View.VISIBLE
                    } else {
                        binding.tvSessionCount.visibility = View.GONE
                    }
                }

                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    /**
     * Navigate to [AdminSessionRouteFragment] to visualise the GPS route
     * recorded during this session.
     *
     * Uses SafeArgs Directions so the argument type is verified at compile time.
     *
     * The argument name in nav_admin.xml is "session_id" (underscore) to match
     * [AdminSessionRouteFragment.ARG_SESSION_ID]. SafeArgs exposes this as the
     * camelCase parameter [sessionId] in the generated Directions class but
     * writes the bundle key as "session_id", so the existing fragment reads it
     * without any modification.
     *
     * @param sessionId The UUID of the attendance session to render on the map.
     */
    private fun navigateToSessionRoute(sessionId: String) {
        val directions = AdminSessionHistoryFragmentDirections
            .actionAdminSessionHistoryToAdminSessionRoute(sessionId = sessionId)
        findNavController().navigate(directions)
    }
}
