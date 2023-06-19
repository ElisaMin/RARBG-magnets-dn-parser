import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

fun getFile(s:String) = File("../bts/rarbg-main/${s}.txt")
suspend fun toMagnetsDB(dist:String,vararg files: File):CoroutineScope {
    fun sequence() =
        files.map { it.bufferedReader().lineSequence() }
            .asSequence()
            .flatten()
    return coroutineScope {
//        launch {
//            val count = sequence().count()
//            displayingProgress(count)
//        }
        launch {
            sequence().toMagnetsDB(
                db("dist/$dist"),
                File("dist/$dist.yaml").bufferedWriter()
            )
        }
        this
    }
}

suspend fun main() {


    toMagnetsDB("rarbg.full",
        getFile("moviesrarbg"),
        getFile("showsrarbg")
    ).coroutineContext[Job]!!.join()
}
