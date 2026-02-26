// v1.4 - Admin Session History
package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.AdminSession
import com.fieldtrack.app.databinding.FragmentEmployeeSessionHistoryBinding
import com.fieldtrack.app.util.showToast
import com.google.android.material.snackbar.Snackbar
import com.fieldtrack.app.ui.UiState
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * v1.4 - Admin Session History
 *
 * Displays the full attendance session history (open + closed) for a specific
 * employee, retrieved via the admin_list_sessions_for_user() RPC.
 *
 * Entry point: [EmployeeDetailsBottomSheet] — tapping "View Session History"
 * on an employee navigates here via [R.id.action_employeeList_to_sessionHistory].
 *
 * Arguments:
 *   - [ARG_USER_ID]       Required. The target employee's UUID.
 *   - [ARG_EMPLOYEE_NAME] Optional. Displayed in the header; defaults to empty.
 *
 * Navigation out:
 *   - "View Route" on any session row →
 *     [R.id.action_sessionHistory_to_sessionRoute] with:
 *       "sessionId" = session.id   (the attendance session UUID)
 *       "userId"    = userId       (the employee's UUID — admin fix for route access)
 *
 * ViewModel: [AdminViewModel] (activity-scoped, shared with other admin fragments).
 *   Observed: sessionHistory: LiveData<UiState<List<AdminSession>>>
 *     - UiState.Loading → shows ProgressBar, hides list and empty state
 *     - UiState.Success → binds list to adapter; empty-state shown when data is empty
 *     - UiState.Error   → shows Snackbar with the repository-mapped error message
 */
class EmployeeSessionHistoryFragment : Fragment() {

    private var _binding: FragmentEmployeeSessionHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: AdminSessionAdapter

    // Arguments
    private var userId: String = ""
    private var employeeName: String = ""

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID) ?: ""
        employeeName = arguments?.getString(ARG_EMPLOYEE_NAME) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeSessionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRecyclerView()
        setupSwipeRefresh()
        setupBackButton()
        observeViewModel()

        // Trigger initial load (guard: userId must be non-empty)
        if (userId.isNotBlank()) {
            viewModel.loadSessionHistory(userId)
        } else {
            // Defensive: should never happen in normal navigation flows
            requireContext().showToast("Unable to load session history: missing user ID")
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupHeader() {
        binding.tvEmployeeName.text = employeeName.ifBlank { "Employee" }
    }

    private fun setupRecyclerView() {
        adapter = AdminSessionAdapter(
            onViewRouteClick = { session -> navigateToSessionRoute(session) }
        )
        binding.rvSessionHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessionHistory.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (userId.isNotBlank()) {
                viewModel.loadSessionHistory(userId)
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ── Observers ────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.sessionHistory.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvSessionHistory.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(state.data)
                    updateEmptyState(state.data)
                    updateSessionCountBadge(state.data.size)
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    /**
     * Toggle the empty-state view based on the current session list size.
     * Only called from [UiState.Success], so loading is already false by definition.
     */
    private fun updateEmptyState(sessions: List<AdminSession>) {
        val isEmpty = sessions.isEmpty()
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvSessionHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** Show the count badge once we have results ("12 sessions"). */
    private fun updateSessionCountBadge(count: Int) {
        if (count > 0) {
            binding.tvSessionCount.text = resources.getQuantityString(
                R.plurals.session_count,
                count,
                count
            )
            binding.tvSessionCount.visibility = View.VISIBLE
        } else {
            binding.tvSessionCount.visibility = View.GONE
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    /**
     * Navigate to [SessionRouteFragment] to visualise the route tracked during
     * this session.
     *
     * Passes:
     *   "sessionId" = session.id — the attendance session UUID used to query
     *                              location points from the server.
     *   "userId"    = userId     — the employee's UUID so the admin can fetch
     *                              that employee's location data (admin fix).
     *
     * Both IDs are guaranteed non-null/non-blank at this point; the RPC would
     * have returned an error before we rendered the list otherwise.
     */
    private fun navigateToSessionRoute(session: AdminSession) {
        val bundle = bundleOf(
            "sessionId" to session.id,
            "userId"    to userId        // Admin fix: pass employee userId for route access
        )
        findNavController().navigate(
            R.id.action_sessionHistory_to_sessionRoute,
            bundle
        )
    }

    // ── Companion ────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "EmployeeSessionHistoryFragment"

        /** Required argument: the target employee's UUID. */
        const val ARG_USER_ID = "userId"

        /** Optional argument: the employee's display name for the header. */
        const val ARG_EMPLOYEE_NAME = "employeeName"
    }
}
