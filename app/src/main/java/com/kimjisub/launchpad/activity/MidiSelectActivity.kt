package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadPRO
import com.kimjisub.launchpad.midi.driver.LaunchpadPROCFW
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
import com.kimjisub.launchpad.midi.driver.Matrix
import com.kimjisub.launchpad.midi.driver.MidiFighter
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import kotlin.reflect.KClass

class MidiSelectActivity : BaseActivity() {

	private val isConnected = mutableStateOf(false)
	private val selectedIndex = mutableIntStateOf(0)
	private val targetModelIndex = mutableStateOf<Int?>(null)
	private val dualPadModeEnabled = mutableStateOf(false)
	private val reflectedModeEnabled = mutableStateOf(false)
	private val reflectedSwapSides = mutableStateOf(false)
	private val connectedSessions = mutableStateOf<List<MidiConnection.SessionSummary>>(emptyList())
	private val selectedSessionId = mutableStateOf<Int?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		selectedSessionId.value = savedInstanceState?.takeIf { it.containsKey("selectedSessionId") }
			?.getInt("selectedSessionId")
		readConnectionState()

		setContent {
			UniPadTheme {
				MidiSelectScreen(
					isConnected = isConnected.value,
					selectedIndex = selectedIndex.intValue,
					targetModelIndex = targetModelIndex.value,
					dualPadModeEnabled = dualPadModeEnabled.value,
					reflectedModeEnabled = reflectedModeEnabled.value,
					reflectedSwapSides = reflectedSwapSides.value,
					connectedSessions = connectedSessions.value,
					selectedSessionId = selectedSessionId.value,
					onSessionTargetSelect = { sessionId ->
						selectedSessionId.value = sessionId
						updateSelectedIndexForTarget()
					},
					onDeviceSelect = { index ->
						selectedIndex.intValue = index
						targetModelIndex.value = index
						val driver = midiDevices[index].createDriver()
						val targetSessionId = selectedSessionId.value
						if (targetSessionId != null && connectedSessions.value.size > 1) {
							// More than one pad connected - target the specific device
							// picked above, never disturbing the other connected pad.
							MidiConnection.setDriverForSession(targetSessionId, driver)
							connectedSessions.value = MidiConnection.connectedSessions
						} else {
							MidiConnection.driver = driver
						}
					},
					onDualPadModeChange = { enabled ->
						dualPadModeEnabled.value = enabled
						MidiConnection.dualPadModeEnabled = enabled
					},
					onReflectedModeChange = { enabled ->
						reflectedModeEnabled.value = enabled
						MidiConnection.reflectedModeEnabled = enabled
					},
					onReflectedSwapSidesChange = { swapped ->
						reflectedSwapSides.value = swapped
						MidiConnection.reflectedSwapSides = swapped
					},
					onClose = { finish() },
				)
			}
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		selectedSessionId.value?.let { outState.putInt("selectedSessionId", it) }
		super.onSaveInstanceState(outState)
	}

	override fun onResume() {
		super.onResume()
		readConnectionState()
	}

	// Mirrors MidiConnection into the screen state without connecting or applying a driver.
	// Keeps the targeted pad while it stays connected, otherwise targets the primary pad.
	private fun readConnectionState() {
		isConnected.value = MidiConnection.connectedDevice != null
		connectedSessions.value = MidiConnection.connectedSessions
		if (connectedSessions.value.none { it.sessionId == selectedSessionId.value }) {
			selectedSessionId.value = connectedSessions.value.firstOrNull { it.isPrimary }?.sessionId
				?: connectedSessions.value.firstOrNull()?.sessionId
		}
		dualPadModeEnabled.value = MidiConnection.dualPadModeEnabled
		reflectedModeEnabled.value = MidiConnection.reflectedModeEnabled
		reflectedSwapSides.value = MidiConnection.reflectedSwapSides
		updateSelectedIndexForTarget()
	}

	private fun updateSelectedIndexForTarget() {
		targetModelIndex.value =
			midiModelIndexForTarget(connectedSessions.value, selectedSessionId.value, MidiConnection.driver::class)
		targetModelIndex.value?.let { selectedIndex.intValue = it }
	}
}

// The model highlighted for the targeted pad, or for MidiConnection.driver when no pad is
// targeted (single pad or none connected). Null when the driver is not in the model list.
internal fun midiModelIndexForTarget(
	sessions: List<MidiConnection.SessionSummary>,
	targetSessionId: Int?,
	fallbackDriverClass: KClass<out DriverRef>,
): Int? {
	val targetClass = sessions.firstOrNull { it.sessionId == targetSessionId }?.driverClass
		?: fallbackDriverClass
	return midiDevices.indexOfFirst { it.driverClass == targetClass }.takeIf { it >= 0 }
}

// The help names the targeted pad's model; a driver outside the list (no pad yet) is unknown,
// even though the grid keeps its last highlight.
internal fun midiHelpModelNameResId(targetModelIndex: Int?): Int =
	targetModelIndex?.let { midiDevices[it].nameResId } ?: android.R.string.unknownName

private data class MidiDeviceData(
	val iconResId: Int,
	val nameResId: Int,
	val driverClass: KClass<out DriverRef>,
	val createDriver: () -> DriverRef,
)

private val midiDevices = listOf(
	MidiDeviceData(R.drawable.midi_lp_s, R.string.midi_lp_s, LaunchpadS::class) { LaunchpadS() },
	MidiDeviceData(R.drawable.midi_lp_mk2, R.string.midi_lp_mk2, LaunchpadMK2::class) { LaunchpadMK2() },
	MidiDeviceData(R.drawable.midi_lp_pro, R.string.midi_lp_pro, LaunchpadPRO::class) { LaunchpadPRO() },
	MidiDeviceData(R.drawable.midi_lp_pro, R.string.midi_lp_pro_cfw, LaunchpadPROCFW::class) { LaunchpadPROCFW() },
	MidiDeviceData(R.drawable.midi_lp_x, R.string.midi_lp_x, LaunchpadX::class) { LaunchpadX() },
	MidiDeviceData(R.drawable.midi_lp_mini_mk3, R.string.midi_lp_mini_mk3, LaunchpadMiniMK3::class) { LaunchpadMiniMK3() },
	MidiDeviceData(R.drawable.midi_lp_mk3, R.string.midi_lp_mk3, LaunchpadMK3::class) { LaunchpadMK3() },
	MidiDeviceData(R.drawable.midi_midifighter, R.string.midi_midi_fighter, MidiFighter::class) { MidiFighter() },
	MidiDeviceData(R.drawable.midi_matrix, R.string.midi_matrix, Matrix::class) { Matrix() },
	MidiDeviceData(R.drawable.midi_master_keyboard, R.string.midi_master_keyboard, MasterKeyboard::class) { MasterKeyboard() },
)

@Composable
private fun MidiSelectScreen(
	isConnected: Boolean,
	selectedIndex: Int,
	targetModelIndex: Int?,
	dualPadModeEnabled: Boolean,
	reflectedModeEnabled: Boolean,
	reflectedSwapSides: Boolean,
	connectedSessions: List<MidiConnection.SessionSummary>,
	selectedSessionId: Int?,
	onSessionTargetSelect: (Int) -> Unit,
	onDeviceSelect: (Int) -> Unit,
	onDualPadModeChange: (Boolean) -> Unit,
	onReflectedModeChange: (Boolean) -> Unit,
	onReflectedSwapSidesChange: (Boolean) -> Unit,
	onClose: () -> Unit,
) {
	var helpOpen by rememberSaveable { mutableStateOf(false) }
	// Screen-only intent, separate from the driver (which can also be chosen by USB detection).
	// Retain each target's explicit choice without adding a stored preference or a command.
	var explicitChoices by rememberSaveable(
		stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
	) { mutableStateOf<List<String>>(emptyList()) }
	val targetKey = "${selectedSessionId}:"
	val explicitlySelected = "$targetKey$targetModelIndex" in explicitChoices
	if (helpOpen) {
		MidiConnectionHelpDialog(
			modelNameResId = midiHelpModelNameResId(targetModelIndex),
			explicitlySelected = explicitlySelected,
			onClose = { helpOpen = false },
		)
	}
	Row(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		// Left panel (~35%) - Selected device preview
		Column(
			modifier = Modifier
				.weight(0.35f)
				.fillMaxHeight()
				.padding(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Column(
				modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {

				Text(
					text = if (isConnected)
						stringResource(R.string.launchpadConnecting)
					else
						stringResource(R.string.midiDevicesNotDetected),
					color = if (isConnected)
						MaterialTheme.colorScheme.onBackground
					else
						MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.titleSmall,
				)

				// With two pads connected, picking a model below needs to know which
				// physical device it applies to - pick that here first.
				if (connectedSessions.size > 1) {
					Spacer(modifier = Modifier.height(12.dp))
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						connectedSessions.forEach { session ->
							val isSelected = session.sessionId == selectedSessionId
							Text(
								text = if (session.isPrimary) "1: ${session.deviceName}" else "2: ${session.deviceName}",
								color = if (isSelected)
									MaterialTheme.colorScheme.primary
								else
									MaterialTheme.colorScheme.onSurfaceVariant,
								style = MaterialTheme.typography.labelMedium,
								modifier = Modifier
									.clip(RoundedCornerShape(8.dp))
									.background(
										if (isSelected)
											MaterialTheme.colorScheme.surface
										else
											Color.Transparent
									)
									.border(
										1.dp,
										if (isSelected)
											MaterialTheme.colorScheme.primary
										else
											MaterialTheme.colorScheme.surfaceVariant,
										RoundedCornerShape(8.dp),
									)
									.clickable { onSessionTargetSelect(session.sessionId) }
									.padding(horizontal = 10.dp, vertical = 6.dp),
							)
						}
					}
				}

				Spacer(modifier = Modifier.height(12.dp))

				Crossfade(
					targetState = selectedIndex,
					label = "devicePreview",
				) { index ->
					val device = midiDevices[index]
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Image(
							painter = painterResource(device.iconResId),
							contentDescription = stringResource(device.nameResId),
							modifier = Modifier.size(120.dp),
						)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							text = stringResource(device.nameResId),
							color = MaterialTheme.colorScheme.onBackground,
							style = MaterialTheme.typography.titleMedium,
							textAlign = TextAlign.Center,
						)
					}
				}

				Spacer(modifier = Modifier.height(16.dp))

				HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

				Spacer(modifier = Modifier.height(16.dp))

				// Dual pad mode - pick this BEFORE plugging in a second Launchpad.
				// Off: only the first device found connects (original single-pad behavior).
				// On: a second device connecting is accepted as a mirrored session.
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.midi_dual_pad_mode),
						modifier = Modifier.weight(1f),
						color = MaterialTheme.colorScheme.onBackground,
						style = MaterialTheme.typography.bodyMedium,
					)
					Switch(
						checked = dualPadModeEnabled,
						onCheckedChange = onDualPadModeChange,
					)
				}

				Spacer(modifier = Modifier.height(8.dp))

				// Only meaningful once a second pad is connected via Dual Pad Mode - flips
				// the second pad's grid left-right instead of duplicating it.
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.midi_reflected),
						modifier = Modifier.weight(1f),
						color = if (dualPadModeEnabled)
							MaterialTheme.colorScheme.onBackground
						else
							MaterialTheme.colorScheme.onSurfaceVariant,
						style = MaterialTheme.typography.bodyMedium,
					)
					Switch(
						checked = reflectedModeEnabled,
						onCheckedChange = onReflectedModeChange,
						enabled = dualPadModeEnabled,
					)
				}

				// "Primary" (the unflipped side) is whichever pad connected first, which
				// may not match physical left/right - use this to correct it if the
				// mirror feels backwards.
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.midi_swap_sides),
						modifier = Modifier.weight(1f),
						color = if (dualPadModeEnabled && reflectedModeEnabled)
							MaterialTheme.colorScheme.onBackground
						else
							MaterialTheme.colorScheme.onSurfaceVariant,
						style = MaterialTheme.typography.bodyMedium,
					)
					Switch(
						checked = reflectedSwapSides,
						onCheckedChange = onReflectedSwapSidesChange,
						enabled = dualPadModeEnabled && reflectedModeEnabled,
					)
				}

			}
			Spacer(modifier = Modifier.height(8.dp))
			Button(
				onClick = { helpOpen = true },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(text = stringResource(R.string.midi_help_title), textAlign = TextAlign.Center)
			}

			Button(
				onClick = { onClose() },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(text = stringResource(android.R.string.ok))
			}
		}

		// Right panel (~65%) - Device grid
		LazyVerticalGrid(
			columns = GridCells.Fixed(4),
			modifier = Modifier
				.weight(0.65f)
				.fillMaxHeight()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(vertical = 8.dp),
		) {
			itemsIndexed(midiDevices) { index, device ->
				DeviceCard(
					device = device,
					isSelected = index == selectedIndex,
					onClick = {
						explicitChoices = explicitChoices.filterNot { it.startsWith(targetKey) } + "$targetKey$index"
						onDeviceSelect(index)
					},
				)
			}
		}
	}
}

@Composable
private fun DeviceCard(
	device: MidiDeviceData,
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val borderColor by animateColorAsState(
		targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
		label = "borderColor",
	)
	val backgroundColor by animateColorAsState(
		targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
		label = "backgroundColor",
	)

	Column(
		modifier = Modifier
			.clip(RoundedCornerShape(12.dp))
			.background(backgroundColor)
			.border(2.dp, borderColor, RoundedCornerShape(12.dp))
			.clickable { onClick() }
			.padding(12.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Image(
			painter = painterResource(device.iconResId),
			contentDescription = stringResource(device.nameResId),
			modifier = Modifier.size(48.dp),
		)
		Spacer(modifier = Modifier.height(6.dp))
		Text(
			text = stringResource(device.nameResId),
			color = MaterialTheme.colorScheme.onSurface,
			fontSize = 11.sp,
			textAlign = TextAlign.Center,
			minLines = 2,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
