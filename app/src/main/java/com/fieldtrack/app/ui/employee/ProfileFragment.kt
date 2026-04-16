package com.fieldtrack.app.ui.employee

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import coil.load
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentProfileBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * ProfileFragment - Allows employees to view and update their profile information
 * including profile image, name, email, and phone.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmployeeHomeViewModel by viewModel()
    
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImagePreview(it)
            binding.btnSaveImage.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
        viewModel.loadUserProfile()
    }

    private fun setupUI() {
        binding.btnChangeImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnSaveImage.setOnClickListener {
            selectedImageUri?.let { uri ->
                viewModel.uploadProfileImage(requireContext(), uri)
            }
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            requireActivity().finish()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.tvUserName.text = it.name
                binding.tvUserEmail.text = it.email ?: "No email"
                binding.tvUserPhone.text = it.phone ?: "No phone"
                binding.tvUserRole.text = it.role.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                
                // Load profile image
                if (!it.profileImageUrl.isNullOrBlank()) {
                    binding.ivProfileImage.load(it.profileImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.bg_image_placeholder)
                        error(R.drawable.bg_image_placeholder)
                        addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                        addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    }
                } else {
                    binding.ivProfileImage.setImageResource(R.drawable.bg_image_placeholder)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnChangeImage.isEnabled = !loading
            binding.btnSaveImage.isEnabled = !loading
        }

        viewModel.uploadSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                requireContext().showToast("Profile image updated successfully")
                binding.btnSaveImage.visibility = View.GONE
                selectedImageUri = null
                viewModel.loadUserProfile() // Reload to show new image
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                requireContext().showToast(it)
                viewModel.clearError()
            }
        }
    }

    private fun showImagePreview(uri: Uri) {
        binding.ivProfileImage.load(uri) {
            crossfade(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
