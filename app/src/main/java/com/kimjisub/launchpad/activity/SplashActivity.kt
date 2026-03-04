package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.string
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.activities.start

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
	companion object {
		private const val SPLASH_DISPLAY_TIME_MS = 2000L
	}

	// Timer
	private var splashJob: Job? = null
	private var showPermissionDialog by mutableStateOf(false)

	// Permission request launcher
	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		val allGranted = permissions.entries.all { it.value }

		if (allGranted) {
			proceedToMain()
		} else {
			val deniedPermissions = permissions.entries
				.filter { !it.value }
				.map { it.key }

			if (deniedPermissions.any { shouldShowRequestPermissionRationale(it) }) {
				showPermissionRationaleDialog()
			} else {
				proceedToMain()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			SplashScreen()

			if (showPermissionDialog) {
				AlertDialog(
					onDismissRequest = {},
					title = { Text(stringResource(string.permissionRequire)) },
					text = { Text(stringResource(string.permissionDenied)) },
					confirmButton = {
						TextButton(onClick = {
							showPermissionDialog = false
							proceedToMain()
						}) {
							Text(stringResource(android.R.string.ok))
						}
					},
				)
			}
		}

		checkAndRequestPermissions()
	}

	private fun checkAndRequestPermissions() {
		// Android 11+ (API 30+): No runtime storage permission needed
		// App uses getExternalFilesDir() (no permission) + SAF for Documents access
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			proceedToMain()
			return
		}

		// Android 10 (API 29): Need storage permissions for Documents/Unipad access
		val permissionsToRequest = arrayOf(
			permission.READ_EXTERNAL_STORAGE,
			permission.WRITE_EXTERNAL_STORAGE,
		).filter {
			ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
		}

		if (permissionsToRequest.isEmpty()) {
			proceedToMain()
		} else {
			permissionLauncher.launch(permissionsToRequest.toTypedArray())
		}
	}

	private fun showPermissionRationaleDialog() {
		showPermissionDialog = true
	}

	private fun proceedToMain() {
		splashJob = lifecycleScope.launch {
			delay(SPLASH_DISPLAY_TIME_MS)
			finish()
			start<MainActivity>()
		}
	}

	override fun onStop() {
		super.onStop()
		splashJob?.cancel()
	}
}

@Composable
private fun SplashScreen() {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = painterResource(R.drawable.logo_unipad_text),
			contentDescription = null,
			modifier = Modifier.height(70.dp),
		)

		Column(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = 10.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			Text(
				text = BuildConfig.VERSION_NAME,
				color = Color.White,
				fontSize = 12.sp,
			)
			Spacer(modifier = Modifier.height(5.dp))
			Text(
				text = stringResource(string.copyright),
				color = Gray1,
				fontSize = 10.sp,
			)
		}
	}
}
