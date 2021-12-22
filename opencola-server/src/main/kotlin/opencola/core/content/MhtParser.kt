package opencola.core.content

import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import java.io.InputStream

// TODO: Store indexed pages as mht archives.
//  Analysis can be done with https://github.com/apache/james-mime4j
//  https://github.com/apache/james-mime4j/blob/master/examples/src/main/java/org/apache/james/mime4j/samples/dom/ParsingMessage.java
//  OR
//  Java Mail APIs
//  https://pretagteam.com/question/how-to-read-or-parse-mhtml-mht-files-in-java
//  which uses https://javaee.github.io/javamail/
fun parseMht(inputStream: InputStream) {
    val defaultMessageBuilder = DefaultMessageBuilder()
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
//    val file = File(System.getProperty("user.dir") + "/sample-docs/" + "log4jVulnerability.mht")
//    val fis = FileInputStream(file)
    val message = defaultMessageBuilder.parseMessage(inputStream)
}