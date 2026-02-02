package com.fieldtrack.app.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentReportBinding
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.fieldtrack.app.R
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by viewModel()
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupUI()
        observeViewModel()
        viewModel.loadReports()
    }

    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter()
        binding.rvReports.apply {
            adapter = reportAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupUI() {
        binding.fabAddReport.setOnClickListener {
            showAddReportDialog()
        }
    }

    private fun showAddReportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_report, null)
        val etSummary = dialogView.findViewById<TextInputEditText>(R.id.et_summary)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Submit Daily Report")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val summary = etSummary.text.toString().trim()
                if (summary.isNotEmpty()) {
                    viewModel.submitReport(summary)
                } else {
                    requireContext().showToast("Please enter a summary")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.reports.observe(viewLifecycleOwner) { reports ->
            reportAdapter.submitList(reports)
            binding.tvNoReports.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.submitResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                requireContext().showToast("Report submitted")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
