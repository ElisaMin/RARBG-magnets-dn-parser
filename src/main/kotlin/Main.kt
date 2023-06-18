import kotlinx.coroutines.*
import java.io.File
import kotlin.concurrent.thread

fun getFile(s:String) = File("../bts/rarbg-main/${s}.txt")
suspend fun toMagnetsDB(dist:String,vararg files: File):CoroutineScope {
    fun sequence() =
        files.map { it.bufferedReader().lineSequence() }
            .asSequence()
            .flatten()
    return coroutineScope {
        launch {
            val count = sequence().count()
            displayingProgress(count)
        }
        launch {
            sequence().toMagnetsDB(
                db("dist/$dist"),
                File("dist/$dist.yaml").bufferedWriter()
            )
        }
        this
    }
}
@OptIn(FlowPreview::class)
suspend fun main() {
    toMagnetsDB("mas.hash.id",
        getFile("moviesrarbg"),
        getFile("showsrarbg")
    ).coroutineContext[Job]!!.join()
}
