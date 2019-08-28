package elamien.abdullah.socialnote.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.iid.FirebaseInstanceId
import elamien.abdullah.socialnote.R
import elamien.abdullah.socialnote.databinding.ActivityCreatePostBinding
import elamien.abdullah.socialnote.models.Post
import elamien.abdullah.socialnote.viewmodel.PostViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener

class CreatePostActivity : AppCompatActivity(), IAztecToolbarClickListener {

	private lateinit var mBinding : ActivityCreatePostBinding
	private val mFirebaseAuth : FirebaseAuth by inject()
	private val mPostViewModel : PostViewModel by viewModel()
	var mRegisterToken : String? = null
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		mBinding =
			DataBindingUtil.setContentView(this@CreatePostActivity, R.layout.activity_create_post)
		mBinding.handlers = this
		Aztec.with(mBinding.aztec, mBinding.source, mBinding.formattingToolbar, this)
		getRegisterToken()
	}

	fun onCreatePostButtonClick(view : View) {
		val body = mBinding.aztec.toFormattedHtml()
		val authorId = mFirebaseAuth.currentUser?.uid
		val authorImage = mFirebaseAuth.currentUser?.photoUrl.toString()
		val categoryName = ""
		val authorName = mFirebaseAuth.currentUser?.displayName
		val post = Post(mRegisterToken, body, authorName, categoryName, authorId, authorImage)
		mPostViewModel.createPost(post)
		finish()
	}

	private fun getRegisterToken() {
		FirebaseInstanceId.getInstance()
				.instanceId.addOnSuccessListener { instanceIdResult ->
			mRegisterToken = instanceIdResult.token
			Toast.makeText(this@CreatePostActivity, instanceIdResult.token, Toast.LENGTH_LONG)
					.show()
		}
	}

	override fun onToolbarHtmlButtonClicked() {
	}

	override fun onToolbarListButtonClicked() {
	}

	override fun onToolbarMediaButtonClicked() : Boolean {
		return false
	}

	override fun onToolbarCollapseButtonClicked() {
	}

	override fun onToolbarExpandButtonClicked() {
	}

	override fun onToolbarFormatButtonClicked(format : ITextFormat, isKeyboardShortcut : Boolean) {
	}

	override fun onToolbarHeadingButtonClicked() {
	}

}
