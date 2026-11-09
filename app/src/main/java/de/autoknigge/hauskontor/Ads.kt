package de.autoknigge.hauskontor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Einwilligungsabfrage (Google UMP), Start-Anzeige und Banner am Fuß der App. */
class AdsController(private val activity: Activity) {
    var ready by mutableStateOf(false)
        private set
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    private var started = false
    private var initialized = false

    fun start() {
        if (started) return
        started = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (info.canRequestAds()) init()
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (info.canRequestAds()) init()
                }
            },
            { if (info.canRequestAds()) init() }
        )
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            if (UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) init()
        }
    }

    private fun init() {
        if (initialized) return
        initialized = true
        MobileAds.initialize(activity) { }
        ready = true
        if (!startAdHandled) {
            startAdHandled = true
            loadStartAd()
        }
    }

    /** Kurze Anzeige beim Start: einmal je App-Start, nur wenn sie schnell genug geladen ist. */
    private fun loadStartAd() {
        var timedOut = false
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ timedOut = true }, 4000)
        AppOpenAd.load(
            activity,
            BuildConfig.ADMOB_START_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    handler.removeCallbacksAndMessages(null)
                    if (timedOut || activity.isFinishing || activity.isDestroyed) return
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    handler.removeCallbacksAndMessages(null)
                }
            }
        )
    }

    companion object {
        private var startAdHandled = false
    }
}

/** Banner am Fuß. Der Platz ist fest reserviert, das Layout springt also nicht. */
@Composable
fun BannerBar(ads: AdsController) {
    val ctx = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    Box(Modifier.fillMaxWidth().height(60.dp)) {
        if (ads.ready) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    AdView(it).apply {
                        adUnitId = BuildConfig.ADMOB_BANNER_ID
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                        var retries = 0
                        val h = Handler(Looper.getMainLooper())
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                retries = 0
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                if (retries >= 3) return
                                val wait = 30_000L shl retries
                                retries++
                                h.postDelayed({ loadAd(AdRequest.Builder().build()) }, wait)
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}
