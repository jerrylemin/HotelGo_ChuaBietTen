package com.example.hotelapp_test2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hotelapp_test2.core.FeatureRegistry
import com.example.hotelapp_test2.core.FeatureRole
import com.example.hotelapp_test2.data.SeedData
import com.example.hotelapp_test2.data.SessionManager
import com.example.hotelapp_test2.data.SupabaseRepository
import com.example.hotelapp_test2.ui.BaseActivity
import com.example.hotelapp_test2.ui.FeatureAdapter
import com.example.hotelapp_test2.ui.GridSpacingItemDecoration
import com.example.hotelapp_test2.ui.auth.AuthActivity
import com.example.hotelapp_test2.ui.features.ProfileActivity
import com.example.hotelapp_test2.ui.features.RecommendationPosterActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : BaseActivity() {
    private lateinit var featureAdapter: FeatureAdapter
    private var currentRole: FeatureRole = FeatureRole.CLIENT
    private var queryText: String = ""
    private lateinit var headerTitle: TextView
    private lateinit var highlightCard: MaterialCardView

    override fun onStart() {
        super.onStart()
        val user = SupabaseRepository.currentUser()
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        } else {
            SeedData.seedIfNeeded()
            refreshUserProfile()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar(R.string.app_name, R.string.main_toolbar_subtitle, showBack = false)

        headerTitle = findViewById(R.id.headerTitle)
        highlightCard = findViewById(R.id.highlightCard)

        featureAdapter = FeatureAdapter { feature ->
            startActivity(Intent(this, feature.activityClass))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.featureRecycler)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = featureAdapter
        val spacing = resources.getDimensionPixelSize(R.dimen.space_m)
        recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, true))

        highlightCard.setOnClickListener {
            startActivity(Intent(this, RecommendationPosterActivity::class.java))
        }

        val searchEdit = findViewById<TextInputEditText>(R.id.searchEdit)
        searchEdit.addTextChangedListener { text ->
            queryText = text?.toString().orEmpty()
            applyFilters()
        }

        val profileButton = findViewById<MaterialButton>(R.id.profileButton)
        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val logoutButton = findViewById<MaterialButton>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            SupabaseRepository.signOut()
            SessionManager.clear(this)
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }

        applyFilters()
    }

    private fun applyFilters() {
        val filtered = FeatureRegistry.items.filter { item ->
            val matchesRole = item.roles.contains(currentRole)
            val title = getString(item.titleRes)
            val subtitle = getString(item.subtitleRes)
            val matchesQuery = queryText.isBlank() ||
                title.contains(queryText, ignoreCase = true) ||
                subtitle.contains(queryText, ignoreCase = true)
            matchesRole && matchesQuery
        }
        featureAdapter.submitList(filtered)
    }

    private fun refreshUserProfile() {
        val user = SupabaseRepository.currentUser() ?: return
        val userId = user.uid
        SupabaseRepository.fetchUserProfile(
            userId = userId,
            onSuccess = { profile ->
                val name = profile?.name?.trim().orEmpty()
                val displayName = when {
                    name.isNotBlank() -> name
                    user.displayName.isNotBlank() -> user.displayName
                    user.email.isNotBlank() -> user.email.substringBefore("@")
                    else -> getString(R.string.main_guest_name)
                }
                headerTitle.text = getString(R.string.main_header_named, displayName)
                val roleFromProfile = profile?.role.orEmpty()
                val role = if (roleFromProfile.isNotBlank()) roleFromProfile else SessionManager.getRole(this)
                SessionManager.setUser(this, userId, role)
                val resolvedRole = SessionManager.getRole(this)
                currentRole = if (resolvedRole == "admin") FeatureRole.ADMIN else FeatureRole.CLIENT
                updateRoleUi()
            },
            onError = {
                headerTitle.text = getString(R.string.main_header_default)
                val role = SessionManager.getRole(this)
                currentRole = if (role == "admin") FeatureRole.ADMIN else FeatureRole.CLIENT
                updateRoleUi()
            }
        )
    }

    private fun updateRoleUi() {
        highlightCard.visibility = if (currentRole == FeatureRole.ADMIN) View.VISIBLE else View.GONE
        applyFilters()
    }
}
