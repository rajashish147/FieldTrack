package com.fieldtrack.app.ui.employee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentEmployeeExpenseBinding

/**
 * EmployeeExpenseFragment — employee expense submission and history.
 *
 * TODO: Implement expense submission form (amount, category, bill image)
 * TODO: Show expense history with status (pending/approved/rejected)
 * TODO: Integrate camera for bill image capture
 * TODO: Upload bill images to Supabase Storage
 */
class EmployeeExpenseFragment : Fragment() {

    private var _binding: FragmentEmployeeExpenseBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
