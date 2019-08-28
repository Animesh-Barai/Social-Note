package elamien.abdullah.socialnote.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import elamien.abdullah.socialnote.models.Comment
import elamien.abdullah.socialnote.models.Post
import elamien.abdullah.socialnote.utils.Constants
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by AbdullahAtta on 26-Aug-19.
 */
class PostRepository : IPostRepository, KoinComponent {


	private val mFirestore : FirebaseFirestore by inject()
	override fun createComment(documentName : String, comment : Comment) {
		mFirestore.collection(Constants.FIRESTORE_POSTS_COLLECTION_NAME)
				.document(documentName)
				.update(Constants.FIRESTORE_POSTS_POST_COMMENTS, FieldValue.arrayUnion(comment))
				.addOnCompleteListener { }
				.addOnFailureListener { }

		mFirestore.collection(Constants.FIRESTORE_COMMENTS_NOTIFICATION_COLLECTION_NAME)
				.document()
				.set(getMappedComment(comment))
				.addOnCompleteListener { }
				.addOnFailureListener { }
	}

	private fun getMappedComment(comment : Comment) : HashMap<String, Any> {
		val commentMap = HashMap<String, Any>()
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_AUTHOR_REGISTER_TOKEN] =
			comment.authorRegisterToken!!
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_COMMENT] = comment.comment!!
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_AUTHOR_NAME] = comment.authorName!!
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_AUTHOR_IMAGE] = comment.authorImage!!
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_AUTHOR_UID] = comment.authorUId!!
		commentMap[Constants.FIRESTORE_COMMENTS_NOTIFICATION_DATE_CREATED] =
			comment.getDateCreated()
		return commentMap
	}

	override fun createNewPost(post : Post) {
		val documentName = "${post.authorUId}${Date().time}"
		post.documentName = documentName
		mFirestore.collection(Constants.FIRESTORE_POSTS_COLLECTION_NAME)
				.document(documentName)
				.set(getMappedPost(post), SetOptions.merge())
				.addOnCompleteListener { }
				.addOnFailureListener {}
	}

	private fun getMappedPost(post : Post) : HashMap<String, Any> {
		val postMap = HashMap<String, Any>()
		postMap[Constants.FIRESTORE_POSTS_POST_BODY] = post.post!!
		postMap[Constants.FIRESTORE_POSTS_POST_AUTHOR_NAME] = post.authorName!!
		postMap[Constants.FIRESTORE_POSTS_POST_AUTHOR_ID] = post.authorUId!!
		postMap[Constants.FIRESTORE_POSTS_POST_AUTHOR_IMAGE] = post.authorImage!!
		postMap[Constants.FIRESTORE_POSTS_POST_CATEGORY_NAME] = post.categoryName!!
		postMap[Constants.FIRESTORE_POSTS_POST_DATE_CREATED] = Date()
		postMap[Constants.FIRESTORE_POSTS_POST_DOC_NAME] = post.documentName!!
		postMap[Constants.FIRESTORE_POSTS_POST_REGISTER_TOKEN] = post.registerToken!!

		return postMap
	}

	override fun getPostsFeed() : LiveData<List<Post>> {
		val posts = MutableLiveData<List<Post>>()
		mFirestore.collection(Constants.FIRESTORE_POSTS_COLLECTION_NAME)
				.get()
				.addOnCompleteListener { querySnapshot ->
					if (querySnapshot.result != null) {
						val documents = ArrayList<Post>()
						querySnapshot.result?.forEach { document ->
							documents.add(document.toObject(Post::class.java))
						}
						posts.value = documents
					}
				}
		return posts
	}

	override fun getCommentsFeed(documentName : String) : LiveData<List<Comment>> {
		val comments = MutableLiveData<List<Comment>>()
		mFirestore.collection(Constants.FIRESTORE_POSTS_COLLECTION_NAME)
				.document(documentName)
				.addSnapshotListener { snapshot, e ->
					if (e != null) {

					} else {
						val commentList = ArrayList<Comment>()
						val post = snapshot?.toObject(Post::class.java)
						post?.comments?.forEach { comment ->
							if (!comment.isCommentEmpty()) {
								commentList.add(comment)
							}
						}
						comments.value = commentList
					}
				}
		return comments
	}

}