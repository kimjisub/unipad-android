package com.kimjisub.launchpad.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.ui.compose.ImportProgressDialog
import com.kimjisub.launchpad.ui.compose.ImportResult
import com.kimjisub.launchpad.ui.compose.ImportResultDialog
import com.kimjisub.launchpad.ui.compose.MainPackPanelScreen
import com.kimjisub.launchpad.ui.compose.MainTotalPanelScreen
import com.kimjisub.launchpad.ui.compose.SearchField
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.ListCursor
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.PackSearch
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.tool.splitties.browse
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.ui.theme.Red
import com.kimjisub.launchpad.ui.theme.SkyBlue
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.viewmodel.MainPackPanelViewModel
import com.kimjisub.launchpad.viewmodel.MainTotalPanelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.activities.start
import java.io.File


class MainActivity : BaseActivity() {

	companion object {
		private const val FLAG_ANIMATION_MS = 500
		private const val GET_STARTED_URL = "https://unipad.io/docs/get-started"
	}

	// Compose state
	private val unipackList = mutableStateListOf<UniPackItem>()
	private var selectedItem by mutableStateOf<UniPackItem?>(null)
	private var lastPlayPath by mutableStateOf<String?>(null)
	private var listRefreshing by mutableStateOf(false)
	private var scrollToTopTrigger by mutableIntStateOf(0)
	private var scrollToCenterIndex by mutableIntStateOf(-1)
	private var scrollToCenterTrigger by mutableIntStateOf(0)
	private var currentSort by mutableStateOf<Pair<MainTotalPanelViewModel.SortMethod, Boolean>?>(null)
	private var importingState by mutableStateOf(false)
	private var importResult by mutableStateOf<ImportResult?>(null)
	private var deleteTargetItem by mutableStateOf<UniPackItem?>(null)
	private var deleteFailed by mutableStateOf(false)
	private var searchOpen by mutableStateOf(false)
	private var searchQuery by mutableStateOf("")

	// Filters the already-loaded list; typing never re-reads the pack folders.
	private val visibleList: List<UniPackItem> by derivedStateOf {
		PackSearch.filter(unipackList.toList(), searchQuery) {
			listOf(it.unipack.title, it.unipack.producerName)
		}
	}

	// ViewModels
	private lateinit var totalPanelVM: MainTotalPanelViewModel

	// Firebase
	private val fbStoreCount: FirebaseManager by lazy {
		val firebaseManager = FirebaseManager("storeCount")
		firebaseManager.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long? = dataSnapshot.getValue(Long::class.java)
				val prev = p.prevStoreCount
				if (data != prev) {
					Log.test("Store count changed: $prev -> $data")
				}
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})
		firebaseManager
	}

	// MIDI controller
	private val midiController: MidiController by lazy {
		object : MidiController() {
			override fun onAttach() {
				Log.driverCycle("MainActivity onConnected()")
				driver.sendClearLed()
				updateLP()
			}

			override fun onDetach() {
				Log.driverCycle("MainActivity onDisconnected()")
			}

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
				if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
					if (upDown) driver.sendPadLed(
						x, y, intArrayOf(40, 61).random()
					) else driver.sendPadLed(x, y, 0)
				}
			}

			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
				if (f == 0 && upDown) prev()
				else if (f == 1 && upDown) next()
				else if (f == 2 && upDown) currentClick()
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {}

			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) {
				if ((cmd == 7) && (sig == 46) && (note == 0) && (velocity == -9)) updateLP()
			}
		}
	}

	// Activity result launchers
	private val settingsActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			update()
		}
	private val storeActivityResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			update()
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		totalPanelVM = ViewModelProvider(
			this,
			MainTotalPanelViewModel.Factory(application, p, ws)
		)[MainTotalPanelViewModel::class.java]

		totalPanelVM.onSortChanged = { sort, order ->
			currentSort = Pair(sort, order)
			update()
		}

		val filePick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
			uri?.let { importUniPack(it) }
		}

		setContent {
			UniPadTheme {
				MainScreen(
					onSettingsClick = {
						settingsActivityResultLauncher.launch(
							Intent(applicationContext, SettingsActivity::class.java)
						)
					},
					onStoreClick = {
						storeActivityResultLauncher.launch(
							Intent(applicationContext, FBStoreActivity::class.java)
						)
					},
					// Same filter as iOS (.zip); picking an MP3 used to fail deep inside zip4j.
					onLoadUniPackClick = { filePick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
					onRestoreClick = {
						settingsActivityResultLauncher.launch(
							Intent(applicationContext, SettingsActivity::class.java).apply {
								putExtra(SettingsActivity.EXTRA_INITIAL_CATEGORY, SettingsActivity.CATEGORY_STORAGE)
								putExtra(SettingsActivity.EXTRA_HIGHLIGHT_BACKUP, true)
							}
						)
					},
					onItemClick = { item -> togglePlay(item) },
					onPlayClick = { item -> pressPlay(item) },
				)

				if (importingState) {
					ImportProgressDialog()
				}

				importResult?.let { result ->
					ImportResultDialog(
						result = result,
						onDismiss = { importResult = null },
						onPlayNow = { success ->
							importResult = null
							playUniPack(success.folder.path)
						},
					)
				}

				deleteTargetItem?.let { item ->
					androidx.compose.material3.AlertDialog(
						onDismissRequest = { deleteTargetItem = null },
						title = { Text(stringResource(string.warning)) },
						text = { Text(stringResource(string.doYouWantToDeleteUniPack)) },
						confirmButton = {
							TextButton(onClick = {
								deleteTargetItem = null
								selectedItem = null
								listRefreshing = true
								lifecycleScope.launch(Dispatchers.IO) {
									val result = ws.deleteUnipack(item.unipack)
									withContext(Dispatchers.Main) {
										listRefreshing = false
										deleteFailed = result != WorkspaceManager.DeleteResult.DELETED
										update()
									}
								}
							}) {
								Text(stringResource(string.accept))
							}
						},
						dismissButton = {
							TextButton(onClick = { deleteTargetItem = null }) {
								Text(stringResource(string.cancel))
							}
						},
					)
				}

				if (deleteFailed) {
					androidx.compose.material3.AlertDialog(
						onDismissRequest = { deleteFailed = false },
						title = { Text(stringResource(string.error)) },
						text = { Text(stringResource(string.errOccur)) },
						confirmButton = {
							TextButton(onClick = { deleteFailed = false }) {
								Text(stringResource(android.R.string.ok))
							}
						},
					)
				}
			}
		}

		checkThings()
	}

	// List management

	private fun update() {
		if (listRefreshing) return
		listRefreshing = true

		lifecycleScope.launch(Dispatchers.IO) {
			val newList = ws.getUnipacks()

			val comparator: Comparator<UniPackItem> = currentSort?.let { sort ->
				Comparator { a, b ->
					sort.first.comparator.compare(a, b) * if (p.sortOrder) 1 else -1
				}
			} ?: Comparator { _, _ -> 0 }

			val sorted = try {
				newList
					.distinctBy { it.unipack.getPathString() }
					.sortedWith(comparator)
			} catch (e: RuntimeException) {
				Log.err("List sort failed", e)
				newList
			}

			withContext(Dispatchers.Main) {
				val selectedPath = selectedItem?.unipack?.getPathString()
				unipackList.clear()
				unipackList.addAll(sorted)

				if (selectedPath != null) {
					selectedItem = unipackList.firstOrNull { it.unipack.getPathString() == selectedPath }
					if (selectedItem == null) lastPlayPath = null
				}
				if (unipackList.isEmpty()) closeSearch() else dropSelectionOutsideResults()

				scrollToTopTrigger++
				listRefreshing = false
			}
		}

		totalPanelVM.update()
	}

	private fun togglePlay(target: UniPackItem?, scrollToCenter: Boolean = false) {
		if (target == null) {
			selectedItem = null
			return
		}

		val path = target.unipack.getPathString()
		val index = visibleList.indexOfFirst { it.unipack.getPathString() == path }
		if (index == -1) return

		if (selectedItem?.unipack?.getPathString() == path) {
			selectedItem = null
		} else {
			lastPlayPath = path
			selectedItem = target
			if (scrollToCenter) {
				scrollToCenterIndex = index
				scrollToCenterTrigger++
			}
		}
		showSelectLPUI()
	}

	private fun pressPlay(item: UniPackItem) = playUniPack(item.unipack.getPathString())

	private fun playUniPack(path: String) {
		start<PlayActivity> {
			putExtra("path", path)
		}
	}

	private fun deselect(): Boolean {
		return if (selectedItem != null) {
			selectedItem = null
			showSelectLPUI()
			true
		} else false
	}

	// MIDI navigation

	// Moves only within the search results, so the launchpad never picks a pack the list hides.
	private fun cursor() = ListCursor(visibleList.map { it.unipack.getPathString() }, lastPlayPath)

	private fun visibleItem(path: String?): UniPackItem? =
		path?.let { visibleList.firstOrNull { item -> item.unipack.getPathString() == it } }

	private fun next() {
		visibleItem(cursor().next())?.let { togglePlay(it, scrollToCenter = true) }
	}

	private fun prev() {
		visibleItem(cursor().prev())?.let { togglePlay(it, scrollToCenter = true) }
	}

	private fun currentClick() {
		visibleItem(cursor().current())?.let { pressPlay(it) }
	}

	// Search

	private fun updateSearchQuery(query: String) {
		searchQuery = query
		scrollToTopTrigger++
		dropSelectionOutsideResults()
	}

	private fun toggleSearch() {
		if (searchOpen) closeSearch() else searchOpen = true
	}

	private fun closeSearch() {
		searchOpen = false
		if (searchQuery.isNotEmpty()) updateSearchQuery("")
	}

	private fun dropSelectionOutsideResults() {
		val path = selectedItem?.unipack?.getPathString()
		if (path != null && visibleItem(path) == null) {
			selectedItem = null
			lastPlayPath = null
		}
		showSelectLPUI()
	}

	// UniPack import

	private fun importUniPack(unipackUri: Uri) {
		UniPackImporter(
			context = applicationContext,
			uri = unipackUri,
			workspace = ws.downloadWorkspace.file,
			onEventListener = object : UniPackImporter.OnEventListener {
				override fun onImportStart() {
					importingState = true
				}

				override fun onImportComplete(folder: File, unipack: UniPack) {
					importingState = false
					when (unipack.errorDetail) {
						null -> {
							importResult = ImportResult.Success(folder, unipack)
							update()
						}

						else -> {
							importResult = ImportResult.Warning(unipack.errorDetail.orEmpty())
							// The pack was kept; without this it only appeared on the next resume.
							update()
						}
					}
				}

				override fun onException(throwable: Throwable) {
					importingState = false
					importResult = ImportResult.Error(throwable.toString())
				}
			},
			scope = lifecycleScope,
		)
	}

	// Version check

	private fun checkThings() {
		versionCheck()
	}

	private fun versionCheck() {
		try {
			val thisVersion = BuildConfig.VERSION_NAME
			if (thisVersion.contains('b')) return

			val remoteConfig = FirebaseRemoteConfig.getInstance()
			val versionListString = remoteConfig.getString("android_version")
			val versionList =
				Gson().fromJson(versionListString, Array<String>::class.java).toList()

			Log.test("versionList $versionList")
			val latestVersion = versionList.contains(thisVersion)
			Log.test("thisVersion $thisVersion")
			Log.test("latestVersion $latestVersion")

			if (!latestVersion) {
				totalPanelVM.updateAvailable = true
			}
		} catch (e: RuntimeException) {
			Log.test("versionCheck error $e")
		}
	}

	// Launchpad LED controller

	private fun updateLP() {
		showWatermark()
		showSelectLPUI()
	}

	private fun showSelectLPUI() {
		val cursor = cursor()
		if (cursor.hasPrev) driver.sendFunctionKeyLed(0, 63)
		else driver.sendFunctionKeyLed(0, 5)
		if (cursor.hasCurrent) driver.sendFunctionKeyLed(2, 61)
		else driver.sendFunctionKeyLed(2, 0)
		if (cursor.hasNext) driver.sendFunctionKeyLed(1, 63)
		else driver.sendFunctionKeyLed(1, 5)
	}

	private fun showWatermark() {
		driver.sendPadLed(3, 3, 61)
		driver.sendPadLed(3, 4, 40)
		driver.sendPadLed(4, 3, 40)
		driver.sendPadLed(4, 4, 61)
	}

	// Delete confirmation

	private fun showDeleteConfirmation(item: UniPackItem) {
		deleteTargetItem = item
	}

	// Activity lifecycle

	override fun onResume() {
		super.onResume()
		ws.migrateOldAppStorageFolder()
		checkThings()
		controller = midiController
		fbStoreCount.attachEventListener(true)
		update()
	}

	override fun onPause() {
		super.onPause()
		removeController(midiController)
		fbStoreCount.attachEventListener(false)
	}

	override fun onDestroy() {
		super.onDestroy()
		removeController(midiController)
	}

	// Compose UI

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun MainScreen(
		onSettingsClick: () -> Unit,
		onStoreClick: () -> Unit,
		onLoadUniPackClick: () -> Unit,
		onRestoreClick: () -> Unit,
		onItemClick: (UniPackItem) -> Unit,
		onPlayClick: (UniPackItem) -> Unit,
	) {
		BackHandler(enabled = searchOpen) {
			closeSearch()
		}
		BackHandler(enabled = selectedItem != null) {
			deselect()
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background),
		) {
			// Main two-panel layout
			Row(modifier = Modifier.fillMaxSize()) {
				// Left panel (weight 2 = 40%)
				Box(
					modifier = Modifier
						.weight(2f)
						.fillMaxHeight()
						.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
				) {
					Crossfade(
						targetState = selectedItem,
						label = "panel",
					) { item ->
						if (item == null) {
							MainTotalPanelScreen(
								vm = totalPanelVM,
								onSettingsClick = onSettingsClick,
								onUpdateClick = { browse("https://play.google.com/store/apps/details?id=$packageName") },
							)
						} else {
							val packVM = remember(item.unipack.getPathString()) {
								ViewModelProvider(
									this@MainActivity,
									MainPackPanelViewModel.Factory(application, item.unipack)
								)["pack_${item.unipack.getPathString()}", MainPackPanelViewModel::class.java]
							}

							// Handle delete event from VM
							if (packVM.deleteRequested) {
								packVM.clearDeleteRequest()
								showDeleteConfirmation(item)
							}

							MainPackPanelScreen(vm = packVM)
						}
					}
				}

				// Right panel (weight 3 = 60%)
				Box(
					modifier = Modifier
						.weight(3f)
						.fillMaxHeight(),
				) {
					PullToRefreshBox(
						isRefreshing = listRefreshing,
						onRefresh = { update() },
					) {
						if (unipackList.isEmpty() && !listRefreshing) {
							// Empty state; scrolls so the guide stays reachable on short screens
							BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
								Column(
									modifier = Modifier
										.verticalScroll(rememberScrollState())
										.fillMaxWidth()
										.heightIn(min = maxHeight)
										.padding(horizontal = 16.dp, vertical = 16.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.Center,
								) {
									EmptyGuide(onGuideClick = { browse(GET_STARTED_URL) })
									Spacer(Modifier.size(16.dp))
									GuidingActions(
										onStoreClick = onStoreClick,
										onLoadUniPackClick = onLoadUniPackClick,
										onRestoreClick = onRestoreClick,
									)
								}
							}
						} else {
							val listState = rememberLazyListState()

							LaunchedEffect(scrollToTopTrigger) {
								listState.scrollToItem(0)
							}

							LaunchedEffect(scrollToCenterTrigger) {
								if (scrollToCenterIndex < 0) return@LaunchedEffect
								val visibleCount = listState.layoutInfo.visibleItemsInfo.size
								val targetIndex = maxOf(0, scrollToCenterIndex - visibleCount / 2)
								listState.animateScrollToItem(targetIndex)
							}

							Column {
								if (unipackList.isNotEmpty()) {
									SortBar(
										vm = totalPanelVM,
										searchOpen = searchOpen,
										onSearchClick = { toggleSearch() },
										onStoreClick = onStoreClick,
										onLoadUniPackClick = onLoadUniPackClick,
									)
									if (searchOpen) {
										MainSearchField()
									}
								}

								if (visibleList.isEmpty() && unipackList.isNotEmpty()) {
									NoSearchResults(
										query = searchQuery,
										onClearClick = { updateSearchQuery("") },
										modifier = Modifier.weight(1f),
									)
								} else LazyColumn(
									state = listState,
									contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp),
									verticalArrangement = Arrangement.spacedBy(8.dp),
									modifier = Modifier.weight(1f),
								) {
									items(
										visibleList,
										key = { it.unipack.getPathString() },
									) { item ->
										val isSelected =
											selectedItem?.unipack?.getPathString() == item.unipack.getPathString()
										UnipackListItem(
											item = item,
											isSelected = isSelected,
											onItemClick = { onItemClick(item) },
											onPlayClick = { onPlayClick(item) },
										)
									}
									item(key = "_footer") {
										GuidingActions(
											onStoreClick = onStoreClick,
											onLoadUniPackClick = onLoadUniPackClick,
											onRestoreClick = onRestoreClick,
											modifier = Modifier.padding(
												horizontal = 16.dp,
												vertical = 8.dp,
											),
										)
									}
								}
							}
						}
					}
				}
			}

		}
	}

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun SortBar(
		vm: MainTotalPanelViewModel,
		searchOpen: Boolean,
		onSearchClick: () -> Unit,
		onStoreClick: () -> Unit,
		onLoadUniPackClick: () -> Unit,
	) {
		val sortMethod = vm.sortMethod
		val sortOrder = vm.sortOrder

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			var expanded by remember { mutableStateOf(false) }
			ExposedDropdownMenuBox(
				expanded = expanded,
				onExpandedChange = { expanded = it },
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.background(
							MaterialTheme.colorScheme.surfaceContainerHighest,
							RoundedCornerShape(6.dp),
						)
						.clickable { expanded = true }
						.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
						.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
				) {
					Text(
						text = vm.sortMethodTitleList.getOrElse(sortMethod) { "" },
						fontSize = 12.sp,
						color = Gray1,
					)
					Icon(
						imageVector = if (sortOrder) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
						contentDescription = null,
						tint = Gray1,
						modifier = Modifier
							.size(18.dp)
							.clickable { vm.updateSortOrder(!sortOrder) },
					)
				}
				ExposedDropdownMenu(
					expanded = expanded,
					onDismissRequest = { expanded = false },
				) {
					vm.sortMethodTitleList.forEachIndexed { index, title ->
						DropdownMenuItem(
							text = { Text(title, fontSize = 13.sp) },
							onClick = {
								vm.updateSortMethod(index)
								expanded = false
							},
						)
					}
				}
			}

			Spacer(Modifier.weight(1f))

			IconButton(
				onClick = onSearchClick,
				modifier = Modifier
					.size(36.dp)
					.background(
						if (searchOpen) SkyBlue.copy(alpha = 0.15f) else Color.Transparent,
						CircleShape,
					),
			) {
				Icon(
					imageVector = Icons.Filled.Search,
					contentDescription = stringResource(string.transfer_search),
					modifier = Modifier.size(20.dp),
					tint = if (searchOpen) SkyBlue else Gray1,
				)
			}
			IconButton(onClick = onStoreClick, modifier = Modifier.size(36.dp)) {
				Icon(
					painter = painterResource(R.drawable.baseline_shopping_basket_white_24),
					contentDescription = stringResource(string.store),
					modifier = Modifier.size(20.dp),
					tint = Gray1,
				)
			}
			IconButton(onClick = onLoadUniPackClick, modifier = Modifier.size(36.dp)) {
				Icon(
					painter = painterResource(R.drawable.baseline_folder_open_white_24),
					contentDescription = stringResource(string.import_unipack),
					modifier = Modifier.size(20.dp),
					tint = Gray1,
				)
			}
		}
	}

	@Composable
	private fun MainSearchField() {
		val focusRequester = remember { FocusRequester() }
		val keyboard = LocalSoftwareKeyboardController.current
		SearchField(
			query = searchQuery,
			onQueryChange = { updateSearchQuery(it) },
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
				.focusRequester(focusRequester),
		)
		LaunchedEffect(Unit) {
			focusRequester.requestFocus()
			keyboard?.show()
		}
	}

	@Composable
	private fun NoSearchResults(
		query: String,
		onClearClick: () -> Unit,
		modifier: Modifier = Modifier,
	) {
		BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.verticalScroll(rememberScrollState())
					.fillMaxWidth()
					.heightIn(min = maxHeight)
					.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Icon(
					imageVector = Icons.Filled.Search,
					contentDescription = null,
					modifier = Modifier.size(40.dp),
					tint = Gray1,
				)
				Spacer(Modifier.size(12.dp))
				Text(
					text = stringResource(string.main_search_no_results, query.trim()),
					fontSize = 14.sp,
					color = Gray1,
					textAlign = TextAlign.Center,
				)
				Spacer(Modifier.size(12.dp))
				TextButton(
					onClick = onClearClick,
					modifier = Modifier.background(
						MaterialTheme.colorScheme.surfaceContainerHighest,
						RoundedCornerShape(8.dp),
					),
				) {
					Text(
						text = stringResource(string.main_search_clear),
						fontSize = 13.sp,
						color = MaterialTheme.colorScheme.onBackground,
					)
				}
			}
		}
	}

	@Composable
	private fun EmptyGuide(onGuideClick: () -> Unit) {
		val steps = listOf(
			stringResource(
				string.guide_empty_step_get,
				stringResource(string.guide_download_new),
				stringResource(string.guide_import_external),
			),
			stringResource(string.guide_empty_step_play, stringResource(string.unipack_play)),
			stringResource(string.guide_empty_step_pads),
		)

		Column(modifier = Modifier.fillMaxWidth()) {
			Text(
				text = stringResource(string.guide_empty_title),
				fontSize = 16.sp,
				color = MaterialTheme.colorScheme.onBackground,
			)
			Spacer(Modifier.size(8.dp))
			steps.forEachIndexed { index, step ->
				Row(modifier = Modifier.padding(vertical = 2.dp)) {
					Text(text = "${index + 1}.", fontSize = 12.sp, color = Gray1)
					Spacer(Modifier.size(6.dp))
					Text(text = step, fontSize = 12.sp, color = Gray1)
				}
			}
			TextButton(
				onClick = onGuideClick,
				contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
			) {
				Text(
					text = stringResource(string.guide_empty_link),
					fontSize = 12.sp,
					color = SkyBlue,
					textDecoration = TextDecoration.Underline,
				)
			}
		}
	}

	@Composable
	private fun GuidingActions(
		onStoreClick: () -> Unit,
		onLoadUniPackClick: () -> Unit,
		onRestoreClick: () -> Unit,
		modifier: Modifier = Modifier,
	) {
		Row(
			modifier = modifier
				.fillMaxWidth()
				.height(IntrinsicSize.Min),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			GuidingChip(
				icon = R.drawable.baseline_shopping_basket_white_24,
				text = stringResource(string.guide_download_new),
				onClick = onStoreClick,
				modifier = Modifier.weight(1f),
			)
			GuidingChip(
				icon = R.drawable.baseline_folder_open_white_24,
				text = stringResource(string.guide_import_external),
				onClick = onLoadUniPackClick,
				modifier = Modifier.weight(1f),
			)
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
				GuidingChip(
					icon = R.drawable.baseline_settings_white_24,
					text = stringResource(string.guide_restore_packs),
					onClick = onRestoreClick,
					modifier = Modifier.weight(1f),
				)
			}
		}
	}

	@Composable
	private fun GuidingChip(
		icon: Int,
		text: String,
		onClick: () -> Unit,
		modifier: Modifier = Modifier,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = modifier
				.fillMaxHeight()
				.background(
					MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
					RoundedCornerShape(8.dp),
				)
				.clickable(onClick = onClick)
				.padding(horizontal = 10.dp, vertical = 10.dp),
		) {
			Icon(
				painter = painterResource(icon),
				contentDescription = null,
				modifier = Modifier.size(18.dp),
				tint = Gray1,
			)
			Spacer(Modifier.size(6.dp))
			Text(
				text = text,
				fontSize = 11.sp,
				color = Gray1,
				textAlign = TextAlign.Center,
			)
		}
	}

	@Composable
	private fun UnipackListItem(
		item: UniPackItem,
		isSelected: Boolean,
		onItemClick: () -> Unit,
		onPlayClick: () -> Unit,
	) {
		val isError = item.unipack.criticalError
		val defaultColor = if (isError) Red else SkyBlue

		val flagColor by animateColorAsState(
			targetValue = if (isSelected) Red else defaultColor,
			animationSpec = tween(FLAG_ANIMATION_MS),
			label = "flagColor",
		)
		val contentOffset by animateDpAsState(
			targetValue = if (isSelected) 95.dp else 0.dp,
			animationSpec = tween(FLAG_ANIMATION_MS),
			label = "contentOffset",
		)

		// Observe bookmark from LiveData
		val unipackEnt by item.unipackENT.observeAsState()
		val isBookmarked = unipackEnt?.bookmark == true

		com.kimjisub.launchpad.ui.compose.UnipackListItem(
			title = if (isError) stringResource(string.errOccur) else item.unipack.title,
			subtitle = if (isError) item.unipack.getPathString() else item.unipack.producerName,
			hasLed = item.unipack.keyLedExist,
			hasAutoPlay = item.unipack.autoPlayExist,
			isBookmarked = isBookmarked,
			flagColor = flagColor,
			flagWidth = 105.dp,
			contentStartPadding = 10.dp + contentOffset,
			flagClickable = isSelected,
			onFlagClick = onPlayClick,
			flagContent = {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.Center,
				) {
					Icon(
						imageVector = Icons.Filled.PlayArrow,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.size(24.dp),
					)
					Text(
						text = stringResource(string.unipack_play),
						color = Color.White,
						fontSize = 13.sp,
					)
				}
			},
			onClick = onItemClick,
		)
	}

}
