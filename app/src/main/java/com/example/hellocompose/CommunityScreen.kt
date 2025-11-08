package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CommunityScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid
    val username = user?.email?.substringBefore("@") ?: "User"
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var newPost by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // live global feed (any users)
    DisposableEffect(Unit) {
        val reg = db.collection("communityPosts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { qs, _ ->
                posts = qs?.documents?.map { d ->
                    CommunityPost(
                        id = d.id,
                        userId = d.getString("userId") ?: "",
                        username = d.getString("username") ?: "User",
                        type = d.getString("type") ?: "TEXT",
                        text = d.getString("text"),
                        payload = (d.get("payload") as? Map<*, *>)?.entries
                            ?.associate { (k, v) -> k.toString() to v } ?: emptyMap(),
                        likesCount = d.getLong("likesCount") ?: 0,
                        commentsCount = d.getLong("commentsCount") ?: 0,
                        createdAt = d.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } ?: emptyList()
                loading = false
            }
        onDispose { reg.remove() }
    }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.COMMUNITY,
        snackbarHost = { SnackbarHost(snack) },
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Community", style = MaterialTheme.typography.headlineSmall)

            // Create box: text + quick share
            Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Share something", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = newPost,
                        onValueChange = { newPost = it },
                        label = { Text("Say hello…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = uid != null && newPost.isNotBlank() && !creating,
                            onClick = {
                                if (uid == null) return@Button
                                scope.launch {
                                    try {
                                        creating = true
                                        createTextPost(db, uid, username, newPost)
                                        newPost = ""
                                        snack.showSnackbar("Posted")
                                    } catch (e: Exception) {
                                        snack.showSnackbar("Failed: ${e.message}")
                                    } finally {
                                        creating = false
                                    }
                                }
                            }
                        ) { Text("Post") }

                        OutlinedButton(
                            enabled = uid != null && !creating,
                            onClick = {
                                if (uid == null) return@OutlinedButton
                                scope.launch {
                                    try {
                                        creating = true
                                        shareCurrentMeals(db, uid, username)
                                        snack.showSnackbar("Shared today's meals")
                                    } catch (e: Exception) {
                                        snack.showSnackbar("Failed: ${e.message}")
                                    } finally { creating = false }
                                }
                            }
                        ) { Text("Share meals") }

                        OutlinedButton(
                            enabled = uid != null && !creating,
                            onClick = {
                                if (uid == null) return@OutlinedButton
                                scope.launch {
                                    try {
                                        creating = true
                                        shareCurrentWorkout(db, uid, username)
                                        snack.showSnackbar("Shared today's workout")
                                    } catch (e: Exception) {
                                        snack.showSnackbar("Failed: ${e.message}")
                                    } finally { creating = false }
                                }
                            }
                        ) { Text("Share workout") }
                    }
                }
            }

            // Feed
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(posts, key = { it.id }) { p ->
                        PostCard(
                            post = p,
                            onLike = {
                                if (uid == null) return@PostCard
                                scope.launch { toggleLike(db, p.id, uid) }
                            },
                            onComment = { text ->
                                if (uid == null) return@PostCard
                                scope.launch { addComment(db, p.id, uid, username, text) }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ------------ UI bits ------------ */

@Composable
private fun PostCard(
    post: CommunityPost,
    onLike: () -> Unit,
    onComment: (String) -> Unit
) {
    var commentText by remember { mutableStateOf(TextFieldValue("")) }

    Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${post.username} • ${createdLabel(post.createdAt)}", style = MaterialTheme.typography.labelMedium)
            when (post.type) {
                "TEXT" -> Text(post.text ?: "", style = MaterialTheme.typography.bodyLarge)
                "MEALS" -> {
                    val kcal = (post.payload["kcal"] as? Number)?.toInt() ?: 0
                    val p = (post.payload["protein"] as? Number)?.toInt() ?: 0
                    val c = (post.payload["carbs"] as? Number)?.toInt() ?: 0
                    val f = (post.payload["fat"] as? Number)?.toInt() ?: 0
                    Text("Meals today • Kcal: $kcal • P:$p C:$c F:$f")
                }
                "WORKOUT" -> {
                    val kcal = (post.payload["kcal"] as? Number)?.toInt() ?: 0
                    val names = (post.payload["workouts"] as? List<*>)?.joinToString() ?: ""
                    Text("Workout calories: $kcal")
                    if (names.isNotBlank()) Text("Sessions: $names", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "PROGRESS" -> Text(post.text ?: "Progress update")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(onClick = onLike, label = { Text("❤️ ${post.likesCount}") })
                AssistChip(onClick = { /* could navigate to details */ }, label = { Text("💬 ${post.commentsCount}") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment…") }
                )
                Button(
                    onClick = {
                        val t = commentText.text.trim()
                        if (t.isNotEmpty()) {
                            onComment(t)
                            commentText = TextFieldValue("")
                        }
                    }
                ) { Text("Send") }
            }
        }
    }
}

/* ------------ data & helpers ------------ */

private data class CommunityPost(
    val id: String,
    val userId: String,
    val username: String,
    val type: String,
    val text: String?,
    val payload: Map<String, Any?>,
    val likesCount: Long,
    val commentsCount: Long,
    val createdAt: Long
)

private suspend fun createTextPost(db: FirebaseFirestore, uid: String, username: String, text: String) {
    val doc = mapOf(
        "userId" to uid,
        "username" to username,
        "type" to "TEXT",
        "text" to text,
        "payload" to emptyMap<String, Any>(),
        "createdAt" to System.currentTimeMillis(),
        "likesCount" to 0L,
        "commentsCount" to 0L
    )
    db.collection("communityPosts").add(doc).await()
}

private suspend fun shareCurrentMeals(db: FirebaseFirestore, uid: String, username: String) {
    val today = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()
    val logs = db.collection("users").document(uid)
        .collection("mealLogs")
        .whereEqualTo("dateEpochDay", today)
        .get().await().documents

    var kcal = 0; var p = 0; var c = 0; var f = 0
    for (d in logs) {
        kcal += (d.get("kcal") as? Number)?.toInt() ?: 0
        p    += (d.get("protein") as? Number)?.toInt() ?: 0
        c    += (d.get("carbs") as? Number)?.toInt() ?: 0
        f    += (d.get("fat") as? Number)?.toInt() ?: 0
    }

    val payload = mapOf("kcal" to kcal, "protein" to p, "carbs" to c, "fat" to f)
    createPost(db, uid, username, "MEALS", null, payload)
}

private suspend fun shareCurrentWorkout(db: FirebaseFirestore, uid: String, username: String) {
    val today = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()
    val logs = db.collection("users").document(uid)
        .collection("workoutLogs")
        .whereEqualTo("dateEpochDay", today)
        .get().await()
        .documents

    val totalKcal = logs.sumOf { (it.get("kcal") as? Number)?.toInt() ?: 0 } // numeric!
    val names = logs.mapNotNull { it.getString("name") }

    val payload = mapOf("kcal" to totalKcal, "workouts" to names)
    createPost(db, uid, username, "WORKOUT", null, payload)
}

private suspend fun createPost(
    db: FirebaseFirestore,
    uid: String,
    username: String,
    type: String,
    text: String?,
    payload: Map<String, Any?>
) {
    val doc = mapOf(
        "userId" to uid,
        "username" to username,
        "type" to type,
        "text" to text,
        "payload" to payload,
        "createdAt" to System.currentTimeMillis(),
        "likesCount" to 0L,
        "commentsCount" to 0L
    )
    db.collection("communityPosts").add(doc).await()
}

private suspend fun toggleLike(db: FirebaseFirestore, postId: String, uid: String) {
    val postRef = db.collection("communityPosts").document(postId)
    val likeRef = postRef.collection("likes").document(uid)

    db.runTransaction { tx ->
        val likeSnap = tx.get(likeRef)
        val postSnap = tx.get(postRef)
        val cur = postSnap.getLong("likesCount") ?: 0L

        if (likeSnap.exists()) {
            tx.delete(likeRef)
            tx.update(postRef, "likesCount", cur - 1)
        } else {
            tx.set(likeRef, mapOf("userId" to uid, "createdAt" to System.currentTimeMillis()))
            tx.update(postRef, "likesCount", cur + 1)
        }
    }.await()
}

private suspend fun addComment(
    db: FirebaseFirestore,
    postId: String,
    uid: String,
    username: String,
    text: String
) {
    val postRef = db.collection("communityPosts").document(postId)
    db.runTransaction { tx ->
        val commentsRef = postRef.collection("comments").document()
        tx.set(
            commentsRef,
            mapOf("userId" to uid, "username" to username, "text" to text, "createdAt" to System.currentTimeMillis())
        )
        val cur = (tx.get(postRef).getLong("commentsCount") ?: 0L) + 1
        tx.update(postRef, "commentsCount", cur)
    }.await()
}

private fun createdLabel(ms: Long): String {
    val inst = Instant.ofEpochMilli(ms)
    val local = inst.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return DateTimeFormatter.ofPattern("MMM d, HH:mm").format(local)
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result) {}
        else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
    }
}
