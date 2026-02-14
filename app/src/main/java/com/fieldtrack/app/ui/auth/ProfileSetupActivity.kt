package com.fieldtrack.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.fieldtrack.app.databinding.ActivityProfileSetupBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * ProfileSetupActivity - Allows new employees to upload a profile/verification image
 * after signup. This is optional but recommended for admin verification.
 */
class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private val viewModel: ProfileSetupViewModel by viewModel()
    
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImagePreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.tvRemoveImage.setOnClickListener {
            selectedImageUri = null
            binding.ivPreview.visibility = View.GONE
            binding.tvRemoveImage.visibility = View.GONE
            binding.tvImageHint.visibility = View.VISIBLE
        }

        binding.btnSkip.setOnClickListener {
            navigateToPendingApproval()
        }

        binding.btnSubmit.setOnClickListener {
            if (selectedImageUri != null) {
                viewModel.uploadProfileImage(this, selectedImageUri!!)
            } else {
                showToast("Please select an image or skip")
            }
        }
    }

    private fun showImagePreview(uri: Uri) {
        binding.ivPreview.visibility = View.VISIBLE
        binding.tvRemoveImage.visibility = View.VISIBLE
        binding.tvImageHint.visibility = View.GONE
        
        binding.ivPreview.load(uri) {
            crossfade(true)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            setLoadingState(loading)
        }

        viewModel.uploadSuccess.observe(this) { success ->
            if (success) {
                showToast("Profile image uploaded successfully")
                navigateToPendingApproval()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSelectImage.isEnabled = !loading
        binding.btnSubmit.isEnabled = !loading
        binding.btnSkip.isEnabled = !loading
    }

    private fun navigateToPendingApproval() {
        val intent = Intent(this, PendingApprovalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
