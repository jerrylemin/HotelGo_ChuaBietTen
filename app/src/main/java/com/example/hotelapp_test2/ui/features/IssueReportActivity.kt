package com.example.hotelapp_test2.ui.features

import android.os.Bundle
import com.example.hotelapp_test2.R
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.data.model.IssueReport
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class IssueReportActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue_report)
        setupToolbar(R.string.issue_title, R.string.toolbar_issue_subtitle)
        if (!requireRole("client")) return

        val roomCodeInput = findViewById<TextInputEditText>(R.id.issueRoomCode)
        val typeInput = findViewById<TextInputEditText>(R.id.issueType)
        val descriptionInput = findViewById<TextInputEditText>(R.id.issueDescription)
        val submitButton = findViewById<MaterialButton>(R.id.issueSubmitButton)

        submitButton.setOnClickListener {
            val userId = SupabaseRepository.currentUser()?.uid.orEmpty()
            if (userId.isBlank()) {
                toast(getString(R.string.error_login_required))
                return@setOnClickListener
            }
            val roomCode = roomCodeInput.text?.toString().orEmpty().trim()
            val type = typeInput.text?.toString().orEmpty().trim()
            val description = descriptionInput.text?.toString().orEmpty().trim()
            if (type.isBlank() || description.isBlank()) {
                toast(getString(R.string.error_issue_required))
                return@setOnClickListener
            }
            val issue = IssueReport(
                userId = userId,
                roomId = roomCode,
                title = type,
                description = description,
                status = "open"
            )
            SupabaseRepository.createIssue(
                issue = issue,
                onSuccess = {
                    toast(getString(R.string.success_issue_sent))
                    typeInput.setText("")
                    descriptionInput.setText("")
                },
                onError = { error ->
                    toast(getString(R.string.error_issue_send, error.message.orEmpty()))
                }
            )
        }
    }
}
