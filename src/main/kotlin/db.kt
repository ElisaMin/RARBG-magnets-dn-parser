import org.jetbrains.exposed.dao.id.IntIdTable


object Magnets: IntIdTable("magnets") {
    val hash = varchar("hash",40)
        .uniqueIndex()
    val dn = text("dn")
    val year = integer("year")
        .nullable()
    val codec = text("codec")
        .nullable()
    val quality = text("quality")
        .nullable()
    val episode = text("episode")
        .nullable()
    val name_id = reference("name_id", Names.id)
}
object Names:IntIdTable("names") {
    val name = text("name")
        .uniqueIndex()
}