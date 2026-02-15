// P2.1 - Admin Open Sessions
package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentOpenSessionsBinding
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Fragment displaying all currently open attendance sessions.
 * Admin-only view showing who is currently checked in.
 */
class OpenSessionsFragment : Fragment() {

    private var _binding: FragmentOpenSessionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: OpenSessionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOpenSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        // Load sessions on first view
        viewModel.loadOpenSessions()
    }

    private fun setupRecyclerView() {
        // P2.2 - Pass force check-out callback to adapter
        adapter = OpenSessionsAdapter { session ->
            showForceCheckOutDialog(session)
        }
        binding.rvOpenSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOpenSessions.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadOpenSessions()
        }
    }

    // P2.2 - Force Check-Out Dialog
    private fun showForceCheckOutDialog(session: com.fieldtrack.app.data.model.OpenSession) {
        val reasonInput = EditText(requireContext()).apply {
            hint = "Reason (optional)"
            setPadding(48, 24, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Force Check-Out")
            .setMessage("Close this session for ${session.name}?")
            .setView(reasonInput)
            .setPositiveButton("Confirm") { _, _ ->
                val reason = reasonInput.text.toString().trim().takeIf { it.isNotEmpty() }
                viewModel.forceCheckOut(session.userId, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.openSessions.observe(viewLifecycleOwner) { sessions ->
            adapter.submitList(sessions)

            val isEmpty = sessions.isEmpty()
            binding.tvNoSessions.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvOpenSessions.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        // P2.2 - Observe force check-out loading state
        viewModel.forceLoading.observe(viewLifecycleOwner) { loading ->
            // Disable swipe refresh while force check-out is in progress
            binding.swipeRefresh.isEnabled = !loading
        }

        // P2.2 - Observe force check-out errors
        viewModel.forceError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearForceError()
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
