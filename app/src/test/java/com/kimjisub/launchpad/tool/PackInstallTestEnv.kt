package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.analytics.PackImportReport
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.unipack.UniPack
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Runs [UniPackDownloader] and [UniPackImporter] on the JVM against a temporary workspace.
 * Android framework calls are mocked, and each download URL answers only when its [Gate] is
 * opened, so a test decides exactly how two requests for the same name overlap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackInstallTestEnv {
	val root: File = Files.createTempDirectory("pack_install_test").toFile()
	val workspace = File(root, "workspace").apply { mkdirs() }
	private val cache = File(root, "cache").apply { mkdirs() }

	// Several threads, so a callback that blocks on another request does not stall that request.
	private val mainExecutor = Executors.newFixedThreadPool(4)

	/** Names of the threads that resolved the target workspace, one per request. */
	val workspaceResolvedOn: MutableList<String> = Collections.synchronizedList(mutableListOf())
	private val resolveWorkspace: () -> File = {
		workspaceResolvedOn += Thread.currentThread().name
		workspace
	}
	private val openStreams = mutableListOf<Closeable>()
	private val gates = ConcurrentHashMap<String, Gate>()
	private val context: Context = mockk(relaxed = true)
	private val resolver: ContentResolver = mockk(relaxed = true)

	fun setUp() {
		Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())

		every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns
			mockk<android.app.NotificationManager>(relaxed = true)
		every { context.contentResolver } returns resolver
		every { context.cacheDir } returns cache
		mockkStatic(PendingIntent::class)
		every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
		mockkConstructor(NotificationCompat.Builder::class)
		every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()

		mockkStatic(Uri::class)
		every { Uri.fromFile(any()) } answers {
			val path = firstArg<File>().path
			mockk<Uri> { every { this@mockk.path } returns path }
		}
		every { resolver.openFileDescriptor(any(), "w") } answers {
			val stream = FileOutputStream(firstArg<Uri>().path!!)
			synchronized(openStreams) { openStreams += stream }
			mockk<ParcelFileDescriptor>(relaxed = true) { every { fileDescriptor } returns stream.fd }
		}

		mockkObject(FileApi)
		every { FileApi.service } returns object : FileApi.FileService {
			override fun download(url: String): Call<ResponseBody> = GatedCall(gate(url))
		}
		mockkStatic(DocumentFile::class)
	}

	fun tearDown() {
		unmockkAll()
		Dispatchers.resetMain()
		mainExecutor.shutdownNow()
		synchronized(openStreams) { openStreams.forEach { runCatching { it.close() } } }
		root.deleteRecursively()
	}

	/** The device refuses to open the download's target file, as with a revoked storage permission. */
	fun cannotOpenFilesForWriting() {
		every { resolver.openFileDescriptor(any(), "w") } returns null
	}

	fun gate(url: String): Gate = gates.getOrPut(url) { Gate() }

	fun download(
		url: String,
		listener: UniPackDownloader.Listener,
		name: String = PACK_NAME,
		usage: PackImportReport = unrecorded(),
	): CoroutineScope {
		val scope = CoroutineScope(SupervisorJob())
		UniPackDownloader(
			context = context,
			title = name,
			url = url,
			workspace = resolveWorkspace,
			folderName = name,
			listener = listener,
			usage = usage,
			scope = scope,
		)
		return scope
	}

	fun import(
		zip: ByteArray,
		listener: UniPackImporter.OnEventListener,
		fileName: String = "$PACK_NAME.zip",
		usage: PackImportReport = unrecorded(),
		openInput: () -> InputStream = { ByteArrayInputStream(zip) },
	): CoroutineScope {
		val uri = mockk<Uri>()
		every { resolver.openInputStream(uri) } answers { openInput() }
		every { DocumentFile.fromSingleUri(context, uri) } returns mockk { every { name } returns fileName }
		val scope = CoroutineScope(SupervisorJob())
		UniPackImporter(
			context = context,
			uri = uri,
			workspace = resolveWorkspace,
			onEventListener = listener,
			usage = usage,
			scope = scope,
		)
		return scope
	}

	/** Waits until every request started on [scope] has finished, cleanup included. */
	fun finish(scope: CoroutineScope) = runBlocking {
		withTimeout(TIMEOUT_MS) { scope.coroutineContext.job.children.toList().joinAll() }
	}

	fun workspaceContents(): List<String> = workspace.list().orEmpty().sorted()

	/** The server side of one download URL: `execute()` blocks until [open] or [fail]. */
	class Gate {
		private val entered = CountDownLatch(1)
		private val response = CompletableFuture<Response<ResponseBody>>()
		val cancelled = CountDownLatch(1)
		private var cancelRead: (() -> Unit)? = null

		fun cancel() {
			cancelled.countDown()
			response.completeExceptionally(IOException("request cancelled"))
			cancelRead?.invoke()
		}

		/** Supplies one chunk, then blocks until the test or cancellation breaks the read. */
		fun openBlockedBody(received: ByteArray, cancelBreaksRead: Boolean): Pair<CountDownLatch, CountDownLatch> {
			val reading = CountDownLatch(1)
			val fail = CountDownLatch(1)
			if (cancelBreaksRead) cancelRead = { fail.countDown() }
			val source = object : Source {
				private var sent = false
				override fun read(sink: Buffer, byteCount: Long): Long {
					if (!sent) {
						sent = true
						sink.write(received)
						return received.size.toLong()
					}
					reading.countDown()
					check(fail.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "read was not stopped" }
					throw IOException("connection broke after cancellation")
				}
				override fun timeout() = Timeout.NONE
				override fun close() {}
			}
			response.complete(Response.success(source.buffer().asResponseBody(null, received.size + PROMISED_MORE_BYTES)))
			return reading to fail
		}

		fun open(body: ByteArray) {
			response.complete(Response.success(body.toResponseBody()))
		}

		/** The response starts with [received], then the connection drops with [error] while the body is read. */
		fun openThenBreak(received: ByteArray, error: IOException) {
			val cutOff = object : Source {
				private var sent = false

				override fun read(sink: Buffer, byteCount: Long): Long {
					if (sent) throw error
					sent = true
					sink.write(received)
					return received.size.toLong()
				}

				override fun timeout() = Timeout.NONE
				override fun close() {}
			}
			response.complete(Response.success(cutOff.buffer().asResponseBody(null, received.size + PROMISED_MORE_BYTES)))
		}

		fun fail() = failWithStatus(500)

		fun failWithStatus(status: Int) {
			response.complete(Response.error(status, "down".toResponseBody()))
		}

		/** The connection breaks before any response, as with no network. */
		fun failWithException(error: IOException) {
			response.completeExceptionally(error)
		}

		/** Returns once the request is waiting for its response. */
		fun awaitRequest() {
			check(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "request never reached the server" }
		}

		fun execute(): Response<ResponseBody> {
			entered.countDown()
			try {
				return response.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			} catch (e: ExecutionException) {
				throw e.cause ?: e
			}
		}
	}

	private class GatedCall(private val gate: Gate) : Call<ResponseBody> {
		override fun execute(): Response<ResponseBody> = gate.execute()
		override fun enqueue(callback: Callback<ResponseBody>) = throw UnsupportedOperationException()
		override fun isExecuted() = false
		override fun cancel() = gate.cancel()
		override fun isCanceled() = gate.cancelled.count == 0L
		override fun clone(): Call<ResponseBody> = this
		override fun request(): Request = Request.Builder().url("https://test.invalid/").build()
		override fun timeout(): Timeout = Timeout.NONE
	}

	/** Records the outcome of one download or import. */
	open class Recorder : UniPackDownloader.Listener, UniPackImporter.OnEventListener {
		@Volatile var installedFolder: File? = null
		@Volatile var error: Throwable? = null

		override fun onInstallStart() {}
		override fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {}
		override fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {}
		override fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {}
		override fun onImportStart(zip: File) {}
		override fun onImportStart() {}

		override fun onInstallComplete(folder: File, unipack: UniPack) {
			installedFolder = folder
		}

		override fun onImportComplete(folder: File, unipack: UniPack) {
			installedFolder = folder
		}

		override fun onException(throwable: Throwable) {
			error = throwable
		}
	}

	companion object {
		const val PACK_NAME = "pack"
		private const val TIMEOUT_MS = 10_000L
		private const val PROMISED_MORE_BYTES = 1_000L

		/** For tests that do not look at usage events. */
		fun unrecorded(): PackImportReport = UsageAnalytics { _, _ -> }.packImport(PackImportSource.FILE)

		/** A minimal valid pack whose every file differs by [title]. */
		fun packFiles(title: String): Map<String, ByteArray> = mapOf(
			"info" to "title=$title\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n".toByteArray(),
			"keySound" to "1 1 1 a.wav\n".toByteArray(),
			"sounds/a.wav" to title.repeat(512).toByteArray(),
		)

		/**
		 * [topFolder] wraps the pack in one folder and [directoryEntries] lists every folder as an
		 * entry ahead of the files; both together is how Finder zips a pack.
		 */
		fun packZip(title: String, topFolder: String? = null, directoryEntries: Boolean = false): ByteArray {
			val files = packFiles(title).mapKeys { (name, _) -> topFolder?.let { "$it/$name" } ?: name }
			val folders = if (directoryEntries) {
				files.keys.flatMap { path -> path.split('/').dropLast(1).runningReduce { parent, child -> "$parent/$child" } }.distinct()
			} else {
				emptyList()
			}
			val bytes = ByteArrayOutputStream()
			ZipOutputStream(bytes).use { zip ->
				folders.forEach { zip.putNextEntry(ZipEntry("$it/")); zip.closeEntry() }
				files.forEach { (path, data) ->
					zip.putNextEntry(ZipEntry(path))
					zip.write(data)
					zip.closeEntry()
				}
			}
			return bytes.toByteArray()
		}

		fun writePack(folder: File, title: String) {
			packFiles(title).forEach { (name, data) ->
				File(folder, name).apply { parentFile?.mkdirs() }.writeBytes(data)
			}
		}

		/** Relative path to SHA-256 of every file in [folder]. */
		fun contentHashes(folder: File): Map<String, String> =
			folder.walkTopDown().filter { it.isFile }.associate { it.relativeTo(folder).invariantSeparatorsPath to sha256(it.readBytes()) }

		fun expectedHashes(title: String): Map<String, String> = packFiles(title).mapValues { sha256(it.value) }

		private fun sha256(data: ByteArray): String =
			MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
	}
}
