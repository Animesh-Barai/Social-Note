package playground.develop.socialnote.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.preference.PreferenceManager
import coil.api.load
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.miguelcatalan.materialsearchview.MaterialSearchView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import playground.develop.socialnote.R
import playground.develop.socialnote.adapter.PagedNoteListAdapter
import playground.develop.socialnote.database.local.notes.Note
import playground.develop.socialnote.database.remote.firestore.models.User
import playground.develop.socialnote.databinding.ActivityHomeBinding
import playground.develop.socialnote.databinding.EmptyHeaderBinding
import playground.develop.socialnote.databinding.NavHeaderLayoutBinding
import playground.develop.socialnote.services.SyncingService
import playground.develop.socialnote.utils.Constants
import playground.develop.socialnote.utils.Constants.Companion.AUTHOR_TITLE
import playground.develop.socialnote.utils.Constants.Companion.CONSIDER_REGISTER_KEY
import playground.develop.socialnote.utils.Constants.Companion.READER_TITLE
import playground.develop.socialnote.viewmodel.NoteViewModel
import playground.develop.socialnote.viewmodel.PostViewModel
import java.util.concurrent.TimeUnit


class HomeActivity : AppCompatActivity(), MaterialSearchView.OnQueryTextListener,
                     NavigationView.OnNavigationItemSelectedListener,
                     PagedNoteListAdapter.LongClickListener {

    private lateinit var adapter: PagedNoteListAdapter
    private lateinit var mBinding: ActivityHomeBinding
    private val mViewModel: NoteViewModel by inject()
    private val mFirebaseAuth: FirebaseAuth by inject()
    private val mDisposables = CompositeDisposable()
    private var isSyncingEnabled = false
    private val mPostViewModel: PostViewModel by viewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        mBinding.handlers = this
        setupToolbar()
        setupNavDrawer()
        adapter = PagedNoteListAdapter(this@HomeActivity, this@HomeActivity)
        setupSyncing()
        loadNotes()
        setupSearchView()
    }

    private fun setupSyncing() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this@HomeActivity)
        isSyncingEnabled = settings.getBoolean(getString(R.string.note_sync_key), false)
    }

    private fun setupNavDrawer() {
        val toggle = actionBarDrawerToggle
        mBinding.drawerLayout.addDrawerListener(toggle)
        mBinding.navigationView.setNavigationItemSelectedListener(this@HomeActivity)
        toggle.syncState()
        setupUserHeader()
    }

    private fun setupUserHeader() {
        if (mFirebaseAuth.currentUser != null) {
            mPostViewModel.getUser().observe(this@HomeActivity, Observer { user ->
                showUserHeader(user)
            })
        } else {
            showEmptyHeader()
        }
    }

    private fun showEmptyHeader() {
        val navHeaderBinding: EmptyHeaderBinding = DataBindingUtil
                .inflate(layoutInflater, R.layout.empty_header, mBinding.navigationView, false)
        mBinding.navigationView.addHeaderView(navHeaderBinding.root)
        navHeaderBinding.handlers = this
        navHeaderBinding.emptyHeaderView.load(R.drawable.register_background) {
            crossfade(true)
        }
    }

    fun onSigninClick(view: View) {
        startActivity(intentFor<RegisterActivity>(CONSIDER_REGISTER_KEY to CONSIDER_REGISTER_KEY))
    }

    private fun showUserHeader(user: User?) {
        val navHeaderBinding: NavHeaderLayoutBinding = DataBindingUtil
                .inflate(layoutInflater, R.layout.nav_header_layout, mBinding.navigationView, false)
        mBinding.navigationView.addHeaderView(navHeaderBinding.root)
        navHeaderBinding.user = user!!
        navHeaderBinding.handlers = this
        showUserTitle(user, navHeaderBinding)
    }

    private fun showUserTitle(user: User, navHeaderBinding: NavHeaderLayoutBinding) {
        when (user.userTitle) {
            READER_TITLE -> showReaderTitle(navHeaderBinding)
            AUTHOR_TITLE -> showAuthorTitle(navHeaderBinding)
        }
    }

    private fun showAuthorTitle(navHeaderBinding: NavHeaderLayoutBinding) {
        navHeaderBinding.navHeaderUserAuthorTitle.visibility = View.VISIBLE
    }

    private fun showReaderTitle(navHeaderBinding: NavHeaderLayoutBinding) {
        navHeaderBinding.navHeaderUserReaderTitle.visibility = View.VISIBLE
    }

    fun onProfileImageClick(view: View) {
        startUserProfileActivity()
    }

    private fun startUserProfileActivity() {
        val userUid = mFirebaseAuth.currentUser?.uid
        startActivity(intentFor<ProfileActivity>(Constants.USER_UID_INTENT_KEY to userUid))
    }

    private val actionBarDrawerToggle: ActionBarDrawerToggle
        get() {
            mBinding.navigationView.itemIconTintList = null
            setSupportActionBar(mBinding.toolbar)
            return object : ActionBarDrawerToggle(this,
                                                  mBinding.drawerLayout,
                                                  mBinding.toolbar,
                                                  R.string.nav_drawer_open,
                                                  R.string.nav_drawer_close) {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    super.onDrawerSlide(drawerView, slideOffset)

                    val moveFactor = mBinding.drawerLayout.width * slideOffset
                    mBinding.homeContainer.translationX = moveFactor
                    super.onDrawerSlide(drawerView, slideOffset)
                }
            }
        }

    private fun setupToolbar() {
        mBinding.toolbar.setNavigationIcon(R.drawable.ic_home_nav)
        setSupportActionBar(mBinding.toolbar)
        title = getString(R.string.app_name)
    }

    private fun loadNotes() {
        mViewModel.loadPagedNotes().observe(this, Observer<PagedList<Note>> { list ->
            when {
                list.isNotEmpty() -> {
                    addNotesToRecyclerView(list)
                }
                isSyncingEnabled -> {
                    hideRecyclerView()
                    getSyncedNotes()
                }
                else -> hideRecyclerView()
            }
        })
    }

    private fun getSyncedNotes() {
        val syncService = Intent(this@HomeActivity, SyncingService::class.java)
        syncService.action = Constants.SYNC_CALL_NOTES_POPULATE_ROOM_INTENT_ACTION
        SyncingService.getSyncingService().enqueueCallSyncedNotes(this@HomeActivity, syncService)
    }

    private fun addNotesToRecyclerView(list: PagedList<Note>) {
        showRecyclerView()
        adapter.submitList(list)
        mBinding.notesRecyclerView.adapter = adapter
    }

    private fun hideRecyclerView() {
        mBinding.notesRecyclerView.visibility = View.GONE
        mBinding.noteEmptyStateLayout.visibility = View.VISIBLE
    }


    private fun showRecyclerView() {
        mBinding.lottieAnimationView.pauseAnimation()
        mBinding.noteEmptyStateLayout.visibility = View.GONE
        mBinding.notesRecyclerView.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        val searchItem = menu!!.findItem(R.id.searchMenuItem)
        mBinding.searchView.setMenuItem(searchItem)
        return super.onCreateOptionsMenu(menu)
    }

    fun onNewNoteFabClick(view: View) {
        startActivity(intentFor<AddEditNoteActivity>())
    }

    override fun onLongClickListener(note: Note) {
        if (isSyncingEnabled) {
            startDeletingService(note.id!!)
        }
        mViewModel.deleteNote(note)
    }

    private fun startDeletingService(id: Long) {
        val syncService = Intent(this@HomeActivity, SyncingService::class.java)
        syncService.action = Constants.SYNC_DELETE_NOTE_INTENT_ACTION
        syncService.putExtra(Constants.SYNC_NOTE_ID_INTENT_KEY, id)
        SyncingService.getSyncingService().enqueueSyncDeleteNote(this@HomeActivity, syncService)
    }

    private fun setupSearchView() {
        mBinding.searchView.setOnQueryTextListener(this@HomeActivity)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val searchSubject = BehaviorSubject.create<String>()
        searchSubject.onNext(newText!!)
        mDisposables.add(searchSubject.debounce(700,
                                                TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe { query ->
            searchNotes(query)
        })
        return false
    }

    private fun searchNotes(query: String?) {
        mViewModel.searchForNote("%$query%")
                .observe(this@HomeActivity, Observer<PagedList<Note>> { list ->
                    if (list.isNotEmpty()) {
                        applySearchResults(list)
                    }
                })
    }

    private fun applySearchResults(list: PagedList<Note>) {
        adapter.submitList(list)
    }

    override fun onStop() {
        mDisposables.dispose()
        super.onStop()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settingsMenuItem -> openSettingsActivity()
            R.id.feedMenuItem -> if (userIsLoggedIn()) {
                openFeedActivity()
            } else {
                toast("You have to login")
            }
        }
        return true
    }

    private fun userIsLoggedIn(): Boolean {
        return mFirebaseAuth.currentUser != null
    }

    private fun openFeedActivity() {
        startActivity(intentFor<FeedActivity>())
    }

    private fun openSettingsActivity() {
        startActivity(intentFor<SettingsActivity>())
    }

    override fun onBackPressed() {
        when {
            mBinding.searchView.isSearchOpen -> mBinding.searchView.closeSearch()
            mBinding.drawerLayout.isDrawerOpen(GravityCompat.START) -> mBinding.drawerLayout.closeDrawer(
                GravityCompat.START)
            else -> super.onBackPressed()
        }
    }

}
