package me.rerere.rikkahub.data.sync.importer

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

class DeepSeekImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun zip(conversationsJson: String): java.io.File {
        val file = temporaryFolder.newFile("deepseek.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry("conversations.json", conversationsJson.encodeToByteArray())
        }
        return file
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    /** 构造一个 mapping 节点，省得每个用例都手写字段样板。 */
    private fun node(
        key: String,
        parent: String?,
        children: List<String>,
        vararg fragments: String,
    ): String {
        val childrenJson = children.joinToString(", ") { "\"$it\"" }
        val parentJson = parent?.let { "\"$it\"" } ?: "null"
        val fragmentsJson = fragments.joinToString(", ")
        return """
            "$key": {"id": "$key", "parent": $parentJson, "children": [$childrenJson], "message": {
              "fragments": [$fragmentsJson]}}
        """.trimIndent()
    }

    private fun request(content: String) = """{"type": "REQUEST", "content": "$content"}"""

    private fun response(content: String) = """{"type": "RESPONSE", "content": "$content"}"""

    private fun textOf(conversation: Conversation): List<String> = conversation.messageNodes.flatMap { node ->
        node.messages.flatMap { message ->
            message.parts.filterIsInstance<UIMessagePart.Text>().map { it.text }
        }
    }

    @Test
    fun `maps fragments onto roles and parts`() = runBlocking {
        val file = zip(
            """
            [{
              "id": "conv-1",
              "title": "Fragment mapping",
              "inserted_at": "2025-01-26T20:20:33.713000+08:00",
              "updated_at": "2025-01-26T20:20:38.677000+08:00",
              "mapping": {
                "root": {"id": "root", "parent": null, "children": ["1"], "message": null},
                "1": {"id": "1", "parent": "root", "children": ["2"], "message": {
                  "model": "deepseek-chat",
                  "inserted_at": "2025-01-26T20:20:34.425000+08:00",
                  "fragments": [
                    {"type": "REQUEST", "content": "帮我看下这个文件"},
                    {"type": "FILE", "files": [{"file_id": "f1", "file_name": "a.png", "file_size": 2048}]}
                  ]}},
                "2": {"id": "2", "parent": "1", "children": [], "message": {
                  "model": "deepseek-chat",
                  "inserted_at": "2025-01-26T20:20:35.425000+08:00",
                  "fragments": [
                    {"type": "THINK", "content": "先分析一下"},
                    {"type": "SEARCH", "results": [{"url": "https://a.com", "title": "A"}]},
                    {"type": "TOOL_OPEN"},
                    {"type": "RESPONSE", "content": "这是回答"}
                  ]}}
              }
            }]
            """.trimIndent()
        )

        var imported: Conversation? = null
        val result = DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            onConversation = { imported = it },
        )

        assertEquals(1, result.parsedConversations)
        assertEquals(1, result.importedReasoningParts)
        assertEquals(1, result.importedSearchParts)
        assertEquals(1, result.skippedAttachmentParts)

        val conversation = assertNotNull(imported).let { imported!! }
        assertEquals("Fragment mapping", conversation.title)
        assertEquals(2, conversation.messageNodes.size)

        val userMessage = conversation.messageNodes[0].currentMessage
        assertEquals(MessageRole.USER, userMessage.role)
        assertTrue(userMessage.parts.filterIsInstance<UIMessagePart.Text>().any { it.text == "帮我看下这个文件" })
        assertTrue(userMessage.parts.filterIsInstance<UIMessagePart.Text>().any { it.text.contains("[附件] a.png") })

        val assistantParts = conversation.messageNodes[1].currentMessage.parts
        assertEquals(MessageRole.ASSISTANT, conversation.messageNodes[1].currentMessage.role)
        // 思考片段排在正文之前
        assertTrue(assistantParts.first() is UIMessagePart.Reasoning)
        assertEquals("先分析一下", (assistantParts.first() as UIMessagePart.Reasoning).reasoning)
        assertTrue(assistantParts.filterIsInstance<UIMessagePart.Text>().any { it.text == "这是回答" })

        val search = assistantParts.filterIsInstance<UIMessagePart.ServerTool>()
            .first { it.toolName == "search_web" }
        assertEquals(ServerToolStatus.COMPLETED, search.status)
        assertTrue(search.output.toString().contains("https://a.com"))
    }

    @Test
    fun `keeps every turn of a multi turn conversation as its own node`() = runBlocking {
        // 多轮追问是单子节点链（回复 → 追问 → 回复…），不能被误判成「重新生成」而折叠成一个节点。
        val file = zip(
            """
            [{"id": "conv-multi", "title": "Multi turn", "mapping": {
              "root": {"id": "root", "parent": null, "children": ["1"], "message": null},
              ${node("1", "root", listOf("2"), request("问题一"))},
              ${node("2", "1", listOf("3"), response("回答一"))},
              ${node("3", "2", listOf("4"), request("问题二"))},
              ${node("4", "3", listOf("5"), response("回答二"))},
              ${node("5", "4", listOf("6"), request("问题三"))},
              ${node("6", "5", emptyList(), response("回答三"))}
            }}]
            """.trimIndent()
        )

        var imported: Conversation? = null
        DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            onConversation = { imported = it },
        )

        val conversation = assertNotNull(imported).let { imported!! }
        assertEquals(6, conversation.messageNodes.size)
        assertEquals(
            listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT),
            conversation.messageNodes.map { it.currentMessage.role },
        )
        assertEquals(
            listOf("问题一", "回答一", "问题二", "回答二", "问题三", "回答三"),
            conversation.messageNodes.map { (it.currentMessage.parts.single() as UIMessagePart.Text).text },
        )
    }

    @Test
    fun `merges sibling assistant replies as branch alternatives`() = runBlocking {
        // 真实导出里的「重新生成」：用户提问节点下挂了多条助手回复（例如「搜索繁忙」+ 真正回复）。
        val file = zip(
            """
            [{"id": "conv-branch", "title": "Branch", "mapping": {
              "root": {"id": "root", "parent": null, "children": ["1"], "message": null},
              ${node("1", "root", listOf("2"), request("问题一"))},
              ${node("2", "1", listOf("4"), response("回答一"))},
              ${node("4", "2", listOf("5", "13"), request("你真好，我现在离开家"))},
              ${node("5", "4", emptyList(), response("搜索繁忙"))},
              ${node("13", "4", emptyList(), response("真正的回复"))}
            }}]
            """.trimIndent()
        )

        var imported: Conversation? = null
        DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            onConversation = { imported = it },
        )

        val conversation = assertNotNull(imported).let { imported!! }
        // 问题一 / 回答一 / 提问 / 承载两条回复版本的回答节点
        assertEquals(4, conversation.messageNodes.size)

        val question = conversation.messageNodes[2]
        assertEquals(MessageRole.USER, question.currentMessage.role)
        assertEquals(1, question.messages.size)
        assertEquals("你真好，我现在离开家", (question.currentMessage.parts.single() as UIMessagePart.Text).text)

        // 多个回复版本合并成同一个节点的候选项，默认选中导出时保留的第一条
        val answer = conversation.messageNodes[3]
        assertEquals(MessageRole.ASSISTANT, answer.currentMessage.role)
        assertEquals(2, answer.messages.size)
        assertEquals("搜索繁忙", (answer.currentMessage.parts.single() as UIMessagePart.Text).text)
        assertEquals("真正的回复", (answer.messages[1].parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `keeps reasked question branches as separate tail nodes`() = runBlocking {
        val file = zip(
            """
            [{"id": "conv-user-branch", "title": "User branch", "mapping": {
              "root": {"id": "root", "parent": null, "children": ["1", "3"], "message": null},
              ${node("1", "root", listOf("2"), request("keil的uproject"))},
              ${node("2", "1", emptyList(), response("回答1"))},
              ${node("3", "root", listOf("4"), request("uproject.用户名文件干什么的"))},
              ${node("4", "3", emptyList(), response("回答2"))}
            }}]
            """.trimIndent()
        )

        var imported: Conversation? = null
        DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            onConversation = { imported = it },
        )

        val conversation = assertNotNull(imported).let { imported!! }
        val allTexts = textOf(conversation)
        assertTrue(allTexts.contains("keil的uproject"))
        assertTrue(allTexts.contains("回答1"))
        // 非主链的重新提问分支也保留下来
        assertTrue(allTexts.contains("uproject.用户名文件干什么的"))
        assertTrue(allTexts.contains("回答2"))
    }

    @Test
    fun `skips conversations that already exist`() = runBlocking {
        val file = zip(
            """
            [{"id": "dup", "title": "Dup", "mapping": {
              "root": {"id": "root", "parent": null, "children": ["1"], "message": null},
              "1": {"id": "1", "parent": "root", "children": [], "message": {
                "fragments": [{"type": "REQUEST", "content": "hi"}]}}
            }}]
            """.trimIndent()
        )

        var importedAny = false
        val result = DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            shouldImportConversation = { false },
            onConversation = { importedAny = true },
        )

        assertEquals(false, importedAny)
        assertEquals(0, result.parsedConversations)
        // 被 shouldImportConversation 拦下的对话既不算导入也不算解析失败
        assertEquals(0, result.skippedConversations)
    }

    @Test
    fun `fails when conversations json is missing`() {
        val file = temporaryFolder.newFile("empty.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry("user.json", "{}".encodeToByteArray())
        }
        val result = runCatching {
            runBlocking {
                DeepSeekImporter.importStreaming(
                    file = file,
                    assistantId = Uuid.random(),
                    onConversation = {},
                )
            }
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("conversations.json") == true)
    }

    @Test
    fun `skips unusable conversations without failing the import`() = runBlocking {
        val file = zip(
            """
            [{"id": "bad", "title": "No mapping"},
             {"id": "empty", "title": "Empty mapping", "mapping": {
               "root": {"id": "root", "parent": null, "children": [], "message": null}}}]
            """.trimIndent()
        )

        val result = DeepSeekImporter.importStreaming(
            file = file,
            assistantId = Uuid.random(),
            onConversation = { error("should not import") },
        )

        assertEquals(0, result.parsedConversations)
        assertEquals(2, result.skippedConversations)
    }
}
