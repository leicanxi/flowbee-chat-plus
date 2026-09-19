package me.rerere.rikkahub.data.sync.importer

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.time.Instant as KotlinInstant
import kotlin.uuid.Uuid

/**
 * Imports the `conversations.json` archive produced by DeepSeek's official data export.
 *
 * The export is a flat JSON array of conversations. Each conversation carries a `mapping`
 * (node key -> node) describing a message tree, similar to the ChatGPT export:
 *
 * ```
 * node = { id, parent, children[], message|null }
 * message = { model, inserted_at, fragments[] }
 * ```
 *
 * `parent` / `children` reference the mapping **key**, which is not always equal to the node's
 * own `id` field — every lookup here therefore indexes by key.
 *
 * Fragment types map onto app concepts as follows:
 *
 * | fragment | 归属 | 转换结果 |
 * |---|---|---|
 * | `REQUEST` | 用户 | `UIMessagePart.Text` |
 * | `FILE` | 用户 | `UIMessagePart.Text`（导出包不含文件实体，仅保留提示行） |
 * | `RESPONSE` | 助手 | `UIMessagePart.Text` |
 * | `THINK` | 助手 | `UIMessagePart.Reasoning`（App 内渲染为「思考」块） |
 * | `SEARCH` / `TOOL_SEARCH` | 助手 | `UIMessagePart.ServerTool("search_web")`，带可跳转的结果列表 |
 * | `TOOL_FIND` / `TOOL_OPEN` | 助手 | `UIMessagePart.ServerTool`，仅标记动作 |
 *
 * 由于 `REQUEST`/`FILE` 只会出现在用户轮次，`RESPONSE`/`THINK`/`SEARCH`/`TOOL_*` 只会出现在
 * 助手轮次，消息角色可以直接由片段类型推导，无需依赖节点深度。一个节点内部的片段仍按原始
 * 顺序保留，因此「搜索 → 思考 → 正文」这类交错过程不会被打乱。
 *
 * 正常对话是一条单子节点链（回复 → 追问 → 回复…）。只有 children 数量大于 1 才代表分叉：
 * - children 全是助手回复 → 用户点了「重新生成」。提问照常成一个节点，这些回复变成一个
 *   **只装回答**的节点，它们是同一轮回答的不同版本，可在 App 内左右切换；
 * - 根节点的多个 children → 用户「重新提问」，第一条继续做主链，其余原样保留在末尾。
 *
 * 导出包只有 `conversations.json` / `user.json`，没有任何二进制资源，因此附件一律降级为文本。
 */
object DeepSeekImporter {
    private const val CONVERSATIONS_PATH = "conversations.json"
    private const val MAX_JSON_ENTRY_SIZE = 256L * 1024 * 1024

    private const val FRAGMENT_REQUEST = "REQUEST"
    private const val FRAGMENT_RESPONSE = "RESPONSE"
    private const val FRAGMENT_THINK = "THINK"
    private const val FRAGMENT_FILE = "FILE"
    private const val FRAGMENT_SEARCH = "SEARCH"
    private const val FRAGMENT_TOOL_SEARCH = "TOOL_SEARCH"
    private const val FRAGMENT_TOOL_FIND = "TOOL_FIND"
    private const val FRAGMENT_TOOL_OPEN = "TOOL_OPEN"

    private const val SEARCH_TOOL_NAME = "search_web"

    private val USER_FRAGMENT_TYPES = setOf(FRAGMENT_REQUEST, FRAGMENT_FILE)
    private val ASSISTANT_FRAGMENT_TYPES = setOf(
        FRAGMENT_RESPONSE,
        FRAGMENT_THINK,
        FRAGMENT_SEARCH,
        FRAGMENT_TOOL_SEARCH,
        FRAGMENT_TOOL_FIND,
        FRAGMENT_TOOL_OPEN,
    )

    /**
     * @param shouldImportConversation 返回 false 时跳过该对话（用于避免重复导入已存在的记录）
     * @param onConversation 每解析出一个对话就回调一次，便于边解析边落库
     */
    suspend fun importStreaming(
        file: File,
        assistantId: Uuid,
        shouldImportConversation: suspend (Uuid) -> Boolean = { true },
        onConversation: suspend (Conversation) -> Unit,
    ): DeepSeekStreamingImportResult {
        var parsedConversations = 0
        var importedReasoningParts = 0
        var importedSearchParts = 0
        var skippedAttachmentParts = 0
        var skippedEmptyMessages = 0
        var skippedBranchMessages = 0
        var skippedConversations = 0

        ZipFile(file).use { zip ->
            zip.readConversationArray().forEach { element ->
                val raw = element as? JsonObject ?: return@forEach
                val rawId = raw["id"].ownString()?.takeIf { it.isNotBlank() } ?: return@forEach
                val conversationId = stableUuid("deepseek:conversation:$rawId")
                if (!shouldImportConversation(conversationId)) return@forEach

                val parsed = parseConversation(
                    raw = raw,
                    rawId = rawId,
                    conversationId = conversationId,
                    assistantId = assistantId,
                )
                if (parsed == null) {
                    skippedConversations++
                    return@forEach
                }
                importedReasoningParts += parsed.stats.reasoningParts
                importedSearchParts += parsed.stats.searchParts
                skippedAttachmentParts += parsed.stats.attachmentNotices
                skippedEmptyMessages += parsed.stats.emptyMessages
                skippedBranchMessages += parsed.stats.branchMessages
                parsedConversations++
                onConversation(parsed.conversation)
            }
        }

        return DeepSeekStreamingImportResult(
            parsedConversations = parsedConversations,
            importedReasoningParts = importedReasoningParts,
            importedSearchParts = importedSearchParts,
            skippedAttachmentParts = skippedAttachmentParts,
            skippedEmptyMessages = skippedEmptyMessages,
            skippedBranchMessages = skippedBranchMessages,
            skippedConversations = skippedConversations,
        )
    }

    private fun parseConversation(
        raw: JsonObject,
        rawId: String,
        conversationId: Uuid,
        assistantId: Uuid,
    ): ParsedConversation? {
        val mapping = raw["mapping"] as? JsonObject ?: return null
        val nodes = mapping.mapNotNull { (key, value) ->
            val node = value as? JsonObject ?: return@mapNotNull null
            key to parseNode(key, node)
        }.toMap()
        if (nodes.isEmpty()) return null

        // 统一用 mapping 的 key 作为节点索引；parent / children 里的引用也都是 key
        val rootIds = nodes.values
            .filter { it.parentId == null || it.parentId !in nodes }
            .map { it.key }
            .sortedBy { it }
        if (rootIds.isEmpty()) return null

        val order = chainOrder(nodes, rootIds)

        // 导出里只有一种「树」：根节点下的第一个子节点开始一条单链，链条上每个节点的 children
        // 都只有一个元素——那个元素就是下一轮发言。真正出现分叉的情况只有两类：
        //
        //   A. 某节点的 children 全是助手回复 —— 用户点了「重新生成」：
        //        10(提问) → [11(搜索繁忙的回复), 13(真正的回复)]
        //      这些回复是同一轮回答的多个版本，合并成一个 MessageNode 的候选列表。
        //
        //   B. 根节点的 children 有多个 —— 用户「重新提问」留下了分支：
        //        root → [1(第一条问题), 3(另一条问题)]
        //      第一条继续做主链，其余分支原样保留在末尾，保证不丢消息。
        //
        // 判定的关键就是「children 数量 > 1」：单子节点的链接一律直接串下去，不能把
        // 「回复 → 追问」的衔接误判成分支（那会把整段多轮对话折叠成一个节点）。
        val topRootId = rootIds.first()
        val topStartKey = nodes[topRootId]?.childIds?.firstOrNull { nodes.containsKey(it) }

        // 会话时间统一折算成毫秒，构造 Conversation 时再转成 java.time.Instant
        val timestamps = nodes.values.mapNotNull { it.insertedAt }
        val createMillis = raw["inserted_at"].asInstant()
            ?: timestamps.minOrNull()
            ?: System.currentTimeMillis()
        val updateMillis = raw["updated_at"].asInstant()
            ?: timestamps.maxOrNull()
            ?: createMillis

        val stats = ImportStats()

        /**
         * 情况 A：某节点的 children 全是助手回复，且数量大于 1 —— 用户在浏览器里点过「重新生成」。
         * 例如真实导出里的
         *
         *     10(提问) → [11(搜索繁忙的回复), 13(真正的回复)]
         *
         * 这几条回复是同一轮回答的不同版本。注意提问节点本身只有 REQUEST、没有自己的回复，
         * 所以不能把回复并到提问节点上（那样一个节点的候选里会既有提问又有回答，切换候选等于
         * 换角色）。正确做法是：提问照常成节点，回复另起一个节点、候选列表里只放这些回复。
         *
         * 先算出全部合并组，因为后面既要用它决定链上跳过哪些节点，也要用它统计落库范围。
         */
        val mergedReplies: Map<String, List<ExportNode>> = nodes.values
            .associate { node ->
                node.key to node.childIds
                    .mapNotNull(nodes::get)
                    .takeIf { children ->
                        children.size > 1 && children.all { it.role == MessageRole.ASSISTANT }
                    }
                    .orEmpty()
                    .sortedBy { order[it.key] ?: Int.MAX_VALUE }
            }
            .filterValues { it.isNotEmpty() }

        val consumedKeys = mutableSetOf<String>()
        mergedReplies.values.flatten().forEach { consumedKeys += it.key }
        val mainlineKeys = topStartKey
            ?.let { walkChain(nodes.getValue(it), nodes, order, consumedKeys) }
            .orEmpty()

        // 先解析出每个节点的正式消息，分支合并都基于这份结果。统计口径覆盖所有会真正落库的
        // 节点（主链 + 合并出来的回复），否则「导入了几条思考/搜索」会明显少算。
        val statsKeys = mainlineKeys.map { it.key }.toMutableSet().apply {
            mergedReplies.values.flatten().forEach { add(it.key) }
        }
        val parsedByNode = nodes.values.mapNotNull { node ->
            val message = parseMessage(
                node = node,
                conversationId = rawId,
                fallbackMillis = createMillis,
                stats = stats,
                countStats = node.key in statsKeys,
            ) ?: return@mapNotNull null
            node.key to message
        }.toMap()
        if (parsedByNode.isEmpty()) return null

        val messageNodes = arrayListOf<MessageNode>()
        mainlineKeys.forEach { node ->
            parsedByNode[node.key]?.let { primary ->
                messageNodes += MessageNode(
                    id = stableUuid("deepseek:node:$rawId:${node.key}"),
                    messages = listOf(primary),
                    selectIndex = 0,
                )
            }

            // 该节点下的多个回复版本 → 另起一个只装回答的节点，默认选中导出时保留的那条
            val replyMessages = mergedReplies[node.key]
                .orEmpty()
                .mapNotNull { parsedByNode[it.key] }
            if (replyMessages.isNotEmpty()) {
                messageNodes += MessageNode(
                    id = stableUuid("deepseek:node:$rawId:${node.key}:replies"),
                    messages = replyMessages,
                    selectIndex = 0,
                )
            }
        }

        // 情况 B：根节点下其余的「重新提问」分支。它们不在主链上，App 里看不到，但保留成
        // 尾部节点，避免导入时静默丢消息。
        val branchNodes = nodes.getValue(topRootId).childIds
            .filter { it != topStartKey && nodes.containsKey(it) }
            .distinct()
            .sortedBy { order[it] ?: Int.MAX_VALUE }
            .mapNotNull { branchStartKey ->
                val tail = walkChain(nodes.getValue(branchStartKey), nodes, order, consumedKeys)
                    .mapNotNull { parsedByNode[it.key] }
                if (tail.isEmpty()) return@mapNotNull null
                stats.branchMessages += (tail.size - 1).coerceAtLeast(0)
                MessageNode(
                    id = stableUuid("deepseek:branch:$rawId:$branchStartKey"),
                    messages = tail,
                    selectIndex = 0,
                )
            }

        val allNodes = (messageNodes + branchNodes)
            .distinctBy { it.id }
            .filter { it.messages.isNotEmpty() }
        if (allNodes.isEmpty()) return null

        val title = raw["title"].ownString()?.takeIf { it.isNotBlank() } ?: rawId
        return ParsedConversation(
            conversation = Conversation(
                id = conversationId,
                assistantId = assistantId,
                title = title,
                messageNodes = allNodes,
                createAt = java.time.Instant.ofEpochMilli(createMillis),
                updateAt = java.time.Instant.ofEpochMilli(updateMillis),
            ),
            stats = stats,
        )
    }

    /** 沿单子节点链收集节点（含起点）；[excluded] 里的节点不再往下走。 */
    private fun walkChain(
        start: ExportNode,
        nodes: Map<String, ExportNode>,
        order: Map<String, Int>,
        excluded: Set<String>,
    ): List<ExportNode> {
        val chain = mutableListOf<ExportNode>()
        var cursor: ExportNode? = start
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor.key)) {
            chain += cursor
            cursor = cursor.childIds
                .filter(nodes::containsKey)
                .filterNot(excluded::contains)
                .minByOrNull { order[it] ?: Int.MAX_VALUE }
                ?.let(nodes::get)
        }
        return chain
    }

    /** 遍历顺序：与导出文件里 `children` 的书写顺序一致，供候选排序使用。 */
    private fun chainOrder(nodes: Map<String, ExportNode>, rootIds: List<String>): Map<String, Int> {
        val order = mutableMapOf<String, Int>()
        var cursor = 0
        val stack = rootIds.asReversed().toMutableList()
        val visited = mutableSetOf<String>()
        while (stack.isNotEmpty()) {
            val id = stack.removeAt(stack.lastIndex)
            if (!visited.add(id)) continue
            val node = nodes[id] ?: continue
            order[id] = cursor++
            node.childIds.asReversed().forEach { child ->
                if (nodes.containsKey(child)) stack += child
            }
        }
        // 孤立节点（父节点缺失但未被判定为根）兜底排序
        nodes.keys.filterNot(visited::contains).forEach { order[it] = cursor++ }
        return order
    }

    private fun parseMessage(
        node: ExportNode,
        conversationId: String,
        fallbackMillis: Long,
        stats: ImportStats,
        countStats: Boolean,
    ): UIMessage? {
        if (node.fragments.isEmpty()) return null

        val userFragments = node.fragments.filter { it.type in USER_FRAGMENT_TYPES }
        val assistantFragments = node.fragments.filter { it.type in ASSISTANT_FRAGMENT_TYPES }
        if (userFragments.isEmpty() && assistantFragments.isEmpty()) return null

        val role = if (assistantFragments.isNotEmpty()) MessageRole.ASSISTANT else MessageRole.USER
        val parts = arrayListOf<UIMessagePart>()

        userFragments.forEach { fragment ->
            when (fragment.type) {
                FRAGMENT_REQUEST -> fragment.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parts += UIMessagePart.Text(it) }

                FRAGMENT_FILE -> fragment.files.forEach { attachment ->
                    parts += UIMessagePart.Text(attachmentNotice(attachment))
                    if (countStats) stats.attachmentNotices++
                }
            }
        }

        // 思考片段统一排在正文之前，与 App 的「思考过程」渲染顺序一致
        val reasoning = arrayListOf<UIMessagePart>()
        val body = arrayListOf<UIMessagePart>()
        assistantFragments.forEach { fragment ->
            when (fragment.type) {
                FRAGMENT_THINK -> fragment.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { content ->
                        reasoning += UIMessagePart.Reasoning(reasoning = content)
                        if (countStats) stats.reasoningParts++
                    }

                FRAGMENT_RESPONSE -> fragment.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { body += UIMessagePart.Text(it) }

                FRAGMENT_SEARCH, FRAGMENT_TOOL_SEARCH -> {
                    if (fragment.results.isNotEmpty()) {
                        body += searchPart(node, fragment)
                        if (countStats) stats.searchParts++
                    }
                }

                FRAGMENT_TOOL_FIND, FRAGMENT_TOOL_OPEN -> body += UIMessagePart.ServerTool(
                    toolCallId = "",
                    toolName = if (fragment.type == FRAGMENT_TOOL_FIND) "find" else "open",
                    input = null,
                    output = null,
                    status = ServerToolStatus.COMPLETED,
                )
            }
        }

        val orderedParts = buildList {
            addAll(reasoning)
            addAll(parts)
            addAll(body)
        }
        if (orderedParts.isEmpty()) {
            if (countStats) stats.emptyMessages++
            return null
        }

        val createdAt = KotlinInstant.fromEpochMilliseconds(node.insertedAt ?: fallbackMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return UIMessage(
            id = stableUuid("deepseek:message:$conversationId:${node.key}"),
            role = role,
            parts = orderedParts,
            createdAt = createdAt,
        )
    }

    /**
     * 联网搜索片段转换为 `search_web` 工具调用，结果写成 `{items:[{title,url}]}`，
     * 与 App 内搜索结果卡片（含引用跳转）使用同一份数据结构。
     */
    private fun searchPart(node: ExportNode, fragment: ExportFragment): UIMessagePart.ServerTool {
        val query = node.fragments
            .firstOrNull { it.type == FRAGMENT_REQUEST }
            ?.content
            .orEmpty()
        return UIMessagePart.ServerTool(
            toolCallId = "",
            toolName = SEARCH_TOOL_NAME,
            input = JsonObject(mapOf("query" to JsonPrimitive(query))),
            output = buildJsonObject {
                put(
                    "items",
                    buildJsonArray {
                        fragment.results.forEach { result ->
                            add(
                                buildJsonObject {
                                    put("title", result.title)
                                    put("url", result.url)
                                }
                            )
                        }
                    },
                )
            },
            status = ServerToolStatus.COMPLETED,
        )
    }

    private fun attachmentNotice(attachment: ExportAttachment): String {
        val name = attachment.fileName?.takeIf { it.isNotBlank() }
            ?: attachment.fileId?.takeIf { it.isNotBlank() }
            ?: "未命名文件"
        val size = attachment.size
            ?.takeIf { it > 0 }
            ?.let { "，%.1f KB".format(it / 1024.0) }
            .orEmpty()
        return "[附件] $name$size（DeepSeek 导出包不包含文件本体，仅保留记录）"
    }

    private fun parseNode(key: String, node: JsonObject): ExportNode {
        val message = node["message"] as? JsonObject
        return ExportNode(
            key = key,
            id = node["id"].ownString()?.takeIf { it.isNotBlank() } ?: key,
            parentId = node["parent"].ownString()?.takeIf { it.isNotBlank() },
            childIds = node["children"].asStringArray(),
            fragments = message?.get("fragments").asFragmentList(),
            insertedAt = message?.get("inserted_at").asInstant(),
        )
    }

    private fun ZipFile.readConversationArray(): List<JsonElement> {
        val entry = getEntry(CONVERSATIONS_PATH)
            ?: error("Invalid DeepSeek export: $CONVERSATIONS_PATH not found")
        require(!entry.isDirectory && entry.hasAllowedSize(MAX_JSON_ENTRY_SIZE)) {
            "Invalid DeepSeek export entry: $CONVERSATIONS_PATH"
        }
        val element = readEntryBytes(entry, MAX_JSON_ENTRY_SIZE)
            .toString(StandardCharsets.UTF_8)
            .let(JsonInstant::parseToJsonElement)
        return when (element) {
            is JsonArray -> element
            // 兼容可能被包成 {"conversations":[...]} 的导出变体
            is JsonObject -> element["conversations"].asArray()
            else -> emptyList()
        }
    }

    private fun ZipFile.readEntryBytes(entry: ZipEntry, maxSize: Long): ByteArray {
        require(entry.hasAllowedSize(maxSize)) { "DeepSeek export entry is too large: ${entry.name}" }
        val initialSize = entry.size.coerceAtMost(8192).toInt()
        return getInputStream(entry).use { input ->
            ByteArrayOutputStream(initialSize).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    require(totalBytes <= maxSize) { "DeepSeek export entry is too large: ${entry.name}" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private fun ZipEntry.hasAllowedSize(maxSize: Long): Boolean = size in 0..maxSize

    private fun stableUuid(value: String): Uuid =
        Uuid.parse(UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString())

    // ---- JSON 取值小工具（对外层为空、类型不符的情况统一返回 null / 空集合）----
    //
    // 说明：这里统一写成 `JsonElement?` 的扩展，并且只用 `.ownString()` / `.asInstant()` 这类
    // 带自定义名字的取值器。原因是 `JsonElement.asString` 在本项目其他文件里已有同名扩展，
    // 若在这里再定义一个同名但接收者为 `JsonElement?` 的版本，可空接收者会让编译器优先选中
    // 本文件的版本、把可空的 `message?.get(...)` 也吞掉，从而产生难以察觉的类型错乱。

    private fun JsonElement?.ownString(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

    private fun JsonElement?.asStringArray(): List<String> =
        asArray().mapNotNull { it.ownString()?.takeIf(String::isNotBlank) }

    /**
     * 导出里的时间是 ISO-8601 字符串（带时区偏移）。这里直接折算成 epoch 毫秒，
     * 避免在 `java.time.Instant` 与 `kotlin.time.Instant` 之间来回转换。
     */
    private fun JsonElement?.asInstant(): Long? {
        val raw = ownString() ?: return null
        return runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull()
            ?.toEpochMilli()
    }

    private fun JsonElement?.asFragmentList(): List<ExportFragment> =
        asArray().mapNotNull { it.toExportFragment() }

    private fun JsonElement?.toExportFragment(): ExportFragment? {
        val obj = this as? JsonObject ?: return null
        val type = obj["type"].ownString() ?: return null
        val results = obj["results"].asArray().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item["url"].ownString() ?: return@mapNotNull null
            ExportSearchResult(url = url, title = item["title"].ownString().orEmpty())
        }
        val files = obj["files"].asArray().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            ExportAttachment(
                fileId = item["file_id"].ownString(),
                fileName = item["file_name"].ownString(),
                size = item["file_size"].ownString()?.toLongOrNull(),
            )
        }
        return ExportFragment(
            type = type,
            content = obj["content"].ownString(),
            results = results,
            files = files,
        )
    }
}

private data class ExportNode(
    /** `mapping` 的键。父子引用用的是它，因此链遍历与候选排序都必须以它为准。 */
    val key: String,
    /** 节点自带的 `id` 字段，仅用于生成稳定 UUID，可能与 [key] 不同。 */
    val id: String,
    val parentId: String?,
    val childIds: List<String>,
    val fragments: List<ExportFragment>,
    /** 消息时间，epoch 毫秒。统一用 Long 传，避免在 java.time / kotlin.time 两套 Instant 之间反复转换。 */
    val insertedAt: Long?,
) {
    val role: MessageRole?
        get() {
            val hasAssistant = fragments.any { it.type in ASSISTANT_TYPES }
            val hasUser = fragments.any { it.type in USER_TYPES }
            return when {
                hasAssistant -> MessageRole.ASSISTANT
                hasUser -> MessageRole.USER
                else -> null
            }
        }

        private companion object {
            private val USER_TYPES = setOf("REQUEST", "FILE")
            private val ASSISTANT_TYPES = setOf(
                "RESPONSE", "THINK", "SEARCH", "TOOL_SEARCH", "TOOL_FIND", "TOOL_OPEN"
            )
        }
    }

private data class ExportFragment(
    val type: String,
    val content: String?,
    val results: List<ExportSearchResult>,
    val files: List<ExportAttachment>,
)

private data class ExportSearchResult(val url: String, val title: String)

private data class ExportAttachment(val fileId: String?, val fileName: String?, val size: Long?)

private class ImportStats {
    var reasoningParts = 0
    var searchParts = 0
    var attachmentNotices = 0
    var emptyMessages = 0
    var branchMessages = 0
}

private data class ParsedConversation(
    val conversation: Conversation,
    val stats: ImportStats,
)

data class DeepSeekStreamingImportResult(
    val parsedConversations: Int,
    val importedReasoningParts: Int,
    val importedSearchParts: Int,
    val skippedAttachmentParts: Int,
    val skippedEmptyMessages: Int,
    val skippedBranchMessages: Int,
    val skippedConversations: Int,
)
