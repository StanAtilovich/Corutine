import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private val gson = Gson()
private const val BASE_URL = "http://localhost:9999/api/slow"

private val client = OkHttpClient.Builder()//2
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()
fun main() {
    CoroutineScope(EmptyCoroutineContext).launch {
        try {
            val posts = getPosts()
           // val authors = getAuthor()

            val result = posts.map {
                async {
                    PostWithComments(it, getComments(it.id))
                }

                async {
                    PostWithAuthor(it,getAuthor(it.id))
                }

                async {
                    CommentWithAuthor(it,getAuthor(it.id))
                }


            }.awaitAll()

            println(result)
        } catch (e: IOException){
            e.printStackTrace()
        }
    }
    Thread.sleep(1_000)
}

suspend fun <T> parseResponse(url: String, typeToken: TypeToken<T>): T {//4
    val response = makeRequest(url)
    return withContext(Dispatchers.Default) {
        gson.fromJson(requireNotNull(response.body).string(), typeToken.type)
    }
}

suspend fun getPosts(): List<Post> = parseResponse(
    "${BASE_URL}/posts",
    object : TypeToken<List<Post>>() {},
)


suspend fun getComments(id: Long): List<Comment> = parseResponse(
    "${BASE_URL}/posts/$id/comments",
    object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(id: Long): Author = parseResponse(
    "${BASE_URL}/authors/$id",
   object :TypeToken<Author>(){}
)


suspend fun makeRequest(url: String): Response =//3
    suspendCoroutine { continuation ->
        client.newCall(
            Request.Builder()
                .url(url)
                .build()
        )
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

            })
    }
