// Keep the public metadata parser in scripts/geely_version.py in sync.
// Deliberately independent of Git tags, timestamps, CI run numbers and secrets.
val versionText = providers.fileContents(
    rootProject.layout.projectDirectory.file("gradle/geely-version.properties"),
).asText.get()
val versionProperties = linkedMapOf<String, String>()
versionText.lineSequence().forEachIndexed { index, raw ->
    val line = raw.trim()
    if (line.isNotEmpty() && !line.startsWith("#")) {
        val match = Regex("([A-Za-z][A-Za-z0-9]*)=([^\\s=]+)").matchEntire(line)
        require(match != null) { "Invalid Geely version property on line ${index + 1}" }
        val (key, value) = match.destructured
        require(versionProperties.put(key, value) == null) { "Duplicate Geely version property: $key" }
    }
}
require(versionProperties.keys == setOf("upstreamVersionName", "upstreamVersionCode", "geelyRevision")) {
    "Geely version properties must contain only upstreamVersionName, upstreamVersionCode and geelyRevision"
}
val upstreamVersionName = versionProperties.getValue("upstreamVersionName")
require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(upstreamVersionName)) {
    "upstreamVersionName must be the original upstream numeric major.minor.patch version"
}
fun positiveVersionNumber(key: String): Long {
    val raw = versionProperties.getValue(key)
    require(Regex("[1-9][0-9]*").matches(raw)) { "$key must be a canonical positive integer" }
    return requireNotNull(raw.toLongOrNull()) { "$key is too large" }
}
val upstreamVersionCode = positiveVersionNumber("upstreamVersionCode")
val geelyRevision = positiveVersionNumber("geelyRevision")
require(geelyRevision in 1..999) { "geelyRevision must be between 1 and 999" }
// Validate before multiplication to avoid overflow. Stay below the Play-compatible cap.
require(upstreamVersionCode <= (2_100_000_000L - geelyRevision) / 1000) {
    "Geely versionCode exceeds the 2100000000 limit; review the versioning policy"
}
val geelyVersionCode = (upstreamVersionCode * 1000 + geelyRevision).toInt()
val geelyVersionName = "$upstreamVersionName-geely.$geelyRevision"
extra["geelyVersionCode"] = geelyVersionCode
extra["geelyVersionName"] = geelyVersionName

tasks.register("writeGeelyVersionMetadata") {
    group = "verification"
    description = "Write public deterministic release metadata without building or signing an APK."
    val metadata = """{"upstreamVersionName":"$upstreamVersionName","upstreamVersionCode":$upstreamVersionCode,"geelyRevision":$geelyRevision,"versionName":"$geelyVersionName","versionCode":$geelyVersionCode,"tag":"v$geelyVersionName"}"""
    val destination = layout.buildDirectory.file("outputs/geely/version.json")
    inputs.property("metadata", metadata)
    outputs.file(destination)
    doLast {
        destination.get().asFile.apply {
            parentFile.mkdirs()
            writeText(metadata + "\n")
        }
    }
}
