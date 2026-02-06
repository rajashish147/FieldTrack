package com.fieldtrack.app.ui.report

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentReportBinding
import com.fieldtrack.app.util.showSnackbar
import com.fieldtrack.app.util.showToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportViewModel by viewModel()
    private lateinit var reportAdapter: ReportAdapter

    private var selectedImageUri: Uri? = null
    private var dialogPreview: ImageView? = null
    private var dialogRemoveText: TextView? = null
    private var dialogAttachButton: MaterialButton? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            dialogPreview?.let { iv ->
                iv.visibility = View.VISIBLE
                iv.load(it) { crossfade(true) }
            }
            dialogRemoveText?.visibility = View.VISIBLE
            dialogAttachButton?.text = "Change Image"
        }
    }

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
        viewModel.checkTodayReport()
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
        selectedImageUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_report, null)
        val etSummary = dialogView.findViewById<TextInputEditText>(R.id.et_summary)
        val btnAttach = dialogView.findViewById<MaterialButton>(R.id.btn_attach_image)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.iv_preview)
        val tvRemove = dialogView.findViewById<TextView>(R.id.tv_remove_image)

        dialogPreview = ivPreview
        dialogRemoveText = tvRemove
        dialogAttachButton = btnAttach

        btnAttach.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        tvRemove.setOnClickListener {
            selectedImageUri = null
            ivPreview.visibility = View.GONE
            tvRemove.visibility = View.GONE
            btnAttach.text = "Attach Image (Optional)"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Submit Daily Report")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val summary = etSummary.text.toString().trim()
                if (summary.isNotEmpty()) {
                    viewModel.submitReport(requireContext(), summary, selectedImageUri)
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

        viewModel.hasReportToday.observe(viewLifecycleOwner) { hasReport ->
            binding.fabAddReport.isEnabled = !hasReport
            if (hasReport) {
                binding.fabAddReport.alpha = 0.5f
            } else {
                binding.fabAddReport.alpha = 1.0f
            }
        }

        viewModel.submitResult.observe(viewLifecycleOwner) { success ->
            success?.let {
                if (it) {
                    requireContext().showToast("Report submitted")
                } else {
                    requireContext().showToast("Failed to submit report")
                }
                viewModel.clearSubmitResult()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.fabAddReport.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                binding.root.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialogPreview = null
        dialogRemoveText = null
        dialogAttachButton = null
        _binding = null
    }
}
